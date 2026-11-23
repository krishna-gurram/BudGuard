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
import dev.krishnagurram.budguard.data.Prefs

/**
 * When the buds drop, fence the spot. Walking out of the fence while the buds
 * are still gone means they were left behind.
 */
object LeftBehind {

    const val RADIUS_M = 150f
    const val WATCH_MS = 8L * 60 * 60 * 1000 // stop watching after 8 hours
    private const val FENCE_ID = "buds"

    /** Pure decision, unit-tested: should a fence exit raise the alert? */
    fun shouldAlert(budsGone: Boolean, armed: Boolean, now: Long, expiresAt: Long): Boolean =
        budsGone && armed && now < expiresAt

    @SuppressLint("MissingPermission")
    fun arm(context: Context, location: Location, place: String?) {
        val prefs = Prefs(context)
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
            .setCircularRegion(prefs.fenceLat, prefs.fenceLng, RADIUS_M)
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
        if (LeftBehind.shouldAlert(budsGone, prefs.fenceArmed, System.currentTimeMillis(), prefs.fenceExpiresAt)) {
            prefs.leftBehindAlerts = prefs.leftBehindAlerts + 1
            Alerts.leftBehind(context, prefs.fencePlace, prefs.fenceLat, prefs.fenceLng, prefs.fenceSince)
        }
        LeftBehind.disarm(context)
        prefs.fenceStatus = "Fired, then off"
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
