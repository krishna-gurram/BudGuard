package dev.krishnagurram.budguard.tracking

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileWriter

/** One Bluetooth address that broadcast a Samsung tag signal. */
data class TagSighting(
    val address: String,
    val service: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val count: Int,
    val rssi: Double,
    val minRssi: Int,
    val maxRssi: Int,
    val dataHex: String,
    val dataChanges: Int,
)

/** Pure helpers, unit-tested. */
object TagLab {

    const val FD5A = "FD5A"
    const val FD59 = "FD59"
    const val FRESH_MS = 10_000L

    fun serviceName(uuid: String): String? {
        val u = uuid.lowercase()
        return when {
            u.startsWith("0000fd5a") -> FD5A
            u.startsWith("0000fd59") -> FD59
            else -> null
        }
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }

    fun update(old: TagSighting?, address: String, service: String, rssi: Int, data: String, now: Long): TagSighting =
        if (old == null) {
            TagSighting(address, service, now, now, 1, rssi.toDouble(), rssi, rssi, data, 0)
        } else {
            old.copy(
                lastSeen = now,
                count = old.count + 1,
                rssi = Radar.smooth(old.rssi, rssi),
                minRssi = minOf(old.minRssi, rssi),
                maxRssi = maxOf(old.maxRssi, rssi),
                dataHex = data,
                dataChanges = old.dataChanges + if (data != old.dataHex) 1 else 0,
            )
        }

    /** The strongest tag heard recently and steadily: most likely the one in your hand. */
    fun likelyYours(all: Collection<TagSighting>, now: Long): String? =
        all.filter { now - it.lastSeen < FRESH_MS && it.count >= 5 }.maxByOrNull { it.rssi }?.address

    fun label(s: TagSighting, likely: String?, now: Long): String = when {
        s.address == likely -> "Likely yours"
        now - s.lastSeen >= FRESH_MS -> "Gone quiet"
        s.service == FD59 -> "Unregistered tag"
        s.rssi > -65 -> "Near, maybe your case tag"
        else -> "Probably a stranger's"
    }

    fun summary(all: Collection<TagSighting>, now: Long): String {
        if (all.isEmpty()) return "No Samsung tag broadcasts heard."
        val likely = likelyYours(all, now)
        return all.sortedByDescending { it.count }.take(10).joinToString("\n") { s ->
            val secs = (s.lastSeen - s.firstSeen) / 1000
            "${s.service} …${s.address.takeLast(5)} · ${s.count} seen over ${secs}s · " +
                "rssi ${s.minRssi}..${s.maxRssi} (now ${s.rssi.toInt()}) · data changed ${s.dataChanges}x · " +
                "quiet ${(now - s.lastSeen) / 1000}s · ${label(s, likely, now)}"
        }
    }
}

/**
 * Listens for Samsung tag broadcasts (service data FD5A for registered tags,
 * FD59 for unregistered) and optionally records every one to a CSV file.
 */
class TagScanner(private val context: Context) {

    private val _sightings = MutableStateFlow<Map<String, TagSighting>>(emptyMap())
    val sightings: StateFlow<Map<String, TagSighting>> = _sightings

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private var writer: FileWriter? = null
    var recordingFile: File? = null
        private set
    var recordedRows = 0
        private set

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result)

        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::handle)

        override fun onScanFailed(errorCode: Int) {
            _scanning.value = false
        }
    }

    @Synchronized
    private fun handle(result: ScanResult) {
        val data: Map<ParcelUuid, ByteArray> = result.scanRecord?.serviceData ?: return
        val entry = data.entries.firstOrNull { TagLab.serviceName(it.key.toString()) != null } ?: return
        val service = TagLab.serviceName(entry.key.toString()) ?: return
        val address = runCatching { result.device.address }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        val hex = TagLab.hex(entry.value)

        val map = _sightings.value.toMutableMap()
        map[address] = TagLab.update(map[address], address, service, result.rssi, hex, now)
        _sightings.value = map

        writer?.let {
            runCatching {
                it.write("$now,$address,$service,${result.rssi},$hex\n")
                recordedRows++
                if (recordedRows % 50 == 0) it.flush()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: return
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { scanner.startScan(null, settings, callback) }.onSuccess { _scanning.value = true }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
        runCatching { scanner?.stopScan(callback) }
        _scanning.value = false
        stopRecording()
    }

    @Synchronized
    fun startRecording(): File {
        stopRecording()
        val dir = File(context.filesDir, "taglab").apply { mkdirs() }
        val file = File(dir, "taglab-${System.currentTimeMillis()}.csv")
        writer = FileWriter(file).apply { write("time_ms,address,service,rssi,data\n") }
        recordingFile = file
        recordedRows = 0
        _sightings.value = emptyMap()
        return file
    }

    @Synchronized
    fun stopRecording() {
        runCatching { writer?.flush(); writer?.close() }
        writer = null
    }

    val isRecording: Boolean get() = writer != null

    companion object {
        /** Devices any app on the phone holds a live Bluetooth LE link to (Samsung's app may hold one to your tags). */
        @SuppressLint("MissingPermission")
        fun linkedNow(context: Context): List<String> {
            val manager = context.getSystemService(BluetoothManager::class.java) ?: return emptyList()
            return runCatching {
                manager.getConnectedDevices(BluetoothProfile.GATT).map { d ->
                    "${runCatching { d.name }.getOrNull() ?: "Unnamed"} · …${d.address.takeLast(5)}"
                }
            }.getOrDefault(emptyList())
        }

        @SuppressLint("MissingPermission")
        fun pairedTags(context: Context): List<String> =
            CompanionLink.bondedAudioFirst(context)
                .filter { it.name.contains("tag", ignoreCase = true) }
                .map { "${it.name} · ${it.kind} · …${it.address.takeLast(5)}" }
    }
}
