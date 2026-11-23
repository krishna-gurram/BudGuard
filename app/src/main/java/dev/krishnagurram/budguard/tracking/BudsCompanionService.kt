package dev.krishnagurram.budguard.tracking

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dev.krishnagurram.budguard.data.EventSource
import dev.krishnagurram.budguard.data.EventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android binds this while the linked buds are nearby and wakes the app when
 * they appear or vanish. While bound, it also listens for battery reports.
 */
class BudsCompanionService : CompanionDeviceService() {

    // Not cancelled in onDestroy: a disappear event must finish saving its location.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            BluetoothReceiver.handleBatteryIntent(context, intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(BluetoothReceiver.ACTION_BATTERY_LEVEL_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(batteryReceiver) }
        super.onDestroy()
    }

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        Presence.onRaw(applicationContext, present = true, source = EventSource.ANDROID_LINK)
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        Presence.onRaw(applicationContext, present = false, source = EventSource.ANDROID_LINK)
    }
}
