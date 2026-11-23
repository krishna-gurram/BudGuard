package dev.krishnagurram.budguard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.krishnagurram.budguard.data.BudEvent
import dev.krishnagurram.budguard.data.EventSource
import dev.krishnagurram.budguard.data.EventType
import dev.krishnagurram.budguard.data.Format
import dev.krishnagurram.budguard.data.Prefs
import dev.krishnagurram.budguard.tracking.CompanionLink

private data class Line(val ok: Boolean?, val title: String, val detail: String)

@Composable
fun DiagnosticsScreen(
    modifier: Modifier,
    events: List<BudEvent>,
    status: SetupStatus,
    refresh: Int,
    onSetup: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val sections = remember(events, status, refresh) { buildSections(context, events, status) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenTitle("Diagnostics", "Send me a copy of this after a day of use")

        Button(
            onClick = { copyReport(context, sections) },
            colors = blueButton,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Copy report for Claude") }

        sections.forEach { (name, lines) ->
            SectionLabel(name)
            Card {
                lines.forEach { StatusRow(it.ok, it.title, it.detail) }
            }
        }

        Spacer(Modifier.height(4.dp))
        Button(onClick = onSetup, colors = softButton, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth()) {
            Text("Open setup")
        }
        TextButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Text("Clear timeline") }
        Spacer(Modifier.height(16.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the timeline?") },
            text = { Text("Every saved event and location is deleted from this phone.") },
            confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

private fun buildSections(context: Context, events: List<BudEvent>, s: SetupStatus): List<Pair<String, List<Line>>> {
    val prefs = Prefs(context)
    val now = System.currentTimeMillis()

    fun count(type: EventType, source: EventSource) = events.count { it.type == type && it.source == source }

    val btOn = count(EventType.CONNECTED, EventSource.BLUETOOTH)
    val btOff = count(EventType.DISCONNECTED, EventSource.BLUETOOTH)
    val linkIn = count(EventType.APPEARED, EventSource.ANDROID_LINK)
    val linkOut = count(EventType.DISAPPEARED, EventSource.ANDROID_LINK)
    val losses = events.filter { it.isLoss }
    val located = losses.count { it.hasLocation }
    val hiddenBattery = CompanionLink.systemBattery(context, s.budsAddress)
    val broadcasts = prefs.batteryBroadcasts

    val spikeA = when {
        broadcasts > 0 -> true
        hiddenBattery.endsWith("%") -> true
        else -> null
    }
    val spikeB = when {
        !s.linked -> null
        linkIn + linkOut > 0 -> true
        btOn + btOff >= 4 -> false
        else -> null
    }

    return listOf(
        "Phase 0 answers" to listOf(
            Line(
                spikeA,
                "A. Does Android report bud battery?",
                when (spikeA) {
                    true -> "Yes. $broadcasts reports, Android says $hiddenBattery now"
                    else -> "Not yet. 0 reports; Android says $hiddenBattery"
                },
            ),
            Line(
                spikeB,
                "B. Does the Android link wake BudGuard?",
                when {
                    !s.linked -> "Link not set up (Setup step 4)"
                    spikeB == true -> "Yes. $linkIn nearby, $linkOut out-of-range events"
                    spikeB == false -> "No link events after ${btOn + btOff} Bluetooth events"
                    else -> "Waiting for events"
                },
            ),
        ),
        "Events captured" to listOf(
            Line(btOn + btOff > 0, "Bluetooth", "$btOn connects, $btOff disconnects"),
            Line(if (s.linked) linkIn + linkOut > 0 else null, "Android link", "$linkIn nearby, $linkOut out of range"),
            Line(
                if (losses.isEmpty()) null else located == losses.size,
                "Location on disconnects",
                if (losses.isEmpty()) "No disconnects yet" else "$located of ${losses.size} have a location",
            ),
            Line(
                null,
                "Last battery report",
                if (prefs.lastBattery >= 0) "${prefs.lastBattery}%, ${Format.ago(now, prefs.lastBatteryTime)}" else "None",
            ),
        ),
        "Setup" to listOf(
            Line(s.budsAddress != null, "Buds picked", s.budsName ?: "None"),
            Line(s.bluetooth, "Bluetooth permission", if (s.bluetooth) "Allowed" else "Missing"),
            Line(s.location && s.backgroundLocation, "Location", locationText(s)),
            Line(s.notifications, "Notifications", if (s.notifications) "Allowed" else "Missing"),
            Line(s.linked, "Android link", if (s.linked) "Linked" else "Not linked"),
            Line(s.unrestricted, "Battery optimization", if (s.unrestricted) "Unrestricted" else "Restricted, may miss events"),
        ),
        "Phone" to listOf(
            Line(null, "BudGuard ${appVersion(context)}", "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"),
        ),
    )
}

private fun locationText(s: SetupStatus) = when {
    s.location && s.backgroundLocation -> "All the time"
    s.location -> "Only while using the app"
    else -> "Missing"
}

private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?" }.getOrDefault("?")

private fun copyReport(context: Context, sections: List<Pair<String, List<Line>>>) {
    val text = buildString {
        appendLine("BudGuard diagnostics")
        sections.forEach { (name, lines) ->
            appendLine()
            appendLine("## $name")
            lines.forEach { l ->
                val mark = when (l.ok) { true -> "OK"; false -> "!!"; null -> "--" }
                appendLine("[$mark] ${l.title}: ${l.detail}")
            }
        }
    }
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("BudGuard diagnostics", text))
    Toast.makeText(context, "Copied. Paste it into the chat.", Toast.LENGTH_SHORT).show()
}
