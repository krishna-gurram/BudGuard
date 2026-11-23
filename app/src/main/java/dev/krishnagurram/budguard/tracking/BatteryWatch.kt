package dev.krishnagurram.budguard.tracking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.krishnagurram.budguard.data.EventStore
import dev.krishnagurram.budguard.data.Prefs
import dev.krishnagurram.budguard.widget.LastSeenWidget
import java.util.concurrent.TimeUnit

/**
 * Every 15 minutes: read the buds' battery straight from Android (in case a
 * report was skipped), and nag to charge if they've been gone a while and low.
 */
object BatteryWatch {

    private const val WORK = "battery_watch"
    const val NAG_AFTER_MS = 6L * 60 * 60 * 1000
    const val NAG_BELOW = 20

    fun schedule(context: Context) {
        val work = PeriodicWorkRequestBuilder<BatteryWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, work)
    }

    /** Pure decision, unit-tested: time to remind about charging? */
    fun shouldNag(budsGone: Boolean, goneSince: Long, lastBattery: Int, lastNag: Long, now: Long): Boolean =
        budsGone &&
            lastBattery in 0 until NAG_BELOW &&
            now - goneSince >= NAG_AFTER_MS &&
            lastNag < goneSince
}

class BatteryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        prefs.batteryChecks = prefs.batteryChecks + 1

        val level = CompanionLink.batteryLevel(applicationContext, prefs.budsAddress)
        if (level != null && level >= 0 && level != prefs.lastBattery) {
            BluetoothReceiver.onBattery(applicationContext, prefs, level, countReport = false)
        }

        val now = System.currentTimeMillis()
        val gone = prefs.recordedState == Presence.LOST
        EventStore.get(applicationContext).prune(keepDays = 90)
        LastSeenWidget.refresh(applicationContext)

        if (prefs.chargeOn && BatteryWatch.shouldNag(gone, prefs.rawSince, prefs.lastBattery, prefs.lastChargeNag, now)) {
            prefs.lastChargeNag = now
            Alerts.charge(applicationContext, prefs.lastBattery)
        }
        return Result.success()
    }
}
