package dev.krishnagurram.budguard.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import dev.krishnagurram.budguard.data.Prefs

/** Knows when you're in a car, so a disconnect then means "check the seat". */
object Driving {

    @SuppressLint("MissingPermission")
    fun register(context: Context) {
        val prefs = Prefs(context)
        if (!EventRecorder.granted(context, Manifest.permission.ACTIVITY_RECOGNITION)) {
            prefs.drivingStatus = "Needs physical activity permission"
            return
        }
        val transitions = listOf(
            ActivityTransition.ACTIVITY_TRANSITION_ENTER,
            ActivityTransition.ACTIVITY_TRANSITION_EXIT,
        ).map {
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(it)
                .build()
        }
        ActivityRecognition.getClient(context)
            .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pendingIntent(context))
            .addOnSuccessListener { prefs.drivingStatus = "Watching" }
            .addOnFailureListener { prefs.drivingStatus = "Failed: ${it.message}" }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            20,
            Intent(context, DrivingReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
}

class DrivingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val last = result.transitionEvents.lastOrNull { it.activityType == DetectedActivity.IN_VEHICLE } ?: return
        val prefs = Prefs(context)
        prefs.inVehicle = last.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
        TagWatch.checkSoon(context, if (prefs.inVehicle) "you started driving" else "you stopped driving")
        prefs.drivingChanges = prefs.drivingChanges + 1
    }
}

/** Geofences and activity watching are cleared on reboot; put them back. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Driving.register(context)
        LeftBehind.rearm(context)
        BatteryWatch.schedule(context)
        TagWatch.schedule(context)
        CompanionLink.observe(context, Prefs(context).budsAddress)
    }
}
