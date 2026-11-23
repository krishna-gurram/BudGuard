package dev.krishnagurram.budguard.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.krishnagurram.budguard.R
import dev.krishnagurram.budguard.data.EventStore
import dev.krishnagurram.budguard.data.Format
import dev.krishnagurram.budguard.ui.MainActivity

/** Home-screen widget: are the buds with you, and if not, where were they left. */
class LastSeenWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    companion object {

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LastSeenWidget::class.java))
            if (ids.isEmpty()) return

            val events = runCatching { EventStore.get(context).recent(50) }.getOrDefault(emptyList())
            val latest = events.firstOrNull { it.isLoss || it.isPresence }
            val spot = events.firstOrNull { it.isLoss && it.hasLocation }
            val now = System.currentTimeMillis()

            val status = when {
                latest == null -> "BudGuard"
                latest.isPresence -> "Buds with you"
                else -> "Buds left ${Format.ago(now, latest.time)}"
            }
            val place = when {
                latest == null -> "Waiting for the first event"
                latest.isPresence -> "Connected ${Format.clock(latest.time)}"
                spot != null -> spot.place ?: "Saved spot, tap to open"
                else -> "No location saved"
            }

            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_last_seen).apply {
                setTextViewText(R.id.widget_status, status)
                setTextViewText(R.id.widget_place, place)
                setOnClickPendingIntent(R.id.widget_root, open)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
