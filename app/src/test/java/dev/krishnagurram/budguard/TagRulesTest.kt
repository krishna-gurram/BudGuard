package dev.krishnagurram.budguard

import dev.krishnagurram.budguard.data.Crumb
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
    fun spotFromBeforeYouLeftIsRidingAlongNotLeftBehind() {
        // Office at 33.4000. Samsung saw the wallet there 4 min ago; you drove off 3 min ago
        // and are now 1.1 km away. The tag is in the car; Samsung just hasn't caught up.
        val office = Crumb(33.4000, -112.1000, 150.0, now - 3 * 60_000)
        val v = TagRules.decide(33.4100, -112.1000, 10.0, wallet(33.4000, -112.1000, 4 * 60_000), 150, emptyList(), now, refreshed = true, trail = listOf(office))
        assertEquals(TagState.UNKNOWN, v.state)
        assertTrue(v.trailing)
    }

    @Test
    fun spotSeenAfterYouLeftIsLeftBehind() {
        // You left the cafe 20 min ago; Samsung saw the wallet still there 5 min ago.
        val cafe = Crumb(33.4000, -112.1000, 150.0, now - 20 * 60_000)
        val v = TagRules.decide(33.4100, -112.1000, 10.0, wallet(33.4000, -112.1000, 5 * 60_000), 150, emptyList(), now, refreshed = false, trail = listOf(cafe))
        assertEquals(TagState.LEFT_BEHIND, v.state)
        assertFalse(v.trailing)
    }

    @Test
    fun trailKeepsTwelveHoursAndRoundTrips() {
        val old = Crumb(33.0, -112.0, 20.0, now - 13 * 3_600_000L)
        val fresh = Crumb(33.1, -112.1, 150.0, now - 60_000)
        assertEquals(listOf(fresh), TagRules.pruneTrail(listOf(fresh, old), now))
        assertEquals(listOf(fresh), ItemStore.decodeTrail(ItemStore.encodeTrail(listOf(fresh))))
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
