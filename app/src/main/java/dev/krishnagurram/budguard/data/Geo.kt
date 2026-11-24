package dev.krishnagurram.budguard.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Small geometry for the live tracking screen. Pure, unit-tested. */
object Geo {

    const val WALK_MPS = 1.3
    const val CLOSE_M = 25.0

    /** Compass bearing from point 1 to point 2, 0..360 degrees, 0 = north. */
    fun bearingDeg(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lng2 - lng1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    fun compassWord(deg: Double): String {
        val words = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")
        return words[(((deg % 360) + 360) % 360 / 45.0).roundToInt() % 8]
    }

    /** How far to turn from where the phone points to face the target, -180..180 (right is positive). */
    fun turnDeg(bearingToTarget: Double, heading: Double): Double {
        var d = (bearingToTarget - heading) % 360
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return d
    }

    fun distanceText(m: Double): String = when {
        m < 10 -> "${m.roundToInt()} m"
        m < 1000 -> "${(m / 5).roundToInt() * 5} m"
        else -> String.format(java.util.Locale.US, "%.1f km", m / 1000)
    }

    fun walkText(m: Double): String {
        val minutes = (m / WALK_MPS / 60).roundToInt()
        return if (minutes < 1) "under a minute's walk" else "$minutes min walk"
    }

    /** Smooths a compass heading without the 359 to 0 jump. */
    fun smoothHeading(previous: Double?, reading: Double, alpha: Double = 0.2): Double {
        if (previous == null) return reading
        val delta = turnDeg(reading, previous)
        return ((previous + alpha * delta) % 360 + 360) % 360
    }
}
