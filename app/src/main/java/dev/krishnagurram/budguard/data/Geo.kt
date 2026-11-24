package dev.krishnagurram.budguard.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the tracking screen should show, like Find My's states. */
enum class TrackMode { WITH_YOU, NEARBY, AWAY, LOCATING }

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

    const val FEET_PER_M = 3.28084
    const val M_PER_MILE = 1609.344

    /** US units: feet up to 0.1 mile, then miles. */
    fun distanceText(m: Double): String {
        val ft = m * FEET_PER_M
        return when {
            ft < 30 -> "${ft.roundToInt()} ft"
            m < M_PER_MILE / 10 -> "${(ft / 10).roundToInt() * 10} ft"
            m < M_PER_MILE * 10 -> String.format(java.util.Locale.US, "%.1f mi", m / M_PER_MILE)
            else -> "${(m / M_PER_MILE).roundToInt()} mi"
        }
    }

    /** GPS error radius, e.g. "±30 ft". */
    fun accuracyText(m: Double): String {
        val ft = m * FEET_PER_M
        return if (ft < 10) "±${ft.roundToInt()} ft" else "±${(ft / 5).roundToInt() * 5} ft"
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

    /**
     * With you: the buds are connected ([certain]), or the watcher last saw the tag with you
     * and its spot is still roughly where you are. A tag that's with you is reported at your
     * phone's own position, so chasing it on a map would just chase you.
     * Nearby: within shouting distance once both GPS errors are allowed for. Away: go get it.
     */
    fun trackMode(certain: Boolean, flaggedWithYou: Boolean, distanceM: Double?, itemAccuracyM: Double?, phoneAccuracyM: Double?): TrackMode {
        if (certain) return TrackMode.WITH_YOU
        val d = distanceM ?: return if (flaggedWithYou) TrackMode.WITH_YOU else TrackMode.LOCATING
        val near = CLOSE_M + (itemAccuracyM ?: 0.0).coerceAtMost(150.0) + (phoneAccuracyM ?: 0.0).coerceAtMost(50.0)
        return when {
            flaggedWithYou && d <= near + 150 -> TrackMode.WITH_YOU
            d <= near -> TrackMode.NEARBY
            else -> TrackMode.AWAY
        }
    }
}
