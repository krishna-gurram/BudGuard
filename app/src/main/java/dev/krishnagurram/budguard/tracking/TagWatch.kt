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
 * check, when you start driving, every 15 minutes, and when you open the app.
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

    /** A second look in 2 minutes, to confirm a possible separation before alerting. */
    fun confirmSoon(context: Context) {
        val work = OneTimeWorkRequestBuilder<TagCheckWorker>()
            .setInitialDelay(2, TimeUnit.MINUTES)
            .setInputData(workDataOf("reason" to "confirming a separation"))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("tag_confirm", ExistingWorkPolicy.KEEP, work)
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

        // Samsung first, then the phone, so the two snapshots are as close in time as possible.
        val statuses = tags.associate { it.id to SamsungFind.status(session, it, refresh = true).getOrNull() }
        val phone = EventRecorder.currentLocation(context)
        if (phone != null) armMoveFence(context, phone, prefs.radiusM)
        val places = ItemStore.safePlaces(context)
        val now = System.currentTimeMillis()
        val speed = when {
            phone?.hasSpeed() == true -> phone.speed.toDouble()
            prefs.inVehicle -> 30.0
            else -> 1.5
        }
        val notes = mutableListOf<String>()
        var needConfirm = false

        for (device in tags) {
            val item = ItemStore.items(context).firstOrNull { it.id == device.id } ?: continue
            val status = statuses[device.id]
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
                    phoneTime = phone.time,
                    phoneSpeedMps = speed,
                )
                var next = verdict.state
                var why = ""
                if (next == TagState.LEFT_BEHIND) {
                    if (!updated.isPending) {
                        // Looks separated: hold that thought and look again in 2 minutes.
                        updated = updated.copy(pendingLat = updated.lat, pendingLng = updated.lng, pendingFixTime = updated.fixTime, pendingChecks = 0)
                        next = item.state
                        needConfirm = true
                        why = ", confirming in 2 min"
                    } else {
                        when (TagRules.confirm(updated.pendingLat!!, updated.pendingLng!!, updated.pendingFixTime, updated, updated.pendingChecks)) {
                            TagRules.Confirm.LEFT -> {
                                updated = updated.clearPending()
                                why = ", confirmed"
                            }
                            TagRules.Confirm.MOVED -> {
                                updated = updated.clearPending()
                                next = TagState.WITH_YOU
                                why = ", it moved with you"
                            }
                            TagRules.Confirm.WAIT -> {
                                updated = updated.copy(pendingChecks = updated.pendingChecks + 1)
                                next = item.state
                                needConfirm = true
                                why = ", no new report yet"
                            }
                        }
                    }
                } else if (next != TagState.UNKNOWN) {
                    updated = updated.clearPending()
                } else if (updated.isPending) {
                    needConfirm = true
                }

                val alert = prefs.tagAlertsOn && TagRules.shouldAlert(item.state, next, item.alertsOn)
                if (next != TagState.UNKNOWN && next != item.state) {
                    updated = updated.copy(state = next, stateSince = now, safePlace = if (next == TagState.LEFT_SAFE) verdict.safePlace else null)
                    logState(context, updated.label, next, updated)
                }
                if (alert) Alerts.tagLeftBehind(context, updated, verdict.distanceM)
                if (next == TagState.WITH_YOU) Alerts.clearTag(context, updated.id)
                notes += "${updated.label}: ${next.name.lowercase()}${verdict.distanceM?.let { " (${dev.krishnagurram.budguard.data.Geo.distanceText(it)})" } ?: ""}$why"
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
        if (needConfirm) confirmSoon(context)
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
        LocationServices.getGeofencingClient(context).addGeofences(request, pending)
            .addOnSuccessListener { prefs.moveFenceStatus = "Armed ${Format.clock(System.currentTimeMillis())}" }
            .addOnFailureListener { prefs.moveFenceStatus = "Failed: ${it.message}" }
    }

    /** Marks a tag as with you after you tap "I have it". */
    fun haveIt(context: Context, id: String) {
        val now = System.currentTimeMillis()
        ItemStore.update(context, id) { it.copy(state = TagState.WITH_YOU, stateSince = now).clearPending() }
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
        TagWatch.checkSoon(context, "you moved ${dev.krishnagurram.budguard.data.Geo.distanceText(Prefs(context).radiusM.toDouble())}")
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
