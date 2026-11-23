package dev.krishnagurram.budguard.tracking

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender

/** Wraps the Android "companion device" link that wakes BudGuard for the buds. */
object CompanionLink {

    private fun cdm(context: Context): CompanionDeviceManager? =
        context.getSystemService(CompanionDeviceManager::class.java)

    fun association(context: Context, address: String?): AssociationInfo? {
        if (address == null) return null
        return runCatching {
            cdm(context)?.myAssociations?.firstOrNull {
                it.deviceMacAddress?.toString()?.equals(address, ignoreCase = true) == true
            }
        }.getOrNull()
    }

    fun isLinked(context: Context, address: String?): Boolean = association(context, address) != null

    fun link(
        context: Context,
        address: String,
        onApprovalNeeded: (IntentSender) -> Unit,
        onResult: (String) -> Unit,
    ) {
        val manager = cdm(context) ?: return onResult("This phone doesn't support companion devices")
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build())
            .setSingleDevice(true)
            .build()
        runCatching {
            manager.associate(
                request,
                context.mainExecutor,
                object : CompanionDeviceManager.Callback() {
                    override fun onAssociationPending(intentSender: IntentSender) = onApprovalNeeded(intentSender)

                    override fun onAssociationCreated(associationInfo: AssociationInfo) {
                        val observing = observe(context, address)
                        onResult(if (observing) "Linked. Android will wake BudGuard for your buds." else "Linked, but presence watching failed")
                    }

                    override fun onFailure(error: CharSequence?) = onResult("Link failed: ${error ?: "unknown"}")
                },
            )
        }.onFailure { onResult("Link failed: ${it.message}") }
    }

    /** Safe to call on every app start; it re-arms presence watching. */
    @Suppress("DEPRECATION")
    fun observe(context: Context, address: String?): Boolean {
        if (address == null || !isLinked(context, address)) return false
        return runCatching { cdm(context)?.startObservingDevicePresence(address); true }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun bondedAudioFirst(context: Context): List<Pair<String, String>> {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        val devices = runCatching { adapter.bondedDevices.toList() }.getOrDefault(emptyList())
        return devices
            .sortedByDescending { isAudio(it) }
            .mapNotNull { d -> runCatching { (d.name ?: d.address) to d.address }.getOrNull() }
    }

    @SuppressLint("MissingPermission")
    private fun isAudio(device: BluetoothDevice): Boolean =
        runCatching {
            device.bluetoothClass?.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO
        }.getOrDefault(false)

    /** Reads Android's own battery value for the buds through a hidden API, for diagnostics. */
    @SuppressLint("MissingPermission")
    fun systemBattery(context: Context, address: String?): String {
        if (address == null) return "no buds picked"
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return "no Bluetooth"
        return runCatching {
            val device = adapter.getRemoteDevice(address)
            val level = device.javaClass.getMethod("getBatteryLevel").invoke(device) as Int
            if (level < 0) "not reported (-1)" else "$level%"
        }.getOrElse { "blocked (${it.javaClass.simpleName})" }
    }
}
