package dev.krishnagurram.budguard.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dev.krishnagurram.budguard.data.Crumb
import dev.krishnagurram.budguard.data.EventSource
import dev.krishnagurram.budguard.data.EventStore
import dev.krishnagurram.budguard.data.EventType
import dev.krishnagurram.budguard.data.Format
import dev.krishnagurram.budguard.data.ItemStore
import dev.krishnagurram.budguard.data.Prefs
import dev.krishnagurram.budguard.data.TagRules
import dev.krishnagurram.budguard.data.TagState
import dev.krishnagurram.budguard.widget.LastSeenWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Keeps your tags with you. A check asks Samsung where each tag is and compares
 * it with where your phone is. Checks run when you move 150 m from the last
 * check, when you start or stop driving, every 15 minutes, and when you open the app.
 *
 * Every check also drops a crumb of where the phone is, and leaving the 150 m
 * fence drops one for the spot you just left. Samsung's tag spot lags behind a
 * moving car, so a far spot only counts if Samsung saw the tag there after you left.
 */
object TagWatch {

    private const val ONE_SHOT = "tag_check"
    private const val PERIODIC = "tag_watch"
    private const val MOVE_FENCE = "phone_moved"

    fun schedule(context: Context) {
        val work = PeriodicWorkRequestBuilder<TagCheckWorker>(15, TimeUnit.MINUTES)
            .setInputData(workDataOf("reason" to "every 15 min"))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, work)
    }

    fun checkSoon(context: Context, reason: String) {
        val work = OneTimeWorkRequestBuilder<TagCheckWorker>()
            .setInputData(workDataOf("reason" to reason))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.REPLACE, work)
    }

    /** One full check. Returns a short line for Diagnostics. */
    suspend fun check(context: Context, reason: String): String {
        val prefs = Prefs(context)
        prefs.tagCheckTime = System.currentTimeMillis()
        prefs.tagCheckReason = reason
        val result = runCatching { runCheck(context, prefs) }.getOrElse { "Failed: ${it.message}" }
        prefs.tagCheckResult = result
        LastSeenWidget.refresh(context)
        return result
    }

    private suspend fun runCheck(context: Context, prefs: Prefs): String {
        if (!SamsungFind.isSignedIn()) return "Not signed in to Samsung"
        val session = SamsungFind.connect().getOrElse { return "Samsung session expired; sign in again in Tag Lab" }
        val devices = SamsungFind.devices(session).getOrElse { return "Couldn't list devices: ${it.message}" }
        val tags = devices.filter { it.isTag }
        ItemStore.merge(context, tags.map { Triple(it.id, it.userId, it.name) })
        if (tags.isEmpty()) return "No tags on your Samsung account"

        val phone = EventRecorder.currentLocation(context)
        val now = System.currentTimeMillis()
        if (phone != null) {
            val acc = if (phone.hasAccuracy()) phone.accuracy.toDouble() else 30.0
            ItemStore.addCrumb(context, Crumb(phone.latitude, phone.longitude, acc, now))
            armMoveFence(context, phone, prefs.radiusM)
        }
        val trail = ItemStore.trail(context)
        val places = ItemStore.safePlaces(context)
        val notes = mutableListOf<String>()

        for (device in tags) {
            val item = ItemStore.items(context).firstOrNull { it.id == device.id } ?: continue
            val status = SamsungFind.status(session, device, refresh = true).getOrNull()
            var updated = item.copy(battery = status?.battery ?: item.battery)
            val fix = status?.fix
            if (fix != null && fix.time >= item.fixTime) {
                val sameSpot = item.hasFix && TagRules.distanceM(item.lat!!, item.lng!!, fix.lat, fix.lng) < 30
                val place = if (sameSpot) item.place else placeFor(context, fix.lat, fix.lng)
                updated = updated.copy(lat = fix.lat, lng = fix.lng, accuracyM = fix.accuracyM, fixTime = fix.time, place = place)
            }

            if (phone != null) {
                val verdict = TagRules.decide(
                    phoneLat = phone.latitude,
                    phoneLng = phone.longitude,
                    phoneAccuracyM = if (phone.hasAccuracy()) phone.accuracy.toDouble() else 30.0,
                    tag = updated,
                    radiusM = prefs.radiusM,
                    places = places,
                    now = now,
                    refreshed = fix != null && now - fix.time < 5 * 60_000,
                    trail = trail,
                )
                val alert = prefs.tagAlertsOn && TagRules.shouldAlert(item.state, verdict.state, item.alertsOn)
                if (verdict.state != TagState.UNKNOWN && verdict.state != item.state) {
                    updated = updated.copy(state = verdict.state, stateSince = now, safePlace = verdict.safePlace)
                    logState(context, updated.label, verdict.state, updated)
                }
                if (alert) Alerts.tagLeftBehind(context, updated, verdict.distanceM)
                if (verdict.state == TagState.WITH_YOU) Alerts.clearTag(context, updated.id)
                val state = if (verdict.trailing) "riding along (Samsung's spot is from before you left)" else verdict.state.name.lowercase()
                notes += "${updated.label}: $state${verdict.distanceM?.let { " (${it.toInt()} m)" } ?: ""}"
            } else {
                notes += "${updated.label}: no phone location"
            }

            if (prefs.tagBatteryOn && TagRules.shouldWarnBattery(updated.battery, updated.lowBatteryWarned)) {
                Alerts.tagBattery(context, updated)
                EventStore.get(context).insert(now, EventType.TAG_BATTERY, EventSource.SAMSUNG, updated.battery, updated.label)
                updated = updated.copy(lowBatteryWarned = true)
            } else if ((updated.battery ?: 0) > 15 && updated.lowBatteryWarned) {
                updated = updated.copy(lowBatteryWarned = false)
            }
            val saved = updated
            ItemStore.update(context, item.id) { saved }
        }
        return notes.joinToString("; ")
    }

    private suspend fun placeFor(context: Context, lat: Double, lng: Double): String? {
        val loc = Location("samsung").apply {
            latitude = lat
            longitude = lng
        }
        return EventRecorder.placeName(context, loc)
    }

    private fun logState(context: Context, label: String, state: TagState, item: dev.krishnagurram.budguard.data.TagItem) {
        val type = when (state) {
            TagState.LEFT_BEHIND, TagState.LEFT_SAFE -> EventType.TAG_LEFT
            TagState.WITH_YOU -> EventType.TAG_BACK
            TagState.UNKNOWN -> return
        }
        val store = EventStore.get(context)
        val id = store.insert(System.currentTimeMillis(), type, EventSource.SAMSUNG, null, label)
        val lat = item.lat
        val lng = item.lng
        if (lat != null && lng != null) {
            store.attachLocation(id, lat, lng, item.accuracyM?.toFloat(), item.safePlace ?: item.place)
        }
    }

    /** A fence around where the phone is now; leaving it triggers the next check right away. */
    @SuppressLint("MissingPermission")
    private fun armMoveFence(context: Context, at: Location, radiusM: Int) {
        val prefs = Prefs(context)
        if (!EventRecorder.granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            prefs.moveFenceStatus = "Needs location all the time"
            return
        }
        val fence = Geofence.Builder()
            .setRequestId(MOVE_FENCE)
            .setCircularRegion(at.latitude, at.longitude, radiusM.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .setNotificationResponsiveness(30_000)
            .build()
        val request = GeofencingRequest.Builder().setInitialTrigger(0).addGeofence(fence).build()
        val pending = PendingIntent.getBroadcast(
            context,
            11,
            Intent(context, MoveFenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        prefs.moveFenceLat = at.latitude
        prefs.moveFenceLng = at.longitude
        prefs.moveFenceRadius = radiusM
        LocationServices.getGeofencingClient(context).addGeofences(request, pending)
            .addOnSuccessListener { prefs.moveFenceStatus = "Armed ${Format.clock(System.currentTimeMillis())}" }
            .addOnFailureListener { prefs.moveFenceStatus = "Failed: ${it.message}" }
    }

    /** Marks a tag as with you after you tap "I have it". */
    fun haveIt(context: Context, id: String) {
        val now = System.currentTimeMillis()
        ItemStore.update(context, id) { it.copy(state = TagState.WITH_YOU, stateSince = now) }
        Alerts.clearTag(context, id)
        LastSeenWidget.refresh(context)
    }
}

class TagCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        TagWatch.check(applicationContext, inputData.getString("reason") ?: "scheduled")
        return Result.success()
    }
}

class MoveFenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_EXIT) return
        val prefs = Prefs(context)
        // You were inside this fence until now: that pins down when you left the spot.
        if (prefs.moveFenceRadius > 0) {
            ItemStore.addCrumb(
                context,
                Crumb(prefs.moveFenceLat, prefs.moveFenceLng, prefs.moveFenceRadius.toDouble(), System.currentTimeMillis()),
            )
        }
        TagWatch.checkSoon(context, "you moved ${prefs.radiusM} m")
    }
}

/** Buttons on the tag alert: Ring and I have it. */
class TagActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        when (intent.action) {
            ACTION_HAVE_IT -> TagWatch.haveIt(context, id)
            ACTION_RING -> {
                val pending = goAsync()
                scope.launch {
                    try {
                        val session = SamsungFind.connect().getOrNull()
                        val device = session?.let { s -> SamsungFind.devices(s).getOrNull()?.firstOrNull { it.id == id } }
                        if (session != null && device != null) SamsungFind.ring(session, device, start = true)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_HAVE_IT = "dev.krishnagurram.budguard.TAG_HAVE_IT"
        const val ACTION_RING = "dev.krishnagurram.budguard.TAG_RING"
        const val EXTRA_ID = "id"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
