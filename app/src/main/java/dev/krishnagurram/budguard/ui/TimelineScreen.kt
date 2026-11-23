package dev.krishnagurram.budguard.ui

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

private enum class Filter(val label: String) { All("All"), Away("Left behind"), Battery("Battery"), Moving("Moving") }

@Composable
fun TimelineScreen(modifier: Modifier, events: List<BudEvent>) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var dismissedHero by remember { mutableStateOf(false) }

    val shown = events.filter {
        when (filter) {
            Filter.All -> true
            Filter.Away -> it.isLoss
            Filter.Battery -> it.type == EventType.BATTERY
            Filter.Moving -> it.moving
        }
    }
    val hero = events.firstOrNull { it.isLoss || it.isPresence }?.takeIf { it.isLoss && it.hasLocation && !dismissedHero }
    val groups = shown.filter { it.id != hero?.id }.groupBy { Format.dayLabel(now, it.time) }
    val places = events.mapNotNull { it.place }.distinct().size
    val today = events.count { Format.dayLabel(now, it.time) == "Today" }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("Timeline", "$today events today · $places places") }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Filter.entries.toList()) { f ->
                    val selected = f == filter
                    Text(
                        f.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) Color.White else Palette.Ink,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) Palette.Ink else Palette.Card)
                            .clickable { filter = f }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }

        if (hero != null && (filter == Filter.All || filter == Filter.Away)) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Palette.Card)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconDot(Icons.Filled.LocationOn, Palette.Red, Palette.RedSoft)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Last left at ${hero.place ?: coords(hero)}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Palette.Ink,
                            )
                            Text(
                                listOfNotNull(Format.clock(hero.time), Format.ago(now, hero.time), Format.accuracy(hero.accuracyM))
                                    .joinToString(" · "),
                                fontSize = 13.sp,
                                color = Palette.Quiet,
                            )
                        }
                    }
                    BudsMap(
                        lat = hero.lat!!,
                        lng = hero.lng!!,
                        accuracyM = hero.accuracyM,
                        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp)),
                        interactive = false,
                        alert = true,
                    )
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { navigateTo(context, hero.lat!!, hero.lng!!) },
                            colors = blueButton,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f),
                        ) { Text("Directions") }
                        Button(
                            onClick = {
                                LeftBehind.disarm(context)
                                Alerts.clearLeftBehind(context)
                                dismissedHero = true
                            },
                            colors = softButton,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f),
                        ) { Text("I have them") }
                    }
                }
            }
        }

        if (events.isEmpty()) {
            item {
                Card {
                    Text("Nothing yet", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                    Text(
                        "Put the buds in the case and take them out. Each change lands here with where it happened.",
                        fontSize = 14.sp,
                        color = Palette.Quiet,
                    )
                }
            }
        }

        groups.forEach { (day, dayEvents) ->
            item { SectionLabel(day) }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Palette.Card).padding(vertical = 4.dp),
                ) {
                    dayEvents.forEachIndexed { index, e ->
                        EventRow(e, last = index == dayEvents.lastIndex, onClick = { openMap(context, e) })
                    }
                }
            }
        }
        item { Spacer(Modifier.height(110.dp)) }
    }
}

@Composable
private fun EventRow(e: BudEvent, last: Boolean, onClick: () -> Unit) {
    val (icon, tint, bg) = when (e.type) {
        EventType.CONNECTED -> Triple(Icons.Filled.CheckCircle, Palette.Green, Palette.GreenSoft)
        EventType.DISCONNECTED -> Triple(Icons.Filled.LocationOn, Palette.Red, Palette.RedSoft)
        EventType.APPEARED -> Triple(Icons.Filled.Add, Palette.Blue, Palette.BlueSoft)
        EventType.DISAPPEARED -> Triple(Icons.Filled.Clear, Palette.Grey, Palette.GreySoft)
        EventType.BATTERY -> Triple(Icons.Filled.Warning, Palette.Orange, Palette.OrangeSoft)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(enabled = e.hasLocation, onClick = onClick).padding(horizontal = 16.dp),
    ) {
        IconDot(icon, tint, bg)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Format.title(e.type, e.battery), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink, modifier = Modifier.weight(1f))
                Text(Format.clock(e.time), fontSize = 13.sp, color = Palette.Quiet)
            }
            Text(
                listOfNotNull(
                    e.place ?: if (e.hasLocation) coords(e) else "Location unavailable",
                    Format.accuracy(e.accuracyM),
                    if (e.moving) "while moving" else null,
                ).joinToString(" · "),
                fontSize = 13.sp,
                color = Palette.Quiet,
            )
        }
    }
    if (!last) {
        Row(Modifier.fillMaxWidth().padding(start = 70.dp, end = 16.dp).height(1.dp).background(Palette.Rule)) {}
    }
}
