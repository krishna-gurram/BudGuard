package dev.krishnagurram.budguard

import dev.krishnagurram.budguard.data.Geo
import dev.krishnagurram.budguard.data.TrackMode
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {

    @Test
    fun bearings() {
        assertEquals(0.0, Geo.bearingDeg(33.0, -112.0, 33.01, -112.0), 0.5)
        assertEquals(90.0, Geo.bearingDeg(33.0, -112.0, 33.0, -111.99), 0.5)
        assertEquals(180.0, Geo.bearingDeg(33.01, -112.0, 33.0, -112.0), 0.5)
        assertEquals(270.0, Geo.bearingDeg(33.0, -111.99, 33.0, -112.0), 0.5)
    }

    @Test
    fun words() {
        assertEquals("north", Geo.compassWord(5.0))
        assertEquals("north-east", Geo.compassWord(40.0))
        assertEquals("west", Geo.compassWord(268.0))
        assertEquals("north", Geo.compassWord(355.0))
    }

    @Test
    fun turnsTakeTheShortWay() {
        assertEquals(20.0, Geo.turnDeg(10.0, 350.0), 1e-9)
        assertEquals(-20.0, Geo.turnDeg(350.0, 10.0), 1e-9)
        assertEquals(90.0, Geo.turnDeg(180.0, 90.0), 1e-9)
    }

    @Test
    fun smoothingCrossesNorthWithoutSpinning() {
        val h = Geo.smoothHeading(350.0, 10.0, alpha = 0.5)
        assertEquals(0.0, h, 1e-9)
    }

    @Test
    fun labels() {
        assertEquals("7 ft", Geo.distanceText(2.0))
        assertEquals("330 ft", Geo.distanceText(100.0))
        assertEquals("1.7 mi", Geo.distanceText(2700.0))
        assertEquals("12 mi", Geo.distanceText(20_000.0))
        assertEquals("±35 ft", Geo.accuracyText(10.0))
        assertEquals("4 min walk", Geo.walkText(320.0))
        assertEquals("under a minute's walk", Geo.walkText(20.0))
    }

    @Test
    fun trackModes() {
        // Buds connected: with you, wherever the old spot was.
        assertEquals(TrackMode.WITH_YOU, Geo.trackMode(true, false, 5000.0, 10.0, 5.0))
        // Wallet flagged with you, reported a block behind: still with you, no chasing.
        assertEquals(TrackMode.WITH_YOU, Geo.trackMode(false, true, 120.0, 20.0, 5.0))
        // Flag is stale and it's miles away: go get it.
        assertEquals(TrackMode.AWAY, Geo.trackMode(false, true, 4000.0, 20.0, 5.0))
        // Not flagged, a few yards off: nearby.
        assertEquals(TrackMode.NEARBY, Geo.trackMode(false, false, 30.0, 10.0, 5.0))
        assertEquals(TrackMode.AWAY, Geo.trackMode(false, false, 300.0, 10.0, 5.0))
        assertEquals(TrackMode.LOCATING, Geo.trackMode(false, false, null, 10.0, null))
    }
}
