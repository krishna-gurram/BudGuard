package dev.krishnagurram.budguard.ui

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

    Box(modifier) {
        // Map fills the top; the sheet slides over its lower edge.
        if (lastSeen != null) {
            BudsMap(
                lat = lastSeen.lat!!,
                lng = lastSeen.lng!!,
                accuracyM = lastSeen.accuracyM,
                modifier = Modifier.fillMaxWidth().height(440.dp),
                alert = !connected,
            )
        } else {
            Box(
                Modifier.fillMaxWidth().height(440.dp).background(Color(0xFFE7EAEE)),
                contentAlignment = Alignment.Center,
            ) { Text("The map appears after the first saved spot", color = Palette.Quiet, fontSize = 14.sp) }
        }

        StatusChip(
            text = when {
                latest == null -> "Waiting"
                connected -> "With you"
                leftBehind -> "Left behind"
                else -> "Not connected"
            },
            color = when {
                latest == null -> Palette.Grey
                connected -> Palette.Green
                else -> Palette.Red
            },
            modifier = Modifier.statusBarsPadding().padding(16.dp),
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 380.dp)
                .shadow(16.dp, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(Palette.Bg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.padding(top = 10.dp).width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFC9C9CF)).align(Alignment.CenterHorizontally),
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Card),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_buds), contentDescription = null, tint = Palette.Ink, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(status.budsName ?: "No buds picked", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
                    Text(
                        when {
                            latest == null -> "Connect and disconnect once to start"
                            connected -> "Connected ${Format.ago(now, latest.time)}"
                            else -> listOfNotNull(
                                lastSeen?.place ?: lastSeen?.let { coords(it) },
                                Format.ago(now, latest.time),
                            ).joinToString(" · ")
                        },
                        fontSize = 14.sp,
                        color = Palette.Quiet,
                    )
                }
            }

            if (!status.coreReady) {
                Card {
                    Text("Finish setup", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                    Text("BudGuard can't watch your buds until setup is done.", fontSize = 14.sp, color = Palette.Quiet)
                    Button(onClick = onSetup, colors = blueButton, shape = RoundedCornerShape(50)) { Text("Open setup") }
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Card).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row {
                        Text("Battery", fontSize = 13.sp, color = Palette.Quiet, modifier = Modifier.weight(1f))
                        Text(if (battery >= 0) "$battery%" else "—", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFECECF0))) {
                        Box(
                            Modifier.fillMaxWidth((battery.coerceIn(0, 100)) / 100f).height(6.dp).clip(RoundedCornerShape(3.dp))
                                .background(if (battery in 0..15) Palette.Orange else Palette.Blue),
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("Directions", Icons.Filled.Place, primary = true, enabled = lastSeen != null) {
                    lastSeen?.let { navigateTo(context, it.lat!!, it.lng!!) }
                }
                Tile(if (playing) "Stop sound" else "Play sound", Icons.Filled.PlayArrow, primary = false) {
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
                Tile("Find", Icons.Filled.Search, primary = false, onClick = onFind)
                Tile("Timeline", Icons.Filled.List, primary = false, onClick = onTimeline)
            }
            soundNote?.let { Text(it, fontSize = 13.sp, color = Palette.Quiet, modifier = Modifier.padding(horizontal = 8.dp)) }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Card)
                    .clickable { openSamsungFind(context) }.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconDot(Icons.Filled.Search, Palette.Orange, Palette.OrangeSoft, size = 36)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Case tag", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
                    Text("Closed case or dead buds? Follow the SmartTag2 in Samsung Find", fontSize = 13.sp, color = Palette.Quiet)
                }
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.Quiet)
            }

            Spacer(Modifier.height(110.dp))
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color, modifier: Modifier) {
    Row(
        modifier.shadow(6.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(color)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Color.White))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Tile(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = if (primary) Palette.Blue else Palette.Card
    val fg = if (primary) Color.White else Palette.Ink
    Column(
        Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(22.dp)).background(if (enabled) bg else Palette.GreySoft)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (primary) Color.White else Palette.Blue)
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

fun coords(e: BudEvent): String = String.format(java.util.Locale.US, "%.5f, %.5f", e.lat, e.lng)

fun openMap(context: Context, e: BudEvent) {
    if (!e.hasLocation) return
    val uri = Uri.parse("geo:${e.lat},${e.lng}?q=${e.lat},${e.lng}(Buds)")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}
