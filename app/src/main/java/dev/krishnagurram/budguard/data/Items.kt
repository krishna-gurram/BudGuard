package dev.krishnagurram.budguard.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class TagState { WITH_YOU, LEFT_SAFE, LEFT_BEHIND, UNKNOWN }

/** A SmartTag (or any Samsung Find device) you track in BudGuard. */
data class TagItem(
    val id: String,
    val userId: String,
    val samsungName: String,
    val label: String,
    val icon: String = "tag",
    val alertsOn: Boolean = true,
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracyM: Double? = null,
    val fixTime: Long = 0L,
    val place: String? = null,
    val battery: Int? = null,
    val state: TagState = TagState.UNKNOWN,
    val stateSince: Long = 0L,
    val safePlace: String? = null,
    val lowBatteryWarned: Boolean = false,
) {
    val hasFix: Boolean get() = lat != null && lng != null
}

data class SafePlace(val name: String, val lat: Double, val lng: Double, val radiusM: Int = 150)

/** Tags and safe places, kept as small JSON blobs on the phone. */
object ItemStore {

    private fun sp(context: Context) = context.applicationContext.getSharedPreferences("items", Context.MODE_PRIVATE)

    fun items(context: Context): List<TagItem> =
        runCatching { decodeItems(sp(context).getString("tags", "[]") ?: "[]") }.getOrDefault(emptyList())

    fun saveItems(context: Context, items: List<TagItem>) =
        sp(context).edit().putString("tags", encodeItems(items)).apply()

    fun update(context: Context, id: String, change: (TagItem) -> TagItem) {
        saveItems(context, items(context).map { if (it.id == id) change(it) else it })
    }

    /** Adds tags Samsung knows about; keeps your labels and settings for ones already here. */
    fun merge(context: Context, fromSamsung: List<Triple<String, String, String>>) {
        val current = items(context).associateBy { it.id }
        val merged = fromSamsung.map { (id, userId, name) ->
            current[id]?.copy(userId = userId, samsungName = name)
                ?: TagItem(id = id, userId = userId, samsungName = name, label = name, icon = guessIcon(name))
        }
        saveItems(context, merged)
    }

    fun safePlaces(context: Context): List<SafePlace> =
        runCatching { decodePlaces(sp(context).getString("places", "[]") ?: "[]") }.getOrDefault(emptyList())

    fun saveSafePlaces(context: Context, places: List<SafePlace>) =
        sp(context).edit().putString("places", encodePlaces(places)).apply()

    fun guessIcon(name: String): String = when {
        name.contains("wallet", true) -> "wallet"
        name.contains("key", true) -> "keys"
        name.contains("bag", true) || name.contains("backpack", true) -> "bag"
        name.contains("case", true) || name.contains("bud", true) -> "case"
        else -> "tag"
    }

    // ---- JSON (pure, unit-tested) ----

    fun encodeItems(items: List<TagItem>): String = JSONArray().apply {
        items.forEach { t ->
            put(
                JSONObject()
                    .put("id", t.id).put("userId", t.userId).put("samsungName", t.samsungName)
                    .put("label", t.label).put("icon", t.icon).put("alertsOn", t.alertsOn)
                    .put("lat", t.lat ?: JSONObject.NULL).put("lng", t.lng ?: JSONObject.NULL)
                    .put("accuracyM", t.accuracyM ?: JSONObject.NULL).put("fixTime", t.fixTime)
                    .put("place", t.place ?: JSONObject.NULL).put("battery", t.battery ?: JSONObject.NULL)
                    .put("state", t.state.name).put("stateSince", t.stateSince)
                    .put("safePlace", t.safePlace ?: JSONObject.NULL).put("lowBatteryWarned", t.lowBatteryWarned),
            )
        }
    }.toString()

    fun decodeItems(json: String): List<TagItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TagItem(
                id = o.getString("id"),
                userId = o.optString("userId"),
                samsungName = o.optString("samsungName"),
                label = o.optString("label"),
                icon = o.optString("icon", "tag"),
                alertsOn = o.optBoolean("alertsOn", true),
                lat = o.optDoubleOrNull("lat"),
                lng = o.optDoubleOrNull("lng"),
                accuracyM = o.optDoubleOrNull("accuracyM"),
                fixTime = o.optLong("fixTime"),
                place = if (o.isNull("place")) null else o.optString("place"),
                battery = if (o.isNull("battery")) null else o.optInt("battery"),
                state = runCatching { TagState.valueOf(o.optString("state")) }.getOrDefault(TagState.UNKNOWN),
                stateSince = o.optLong("stateSince"),
                safePlace = if (o.isNull("safePlace")) null else o.optString("safePlace"),
                lowBatteryWarned = o.optBoolean("lowBatteryWarned"),
            )
        }
    }

    fun encodePlaces(places: List<SafePlace>): String = JSONArray().apply {
        places.forEach { put(JSONObject().put("name", it.name).put("lat", it.lat).put("lng", it.lng).put("radiusM", it.radiusM)) }
    }.toString()

    fun decodePlaces(json: String): List<SafePlace> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SafePlace(o.getString("name"), o.getDouble("lat"), o.getDouble("lng"), o.optInt("radiusM", 150))
        }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key)
}

/** Decisions about where a tag is relative to you. Pure, unit-tested. */
object TagRules {

    const val FRESH_MS = 30L * 60 * 1000

    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    fun safePlaceAt(lat: Double, lng: Double, places: List<SafePlace>): SafePlace? =
        places.firstOrNull { distanceM(lat, lng, it.lat, it.lng) <= it.radiusM }

    data class Verdict(val state: TagState, val distanceM: Double?, val safePlace: String?)

    /**
     * Is the tag with you? "With you" when its newest spot is within the alert distance
     * (widened by both GPS errors). A far spot only counts as left behind when it is
     * recent or was just refreshed, so a stale old spot never raises a false alarm.
     */
    fun decide(
        phoneLat: Double,
        phoneLng: Double,
        phoneAccuracyM: Double,
        tag: TagItem,
        radiusM: Int,
        places: List<SafePlace>,
        now: Long,
        refreshed: Boolean,
    ): Verdict {
        val lat = tag.lat ?: return Verdict(TagState.UNKNOWN, null, null)
        val lng = tag.lng ?: return Verdict(TagState.UNKNOWN, null, null)
        val d = distanceM(phoneLat, phoneLng, lat, lng)
        val slack = radiusM + phoneAccuracyM + (tag.accuracyM ?: 0.0)
        if (d <= slack) return Verdict(TagState.WITH_YOU, d, null)
        safePlaceAt(lat, lng, places)?.let { return Verdict(TagState.LEFT_SAFE, d, it.name) }
        val fresh = now - tag.fixTime <= FRESH_MS
        return if (fresh || refreshed) Verdict(TagState.LEFT_BEHIND, d, null) else Verdict(TagState.UNKNOWN, d, null)
    }

    /** Alert once per separation: only when the tag newly becomes left behind. */
    fun shouldAlert(previous: TagState, next: TagState, alertsOn: Boolean): Boolean =
        alertsOn && next == TagState.LEFT_BEHIND && previous != TagState.LEFT_BEHIND

    fun shouldWarnBattery(battery: Int?, alreadyWarned: Boolean): Boolean =
        battery != null && battery in 0..15 && !alreadyWarned
}
