package dev.krishnagurram.budguard.data

import android.content.Context
import android.content.SharedPreferences

/** Small settings and diagnostic counters. */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("budguard", Context.MODE_PRIVATE)

    var budsAddress: String?
        get() = sp.getString("buds_address", null)
        set(value) = sp.edit().putString("buds_address", value).apply()

    var budsName: String?
        get() = sp.getString("buds_name", null)
        set(value) = sp.edit().putString("buds_name", value).apply()

    var batteryBroadcasts: Int
        get() = sp.getInt("battery_broadcasts", 0)
        set(value) = sp.edit().putInt("battery_broadcasts", value).apply()

    var lastBattery: Int
        get() = sp.getInt("last_battery", -1)
        set(value) = sp.edit().putInt("last_battery", value).apply()

    var lastBatteryTime: Long
        get() = sp.getLong("last_battery_time", 0L)
        set(value) = sp.edit().putLong("last_battery_time", value).apply()

    var lastLossNotifyTime: Long
        get() = sp.getLong("last_loss_notify", 0L)
        set(value) = sp.edit().putLong("last_loss_notify", value).apply()

    fun isBuds(address: String?): Boolean =
        address != null && budsAddress?.equals(address, ignoreCase = true) == true
}
