package dev.krishnagurram.budguard.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.krishnagurram.budguard.data.BudEvent
import dev.krishnagurram.budguard.data.EventType
import dev.krishnagurram.budguard.data.Format
import dev.krishnagurram.budguard.tracking.Alerts
import dev.krishnagurram.budguard.tracking.LeftBehind

private enum class Filter(val label: String) { All("All"), Away("Left behind"), Items("Tags"), Battery("Battery"), Moving("Moving") }

@Composable
fun TimelineScreen(modifier: Modifier, events: List<BudEvent>) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var dismissedHero by remember { mutableStateOf(false) }

    val shown = events.filter {
        when (filter) {
            Filter.All -> true
            Filter.Away -> it.isLoss || it.type == EventType.TAG_LEFT
            Filter.Items -> it.item != null
            Filter.Battery -> it.type == EventType.BATTERY || it.type == EventType.TAG_BATTERY
            Filter.Moving -> it.moving
        }
    }
    val hero = events.firstOrNull { it.isLoss || it.isPresence }?.takeIf { it.isLoss && it.hasLocation && !dismissedHero }
    val groups = shown.filter { it.id != hero?.id }.groupBy { Format.dayLabel(now, it.time) }
    val places = events.mapNotNull { it.place }.distinct().size
    val today = events.count { Format.dayLabel(now, it.time) == "Today" }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(Palette.Bg).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Timeline", "$today events today · $places places") }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Filter.entries.toList()) { f -> Chip(f.label, f == filter) { filter = f } }
            }
        }

        if (hero != null && (filter == Filter.All || filter == Filter.Away)) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Palette.Card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                        IconDot(Sym.DirectionsWalk, Palette.Red, Palette.RedSoft, size = 42)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Left behind at ${hero.place ?: coords(hero)}", style = Type.Row)
                            Text(
                                listOfNotNull(Format.clock(hero.time), Format.ago(now, hero.time), Format.accuracy(hero.accuracyM)).joinToString(" · "),
                                style = Type.Sub,
                            )
                        }
                    }
                    BudsMap(
                        lat = hero.lat!!,
                        lng = hero.lng!!,
                        accuracyM = hero.accuracyM,
                        modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(18.dp)),
                        interactive = false,
                        alert = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Directions", Modifier.weight(1f)) { navigateTo(context, hero.lat!!, hero.lng!!) }
                        PillButton("I have them", Modifier.weight(1f), style = PillStyle.Neutral) {
                            LeftBehind.disarm(context)
                            Alerts.clearLeftBehind(context)
                            dismissedHero = true
                        }
                    }
                }
            }
        }

        if (events.isEmpty()) {
            item {
                Card {
                    Text("Nothing yet", style = Type.Headline)
                    Text("Put the buds in the case and take them out. Each change lands here with where it happened.", style = Type.Sub.copy(fontSize = 14.sp))
                }
            }
        }

        groups.forEach { (day, dayEvents) ->
            item { SectionLabel(day) }
            item {
                Group {
                    dayEvents.forEachIndexed { index, e ->
                        EventRow(e, last = index == dayEvents.lastIndex, onClick = { openMap(context, e) })
                    }
                }
            }
        }
        item { Spacer(Modifier.height(120.dp)) }
    }
}

@Composable
private fun EventRow(e: BudEvent, last: Boolean, onClick: () -> Unit) {
    val (icon, tint, bg) = when (e.type) {
        EventType.CONNECTED -> Triple(Sym.Check, Palette.Green, Palette.GreenSoft)
        EventType.DISCONNECTED -> Triple(Sym.DirectionsWalk, Palette.Red, Palette.RedSoft)
        EventType.APPEARED -> Triple(Sym.Bluetooth, Palette.Blue, Palette.BlueSoft)
        EventType.DISAPPEARED -> Triple(Sym.LinkOff, Palette.Grey, Palette.GreySoft)
        EventType.BATTERY -> Triple(Sym.BatteryAlert, Palette.Orange, Palette.OrangeSoft)
        EventType.TAG_LEFT -> Triple(Sym.DirectionsWalk, Palette.Red, Palette.RedSoft)
        EventType.TAG_BACK -> Triple(Sym.Check, Palette.Green, Palette.GreenSoft)
        EventType.TAG_BATTERY -> Triple(Sym.BatteryAlert, Palette.Orange, Palette.OrangeSoft)
    }
    ListRow(
        title = listOfNotNull(e.item, Format.title(e.type, e.battery)).joinToString(" · "),
        detail = listOfNotNull(
            e.place ?: if (e.hasLocation) coords(e) else "Location unavailable",
            Format.accuracy(e.accuracyM),
            if (e.moving) "while moving" else null,
        ).joinToString(" · "),
        leading = { IconDot(icon, tint, bg, size = 40) },
        onClick = if (e.hasLocation) onClick else null,
    ) {
        Text(Format.clock(e.time), style = Type.Sub.copy(fontSize = 12.sp))
    }
    if (!last) RowDivider(74.dp)
}
