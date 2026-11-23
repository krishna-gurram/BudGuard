package dev.krishnagurram.budguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.krishnagurram.budguard.R
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

// Free vector map built from OpenStreetMap data; no account or API key.
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/**
 * A map centred on the saved spot, with the buds pin and the accuracy circle
 * drawn on top. Tiles download from OpenFreeMap; the events themselves never leave the phone.
 */
@Composable
fun BudsMap(
    lat: Double,
    lng: Double,
    accuracyM: Float?,
    modifier: Modifier = Modifier,
    zoom: Double = 16.5,
    interactive: Boolean = true,
    alert: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    var metersPerPixel by remember { mutableStateOf<Double?>(null) }

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

    Box(modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.getMapAsync { map ->
                    if (map.style == null) map.setStyle(STYLE_URL)
                    map.uiSettings.isLogoEnabled = false
                    map.uiSettings.isCompassEnabled = false
                    map.uiSettings.setAllGesturesEnabled(interactive)
                    map.cameraPosition = CameraPosition.Builder().target(LatLng(lat, lng)).zoom(zoom).build()
                    metersPerPixel = runCatching { map.projection.getMetersPerPixelAtLatitude(lat) }.getOrNull()
                }
            },
        )

        val accent = if (alert) Palette.Red else Palette.Blue
        val mpp = metersPerPixel
        if (accuracyM != null && mpp != null && mpp > 0) {
            val diameterPx = (2 * accuracyM / mpp).toFloat()
            val diameter = with(density) { diameterPx.toDp() }.coerceIn(28.dp, 320.dp)
            Box(
                Modifier.align(Alignment.Center).size(diameter).clip(CircleShape).background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
            )
        }
        Box(
            Modifier.align(Alignment.Center).size(44.dp).shadow(6.dp, CircleShape).clip(CircleShape)
                .background(Color.White).border(3.dp, accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_buds), contentDescription = "Buds", tint = accent, modifier = Modifier.size(22.dp))
        }
    }
}
