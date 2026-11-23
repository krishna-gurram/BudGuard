package dev.krishnagurram.budguard.tracking

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Makes the buds beep, the way Samsung's "Find my earbuds" does, over the
 * buds' serial channel. Frame layout learned from the open GalaxyBudsClient
 * project's protocol notes (Buds+ and newer, including Buds4 Pro):
 *
 *   0xFD | size (2 bytes, little-endian) | id | payload | CRC16 (LE) | 0xDD
 *
 * size counts id + payload + CRC. CRC16 is CCITT (poly 0x1021, init 0) over id + payload.
 */
object BudsSound {

    private val SERVICE: UUID = UUID.fromString("2e73a4ad-332d-41fc-90e2-16bef06523f2")
    private const val SOM: Byte = 0xFD.toByte()
    private const val EOM: Byte = 0xDD.toByte()
    const val FIND_START = 160
    const val FIND_STOP = 161

    @Volatile private var socket: BluetoothSocket? = null

    val isPlaying: Boolean get() = socket?.isConnected == true

    fun crc16(data: ByteArray): Int {
        var crc = 0
        for (b in data) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc
    }

    fun frame(id: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val body = byteArrayOf(id.toByte()) + payload
        val crc = crc16(body)
        val size = body.size + 2
        return byteArrayOf(SOM, (size and 0xFF).toByte(), ((size shr 8) and 0xFF).toByte()) +
            body +
            byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte(), EOM)
    }

    /** Opens the channel and starts the beep. Returns a short status for the screen. */
    @SuppressLint("MissingPermission")
    suspend fun start(context: Context, address: String?): String = withContext(Dispatchers.IO) {
        if (address == null) return@withContext "Pick your buds in Setup first"
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return@withContext "Bluetooth is off"
        runCatching {
            stopQuietly()
            val device = adapter.getRemoteDevice(address)
            val s = device.createRfcommSocketToServiceRecord(SERVICE)
            s.connect()
            s.outputStream.write(frame(FIND_START))
            s.outputStream.flush()
            socket = s
            "Playing. Listen for the beep."
        }.getOrElse {
            stopQuietly()
            "Couldn't reach the buds (${it.message ?: it.javaClass.simpleName}). They must be out of the case and connected."
        }
    }

    suspend fun stop(): String = withContext(Dispatchers.IO) {
        runCatching { socket?.outputStream?.write(frame(FIND_STOP)) }
        stopQuietly()
        "Stopped"
    }

    private fun stopQuietly() {
        runCatching { socket?.close() }
        socket = null
    }
}
