package dev.krishnagurram.budguard.tracking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.krishnagurram.budguard.data.EventSource
import dev.krishnagurram.budguard.data.EventType
import dev.krishnagurram.budguard.data.Prefs
import java.util.concurrent.TimeUnit

/**
 * One case close makes several raw signals (Bluetooth drops and reconnects,
 * the Android link). Raw signals only update state; after things settle for
 * a few seconds, one event is written if the state really changed.
 */
object Presence {

    const val PRESENT = "PRESENT"
    const val LOST = "LOST"
    const val SETTLE_SECONDS = 12L
    private const val WORK = "settle"

    /** Pure decision, unit-tested: does the settled raw state need a new event? */
    fun needsEvent(raw: String?, recorded: String?): Boolean = raw != null && raw != recorded

    fun onRaw(context: Context, present: Boolean, source: EventSource) {
        val prefs = Prefs(context)
        val state = if (present) PRESENT else LOST
        if (prefs.rawState != state) {
            prefs.rawState = state
            prefs.rawSince = System.currentTimeMillis()
            prefs.rawSource = source.name
        }
        prefs.rawSignals = prefs.rawSignals + 1

        val work = OneTimeWorkRequestBuilder<SettleWorker>()
            .setInitialDelay(SETTLE_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, work)
    }
}

class SettleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val raw = prefs.rawState
        if (!Presence.needsEvent(raw, prefs.recordedState)) return Result.success()

        prefs.recordedState = raw
        val type = if (raw == Presence.PRESENT) EventType.CONNECTED else EventType.DISCONNECTED
        val source = runCatching { EventSource.valueOf(prefs.rawSource ?: "") }.getOrDefault(EventSource.BLUETOOTH)
        EventRecorder.record(applicationContext, type, source, time = prefs.rawSince)
        return Result.success()
    }
}
