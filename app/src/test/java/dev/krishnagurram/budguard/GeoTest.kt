package dev.krishnagurram.budguard

import dev.krishnagurram.budguard.data.Geo
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
        assertEquals("7 m", Geo.distanceText(7.2))
        assertEquals("320 m", Geo.distanceText(321.0))
        assertEquals("1.2 km", Geo.distanceText(1234.0))
        assertEquals("4 min walk", Geo.walkText(320.0))
        assertEquals("under a minute's walk", Geo.walkText(20.0))
    }
}
