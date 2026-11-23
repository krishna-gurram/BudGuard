package dev.krishnagurram.budguard

import dev.krishnagurram.budguard.tracking.BatteryWatch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryWatchTest {

    private val hours = 60L * 60 * 1000

    @Test
    fun nagsOnceWhenGoneSixHoursAndLow() {
        val gone = 1_000L
        val now = gone + 7 * hours
        assertTrue(BatteryWatch.shouldNag(budsGone = true, goneSince = gone, lastBattery = 12, lastNag = 0, now = now))
        // Already nagged for this absence.
        assertFalse(BatteryWatch.shouldNag(true, gone, 12, lastNag = gone + 6 * hours, now = now))
        // Not long enough, battery fine, or buds are back.
        assertFalse(BatteryWatch.shouldNag(true, gone, 12, 0, now = gone + 2 * hours))
        assertFalse(BatteryWatch.shouldNag(true, gone, 55, 0, now))
        assertFalse(BatteryWatch.shouldNag(false, gone, 12, 0, now))
        assertFalse(BatteryWatch.shouldNag(true, gone, -1, 0, now))
    }
}
