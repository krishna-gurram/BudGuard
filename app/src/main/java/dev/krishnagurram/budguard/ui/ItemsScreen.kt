package dev.krishnagurram.budguard.ui

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
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.krishnagurram.budguard.data.BudEvent
import dev.krishnagurram.budguard.data.Format
import dev.krishnagurram.budguard.data.ItemStore
import dev.krishnagurram.budguard.data.TagItem
import dev.krishnagurram.budguard.data.TagState
import dev.krishnagurram.budguard.tracking.SamsungFind
import dev.krishnagurram.budguard.tracking.TagWatch
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val STYLE = "https://tiles.openfreemap.org/styles/liberty"

data class MapPin(val name: String, val lat: Double, val lng: Double, val color: String)

fun iconFor(kind: String): ImageVector = when (kind) {
    "wallet" -> Icons.Filled.AccountBox
    "keys" -> Icons.Filled.Lock
    "bag" -> Icons.Filled.ShoppingCart
    "case" -> Icons.Filled.Star
    else -> Icons.Filled.Place
}

fun stateText(t: TagItem, now: Long): Pair<String, Color> = when (t.state) {
    TagState.WITH_YOU -> "With you" to Palette.Green
    TagState.LEFT_SAFE -> "At ${t.safePlace ?: "a safe place"}" to Palette.Blue
    TagState.LEFT_BEHIND -> "Left at ${t.place ?: "last spot"} · ${Format.ago(now, t.stateSince)}" to Palette.Red
    TagState.UNKNOWN -> (if (t.hasFix) "Last seen ${Format.ago(now, t.fixTime)}" else "Waiting for a first spot") to Palette.Quiet
}

/** Several items on one map: dots from rows, fitted to show them all. */
@Composable
fun ItemsMap(pins: List<MapPin>, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            runCatching {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }
    }
    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.getMapAsync { map ->
                map.uiSettings.isLogoEnabled = false
                map.uiSettings.isCompassEnabled = false
                val features = pins.map { p ->
                    Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat)).apply {
                        addStringProperty("color", p.color)
                        addStringProperty("name", p.name)
                    }
                }
                val data = FeatureCollection.fromFeatures(features)
                val draw = { style: Style ->
                    val existing = style.getSourceAs<GeoJsonSource>("items")
                    if (existing == null) {
                        style.addSource(GeoJsonSource("items", data))
                        style.addLayer(
                            CircleLayer("items-dots", "items").withProperties(
                                PropertyFactory.circleRadius(11f),
                                PropertyFactory.circleColor(Expression.get("color")),
                                PropertyFactory.circleStrokeColor("#FFFFFF"),
                                PropertyFactory.circleStrokeWidth(3f),
                            ),
                        )
                        style.addLayer(
                            SymbolLayer("items-names", "items").withProperties(
                                PropertyFactory.textField(Expression.get("name")),
                                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                                PropertyFactory.textSize(12f),
                                PropertyFactory.textOffset(arrayOf(0f, 1.6f)),
                                PropertyFactory.textColor("#111114"),
                                PropertyFactory.textHaloColor("#FFFFFF"),
                                PropertyFactory.textHaloWidth(1.5f),
                            ),
                        )
                    } else {
                        existing.setGeoJson(data)
                    }
                }
                val loaded = map.style
                if (loaded != null && loaded.isFullyLoaded) draw(loaded) else map.setStyle(STYLE) { draw(it) }

                val distinct = pins.distinctBy { "%.4f,%.4f".format(it.lat, it.lng) }
                when {
                    distinct.isEmpty() -> Unit
                    distinct.size == 1 -> map.cameraPosition =
                        CameraPosition.Builder().target(LatLng(distinct[0].lat, distinct[0].lng)).zoom(15.0).build()
                    else -> runCatching {
                        val bounds = LatLngBounds.Builder().apply { distinct.forEach { include(LatLng(it.lat, it.lng)) } }.build()
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 140))
                    }
                }
            }
        },
    )
}

