package dev.krishnagurram.budguard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.krishnagurram.budguard.data.BudEvent
import dev.krishnagurram.budguard.data.Format
import dev.krishnagurram.budguard.data.Geo
import dev.krishnagurram.budguard.data.ItemStore
import dev.krishnagurram.budguard.data.Prefs
import dev.krishnagurram.budguard.data.TagRules
import dev.krishnagurram.budguard.data.TrackMode
import dev.krishnagurram.budguard.tracking.BudsSound
import dev.krishnagurram.budguard.tracking.SamsungFind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val TRACK_STYLE = "https://tiles.openfreemap.org/styles/liberty"
const val TRACK_BUDS = "buds"

private data class Target(
    val label: String,
    val lat: Double,
    val lng: Double,
    val accuracyM: Double?,
    val seenAt: Long,
    val place: String?,
    val isTag: Boolean,
    val withYou: Boolean,
    val connected: Boolean = false,
    val icon: String = "case",
)

/**
 * Live tracking, like Find My: your moving dot, the item, the line between,
 * distance, walk time and a compass arrow. GPS and compass run only while
 * this screen is open and stop the moment it isn't.
 */
@SuppressLint("MissingPermission")
@Composable
fun TrackScreen(modifier: Modifier, targetId: String, events: List<BudEvent>, onBack: () -> Unit, onNearby: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val prefs = remember { Prefs(context) }

    var tagVersion by remember { mutableIntStateOf(0) }
    val target = remember(targetId, events, tagVersion) { resolveTarget(context, targetId, events) }

    var me by remember { mutableStateOf<Location?>(null) }
    var heading by remember { mutableStateOf<Double?>(null) }
    var follow by remember { mutableStateOf(true) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    // GPS only while this screen is in front.
    LifecycleResumeEffect(Unit) {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { me = it }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000).setMinUpdateIntervalMillis(1_000).build()
        runCatching { client.requestLocationUpdates(request, callback, Looper.getMainLooper()) }
        onPauseOrDispose { client.removeLocationUpdates(callback) }
    }

    // Compass only while this screen is in front.
    LifecycleResumeEffect(Unit) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val rotation = sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            private val matrix = FloatArray(9)
            private val angles = FloatArray(3)
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, angles)
                var deg = Math.toDegrees(angles[0].toDouble())
                me?.let { loc ->
                    deg += GeomagneticField(loc.latitude.toFloat(), loc.longitude.toFloat(), loc.altitude.toFloat(), System.currentTimeMillis()).declination
                }
                heading = Geo.smoothHeading(heading, (deg + 360) % 360)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (rotation != null) sensors?.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_UI)
        onPauseOrDispose { sensors?.unregisterListener(listener) }
    }

    // Tags: ask Samsung for a fresh spot now and every 60 s while open.
    LaunchedEffect(targetId) {
        if (targetId == TRACK_BUDS) return@LaunchedEffect
        while (true) {
            refreshing = true
            refreshTag(context, targetId)
            refreshing = false
            tagVersion++
            delay(60_000)
        }
    }

    val distance = if (me != null && target != null) TagRules.distanceM(me!!.latitude, me!!.longitude, target.lat, target.lng) else null
    val bearing = if (me != null && target != null) Geo.bearingDeg(me!!.latitude, me!!.longitude, target.lat, target.lng) else null
    val mode = if (target == null) TrackMode.LOCATING else Geo.trackMode(
        certain = target.connected,
        flaggedWithYou = target.withYou,
        distanceM = distance,
        itemAccuracyM = target.accuracyM,
        phoneAccuracyM = me?.accuracy?.toDouble(),
    )

    // Map layers: the item's pin and the dashed line to it, only when it's somewhere else.
    LaunchedEffect(me, target, mode, styleReady) {
        val m = map ?: return@LaunchedEffect
        val style = m.style ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        me?.let { runCatching { m.locationComponent.forceLocationUpdate(it) } }
        val empty = FeatureCollection.fromFeatures(emptyList<Feature>())
        val item = target?.let { Point.fromLngLat(it.lng, it.lat) }
        val here = me
        style.getSourceAs<GeoJsonSource>("target")?.let { src ->
            if (item == null || mode == TrackMode.WITH_YOU) src.setGeoJson(empty) else src.setGeoJson(Feature.fromGeometry(item))
        }
        style.getSourceAs<GeoJsonSource>("route")?.let { src ->
            if (item != null && here != null && mode == TrackMode.AWAY) {
                src.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(listOf(Point.fromLngLat(here.longitude, here.latitude), item))))
            } else {
                src.setGeoJson(empty)
            }
        }
    }

    // Camera: frame the item, not you. It moves when the item moves or the state changes,
    // not every time your GPS dot twitches.
    val hasMe = me != null
    LaunchedEffect(mode, target?.lat, target?.lng, follow, styleReady, hasMe) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady || !follow || target == null) return@LaunchedEffect
        val here = me
        val sheet = with(density) { 380.dp.toPx() }.toInt()
        val side = with(density) { 64.dp.toPx() }.toInt()
        val top = with(density) { 120.dp.toPx() }.toInt()
        // Padding keeps the point of interest above the sheet.
        fun center(lat: Double, lng: Double, zoom: Double) = m.easeCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(LatLng(lat, lng)).zoom(zoom).padding(0.0, top.toDouble(), 0.0, sheet.toDouble()).build(),
            ),
            700,
        )
        when {
            mode == TrackMode.WITH_YOU -> if (here != null) center(here.latitude, here.longitude, 16.0) else center(target.lat, target.lng, 16.0)
            mode == TrackMode.AWAY && here != null -> {
                val bounds = LatLngBounds.Builder().include(LatLng(here.latitude, here.longitude)).include(LatLng(target.lat, target.lng)).build()
                val framed = m.getCameraForLatLngBounds(bounds, intArrayOf(side, top, side, sheet))
                if (framed != null) m.easeCamera(CameraUpdateFactory.newCameraPosition(framed), 700) else center(target.lat, target.lng, 15.0)
            }
            mode == TrackMode.NEARBY -> center(target.lat, target.lng, 18.0)
            else -> center(target.lat, target.lng, 16.0)
        }
    }

    val now = System.currentTimeMillis()
    val ui = target?.let {
        TrackUi(
            label = it.label,
            icon = it.icon,
            isTag = it.isTag,
            mode = mode,
            connected = it.connected,
            distanceM = distance,
            bearingDeg = bearing,
            turnDeg = if (bearing != null && heading != null) Geo.turnDeg(bearing, heading!!) else null,
            seenAgo = Format.ago(now, it.seenAt),
            place = it.place,
            accuracyM = it.accuracyM,
            playing = playing,
            refreshing = refreshing,
            note = note,
        )
    }

    Box(modifier.fillMaxSize()) {
        TrackMap(
            onReady = { m ->
                map = m
                styleReady = true
            },
            onUserPan = { follow = false },
        )

        Row(Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(
                Modifier.size(46.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(Color.White).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Palette.Ink) }
            if (!follow) {
                Row(
                    Modifier.height(46.dp).shadow(8.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(Color.White)
                        .clickable { follow = true }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Palette.Blue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Re-center", color = Palette.Blue, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        val sheetModifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(10.dp).shadow(20.dp, RoundedCornerShape(32.dp))
        if (ui == null) {
            Column(
                sheetModifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Palette.Card).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("No saved spot yet", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
                Text("Once your phone or Samsung reports where it is, it shows up here.", fontSize = 14.sp, color = Palette.Quiet)
            }
        } else {
            TrackCard(
                ui = ui,
                modifier = sheetModifier,
                onSound = {
                    scope.launch {
                        if (target.isTag) {
                            note = if (playing) "Stopping…" else "Asking Samsung to ring…"
                            val s = SamsungFind.connect().getOrNull()
                            val d = s?.let { session -> SamsungFind.devices(session).getOrNull()?.firstOrNull { it.id == targetId } }
                            note = if (s != null && d != null) {
                                SamsungFind.ring(s, d, start = !playing).fold(
                                    onSuccess = { playing = !playing; if (playing) "Ringing. Tap Stop when you have it." else null },
                                    onFailure = { "Couldn't ring: ${it.message}" },
                                )
                            } else {
                                "Sign in to Samsung again from Items"
                            }
                        } else if (playing) {
                            note = BudsSound.stop()
                            playing = false
                        } else {
                            note = "Connecting…"
                            note = BudsSound.start(context, prefs.budsAddress)
                            playing = BudsSound.isPlaying
                        }
                    }
                },
                onDirections = { navigateTo(context, target.lat, target.lng) },
                onNearby = { if (target.isTag) openSamsungFind(context) else onNearby() },
            )
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun TrackMap(onReady: (MapLibreMap) -> Unit, onUserPan: () -> Unit) {
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
        modifier = Modifier.fillMaxSize(),
        factory = {
            mapView.getMapAsync { map ->
                map.uiSettings.isLogoEnabled = false
                map.uiSettings.isCompassEnabled = false
                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) onUserPan()
                }
                map.setStyle(TRACK_STYLE) { style ->
                    style.addSource(GeoJsonSource("route", FeatureCollection.fromFeatures(emptyList<Feature>())))
                    style.addSource(GeoJsonSource("target", FeatureCollection.fromFeatures(emptyList<Feature>())))
                    style.addLayer(
                        LineLayer("route-line", "route").withProperties(
                            PropertyFactory.lineColor("#1F6FEB"),
                            PropertyFactory.lineWidth(4f),
                            PropertyFactory.lineOpacity(0.8f),
                            PropertyFactory.lineDasharray(arrayOf(1.5f, 1.5f)),
                        ),
                    )
                    style.addLayer(
                        CircleLayer("target-halo", "target").withProperties(
                            PropertyFactory.circleRadius(30f),
                            PropertyFactory.circleColor("#D92D20"),
                            PropertyFactory.circleOpacity(0.15f),
                        ),
                    )
                    style.addLayer(
                        CircleLayer("target-dot", "target").withProperties(
                            PropertyFactory.circleRadius(11f),
                            PropertyFactory.circleColor("#D92D20"),
                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                            PropertyFactory.circleStrokeWidth(3f),
                        ),
                    )
                    runCatching {
                        val lc = map.locationComponent
                        lc.activateLocationComponent(
                            LocationComponentActivationOptions.builder(context, style).useDefaultLocationEngine(false).build(),
                        )
                        lc.isLocationComponentEnabled = true
                        lc.cameraMode = CameraMode.NONE
                        lc.renderMode = RenderMode.COMPASS
                    }
                    onReady(map)
                }
            }
            mapView
        },
    )
}

private fun resolveTarget(context: Context, id: String, events: List<BudEvent>): Target? {
    if (id == TRACK_BUDS) {
        val spot = events.firstOrNull { it.isLoss && it.hasLocation } ?: events.firstOrNull { it.hasLocation } ?: return null
        val latest = events.firstOrNull { it.isLoss || it.isPresence }
        return Target(
            label = Prefs(context).budsName ?: "Galaxy Buds",
            lat = spot.lat!!,
            lng = spot.lng!!,
            accuracyM = spot.accuracyM?.toDouble(),
            seenAt = spot.time,
            place = spot.place,
            isTag = false,
            withYou = latest?.isPresence == true,
            connected = latest?.isPresence == true,
        )
    }
    val t = ItemStore.items(context).firstOrNull { it.id == id } ?: return null
    val lat = t.lat ?: return null
    val lng = t.lng ?: return null
    return Target(t.label, lat, lng, t.accuracyM, t.fixTime, t.place, isTag = true, withYou = t.state == dev.krishnagurram.budguard.data.TagState.WITH_YOU, icon = t.icon)
}

private suspend fun refreshTag(context: Context, id: String) {
    val session = SamsungFind.connect().getOrNull() ?: return
    val device = SamsungFind.devices(session).getOrNull()?.firstOrNull { it.id == id } ?: return
    val fix = SamsungFind.status(session, device, refresh = true).getOrNull()?.fix ?: return
    ItemStore.update(context, id) { old ->
        if (fix.time >= old.fixTime) old.copy(lat = fix.lat, lng = fix.lng, accuracyM = fix.accuracyM, fixTime = fix.time) else old
    }
}
