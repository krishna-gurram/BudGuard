package dev.krishnagurram.budguard.data

import android.content.Context
import android.content.SharedPreferences

/** Small settings, live state and diagnostic counters. */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("budguard", Context.MODE_PRIVATE)

    private fun str(key: String) = sp.getString(key, null)
    private fun setStr(key: String, v: String?) = sp.edit().putString(key, v).apply()
    private fun int(key: String, d: Int = 0) = sp.getInt(key, d)
    private fun setInt(key: String, v: Int) = sp.edit().putInt(key, v).apply()
    private fun long(key: String) = sp.getLong(key, 0L)
    private fun setLong(key: String, v: Long) = sp.edit().putLong(key, v).apply()
    private fun bool(key: String) = sp.getBoolean(key, false)
    private fun setBool(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    private fun on(key: String) = sp.getBoolean(key, true)
    private fun dbl(key: String) = Double.fromBits(sp.getLong(key, 0L))
    private fun setDbl(key: String, v: Double) = sp.edit().putLong(key, v.toRawBits()).apply()

    // Alert settings (all on by default)
    var leftBehindOn: Boolean get() = on("left_behind_on"); set(v) = setBool("left_behind_on", v)
    var batteryOn: Boolean get() = on("battery_on"); set(v) = setBool("battery_on", v)
    var movingOn: Boolean get() = on("moving_on"); set(v) = setBool("moving_on", v)
    var chargeOn: Boolean get() = on("charge_on"); set(v) = setBool("charge_on", v)
    var radiusM: Int get() = int("radius_m", 150); set(v) = setInt("radius_m", v)

    // Tags
    var tagAlertsOn: Boolean get() = on("tag_alerts_on"); set(v) = setBool("tag_alerts_on", v)
    var tagBatteryOn: Boolean get() = on("tag_battery_on"); set(v) = setBool("tag_battery_on", v)
    var tagCheckTime: Long get() = long("tag_check_time"); set(v) = setLong("tag_check_time", v)
    var tagCheckReason: String? get() = str("tag_check_reason"); set(v) = setStr("tag_check_reason", v)
    var tagCheckResult: String? get() = str("tag_check_result"); set(v) = setStr("tag_check_result", v)
    var moveFenceStatus: String? get() = str("move_fence_status"); set(v) = setStr("move_fence_status", v)

    // Which buds
    var budsAddress: String? get() = str("buds_address"); set(v) = setStr("buds_address", v)
    var budsName: String? get() = str("buds_name"); set(v) = setStr("buds_name", v)

    // Battery
    var batteryBroadcasts: Int get() = int("battery_broadcasts"); set(v) = setInt("battery_broadcasts", v)
    var lastBattery: Int get() = int("last_battery", -1); set(v) = setInt("last_battery", v)
    var lastBatteryTime: Long get() = long("last_battery_time"); set(v) = setLong("last_battery_time", v)

    var batteryChecks: Int get() = int("battery_checks"); set(v) = setInt("battery_checks", v)
    var lastChargeNag: Long get() = long("last_charge_nag"); set(v) = setLong("last_charge_nag", v)

    // Presence: raw signals settle into a recorded state
    var rawState: String? get() = str("raw_state"); set(v) = setStr("raw_state", v)
    var rawSince: Long get() = long("raw_since"); set(v) = setLong("raw_since", v)
    var rawSource: String? get() = str("raw_source"); set(v) = setStr("raw_source", v)
    var rawSignals: Int get() = int("raw_signals"); set(v) = setInt("raw_signals", v)
    var recordedState: String? get() = str("recorded_state"); set(v) = setStr("recorded_state", v)

    // Left-behind fence
    var fenceArmed: Boolean get() = bool("fence_armed"); set(v) = setBool("fence_armed", v)
    var fenceLat: Double get() = dbl("fence_lat"); set(v) = setDbl("fence_lat", v)
    var fenceLng: Double get() = dbl("fence_lng"); set(v) = setDbl("fence_lng", v)
    var fencePlace: String? get() = str("fence_place"); set(v) = setStr("fence_place", v)
    var fenceSince: Long get() = long("fence_since"); set(v) = setLong("fence_since", v)
    var fenceExpiresAt: Long get() = long("fence_expires"); set(v) = setLong("fence_expires", v)
    var fenceStatus: String? get() = str("fence_status"); set(v) = setStr("fence_status", v)
    var leftBehindAlerts: Int get() = int("left_behind_alerts"); set(v) = setInt("left_behind_alerts", v)

    // Driving
    var inVehicle: Boolean get() = bool("in_vehicle"); set(v) = setBool("in_vehicle", v)
    var drivingStatus: String? get() = str("driving_status"); set(v) = setStr("driving_status", v)
    var drivingChanges: Int get() = int("driving_changes"); set(v) = setInt("driving_changes", v)

    fun isBuds(address: String?): Boolean =
        address != null && budsAddress?.equals(address, ignoreCase = true) == true
}
