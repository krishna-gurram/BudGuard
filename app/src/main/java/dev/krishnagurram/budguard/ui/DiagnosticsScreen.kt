package dev.krishnagurram.budguard.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.material3.Icon
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
        modifier = modifier.fillMaxSize().background(Palette.Bg).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenTitle("Diagnostics", "Send me a copy of this after a day of use")

        PillButton("Copy report for Claude", Modifier.fillMaxWidth().height(54.dp)) { copyReport(context, sections) }

        sections.forEach { (name, lines) ->
            SectionLabel(name)
            Card {
                lines.forEach { StatusRow(it.ok, it.title, it.detail) }
            }
        }

        Spacer(Modifier.height(4.dp))
        PillButton("Open setup", Modifier.fillMaxWidth(), style = PillStyle.Soft, onClick = onSetup)
        PillButton("Clear timeline", Modifier.fillMaxWidth(), style = PillStyle.Danger) { confirmClear = true }
        Spacer(Modifier.height(110.dp))
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
            Line(if (btOn + btOff > 0) true else null, "Merged events", "$btOn connects, $btOff disconnects from ${prefs.rawSignals} raw signals"),
            Line(if (s.linked && linkIn + linkOut > 0) true else null, "Android link (v0.1 events)", "$linkIn nearby, $linkOut out of range"),
            Line(
                if (losses.isEmpty()) null else located == losses.size,
                "Location on disconnects",
                if (losses.isEmpty()) "No disconnects yet" else "$located of ${losses.size} have a location",
            ),
            Line(
                null,
                "Last battery (${prefs.batteryChecks} background checks)",
                if (prefs.lastBattery >= 0) "${prefs.lastBattery}%, ${Format.ago(now, prefs.lastBatteryTime)}" else "None",
            ),
        ),
        "Left-behind (v0.2)" to listOf(
            Line(
                if (prefs.fenceArmed) true else null,
                "Fence",
                (prefs.fenceStatus ?: "Not armed yet") +
                    if (prefs.fenceArmed) ", ${prefs.fencePlace ?: "saved spot"}, ${Format.ago(now, prefs.fenceSince)}" else "",
            ),
            Line(null, "Left-behind alerts sent", "${prefs.leftBehindAlerts}"),
            Line(
                if (s.activity) true else null,
                "Driving detection",
                "${prefs.drivingStatus ?: "Not started"} · ${if (prefs.inVehicle) "in a vehicle now" else "not driving"} · ${prefs.drivingChanges} changes",
            ),
            Line(null, "Buds state", "${prefs.recordedState ?: "unknown"} (raw ${prefs.rawState ?: "none"})"),
        ),
        "Tags (v2.0)" to listOf(
            Line(if (dev.krishnagurram.budguard.tracking.SamsungFind.isSignedIn()) true else null, "Samsung sign-in",
                if (dev.krishnagurram.budguard.tracking.SamsungFind.isSignedIn()) "Signed in" else "Not signed in (Items tab)"),
            Line(null, "Tags tracked", dev.krishnagurram.budguard.data.ItemStore.items(context).joinToString { "${it.label}: ${it.state.name.lowercase()}" }.ifBlank { "none" }),
            Line(null, "Last tag check", if (prefs.tagCheckTime > 0) "${Format.ago(now, prefs.tagCheckTime)} · ${prefs.tagCheckReason ?: "?"}" else "never"),
            Line(null, "Result", prefs.tagCheckResult ?: "—"),
            Line(if (prefs.moveFenceStatus?.startsWith("Armed") == true) true else null, "Movement fence", prefs.moveFenceStatus ?: "not armed yet"),
            Line(null, "Safe places", dev.krishnagurram.budguard.data.ItemStore.safePlaces(context).joinToString { it.name }.ifBlank { "none" }),
        ),
        "Setup" to listOf(
            Line(s.budsAddress != null, "Buds picked", s.budsName ?: "None"),
            Line(s.bluetooth, "Bluetooth permission", if (s.bluetooth) "Allowed" else "Missing"),
            Line(s.location && s.backgroundLocation, "Location", locationText(s)),
            Line(s.notifications, "Notifications", if (s.notifications) "Allowed" else "Missing"),
            Line(s.linked, "Android link", if (s.linked) "Linked" else "Not linked"),
            Line(s.unrestricted, "Battery optimization", if (s.unrestricted) "Unrestricted" else "Restricted, may miss events"),
            Line(s.activity, "Physical activity", if (s.activity) "Allowed" else "Missing (Setup step 6)"),
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
