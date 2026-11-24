package dev.krishnagurram.budguard.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dev.krishnagurram.budguard.data.EventSource
import dev.krishnagurram.budguard.data.EventStore
import dev.krishnagurram.budguard.data.EventType
import dev.krishnagurram.budguard.data.Prefs
import dev.krishnagurram.budguard.widget.LastSeenWidget
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Writes a settled event, attaches where the phone was, then arms or disarms alerts. */
object EventRecorder {

    private const val LOCATION_TIMEOUT_MS = 7_000L
    private const val GEOCODE_TIMEOUT_MS = 2_000L

    // Faster than a brisk run: the phone is in a vehicle.
    private const val MOVING_SPEED_MPS = 6f

    suspend fun record(
        context: Context,
        type: EventType,
        source: EventSource,
        time: Long = System.currentTimeMillis(),
        battery: Int? = null,
    ) {
        val store = EventStore.get(context)
        val prefs = Prefs(context)
        val id = store.insert(time, type, source, battery)

        val location = currentLocation(context)
        val moving = prefs.inVehicle || (location?.hasSpeed() == true && location.speed >= MOVING_SPEED_MPS)
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
        if (moving) store.markMoving(id)

        LastSeenWidget.refresh(context)

        when (type) {
            EventType.DISCONNECTED, EventType.DISAPPEARED -> {
                Alerts.disconnected(context, time, place, location, moving)
                if (LeftBehind.shouldArm(hasLocation = location != null, moving = moving)) {
                    LeftBehind.arm(context, location!!, place)
                } else if (moving) {
                    // Case closed in a moving car: they're in the car with you, not at a spot to walk away from.
                    prefs.fenceStatus = "Not armed: buds went off while driving"
                }
            }
            EventType.CONNECTED, EventType.APPEARED -> {
                LeftBehind.disarm(context)
                Alerts.clearLoss(context)
            }
            EventType.BATTERY -> Alerts.battery(context, battery, place)
            EventType.TAG_LEFT, EventType.TAG_BACK, EventType.TAG_BATTERY -> Unit
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Location? {
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

    suspend fun placeName(context: Context, location: Location): String? {
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

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
