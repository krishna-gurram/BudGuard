package dev.krishnagurram.budguard.tracking

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.krishnagurram.budguard.data.EventSource
import dev.krishnagurram.budguard.data.EventType
import dev.krishnagurram.budguard.data.Format
import dev.krishnagurram.budguard.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hears the buds connect, disconnect and report battery, even with the app closed. */
class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        val prefs = Prefs(context)
        if (!prefs.isBuds(addressOf(device))) return

        val pending = goAsync()
        scope.launch {
            try {
                handle(context.applicationContext, intent, prefs)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent, prefs: Prefs) {
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED ->
                Presence.onRaw(context, present = true, source = EventSource.BLUETOOTH)

            BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                Presence.onRaw(context, present = false, source = EventSource.BLUETOOTH)

            ACTION_BATTERY_LEVEL_CHANGED -> {
                val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                onBattery(context, prefs, level)
            }
        }
    }

    companion object {
        // Hidden in the public SDK but sent by the system for headsets that report battery.
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @SuppressLint("MissingPermission")
        fun addressOf(device: BluetoothDevice): String? = runCatching { device.address }.getOrNull()

        /** Shared by the manifest receiver, the companion service and the open app. */
        suspend fun onBattery(context: Context, prefs: Prefs, level: Int, countReport: Boolean = true) {
            if (level < 0) return
            val previous = prefs.lastBattery
            if (countReport) prefs.batteryBroadcasts = prefs.batteryBroadcasts + 1
            prefs.lastBattery = level
            prefs.lastBatteryTime = System.currentTimeMillis()

            val crossed = Format.crossedThreshold(previous = if (previous < 0) 100 else previous, current = level)
            if (crossed != null) {
                EventRecorder.record(context, EventType.BATTERY, EventSource.BATTERY_BROADCAST, battery = level)
            }
        }

        fun handleBatteryIntent(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
            val prefs = Prefs(context)
            if (!prefs.isBuds(addressOf(device))) return
            val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
            scope.launch { onBattery(context.applicationContext, prefs, level) }
        }
    }
}
