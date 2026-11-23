package dev.krishnagurram.budguard.tracking

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.pow

enum class Trend { WARMER, COLDER, STEADY }

data class RadarState(
    val scanning: Boolean = false,
    val rssi: Double? = null,
    val lastSeen: Long = 0L,
    val trend: Trend = Trend.STEADY,
    val history: List<Int> = emptyList(),
    val error: String? = null,
) {
    /** Very rough: Bluetooth signal bounces off walls, so this is a hint, not a ruler. */
    val meters: Double? get() = rssi?.let { Radar.distanceMeters(it) }
}

/** Pure math, unit-tested. */
object Radar {
    private const val TX_AT_1M = -59.0
    private const val PATH_LOSS = 2.2
    private const val ALPHA = 0.3

    fun smooth(previous: Double?, reading: Int): Double =
        if (previous == null) reading.toDouble() else previous + ALPHA * (reading - previous)

    fun distanceMeters(rssi: Double): Double = 10.0.pow((TX_AT_1M - rssi) / (10 * PATH_LOSS))

    fun trend(oldRssi: Double?, newRssi: Double): Trend = when {
        oldRssi == null -> Trend.STEADY
        newRssi - oldRssi >= 2.5 -> Trend.WARMER
        oldRssi - newRssi >= 2.5 -> Trend.COLDER
        else -> Trend.STEADY
    }
}

/**
 * Listens for the buds' Bluetooth LE advertisements and turns signal strength
 * into a warmer/colder reading. Only works while the buds are out of the case
 * or the lid is open; a closed case goes silent.
 */
class BudsRadar(private val context: Context) {

    private val _state = MutableStateFlow(RadarState())
    val state: StateFlow<RadarState> = _state

    private var targets: Set<String> = emptySet()
    private var baseline: Double? = null
    private var baselineTime = 0L

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = runCatching { result.device.address.uppercase() }.getOrNull() ?: return
            if (address !in targets) return
            val now = System.currentTimeMillis()
            val current = _state.value
            val smoothed = Radar.smooth(current.rssi, result.rssi)
            if (now - baselineTime > 3_000) {
                baseline = current.rssi
                baselineTime = now
            }
            _state.value = current.copy(
                rssi = smoothed,
                lastSeen = now,
                trend = Radar.trend(baseline, smoothed),
                history = (current.history + result.rssi).takeLast(12),
                error = null,
            )
        }

        override fun onScanFailed(errorCode: Int) {
            _state.value = _state.value.copy(scanning = false, error = "Scan failed (code $errorCode)")
        }
    }

    @SuppressLint("MissingPermission")
    fun start(addresses: Set<String>) {
        targets = addresses.map { it.uppercase() }.toSet()
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            _state.value = RadarState(error = "Bluetooth is off")
            return
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { scanner.startScan(null, settings, callback) }
            .onSuccess { _state.value = RadarState(scanning = true) }
            .onFailure { _state.value = RadarState(error = "Scan blocked: ${it.message}") }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
        runCatching { scanner?.stopScan(callback) }
        _state.value = _state.value.copy(scanning = false)
    }
}
