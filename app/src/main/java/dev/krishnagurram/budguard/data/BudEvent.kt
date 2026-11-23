package dev.krishnagurram.budguard.data

enum class EventType { CONNECTED, DISCONNECTED, APPEARED, DISAPPEARED, BATTERY, TAG_LEFT, TAG_BACK, TAG_BATTERY }

enum class EventSource { BLUETOOTH, ANDROID_LINK, BATTERY_BROADCAST, SAMSUNG }

data class BudEvent(
    val id: Long,
    val time: Long,
    val type: EventType,
    val source: EventSource,
    val lat: Double?,
    val lng: Double?,
    val accuracyM: Float?,
    val place: String?,
    val battery: Int?,
    val moving: Boolean = false,
    val item: String? = null,
) {
    val hasLocation: Boolean get() = lat != null && lng != null

    /** True for events that mean the buds just left the phone. */
    val isLoss: Boolean get() = type == EventType.DISCONNECTED || type == EventType.DISAPPEARED

    /** True for events that mean the buds are with the phone. */
    val isPresence: Boolean get() = type == EventType.CONNECTED || type == EventType.APPEARED
}
