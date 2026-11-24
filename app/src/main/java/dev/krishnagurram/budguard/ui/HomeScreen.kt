package dev.krishnagurram.budguard.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.krishnagurram.budguard.R
import dev.krishnagurram.budguard.data.BudEvent
import dev.krishnagurram.budguard.data.Format
import dev.krishnagurram.budguard.data.Prefs
import dev.krishnagurram.budguard.tracking.BudsSound
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    modifier: Modifier,
    events: List<BudEvent>,
    status: SetupStatus,
    refresh: Int,
    onSetup: () -> Unit,
    onTimeline: () -> Unit,
    onFind: () -> Unit,
    onTrack: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var playing by remember { mutableStateOf(false) }
    var soundNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    val latest = events.firstOrNull { it.isLoss || it.isPresence }
    val lastSeen = events.firstOrNull { it.isLoss && it.hasLocation } ?: events.firstOrNull { it.hasLocation }
    val connected = latest?.isPresence == true
    val fenceArmed = remember(refresh, events) { prefs.fenceArmed }
    val leftBehind = fenceArmed && !connected
    val battery = prefs.lastBattery

    val caseTag = remember(refresh) { dev.krishnagurram.budguard.data.ItemStore.items(context).firstOrNull { it.icon == "case" } }

    Box(modifier.background(Palette.Bg)) {
        // Map fills the top; the sheet slides over its lower edge.
        if (lastSeen != null) {
            BudsMap(
                lat = lastSeen.lat!!,
                lng = lastSeen.lng!!,
                accuracyM = lastSeen.accuracyM,
                modifier = Modifier.fillMaxWidth().height(470.dp),
                alert = !connected,
            )
        } else {
            Box(Modifier.fillMaxWidth().height(470.dp)) {
                FakeMap(Modifier.fillMaxSize())
                Text(
                    "The map appears after the first saved spot",
                    style = Type.Sub,
                    modifier = Modifier.align(Alignment.Center).padding(bottom = 60.dp).clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        Row(Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.Top) {
            when {
                latest == null -> MapBadge("Waiting for first event", Sym.Schedule, Palette.Grey)
                connected -> MapBadge("With you", Sym.Check, Palette.Green)
                leftBehind -> MapBadge("Left behind", Sym.DirectionsWalk, Palette.Red)
                else -> MapBadge("Not connected", Sym.LinkOff, Palette.Orange)
            }
            Spacer(Modifier.weight(1f))
            if (lastSeen != null) MapButton(Sym.NearMe, "Track live", tint = Palette.Blue, onClick = onTrack)
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 400.dp)
                .shadow(18.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Palette.Bg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHandle()

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Card), contentAlignment = Alignment.Center) {
                    Icon(Sym.Earbuds, contentDescription = null, tint = Palette.Ink, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(status.budsName ?: "No buds picked", style = Type.Title.copy(fontSize = 22.sp), maxLines = 1)
                    Text(
                        when {
                            latest == null -> "Put them in the case and take them out once"
                            connected -> "Connected to this phone · ${Format.ago(now, latest.time)}"
                            else -> listOfNotNull(lastSeen?.place ?: lastSeen?.let { coords(it) }, Format.ago(now, latest.time)).joinToString(" · ")
                        },
                        style = Type.Sub.copy(fontSize = 14.sp),
                        maxLines = 1,
                    )
                }
            }

            if (!status.coreReady) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Sym.Warning, Palette.Orange)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Finish setup", style = Type.Row)
                            Text("BudGuard can't watch your buds until it's done.", style = Type.Sub)
                        }
                    }
                    PillButton("Open setup", Modifier.fillMaxWidth(), onClick = onSetup)
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Card).padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                BatteryMeter("Buds", battery.takeIf { it >= 0 }, Modifier.weight(1f))
                BatteryMeter("Case tag", caseTag?.battery, Modifier.weight(1f), healthy = Palette.Green)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Track", Sym.Directions, primary = true, enabled = lastSeen != null, onClick = onTrack)
                ActionTile(if (playing) "Stop" else "Play sound", Sym.VolumeUp, active = playing) {
                    scope.launch {
                        if (playing) {
                            soundNote = BudsSound.stop()
                            playing = false
                        } else {
                            soundNote = "Connecting…"
                            soundNote = BudsSound.start(context, prefs.budsAddress)
                            playing = BudsSound.isPlaying
                        }
                    }
                }
                ActionTile("Search", Sym.Radar, onClick = onFind)
                ActionTile("Timeline", Sym.History, onClick = onTimeline)
            }
            soundNote?.let { Text(it, style = Type.Sub, modifier = Modifier.padding(horizontal = 8.dp)) }

            Group {
                ListRow(
                    title = "Case tag",
                    detail = caseTag?.let { t -> if (t.fixTime > 0) "Seen ${Format.ago(now, t.fixTime)}${t.place?.let { " · $it" } ?: ""}" else "Waiting for a first spot" }
                        ?: "Closed case or dead buds? Follow its SmartTag",
                    leading = { IconTile(Sym.Sell, Palette.Orange) },
                    onClick = { openSamsungFind(context) },
                ) { Chevron() }
            }

            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun BatteryMeter(label: String, percent: Int?, modifier: Modifier, healthy: Color = Palette.Blue) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.Sub, modifier = Modifier.weight(1f))
            Text(percent?.let { "$it%" } ?: "–", style = Type.Small.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.GreySoft)) {
            if (percent != null) {
                Box(
                    Modifier.fillMaxWidth(percent.coerceIn(2, 100) / 100f).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (percent <= 15) Palette.Orange else healthy),
                )
            }
        }
    }
}

fun coords(e: BudEvent): String = String.format(java.util.Locale.US, "%.5f, %.5f", e.lat, e.lng)

fun openMap(context: Context, e: BudEvent) {
    if (!e.hasLocation) return
    val uri = Uri.parse("geo:${e.lat},${e.lng}?q=${e.lat},${e.lng}(Buds)")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}
