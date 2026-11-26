package dev.krishnagurram.budguard.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Pure formatting helpers, kept free of Android types so they are unit-tested. */
object Format {

    /** Samsung only reports tag battery as Full / Medium / Low / Very low. */
    fun tagBattery(level: Int?): String = when {
        level == null -> "–"
        level >= 100 -> "Full"
        level >= 50 -> "Medium"
        level >= 15 -> "Low"
        else -> "Very low"
    }


    fun ago(now: Long, then: Long): String {
        val minutes = (now - then).coerceAtLeast(0) / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 60 * 24 -> "${minutes / 60} h ago"
            else -> "${minutes / (60 * 24)} d ago"
        }
    }

    fun clock(time: Long): String = SimpleDateFormat("h:mm a", Locale.US).format(Date(time))

    fun dayLabel(now: Long, time: Long): String {
        val today = dayIndex(now)
        return when (dayIndex(time)) {
            today -> "Today"
            today - 1 -> "Yesterday"
            else -> SimpleDateFormat("EEE, MMM d", Locale.US).format(Date(time))
        }
    }

    fun accuracy(meters: Float?): String? = meters?.let { Geo.accuracyText(it.toDouble()) }

    fun title(type: EventType, battery: Int?): String = when (type) {
        EventType.CONNECTED -> "Connected"
        EventType.DISCONNECTED -> "Disconnected"
        EventType.APPEARED -> "Nearby again"
        EventType.DISAPPEARED -> "Out of range"
        EventType.BATTERY -> "Battery ${battery ?: "?"}%, spot saved"
        EventType.TAG_LEFT -> "Left behind"
        EventType.TAG_BACK -> "Back with you"
        EventType.TAG_BATTERY -> "Tag battery ${battery ?: "?"}%"
    }

    fun source(source: EventSource): String = when (source) {
        EventSource.BLUETOOTH -> "Bluetooth"
        EventSource.ANDROID_LINK -> "Android link"
        EventSource.BATTERY_BROADCAST -> "Battery"
        EventSource.SAMSUNG -> "Samsung Find"
    }

    /** Returns the threshold crossed when battery drops from [previous] to [current], or null. */
    fun crossedThreshold(previous: Int, current: Int, thresholds: List<Int> = listOf(15, 5)): Int? =
        thresholds.firstOrNull { previous > it && current in 0..it }

    private fun dayIndex(time: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = time }
        return c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR)
    }
}
