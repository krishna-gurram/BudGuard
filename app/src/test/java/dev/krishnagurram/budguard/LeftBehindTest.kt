package dev.krishnagurram.budguard

import dev.krishnagurram.budguard.data.TagState
import dev.krishnagurram.budguard.tracking.LeftBehind
import dev.krishnagurram.budguard.tracking.Presence
import org.junit.Assert.assertEquals
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
    fun noFenceWhenBudsGoOffWhileDriving() {
        assertTrue(LeftBehind.shouldArm(hasLocation = true, moving = false))
        assertFalse(LeftBehind.shouldArm(hasLocation = true, moving = true))
        assertFalse(LeftBehind.shouldArm(hasLocation = false, moving = false))
    }

    @Test
    fun caseTagDecidesTheBudsAlert() {
        assertEquals(LeftBehind.CaseVerdict.NO_CASE_TAG, LeftBehind.caseSays(emptyList()))
        assertEquals(LeftBehind.CaseVerdict.WITH_YOU, LeftBehind.caseSays(listOf(TagState.WITH_YOU)))
        assertEquals(LeftBehind.CaseVerdict.NOT_WITH_YOU, LeftBehind.caseSays(listOf(TagState.LEFT_BEHIND)))
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