@Composable
fun ItemsScreen(modifier: Modifier, events: List<BudEvent>, onBuds: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val items = remember(version) { ItemStore.items(context) }
    var signedIn by remember { mutableStateOf(SamsungFind.isSignedIn()) }
    var signingIn by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val now = System.currentTimeMillis()

    fun refresh() {
        scope.launch {
            note = "Checking with Samsung (about 10 s)…"
            note = TagWatch.check(context, "manual refresh")
            version++
        }
    }

    if (signingIn) {
        SamsungSignIn(onCancel = { signingIn = false }, onDone = {
            signingIn = false
            signedIn = true
            refresh()
        })
        return
    }

    val budsSpot = events.firstOrNull { it.isLoss && it.hasLocation }
    val budsLatest = events.firstOrNull { it.isLoss || it.isPresence }
    val budsWithYou = budsLatest?.isPresence == true
    val pins = buildList {
        if (budsSpot != null && !budsWithYou) add(MapPin("Buds", budsSpot.lat!!, budsSpot.lng!!, "#1F6FEB"))
        items.filter { it.hasFix }.forEach { t ->
            val color = when (t.state) {
                TagState.LEFT_BEHIND -> "#D92D20"
                TagState.WITH_YOU -> "#12A150"
                else -> "#C2410C"
            }
            add(MapPin(t.label, t.lat!!, t.lng!!, color))
        }
    }

    Box(modifier.fillMaxSize()) {
        if (pins.isNotEmpty()) {
            ItemsMap(pins, Modifier.fillMaxWidth().height(330.dp))
        } else {
            Box(Modifier.fillMaxWidth().height(330.dp).background(Color(0xFFE7EAEE)), contentAlignment = Alignment.Center) {
                Text("Your items appear here once BudGuard has a spot for them", fontSize = 14.sp, color = Palette.Quiet)
            }
        }
        Column(
            Modifier.fillMaxSize().padding(top = 290.dp).clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(Palette.Bg).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.padding(top = 20.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Items", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink, modifier = Modifier.weight(1f))
                if (signedIn) TextButton(onClick = { refresh() }) { Text("Refresh") }
            }
            note?.let { Text(it, fontSize = 13.sp, color = Palette.Quiet, modifier = Modifier.padding(horizontal = 4.dp)) }

            if (!signedIn) {
                Card {
                    Text("Add your SmartTags", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                    Text(
                        "Sign in to Samsung once. BudGuard then reads where your tags are and warns you when one is left behind.",
                        fontSize = 14.sp,
                        color = Palette.Quiet,
                    )
                    Button(onClick = { signingIn = true }, colors = blueButton, shape = RoundedCornerShape(50)) { Text("Sign in to Samsung") }
                }
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Palette.Card).padding(vertical = 4.dp)) {
                ItemRow(
                    icon = Icons.Filled.Place,
                    tint = Palette.Blue,
                    title = "Galaxy Buds",
                    line = when {
                        budsLatest == null -> "No events yet" to Palette.Quiet
                        budsWithYou -> "With you" to Palette.Green
                        else -> "Left at ${budsSpot?.place ?: "last spot"} · ${Format.ago(now, budsLatest.time)}" to Palette.Red
                    },
                    detail = null,
                    onClick = onBuds,
                )
                items.forEach { t ->
                    Box(Modifier.fillMaxWidth().padding(start = 74.dp).height(1.dp).background(Palette.Rule))
                    ItemRow(
                        icon = iconFor(t.icon),
                        tint = if (t.state == TagState.LEFT_BEHIND) Palette.Red else Palette.Orange,
                        title = t.label,
                        line = stateText(t, now),
                        detail = listOfNotNull(
                            t.battery?.let { "tag battery $it%" },
                            if (!t.alertsOn) "alerts off" else null,
                        ).joinToString(" · ").ifBlank { null },
                        onClick = { onOpen(t.id) },
                    )
                }
            }
            if (signedIn && items.isEmpty()) {
                Text("No tags yet. Tap Refresh after a few seconds.", fontSize = 13.sp, color = Palette.Quiet, modifier = Modifier.padding(horizontal = 8.dp))
            }
            Spacer(Modifier.height(110.dp))
        }
    }
}

@Composable
private fun ItemRow(icon: ImageVector, tint: Color, title: String, line: Pair<String, Color>, detail: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
            Text(line.first, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = line.second)
            if (detail != null) Text(detail, fontSize = 12.sp, color = Palette.Quiet)
        }
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.Quiet)
    }
}

