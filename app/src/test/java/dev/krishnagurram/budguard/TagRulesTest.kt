package dev.krishnagurram.budguard

import dev.krishnagurram.budguard.data.ItemStore
import dev.krishnagurram.budguard.data.SafePlace
import dev.krishnagurram.budguard.data.TagItem
import dev.krishnagurram.budguard.data.TagRules
import dev.krishnagurram.budguard.data.TagState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagRulesTest {

    private val home = SafePlace("Home", 33.3700, -112.1700, 150)
    private val now = 10_000_000_000L
    private fun wallet(lat: Double, lng: Double, ageMs: Long, acc: Double = 10.0) =
        TagItem("t1", "u1", "Wallet tag", "Wallet", "wallet", lat = lat, lng = lng, accuracyM = acc, fixTime = now - ageMs)

    @Test
    fun distanceIsRoughlyRight() {
        // 0.01 degrees of latitude is about 1.11 km.
        assertEquals(1112.0, TagRules.distanceM(33.0, -112.0, 33.01, -112.0), 5.0)
    }

    @Test
    fun withYouWhenClose() {
        val v = TagRules.decide(33.4000, -112.1000, 20.0, wallet(33.4005, -112.1000, 60_000), 150, listOf(home), now, refreshed = true)
        assertEquals(TagState.WITH_YOU, v.state)
    }

    @Test
    fun leftBehindWhenFarAndFresh() {
        val v = TagRules.decide(33.4100, -112.1000, 10.0, wallet(33.4000, -112.1000, 120_000), 150, listOf(home), now, refreshed = false)
        assertEquals(TagState.LEFT_BEHIND, v.state)
    }

    @Test
    fun farButStaleIsUnknownNotAnAlarm() {
        val v = TagRules.decide(33.4100, -112.1000, 10.0, wallet(33.4000, -112.1000, 3 * 3_600_000L), 150, emptyList(), now, refreshed = false)
        assertEquals(TagState.UNKNOWN, v.state)
    }

    @Test
    fun leftAtSafePlaceIsQuiet() {
        val v = TagRules.decide(33.4100, -112.1000, 10.0, wallet(33.3701, -112.1701, 60_000), 150, listOf(home), now, refreshed = true)
        assertEquals(TagState.LEFT_SAFE, v.state)
        assertEquals("Home", v.safePlace)
    }

    @Test
    fun drivingWithALaggingReportIsNotASeparation() {
        // You're 1.1 km up the freeway; the wallet's report is 90 s old; you're doing 28 m/s.
        val lagging = wallet(33.4000, -112.1000, 90_000)
        val v = TagRules.decide(33.4100, -112.1000, 10.0, lagging, 150, emptyList(), now, refreshed = true, phoneTime = now, phoneSpeedMps = 28.0)
        assertEquals(TagState.WITH_YOU, v.state)
        // Same gap while walking (1.5 m/s) really is a separation.
        val walking = TagRules.decide(33.4100, -112.1000, 10.0, lagging, 150, emptyList(), now, refreshed = true, phoneTime = now, phoneSpeedMps = 1.5)
        assertEquals(TagState.LEFT_BEHIND, walking.state)
    }

    @Test
    fun secondLookDecides() {
        val t0 = now - 120_000
        val stayed = wallet(33.40005, -112.1000, 10_000)
        val moved = wallet(33.4200, -112.1000, 10_000)
        val noNews = wallet(33.4000, -112.1000, 120_000)
        assertEquals(TagRules.Confirm.LEFT, TagRules.confirm(33.4000, -112.1000, t0, stayed, checks = 0))
        assertEquals(TagRules.Confirm.MOVED, TagRules.confirm(33.4000, -112.1000, t0, moved, checks = 0))
        assertEquals(TagRules.Confirm.WAIT, TagRules.confirm(33.4000, -112.1000, t0, noNews, checks = 0))
        assertEquals(TagRules.Confirm.LEFT, TagRules.confirm(33.4000, -112.1000, t0, noNews, checks = 1))
    }

    @Test
    fun alertsOncePerSeparation() {
        assertTrue(TagRules.shouldAlert(TagState.WITH_YOU, TagState.LEFT_BEHIND, alertsOn = true))
        assertFalse(TagRules.shouldAlert(TagState.LEFT_BEHIND, TagState.LEFT_BEHIND, alertsOn = true))
        assertFalse(TagRules.shouldAlert(TagState.WITH_YOU, TagState.LEFT_BEHIND, alertsOn = false))
        assertFalse(TagRules.shouldAlert(TagState.WITH_YOU, TagState.LEFT_SAFE, alertsOn = true))
    }

    @Test
    fun batteryWarningOnce() {
        assertTrue(TagRules.shouldWarnBattery(15, alreadyWarned = false))
        assertFalse(TagRules.shouldWarnBattery(15, alreadyWarned = true))
        assertFalse(TagRules.shouldWarnBattery(50, alreadyWarned = false))
        assertFalse(TagRules.shouldWarnBattery(null, alreadyWarned = false))
    }

    @Test
    fun itemsAndPlacesRoundTrip() {
        val items = listOf(wallet(33.4, -112.1, 0).copy(state = TagState.LEFT_BEHIND, place = "Rosa's Café", battery = 50), TagItem("t2", "u1", "Tag", "Keys"))
        assertEquals(items, ItemStore.decodeItems(ItemStore.encodeItems(items)))
        assertEquals(listOf(home), ItemStore.decodePlaces(ItemStore.encodePlaces(listOf(home))))
        assertEquals("wallet", ItemStore.guessIcon("Krishna's Wallet"))
        assertEquals("keys", ItemStore.guessIcon("Car keys"))
    }
}
