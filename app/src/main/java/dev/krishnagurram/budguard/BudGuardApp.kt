package dev.krishnagurram.budguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class BudGuardApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LEFT,
                "Buds left behind",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "When your buds disconnect, with where it happened" },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BATTERY,
                "Low battery",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "When the buds hit 15% or 5% and the spot is saved" },
        )
    }

    companion object {
        const val CHANNEL_LEFT = "left_behind"
        const val CHANNEL_BATTERY = "battery"
    }
}
