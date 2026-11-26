package dev.krishnagurram.budguard.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
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
    "wallet" -> Sym.AccountBalanceWallet
    "keys" -> Sym.Key
    "bag" -> Sym.Backpack
    "case" -> Sym.Earbuds
    else -> Sym.Sell
}

fun stateColors(t: TagItem): Pair<Color, Color> = when (t.state) {
    TagState.WITH_YOU -> Palette.Green to Palette.GreenSoft
    TagState.LEFT_SAFE -> Palette.Blue to Palette.BlueSoft
    TagState.LEFT_BEHIND -> Palette.Red to Palette.RedSoft
    TagState.UNKNOWN -> Palette.Orange to Palette.OrangeSoft
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
    if (!LocalMapsLive.current) {
        Box(modifier) {
            FakeMap(Modifier.fillMaxSize())
            pins.forEachIndexed { i, p ->
                val c = Color(android.graphics.Color.parseColor(p.color))
                Box(
                    Modifier.align(Alignment.TopStart).padding(start = (70 + i * 110).dp, top = (90 + (i % 2) * 70).dp).size(36.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape).background(Color.White).padding(4.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape).background(c),
                )
            }
        }
        return
    }
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

    Box(modifier.fillMaxSize().background(Palette.Bg)) {
        if (pins.isNotEmpty()) {
            ItemsMap(pins, Modifier.fillMaxWidth().height(400.dp))
        } else {
            Box(Modifier.fillMaxWidth().height(400.dp)) { FakeMap(Modifier.fillMaxSize()) }
        }
        Column(
            Modifier.fillMaxSize().padding(top = 340.dp)
                .shadow(18.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Palette.Bg).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHandle()
            Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Items", style = Type.Title, modifier = Modifier.weight(1f))
                if (signedIn) PillButton("Refresh", style = PillStyle.Soft, modifier = Modifier.height(40.dp)) { refresh() }
            }
            note?.let { Text(it, style = Type.Sub, modifier = Modifier.padding(horizontal = 6.dp)) }

            if (!signedIn) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Sym.Cloud, Palette.Blue)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Add your SmartTags", style = Type.Row)
                            Text("Sign in to Samsung once. BudGuard then knows where your tags are and warns you when one is left behind.", style = Type.Sub)
                        }
                    }
                    PillButton("Sign in to Samsung", Modifier.fillMaxWidth()) { signingIn = true }
                }
            }

            Group {
                ItemRow(
                    icon = Sym.Earbuds,
                    colors = if (budsWithYou || budsLatest == null) Palette.Blue to Palette.BlueSoft else Palette.Red to Palette.RedSoft,
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
                    RowDivider(78.dp)
                    ItemRow(
                        icon = iconFor(t.icon),
                        colors = stateColors(t),
                        title = t.label,
                        line = stateText(t, now),
                        detail = listOfNotNull(
                            t.battery?.let { "Tag battery ${Format.tagBattery(it)}" },
                            if (!t.alertsOn) "Alerts off" else null,
                        ).joinToString(" · ").ifBlank { null },
                        onClick = { onOpen(t.id) },
                    )
                }
            }
            if (signedIn && items.isEmpty()) {
                Text("No tags yet. Tap Refresh in a few seconds.", style = Type.Sub, modifier = Modifier.padding(horizontal = 8.dp))
            }
            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun ItemRow(icon: ImageVector, colors: Pair<Color, Color>, title: String, line: Pair<String, Color>, detail: String?, onClick: () -> Unit) {
    ListRow(
        title = title,
        detail = line.first,
        detailColor = line.second,
        extra = detail,
        leading = {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(colors.second), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = colors.first, modifier = Modifier.size(26.dp))
            }
        },
        onClick = onClick,
    ) { Chevron() }
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
        Column(modifier.statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            MapButton(Sym.ArrowBack, "Back to items", onClick = onBack)
            Text("This item is gone. Refresh Items.", style = Type.Body)
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

    val (accent, accentSoft) = stateColors(item)
    Box(modifier.fillMaxSize().background(Palette.Bg)) {
        val lat = item.lat
        val lng = item.lng
        if (lat != null && lng != null) {
            BudsMap(lat, lng, item.accuracyM?.toFloat(), Modifier.fillMaxWidth().height(380.dp), alert = item.state == TagState.LEFT_BEHIND, icon = iconFor(item.icon))
        } else {
            FakeMap(Modifier.fillMaxWidth().height(380.dp))
        }
        Row(Modifier.statusBarsPadding().padding(16.dp)) {
            MapButton(Sym.ArrowBack, "Back to items", onClick = onBack)
        }

        Column(
            Modifier.fillMaxSize().padding(top = 310.dp)
                .shadow(18.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Palette.Bg).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHandle()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(accentSoft), contentAlignment = Alignment.Center) {
                    Icon(iconFor(item.icon), contentDescription = null, tint = accent, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(item.label, style = Type.Title)
                    val (text, color) = stateText(item, now)
                    Text(text, style = Type.Sub.copy(fontSize = 14.sp, color = color, fontWeight = FontWeight.SemiBold))
                }
            }

            Group {
                ListRow(
                    title = listOfNotNull(
                        item.place ?: if (item.hasFix) String.format(java.util.Locale.US, "%.5f, %.5f", item.lat, item.lng) else "No spot yet",
                        if (item.fixTime > 0) Format.clock(item.fixTime) else null,
                    ).joinToString(" · "),
                    detail = listOfNotNull(
                        if (item.fixTime > 0) "Reported ${Format.ago(now, item.fixTime)}" else null,
                        item.accuracyM?.let { dev.krishnagurram.budguard.data.Geo.accuracyText(it) },
                    ).joinToString(" · ").ifBlank { null },
                    leading = { IconDot(Sym.LocationOn, Palette.Blue, Palette.BlueSoft, size = 40) },
                )
                RowDivider(74.dp)
                ListRow(
                    title = "Tag battery ${if (item.battery == null) "unknown" else Format.tagBattery(item.battery).lowercase()}",
                    detail = "Samsung name: ${item.samsungName}",
                    leading = { IconDot(Sym.BatteryFull, if ((item.battery ?: 100) <= 15) Palette.Orange else Palette.Green, if ((item.battery ?: 100) <= 15) Palette.OrangeSoft else Palette.GreenSoft, size = 40) },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Track live", Sym.NearMe, primary = true, enabled = item.hasFix) { onTrack(item.id) }
                ActionTile(if (ringing) "Stop" else "Ring tag", Sym.VolumeUp, active = ringing) { ring(!ringing) }
                ActionTile("Arrow", Sym.Explore) { openSamsungFind(context) }
                ActionTile("Refresh", Sym.Refresh) {
                    scope.launch {
                        note = "Checking with Samsung…"
                        note = TagWatch.check(context, "item refresh")
                        version++
                    }
                }
            }
            if (item.state == TagState.LEFT_BEHIND) {
                PillButton("I have it", Modifier.fillMaxWidth()) { TagWatch.haveIt(context, item.id); version++ }
            }
            note?.let { Text(it, style = Type.Sub, modifier = Modifier.padding(horizontal = 8.dp)) }

            Group {
                ListRow(
                    title = "Notify when left behind",
                    detail = "Never at your safe places",
                    leading = { IconTile(Sym.DirectionsWalk, Palette.Red) },
                ) {
                    Toggle(item.alertsOn) { on -> ItemStore.update(context, id) { it.copy(alertsOn = on) }; version++ }
                }
            }

            SectionLabel("Name and icon")
            Card {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("wallet" to "Wallet", "keys" to "Keys", "bag" to "Bag", "case" to "Buds case", "tag" to "Other").forEach { (kind, label) ->
                        Chip(label, item.icon == kind, idle = Palette.GreySoft) { ItemStore.update(context, id) { it.copy(icon = kind) }; version++ }
                    }
                }
                PillButton("Save name", Modifier.fillMaxWidth(), style = PillStyle.Soft) {
                    ItemStore.update(context, id) { it.copy(label = name.trim().ifBlank { it.samsungName }) }
                    version++
                }
            }
            Text(
                "Arrow opens Samsung Find, which has the tag's UWB pointer and Lost mode.",
                style = Type.Sub.copy(fontSize = 12.sp),
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            Spacer(Modifier.height(120.dp))
        }
    }
}
