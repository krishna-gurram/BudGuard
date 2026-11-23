package dev.krishnagurram.budguard

import dev.krishnagurram.budguard.tracking.LeftBehind
import dev.krishnagurram.budguard.tracking.Presence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeftBehindTest {

    @Test
    fun alertsOnlyWhenBudsGoneAndFenceLive() {
        assertTrue(LeftBehind.shouldAlert(budsGone = true, armed = true, now = 10, expiresAt = 20))
        assertFalse(LeftBehind.shouldAlert(budsGone = false, armed = true, now = 10, expiresAt = 20))
        assertFalse(LeftBehind.shouldAlert(budsGone = true, armed = false, now = 10, expiresAt = 20))
        assertFalse(LeftBehind.shouldAlert(budsGone = true, armed = true, now = 30, expiresAt = 20))
    }

    @Test
    fun noisyCaseCloseMakesOneEvent() {
        // Raw: lost, lost, present-blip, lost. Only the settled state matters.
        assertTrue(Presence.needsEvent(raw = Presence.LOST, recorded = Presence.PRESENT))
        assertFalse(Presence.needsEvent(raw = Presence.LOST, recorded = Presence.LOST))
        assertFalse(Presence.needsEvent(raw = null, recorded = Presence.LOST))
        assertTrue(Presence.needsEvent(raw = Presence.PRESENT, recorded = null))
    }
}
