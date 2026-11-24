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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
            refreshTag(context, targetId)
            tagVersion++
            delay(60_000)
        }
    }

    val distance = if (me != null && target != null) TagRules.distanceM(me!!.latitude, me!!.longitude, target.lat, target.lng) else null
    val bearing = if (me != null && target != null) Geo.bearingDeg(me!!.latitude, me!!.longitude, target.lat, target.lng) else null
    val close = distance != null && distance <= Geo.CLOSE_M

    // Keep the map's layers and camera in step with you and the item.
    LaunchedEffect(me, target, follow, styleReady) {
        val m = map ?: return@LaunchedEffect
        val style = m.style ?: return@LaunchedEffect
        if (!styleReady || target == null) return@LaunchedEffect
        val item = Point.fromLngLat(target.lng, target.lat)
        style.getSourceAs<GeoJsonSource>("target")?.setGeoJson(Feature.fromGeometry(item))
        val here = me
        if (here != null) {
            runCatching { m.locationComponent.forceLocationUpdate(here) }
            style.getSourceAs<GeoJsonSource>("route")
                ?.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(listOf(Point.fromLngLat(here.longitude, here.latitude), item))))
        }
        if (!follow) return@LaunchedEffect
        val bottomPad = with(density) { 330.dp.toPx() }.toInt()
        val sidePad = with(density) { 56.dp.toPx() }.toInt()
        val topPad = with(density) { 110.dp.toPx() }.toInt()
        if (here == null || (distance ?: 0.0) < 15) {
            m.easeCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(target.lat, target.lng)).zoom(18.0).build()), 500)
        } else {
            val bounds = LatLngBounds.Builder().include(LatLng(here.latitude, here.longitude)).include(LatLng(target.lat, target.lng)).build()
            m.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, sidePad, topPad, sidePad, bottomPad), 500)
        }
    }

    Box(modifier.fillMaxSize()) {
        TrackMap(
            onReady = { m ->
                map = m
                styleReady = true
            },
            onUserPan = { follow = false },
        )

        Row(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack, modifier = Modifier.shadow(6.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(Color.White)) {
                Text("‹ Back", color = Palette.Ink)
            }
            if (!follow) {
                TextButton(
                    onClick = { follow = true },
                    modifier = Modifier.shadow(6.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(Color.White),
                ) { Text("Re-center", color = Palette.Blue) }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(Palette.Card).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (target == null) {
                Text("No saved spot for this item yet", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
            } else {
            Text(target.label, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
            Text(
                listOfNotNull(
                    if (target.withYou) "With you" else target.place?.let { "At $it" },
                    "seen ${Format.ago(System.currentTimeMillis(), target.seenAt)}",
                    target.accuracyM?.let { Geo.accuracyText(it) },
                ).joinToString(" · "),
                fontSize = 14.sp,
                color = Palette.Quiet,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                val turn = if (bearing != null && heading != null) Geo.turnDeg(bearing, heading!!) else null
                Arrow(turnDeg = turn, close = close)
                Spacer(Modifier.width(18.dp))
                Column {
                    Text(
                        when {
                            me == null -> "Finding you…"
                            close -> "You're close"
                            else -> Geo.distanceText(distance!!)
                        },
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Palette.Ink,
                    )
                    Text(
                        when {
                            me == null -> "Allow location if this stays here"
                            close && target.isTag -> "Ring it, or use Samsung's arrow"
                            close -> "Play a sound or use the hot/cold meter"
                            else -> "Head ${Geo.compassWord(bearing!!)} · ${Geo.walkText(distance!!)}"
                        },
                        fontSize = 14.sp,
                        color = Palette.Quiet,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (close) {
                            if (target.isTag) openSamsungFind(context) else onNearby()
                        } else {
                            navigateTo(context, target.lat, target.lng)
                        }
                    },
                    colors = blueButton,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f),
                ) { Text(if (close) (if (target.isTag) "Samsung arrow" else "Hot/cold") else "Directions") }
                Button(
                    onClick = {
                        scope.launch {
                            if (target.isTag) {
                                note = "Asking Samsung to ring…"
                                val s = SamsungFind.connect().getOrNull()
                                val d = s?.let { session -> SamsungFind.devices(session).getOrNull()?.firstOrNull { it.id == targetId } }
                                note = if (s != null && d != null) {
                                    SamsungFind.ring(s, d, start = !playing).fold(
                                        onSuccess = { playing = !playing; if (playing) "Ringing" else "Stopped" },
                                        onFailure = { "Ring failed: ${it.message}" },
                                    )
                                } else {
                                    "Sign in to Samsung again from Items"
                                }
                            } else if (playing) {
                                note = BudsSound.stop()
                                playing = false
                            } else {
                                note = BudsSound.start(context, prefs.budsAddress)
                                playing = BudsSound.isPlaying
                            }
                        }
                    },
                    colors = softButton,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f),
                ) { Text(if (playing) "Stop" else if (target.isTag) "Ring" else "Play sound") }
            }
            note?.let { Text(it, fontSize = 13.sp, color = Palette.Quiet) }
            }
        }
    }
}

@Composable
private fun Arrow(turnDeg: Double?, close: Boolean) {
    val color = when {
        close -> Palette.Green
        turnDeg == null -> Palette.Grey
        else -> Palette.Blue
    }
    Box(Modifier.size(84.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(56.dp).rotate((turnDeg ?: 0.0).toFloat())) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w / 2, 0f)
                lineTo(w, h)
                lineTo(w / 2, h * 0.72f)
                lineTo(0f, h)
                close()
            }
            if (close) {
                drawCircle(color, radius = w / 3, center = Offset(w / 2, h / 2))
            } else {
                drawPath(path, color)
            }
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
                            PropertyFactory.circleRadius(26f),
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
        )
    }
    val t = ItemStore.items(context).firstOrNull { it.id == id } ?: return null
    val lat = t.lat ?: return null
    val lng = t.lng ?: return null
    return Target(t.label, lat, lng, t.accuracyM, t.fixTime, t.place, isTag = true, withYou = t.state == dev.krishnagurram.budguard.data.TagState.WITH_YOU)
}

private suspend fun refreshTag(context: Context, id: String) {
    val session = SamsungFind.connect().getOrNull() ?: return
    val device = SamsungFind.devices(session).getOrNull()?.firstOrNull { it.id == id } ?: return
    val fix = SamsungFind.status(session, device, refresh = true).getOrNull()?.fix ?: return
    ItemStore.update(context, id) { old ->
        if (fix.time >= old.fixTime) old.copy(lat = fix.lat, lng = fix.lng, accuracyM = fix.accuracyM, fixTime = fix.time) else old
    }
}
