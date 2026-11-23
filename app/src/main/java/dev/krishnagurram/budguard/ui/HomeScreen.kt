package dev.krishnagurram.budguard.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.krishnagurram.budguard.data.BudEvent
import dev.krishnagurram.budguard.data.Format
import dev.krishnagurram.budguard.data.Prefs
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    modifier: Modifier,
    events: List<BudEvent>,
    status: SetupStatus,
    onSetup: () -> Unit,
    onTimeline: () -> Unit,
) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    val latest = events.firstOrNull { it.isLoss || it.isPresence }
    val lastSeen = events.firstOrNull { it.isLoss && it.hasLocation } ?: events.firstOrNull { it.hasLocation }
    val connected = latest?.isPresence == true
    val prefs = remember { Prefs(context) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("BudGuard", status.budsName ?: "No buds picked yet")

        if (!status.coreReady) {
            Card {
                Text("Finish setup", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Text(
                    "BudGuard needs Bluetooth, location \"all the time\" and your buds picked before it can watch them.",
                    fontSize = 14.sp,
                    color = Palette.Quiet,
                )
                Button(onClick = onSetup, colors = blueButton, shape = RoundedCornerShape(50)) { Text("Open setup") }
            }
        }

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    latest == null -> IconDot(Icons.Filled.Warning, Palette.Grey, Palette.GreySoft)
                    connected -> IconDot(Icons.Filled.CheckCircle, Palette.Green, Palette.GreenSoft)
                    else -> IconDot(Icons.Filled.LocationOn, Palette.Red, Palette.RedSoft)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            latest == null -> "Waiting for the first event"
                            connected -> "With you"
                            else -> "Not connected"
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Palette.Ink,
                    )
                    Text(
                        latest?.let { "${Format.title(it.type, it.battery)} ${Format.ago(now, it.time)}" }
                            ?: "Connect and disconnect your buds once to test",
                        fontSize = 14.sp,
                        color = Palette.Quiet,
                    )
                }
            }
        }

        SectionLabel("Last seen")
        Card {
            if (lastSeen == null) {
                Text("No location saved yet", fontSize = 15.sp, color = Palette.Quiet)
            } else {
                Text(
                    lastSeen.place ?: coords(lastSeen),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Ink,
                )
                Text(
                    listOfNotNull(
                        "${Format.clock(lastSeen.time)} · ${Format.ago(now, lastSeen.time)}",
                        Format.accuracy(lastSeen.accuracyM),
                    ).joinToString(" · "),
                    fontSize = 14.sp,
                    color = Palette.Quiet,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { openMap(context, lastSeen) },
                        colors = blueButton,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f),
                    ) { Text("Open in Maps") }
                    Button(
                        onClick = onTimeline,
                        colors = softButton,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f),
                    ) { Text("Timeline") }
                }
            }
        }

        SectionLabel("Battery")
        Card {
            if (prefs.lastBattery >= 0) {
                Text("${prefs.lastBattery}%", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
                Text("Reported ${Format.ago(now, prefs.lastBatteryTime)}", fontSize = 14.sp, color = Palette.Quiet)
            } else {
                Text("Not reported by Android yet", fontSize = 15.sp, color = Palette.Ink, fontWeight = FontWeight.SemiBold)
                Text(
                    "Wear the buds for a few minutes, then check Diagnostics. This is the battery test from Phase 0.",
                    fontSize = 13.sp,
                    color = Palette.Quiet,
                )
            }
        }

        Text(
            "Coming next: left-behind alerts (v0.2), finder with Play sound (v0.4).",
            fontSize = 12.sp,
            color = Palette.Quiet,
            modifier = Modifier.padding(8.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

fun coords(e: BudEvent): String = String.format(java.util.Locale.US, "%.5f, %.5f", e.lat, e.lng)

fun openMap(context: Context, e: BudEvent) {
    if (!e.hasLocation) return
    val uri = Uri.parse("geo:${e.lat},${e.lng}?q=${e.lat},${e.lng}(Buds)")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}
