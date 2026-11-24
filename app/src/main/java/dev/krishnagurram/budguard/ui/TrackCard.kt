package dev.krishnagurram.budguard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.krishnagurram.budguard.data.Geo
import dev.krishnagurram.budguard.data.TrackMode

/** Everything the tracking sheet shows. Plain data so the sheet can be screenshot-tested. */
data class TrackUi(
    val label: String,
    val icon: String,
    val isTag: Boolean,
    val mode: TrackMode,
    val connected: Boolean = false,
    val distanceM: Double? = null,
    val bearingDeg: Double? = null,
    val turnDeg: Double? = null,
    val seenAgo: String? = null,
    val place: String? = null,
    val accuracyM: Double? = null,
    val playing: Boolean = false,
    val refreshing: Boolean = false,
    val note: String? = null,
)

/** The Find My-style sheet at the bottom of the tracking screen. */
@Composable
fun TrackCard(
    ui: TrackUi,
    modifier: Modifier = Modifier,
    onSound: () -> Unit = {},
    onDirections: () -> Unit = {},
    onNearby: () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Palette.Card)
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 18.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(Palette.Rule))
        Spacer(Modifier.height(14.dp))

        // Header: what it is and, in one line, where.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconDot(iconFor(ui.icon), tint = Palette.Ink, bg = Palette.GreySoft, size = 48)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(ui.label, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle(ui), fontSize = 14.sp, color = Palette.Quiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(Modifier.height(18.dp))
        Hero(ui)
        Spacer(Modifier.height(18.dp))

        // Actions, like Find My's tiles.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val soundLabel = when {
                ui.playing -> "Stop"
                ui.isTag -> "Ring"
                else -> "Play sound"
            }
            Tile(
                label = soundLabel,
                icon = if (ui.isTag) Icons.Filled.Notifications else Icons.Filled.PlayArrow,
                active = ui.playing,
                modifier = Modifier.weight(1f),
                onClick = onSound,
            )
            if (ui.mode != TrackMode.WITH_YOU) {
                Tile("Directions", Icons.Filled.Send, active = false, modifier = Modifier.weight(1f), onClick = onDirections)
            }
            Tile(
                label = if (ui.isTag) "Find nearby" else "Hot / cold",
                icon = Icons.Filled.Search,
                active = false,
                modifier = Modifier.weight(1f),
                onClick = onNearby,
            )
        }

        val foot = ui.note ?: footer(ui)
        if (foot != null) {
            Spacer(Modifier.height(12.dp))
            Text(foot, fontSize = 13.sp, color = Palette.Quiet)
        }
    }
}

private fun subtitle(ui: TrackUi): String = when (ui.mode) {
    TrackMode.WITH_YOU -> if (ui.connected) "Connected to this phone" else "Traveling with this phone"
    else -> listOfNotNull(ui.place?.let { "Near $it" }, ui.seenAgo?.let { "seen $it" }).joinToString(" · ").ifEmpty { "Last known spot" }
}

private fun footer(ui: TrackUi): String? = when {
    ui.refreshing -> "Asking Samsung for a fresh spot…"
    ui.mode == TrackMode.WITH_YOU && ui.isTag -> "Its spot comes from your phone, so the map shows you. You'll get an alert if you walk away from it."
    ui.mode == TrackMode.WITH_YOU -> "You'll get an alert with the spot if you walk away from them."
    ui.accuracyM != null && ui.mode != TrackMode.LOCATING -> "Spot accurate to ${Geo.accuracyText(ui.accuracyM)}"
    else -> null
}

@Composable
private fun Hero(ui: TrackUi) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        when (ui.mode) {
            TrackMode.WITH_YOU -> Badge(Palette.Green) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            TrackMode.NEARBY -> Badge(Palette.Green) { Rings(Color.White) }
            TrackMode.AWAY -> Badge(if (ui.turnDeg == null) Palette.Grey else Palette.Blue) { Arrow(ui.turnDeg) }
            TrackMode.LOCATING -> Badge(Palette.Grey) { Rings(Color.White) }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                when (ui.mode) {
                    TrackMode.WITH_YOU -> "With you"
                    TrackMode.NEARBY -> "Nearby"
                    TrackMode.AWAY -> Geo.distanceText(ui.distanceM ?: 0.0)
                    TrackMode.LOCATING -> "Finding you…"
                },
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Palette.Ink,
            )
            Text(
                when (ui.mode) {
                    TrackMode.WITH_YOU -> if (ui.isTag) "Checked ${ui.seenAgo ?: "recently"}" else "Nothing to find"
                    TrackMode.NEARBY -> if (ui.isTag) "Within shouting distance. Ring it." else "Close by. Play a sound."
                    TrackMode.AWAY -> listOfNotNull(
                        ui.bearingDeg?.let { "Head ${Geo.compassWord(it)}" },
                        ui.distanceM?.let { Geo.walkText(it) },
                    ).joinToString(" · ")
                    TrackMode.LOCATING -> "Allow location if this stays here"
                },
                fontSize = 15.sp,
                color = Palette.Quiet,
            )
        }
    }
}

@Composable
private fun Badge(color: Color, content: @Composable () -> Unit) {
    Box(Modifier.size(72.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Arrow(turnDeg: Double?) {
    Canvas(Modifier.size(36.dp).rotate((turnDeg ?: 0.0).toFloat())) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w / 2, 0f)
            lineTo(w * 0.92f, h)
            lineTo(w / 2, h * 0.74f)
            lineTo(w * 0.08f, h)
            close()
        }
        drawPath(path, Color.White)
    }
}

@Composable
private fun Rings(color: Color) {
    Canvas(Modifier.size(44.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(color, radius = size.width * 0.14f, center = c)
        drawCircle(color.copy(alpha = 0.7f), radius = size.width * 0.32f, center = c, style = Stroke(width = 3.dp.toPx()))
        drawCircle(color.copy(alpha = 0.4f), radius = size.width * 0.48f, center = c, style = Stroke(width = 3.dp.toPx()))
    }
}

@Composable
private fun Tile(label: String, icon: ImageVector, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) Palette.Blue else Palette.GreySoft)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(if (active) Color.White.copy(alpha = 0.2f) else Palette.Blue),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else Palette.Ink, maxLines = 1)
    }
}
