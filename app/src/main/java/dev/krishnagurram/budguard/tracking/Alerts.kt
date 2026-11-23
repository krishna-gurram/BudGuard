package dev.krishnagurram.budguard.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.krishnagurram.budguard.BudGuardApp
import dev.krishnagurram.budguard.R
import dev.krishnagurram.budguard.data.Format
import dev.krishnagurram.budguard.ui.MainActivity
import java.util.Locale

/** Every notification BudGuard posts. */
object Alerts {

    private const val ID_DISCONNECT = 1
    private const val ID_BATTERY = 2
    private const val ID_LEFT_BEHIND = 3

    fun disconnected(context: Context, time: Long, place: String?, location: Location?, moving: Boolean) {
        val where = place
            ?: location?.let { String.format(Locale.US, "%.5f, %.5f", it.latitude, it.longitude) }
            ?: "location unavailable"
        if (moving) {
            post(
                context,
                channel = BudGuardApp.CHANNEL_LEFT,
                id = ID_LEFT_BEHIND,
                title = "Buds went off while you're moving",
                text = "Check the seat before you get out. Saved: $where",
                lat = location?.latitude,
                lng = location?.longitude,
            )
        } else {
            post(
                context,
                channel = BudGuardApp.CHANNEL_LOG,
                id = ID_DISCONNECT,
                title = "Buds disconnected at ${Format.clock(time)}",
                text = "Saved: $where. You'll get an alert if you walk away without them.",
            )
        }
    }

    fun leftBehind(context: Context, place: String?, lat: Double, lng: Double, since: Long) {
        post(
            context,
            channel = BudGuardApp.CHANNEL_LEFT,
            id = ID_LEFT_BEHIND,
            title = "You left your buds behind",
            text = "${place ?: "Saved spot"}, ${Format.ago(System.currentTimeMillis(), since)}.",
            lat = lat,
            lng = lng,
        )
    }

    fun battery(context: Context, battery: Int?, place: String?) {
        post(
            context,
            channel = BudGuardApp.CHANNEL_BATTERY,
            id = ID_BATTERY,
            title = "Buds at ${battery ?: "?"}%, spot saved",
            text = place ?: "They may go dark soon. Charge them when you can.",
        )
    }

    fun clearLoss(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(ID_DISCONNECT)
        nm.cancel(ID_LEFT_BEHIND)
    }

    fun clearLeftBehind(context: Context) = NotificationManagerCompat.from(context).cancel(ID_LEFT_BEHIND)

    @SuppressLint("MissingPermission")
    private fun post(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        text: String,
        lat: Double? = null,
        lng: Double? = null,
    ) {
        if (!EventRecorder.granted(context, Manifest.permission.POST_NOTIFICATIONS)) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_buds)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)

        if (lat != null && lng != null) {
            val directions = PendingIntent.getActivity(
                context,
                1,
                Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng&mode=w"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val haveThem = PendingIntent.getBroadcast(
                context,
                2,
                Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_HAVE_THEM),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .addAction(0, "Directions", directions)
                .addAction(0, "I have them", haveThem)
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }
}
