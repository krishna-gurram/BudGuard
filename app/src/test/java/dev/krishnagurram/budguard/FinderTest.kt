package dev.krishnagurram.budguard

import dev.krishnagurram.budguard.tracking.BudsSound
import dev.krishnagurram.budguard.tracking.Radar
import dev.krishnagurram.budguard.tracking.Trend
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinderTest {

    @Test
    fun crcMatchesStandardCcittVector() {
        assertEquals(0x31C3, BudsSound.crc16("123456789".toByteArray()))
    }

    @Test
    fun crcMatchesCapturedBudsMessage() {
        // A real Buds message (id 0x61 + payload) whose transmitted CRC bytes were 0x0F, 0xF3.
        val data = byteArrayOf(0x61, 0x02, 0x00, 0x4B, 0x5F, 0x01, 0x00, 0x00, 0x00, 0x01, 0x05, 0x00, 0x02, 0x00, 0x13)
        assertEquals(0xF30F, BudsSound.crc16(data))
    }

    @Test
    fun findStartFrameLayout() {
        val f = BudsSound.frame(BudsSound.FIND_START)
        assertEquals(7, f.size)
        assertEquals(0xFD.toByte(), f.first())
        assertEquals(0xDD.toByte(), f.last())
        assertArrayEquals(byteArrayOf(3, 0), f.copyOfRange(1, 3)) // size = id + crc
        assertEquals(160.toByte(), f[3])
        val crc = BudsSound.crc16(byteArrayOf(160.toByte()))
        assertEquals((crc and 0xFF).toByte(), f[4])
        assertEquals((crc shr 8).toByte(), f[5])
    }

    @Test
    fun radarMath() {
        assertEquals(-60.0, Radar.smooth(null, -60), 0.001)
        assertEquals(-57.0, Radar.smooth(-60.0, -50), 0.001)
        assertEquals(1.0, Radar.distanceMeters(-59.0), 0.01)
        assertTrue(Radar.distanceMeters(-80.0) > Radar.distanceMeters(-60.0))
        assertEquals(Trend.WARMER, Radar.trend(-70.0, -60.0))
        assertEquals(Trend.COLDER, Radar.trend(-60.0, -70.0))
        assertEquals(Trend.STEADY, Radar.trend(-60.0, -61.0))
    }
}
