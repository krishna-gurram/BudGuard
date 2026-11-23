package dev.krishnagurram.budguard.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dev.krishnagurram.budguard.data.Prefs
import dev.krishnagurram.budguard.tracking.CompanionLink

data class SetupStatus(
    val bluetooth: Boolean,
    val location: Boolean,
    val backgroundLocation: Boolean,
    val notifications: Boolean,
    val budsName: String?,
    val budsAddress: String?,
    val linked: Boolean,
    val unrestricted: Boolean,
) {
    val coreReady: Boolean get() = bluetooth && location && backgroundLocation && budsAddress != null

    companion object {
        fun read(context: Context): SetupStatus {
            val prefs = Prefs(context)

            fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

            val power = context.getSystemService(PowerManager::class.java)
            return SetupStatus(
                bluetooth = has(Manifest.permission.BLUETOOTH_CONNECT),
                location = has(Manifest.permission.ACCESS_FINE_LOCATION),
                backgroundLocation = has(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                notifications = has(Manifest.permission.POST_NOTIFICATIONS),
                budsName = prefs.budsName,
                budsAddress = prefs.budsAddress,
                linked = CompanionLink.isLinked(context, prefs.budsAddress),
                unrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true,
            )
        }
    }
}
