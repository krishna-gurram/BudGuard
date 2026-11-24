package dev.krishnagurram.budguard.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.krishnagurram.budguard.data.ItemStore
import dev.krishnagurram.budguard.data.Prefs
import dev.krishnagurram.budguard.data.TagItem
import dev.krishnagurram.budguard.data.TagState

/**
 * When the buds drop, fence the spot. Walking out of the fence while the buds
 * are still gone means they were left behind.
 *
 * A closed case goes silent, so "gone" can also mean "in your pocket". Two guards:
 * no fence when the buds go off while you're driving (they're in the car), and
 * if a tag rides on the case, ask it before alerting.
 */
object LeftBehind {

    const val RADIUS_M = 150f
    const val WATCH_MS = 8L * 60 * 60 * 1000 // stop watching after 8 hours
    private const val FENCE_ID = "buds"

    /** Pure decision, unit-tested: should a fence exit raise the alert? */
    fun shouldAlert(budsGone: Boolean, armed: Boolean, now: Long, expiresAt: Long): Boolean =
        budsGone && armed && now < expiresAt

    /** Pure decision, unit-tested: fence the spot only if we know it and you're standing there. */
    fun shouldArm(hasLocation: Boolean, moving: Boolean): Boolean = hasLocation && !moving

    /** Pure decision, unit-tested: what the case tags say about the buds. */
    fun caseSays(caseTags: List<TagState>): CaseVerdict = when {
        caseTags.isEmpty() -> CaseVerdict.NO_CASE_TAG
        caseTags.any { it == TagState.WITH_YOU } -> CaseVerdict.WITH_YOU
        else -> CaseVerdict.NOT_WITH_YOU
    }

    enum class CaseVerdict { NO_CASE_TAG, WITH_YOU, NOT_WITH_YOU }

    fun isCaseTag(item: TagItem): Boolean = item.icon == "case"

    @SuppressLint("MissingPermission")
    fun arm(context: Context, location: Location, place: String?) {
        val prefs = Prefs(context)
        if (!prefs.leftBehindOn) {
            prefs.fenceStatus = "Off in Alerts settings"
            return
        }
        prefs.fenceLat = location.latitude
        prefs.fenceLng = location.longitude
        prefs.fencePlace = place
        prefs.fenceSince = System.currentTimeMillis()
        prefs.fenceExpiresAt = prefs.fenceSince + WATCH_MS
        prefs.fenceArmed = true
        register(context)
    }

    /** Re-adds the fence after a reboot, if one is still active. */
    fun rearm(context: Context) {
        val prefs = Prefs(context)
        if (prefs.fenceArmed && System.currentTimeMillis() < prefs.fenceExpiresAt) register(context)
    }

    @SuppressLint("MissingPermission")
    private fun register(context: Context) {
        val prefs = Prefs(context)
        if (!EventRecorder.granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            prefs.fenceStatus = "Needs location all the time"
            return
        }
        val remaining = (prefs.fenceExpiresAt - System.currentTimeMillis()).coerceAtLeast(60_000)
        val fence = Geofence.Builder()
            .setRequestId(FENCE_ID)
            .setCircularRegion(prefs.fenceLat, prefs.fenceLng, prefs.radiusM.toFloat())
            .setExpirationDuration(remaining)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .setNotificationResponsiveness(30_000)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(fence)
            .build()
        LocationServices.getGeofencingClient(context)
            .addGeofences(request, pendingIntent(context))
            .addOnSuccessListener { prefs.fenceStatus = "Armed" }
            .addOnFailureListener { prefs.fenceStatus = "Failed: ${it.message}" }
    }

    fun disarm(context: Context) {
        val prefs = Prefs(context)
        if (!prefs.fenceArmed) return
        prefs.fenceArmed = false
        prefs.fenceStatus = "Off (buds with you)"
        runCatching { LocationServices.getGeofencingClient(context).removeGeofences(listOf(FENCE_ID)) }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            10,
            Intent(context, FenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
}

class FenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_EXIT) return

        val prefs = Prefs(context)
        val budsGone = prefs.recordedState == Presence.LOST
        val alert = LeftBehind.shouldAlert(budsGone, prefs.fenceArmed, System.currentTimeMillis(), prefs.fenceExpiresAt)
        LeftBehind.disarm(context)
        if (!alert) {
            prefs.fenceStatus = "Fired, buds were with you"
            return
        }
        if (ItemStore.items(context).none(LeftBehind::isCaseTag)) {
            prefs.leftBehindAlerts = prefs.leftBehindAlerts + 1
            Alerts.leftBehind(context, prefs.fencePlace, prefs.fenceLat, prefs.fenceLng, prefs.fenceSince)
            prefs.fenceStatus = "Fired, then off"
            return
        }
        prefs.fenceStatus = "Fired, asking the case tag"
        WorkManager.getInstance(context).enqueueUniqueWork(
            CaseCheckWorker.NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CaseCheckWorker>().build(),
        )
    }
}

/** Refreshes the case tag, then alerts only if it isn't with you. */
class CaseCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val prefs = Prefs(context)
        TagWatch.check(context, "buds fence fired")
        if (prefs.recordedState != Presence.LOST) {
            prefs.fenceStatus = "Buds came back during the check"
            return Result.success()
        }
        val verdict = LeftBehind.caseSays(ItemStore.items(context).filter(LeftBehind::isCaseTag).map { it.state })
        if (verdict == LeftBehind.CaseVerdict.WITH_YOU) {
            prefs.fenceStatus = "Case tag is with you; no alert"
        } else {
            prefs.leftBehindAlerts = prefs.leftBehindAlerts + 1
            Alerts.leftBehind(context, prefs.fencePlace, prefs.fenceLat, prefs.fenceLng, prefs.fenceSince)
            prefs.fenceStatus = "Case tag not with you; alerted"
        }
        return Result.success()
    }

    companion object {
        const val NAME = "case_check"
    }
}

/** Handles the "I have them" button on alerts. */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_HAVE_THEM) {
            LeftBehind.disarm(context)
            Alerts.clearLeftBehind(context)
        }
    }

    companion object {
        const val ACTION_HAVE_THEM = "dev.krishnagurram.budguard.HAVE_THEM"
    }
}
