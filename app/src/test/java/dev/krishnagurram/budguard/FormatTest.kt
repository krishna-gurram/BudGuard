package dev.krishnagurram.budguard

import dev.krishnagurram.budguard.data.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {

    @Test
    fun agoBuckets() {
        val now = 10L * 24 * 60 * 60_000
        assertEquals("just now", Format.ago(now, now - 30_000))
        assertEquals("12 min ago", Format.ago(now, now - 12 * 60_000))
        assertEquals("3 h ago", Format.ago(now, now - 3 * 60 * 60_000))
        assertEquals("2 d ago", Format.ago(now, now - 2L * 24 * 60 * 60_000))
        assertEquals("just now", Format.ago(now, now + 5_000))
    }

    @Test
    fun batteryThresholds() {
        assertEquals(15, Format.crossedThreshold(previous = 20, current = 15))
        assertEquals(5, Format.crossedThreshold(previous = 10, current = 4))
        assertEquals(15, Format.crossedThreshold(previous = 40, current = 3))
        assertNull(Format.crossedThreshold(previous = 15, current = 14))
        assertNull(Format.crossedThreshold(previous = 60, current = 50))
        assertNull(Format.crossedThreshold(previous = 20, current = -1))
    }

    @Test
    fun accuracyLabel() {
        assertEquals("±40 ft", Format.accuracy(12.7f))
        assertNull(Format.accuracy(null))
    }

    @Test
    fun tagBatteryWords() {
        assertEquals("Full", Format.tagBattery(100))
        assertEquals("Medium", Format.tagBattery(50))
        assertEquals("Low", Format.tagBattery(15))
        assertEquals("Very low", Format.tagBattery(5))
        assertEquals("–", Format.tagBattery(null))
    }
}
