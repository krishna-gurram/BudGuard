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

    data class Paired(val name: String, val address: String, val connected: Boolean, val kind: String)

    /** The phone's own paired-device list, connected devices first, then audio devices. */
    @SuppressLint("MissingPermission")
    fun bondedAudioFirst(context: Context): List<Paired> {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        val devices = runCatching { adapter.bondedDevices.toList() }.getOrDefault(emptyList())
        return devices
            .sortedWith(compareByDescending<BluetoothDevice> { isConnected(it) }.thenByDescending { isAudio(it) })
            .mapNotNull { d ->
                runCatching { Paired(d.name ?: d.address, d.address, isConnected(d), kind(d)) }.getOrNull()
            }
    }

    // Hidden but long-standing API; falls back to "not connected" if blocked.
    private fun isConnected(device: BluetoothDevice): Boolean =
        runCatching { device.javaClass.getMethod("isConnected").invoke(device) as Boolean }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    private fun kind(device: BluetoothDevice): String = when (runCatching { device.type }.getOrDefault(0)) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
        BluetoothDevice.DEVICE_TYPE_LE -> "LE Audio"
        BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
        else -> "Unknown"
    }

    @SuppressLint("MissingPermission")
    private fun isAudio(device: BluetoothDevice): Boolean =
        runCatching {
            device.bluetoothClass?.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO
        }.getOrDefault(false)

    /** Android's own battery value for the buds (hidden API), or null if unavailable. */
    @SuppressLint("MissingPermission")
    fun batteryLevel(context: Context, address: String?): Int? {
        if (address == null) return null
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        return runCatching {
            val device = adapter.getRemoteDevice(address)
            device.javaClass.getMethod("getBatteryLevel").invoke(device) as Int
        }.getOrNull()
    }

    fun systemBattery(context: Context, address: String?): String {
        if (address == null) return "no buds picked"
        val level = batteryLevel(context, address) ?: return "blocked"
        return if (level < 0) "not reported (-1)" else "$level%"
    }

    /** Every paired identity of the picked buds: the picked one plus same-named or "Buds" LE entries. */
    @SuppressLint("MissingPermission")
    fun budsAddresses(context: Context, picked: String?, pickedName: String?): Set<String> {
        val out = mutableSetOf<String>()
        if (picked != null) out += picked.uppercase()
        bondedAudioFirst(context)
            .filter { it.name == pickedName || it.name.contains("Buds", ignoreCase = true) }
            .forEach { out += it.address.uppercase() }
        return out
    }
}
