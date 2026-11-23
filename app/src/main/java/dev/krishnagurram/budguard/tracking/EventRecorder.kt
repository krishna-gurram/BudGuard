package dev.krishnagurram.budguard.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dev.krishnagurram.budguard.BudGuardApp
import dev.krishnagurram.budguard.R
import dev.krishnagurram.budguard.data.EventSource
import dev.krishnagurram.budguard.data.EventStore
import dev.krishnagurram.budguard.data.EventType
import dev.krishnagurram.budguard.data.Format
import dev.krishnagurram.budguard.data.Prefs
import dev.krishnagurram.budguard.ui.MainActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Writes an event, then attaches where the phone was at that moment. */
object EventRecorder {

    private const val LOCATION_TIMEOUT_MS = 7_000L
    private const val GEOCODE_TIMEOUT_MS = 2_000L
    private const val NOTIFY_DEDUPE_MS = 90_000L

    suspend fun record(context: Context, type: EventType, source: EventSource, battery: Int? = null) {
        val store = EventStore.get(context)
        val now = System.currentTimeMillis()
        val id = store.insert(now, type, source, battery)

        val location = currentLocation(context)
        var place: String? = null
        if (location != null) {
            place = placeName(context, location)
            store.attachLocation(
                id = id,
                lat = location.latitude,
                lng = location.longitude,
                accuracyM = if (location.hasAccuracy()) location.accuracy else null,
                place = place,
            )
        }

        when (type) {
            EventType.DISCONNECTED, EventType.DISAPPEARED -> notifyLoss(context, now, place, location)
            EventType.BATTERY -> notifyBattery(context, battery, place)
            else -> Unit
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context): Location? {
        if (!granted(context, Manifest.permission.ACCESS_FINE_LOCATION) &&
            !granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        ) return null

        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        val fresh = runCatching {
            withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            }
        }.getOrNull()
        if (fresh != null) return fresh

        cts.cancel()
        return runCatching { client.lastLocation.await() }.getOrNull()
    }

    private suspend fun placeName(context: Context, location: Location): String? {
        if (!Geocoder.isPresent()) return null
        return withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                runCatching {
                    Geocoder(context, Locale.getDefault()).getFromLocation(
                        location.latitude,
                        location.longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull()?.let(::label))
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        },
                    )
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
    }

    private fun label(address: Address): String? {
        val street = listOfNotNull(address.subThoroughfare, address.thoroughfare).joinToString(" ")
        return listOf(street, address.locality.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .ifBlank { address.featureName }
    }

    private fun notifyLoss(context: Context, now: Long, place: String?, location: Location?) {
        val prefs = Prefs(context)
        // Bluetooth and the Android link can both report the same disconnect.
        if (now - prefs.lastLossNotifyTime < NOTIFY_DEDUPE_MS) return
        prefs.lastLossNotifyTime = now

        val where = place
            ?: location?.let { String.format(Locale.US, "%.5f, %.5f", it.latitude, it.longitude) }
            ?: "location unavailable"
        post(
            context,
            channel = BudGuardApp.CHANNEL_LEFT,
            id = 1,
            title = "Buds disconnected at ${Format.clock(now)}",
            text = "Saved: $where",
        )
    }

    private fun notifyBattery(context: Context, battery: Int?, place: String?) {
        post(
            context,
            channel = BudGuardApp.CHANNEL_BATTERY,
            id = 2,
            title = "Buds at ${battery ?: "?"}%, spot saved",
            text = place ?: "They may go dark soon. Charge them when you can.",
        )
    }

    @SuppressLint("MissingPermission")
    private fun post(context: Context, channel: String, id: Int, title: String, text: String) {
        if (!granted(context, Manifest.permission.POST_NOTIFICATIONS)) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_buds)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