@Composable
fun ItemDetailScreen(modifier: Modifier, id: String, onBack: () -> Unit, onTrack: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val item = remember(version) { ItemStore.items(context).firstOrNull { it.id == id } }
    var name by remember(item?.label) { mutableStateOf(item?.label.orEmpty()) }
    var note by remember { mutableStateOf<String?>(null) }
    var ringing by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()

    if (item == null) {
        Column(modifier.padding(16.dp)) {
            TextButton(onClick = onBack) { Text("‹ Items") }
            Text("This item is gone. Refresh Items.", color = Palette.Quiet)
        }
        return
    }

    fun ring(start: Boolean) {
        scope.launch {
            note = if (start) "Asking Samsung to ring…" else "Stopping…"
            val session = SamsungFind.connect().getOrNull()
            val device = session?.let { s -> SamsungFind.devices(s).getOrNull()?.firstOrNull { it.id == id } }
            note = if (session == null || device == null) {
                "Samsung sign-in expired; sign in again from Items"
            } else {
                SamsungFind.ring(session, device, start).fold(
                    onSuccess = { ringing = start; if (start) "Ring sent. A tag in range of any Galaxy phone will play." else "Stopped" },
                    onFailure = { "Ring failed: ${it.message}" },
                )
            }
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box {
            val lat = item.lat
            val lng = item.lng
            if (lat != null && lng != null) {
                BudsMap(lat, lng, item.accuracyM?.toFloat(), Modifier.fillMaxWidth().height(280.dp), alert = item.state == TagState.LEFT_BEHIND)
            } else {
                Box(Modifier.fillMaxWidth().height(280.dp).background(Color(0xFFE7EAEE)))
            }
            TextButton(
                onClick = onBack,
                modifier = Modifier.statusBarsPadding().padding(8.dp).clip(RoundedCornerShape(50)).background(Color.White),
            ) { Text("‹ Items") }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Card), contentAlignment = Alignment.Center) {
                    Icon(iconFor(item.icon), contentDescription = null, tint = Palette.Ink, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(item.label, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
                    val (text, color) = stateText(item, now)
                    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color)
                }
            }

            Card {
                Text(
                    listOfNotNull(
                        item.place ?: if (item.hasFix) String.format(java.util.Locale.US, "%.5f, %.5f", item.lat, item.lng) else "No spot yet",
                        if (item.fixTime > 0) "seen ${Format.ago(now, item.fixTime)}" else null,
                        item.accuracyM?.let { dev.krishnagurram.budguard.data.Geo.accuracyText(it) },
                    ).joinToString(" · "),
                    fontSize = 15.sp,
                    color = Palette.Ink,
                )
                Text("Tag battery ${item.battery?.let { "$it%" } ?: "unknown"} · Samsung name: ${item.samsungName}", fontSize = 13.sp, color = Palette.Quiet)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onTrack(item.id) },
                    enabled = item.hasFix,
                    colors = blueButton,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f),
                ) { Text("Track live") }
                Button(onClick = { ring(!ringing) }, colors = softButton, shape = RoundedCornerShape(50), modifier = Modifier.weight(1f)) {
                    Text(if (ringing) "Stop ring" else "Ring")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            note = "Checking with Samsung…"
                            note = TagWatch.check(context, "item refresh")
                            version++
                        }
                    },
                    colors = softButton,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f),
                ) { Text("Refresh") }
                Button(onClick = { openSamsungFind(context) }, colors = softButton, shape = RoundedCornerShape(50), modifier = Modifier.weight(1f)) {
                    Text("Arrow & Lost mode")
                }
            }
            if (item.state == TagState.LEFT_BEHIND) {
                Button(
                    onClick = { TagWatch.haveIt(context, item.id); version++ },
                    colors = blueButton,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("I have it") }
            }
            note?.let { Text(it, fontSize = 13.sp, color = Palette.Quiet) }

            SectionLabel("Settings")
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Notify when left behind", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
                        Text("Never at your safe places", fontSize = 13.sp, color = Palette.Quiet)
                    }
                    Switch(
                        checked = item.alertsOn,
                        onCheckedChange = { on -> ItemStore.update(context, id) { it.copy(alertsOn = on) }; version++ },
                        colors = SwitchDefaults.colors(checkedTrackColor = Palette.Blue, checkedThumbColor = Color.White),
                    )
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("wallet" to "Wallet", "keys" to "Keys", "bag" to "Bag", "case" to "Case", "tag" to "Other").forEach { (kind, label) ->
                        val selected = item.icon == kind
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) Color.White else Palette.Ink,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) Palette.Ink else Palette.Bg)
                                .clickable { ItemStore.update(context, id) { it.copy(icon = kind) }; version++ }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                Button(
                    onClick = { ItemStore.update(context, id) { it.copy(label = name.trim().ifBlank { it.samsungName }) }; version++ },
                    colors = softButton,
                    shape = RoundedCornerShape(50),
                ) { Text("Save name") }
            }
            Spacer(Modifier.height(110.dp))
        }
    }
}
