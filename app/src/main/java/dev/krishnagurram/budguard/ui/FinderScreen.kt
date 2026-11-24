package dev.krishnagurram.budguard.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.*
import androidx.compose.material3.Icon
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.krishnagurram.budguard.data.Prefs
import dev.krishnagurram.budguard.tracking.BudsRadar
import dev.krishnagurram.budguard.tracking.BudsSound
import dev.krishnagurram.budguard.tracking.CompanionLink
import dev.krishnagurram.budguard.tracking.Trend
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private object Dark {
    val Bg = Color(0xFF0A0C10)
    val Surface = Color(0xFF1A1D24)
    val Raised = Color(0xFF2C313C)
    val Ink = Color(0xFFF2F3F5)
    val Quiet = Color(0xFFA6A9B2)
    val Blue = Color(0xFF4C8DFF)
    val BlueText = Color(0xFF8AB4FF)
}

@Composable
fun FinderScreen(modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(context) }
    val radar = remember { BudsRadar(context.applicationContext) }
    val state by radar.state.collectAsStateWithLifecycle()

    var canScan by remember { mutableStateOf(has(context, Manifest.permission.BLUETOOTH_SCAN)) }
    val scanPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canScan = it }

    var soundStatus by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(canScan) {
        if (canScan) radar.start(CompanionLink.budsAddresses(context, prefs.budsAddress, prefs.budsName))
        onDispose {
            radar.stop()
            setTorch(context, false)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    val fresh = state.rssi != null && now - state.lastSeen < 8_000
    val headline = when {
        !canScan -> "–"
        fresh -> state.meters?.let { formatMeters(it) } ?: "Nearby"
        else -> "–"
    }
    val trendText = when {
        !canScan -> "Allow nearby devices to start"
        !fresh -> "Open the case or take the buds out"
        state.trend == Trend.WARMER -> "Warmer, keep going"
        state.trend == Trend.COLDER -> "Colder, turn around"
        else -> "Steady, try another direction"
    }
    val orbColor = when {
        !fresh -> Dark.Raised
        state.trend == Trend.COLDER -> Color(0xFF6B7A99)
        else -> Dark.Blue
    }

    Column(
        modifier = modifier.fillMaxSize().background(Dark.Bg).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Dark.Surface).padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (state.scanning) Dark.Blue else Dark.Quiet))
                Spacer(Modifier.width(7.dp))
                Text(if (state.scanning) "Searching" else "Paused", style = Type.Small.copy(color = Dark.BlueText))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(prefs.budsName ?: "Your buds", style = Type.Title.copy(color = Dark.Ink))
        Spacer(Modifier.height(6.dp))
        Text(
            state.error ?: "Keep the phone level and walk slowly",
            style = Type.Sub.copy(color = Dark.Quiet, fontSize = 14.sp),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))
        Radar(fresh = fresh, rssi = state.rssi, colder = state.trend == Trend.COLDER)
        Spacer(Modifier.height(20.dp))

        Text(headline, style = Type.Display.copy(fontSize = 56.sp, color = Dark.Ink))
        Text(trendText, style = Type.Row.copy(color = if (fresh && state.trend == Trend.COLDER) Color(0xFF9AA6BF) else Dark.BlueText), textAlign = TextAlign.Center)

        if (!canScan) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { scanPermission.launch(Manifest.permission.BLUETOOTH_SCAN) },
                colors = ButtonDefaults.buttonColors(containerColor = Dark.Blue),
                shape = RoundedCornerShape(50),
            ) { Text("Allow nearby devices") }
        }

        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.Bottom) {
            RoundAction(if (torch) "Light on" else "Flashlight", Sym.FlashlightOn, big = false, active = torch) {
                torch = !torch
                setTorch(context, torch)
            }
            RoundAction(if (playing) "Stop sound" else "Play sound", Sym.VolumeUp, big = true, active = true) {
                scope.launch {
                    if (playing) {
                        soundStatus = BudsSound.stop()
                        playing = false
                    } else {
                        soundStatus = "Connecting…"
                        soundStatus = BudsSound.start(context, prefs.budsAddress)
                        playing = BudsSound.isPlaying
                    }
                }
            }
            RoundAction("Case tag", Sym.Sell, big = false, active = false) { openSamsungFind(context) }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            soundStatus ?: "No signal? The lid may be closed. Tap Case tag to follow the SmartTag instead.",
            style = Type.Sub.copy(color = Dark.Quiet),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(120.dp))
    }
}

/** Rings with the buds at the centre; a dot moves inward as the signal gets stronger. */
@Composable
private fun Radar(fresh: Boolean, rssi: Double?, colder: Boolean) {
    val accent = if (!fresh) Dark.Raised else if (colder) Color(0xFF6B7A99) else Dark.Blue
    Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2
            drawCircle(Color(0xFF151922), radius = r, center = c)
            drawCircle(Color(0xFF232A36), radius = r, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            drawCircle(Color(0xFF232A36), radius = r * 0.66f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0f)), center = c, radius = r * 0.6f),
                radius = r * 0.6f,
                center = c,
            )
            drawCircle(accent.copy(alpha = 0.5f), radius = r * 0.42f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f.dp.toPx()))
            if (fresh && rssi != null) {
                // -40 dBm sits near the middle, -95 at the edge.
                val t = ((-rssi - 40) / 55.0).coerceIn(0.0, 1.0).toFloat()
                val dist = r * (0.3f + 0.65f * t)
                drawCircle(Color(0xFF8AB4FF), radius = 7.dp.toPx(), center = androidx.compose.ui.geometry.Offset(c.x, c.y - dist))
            }
        }
        Box(Modifier.size(80.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(Sym.Earbuds, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun RoundAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, big: Boolean, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(if (big) 84.dp else 60.dp).clip(CircleShape).background(if (active) Dark.Blue else Dark.Surface).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(if (big) 36.dp else 26.dp))
        }
        Text(label, style = Type.Small.copy(color = if (big) Dark.Ink else Dark.Quiet, fontWeight = if (big) FontWeight.Bold else FontWeight.Medium))
    }
}

private fun formatMeters(m: Double): String = when {
    m < 1 -> "< 3 ft"
    m < 10 -> String.format(Locale.US, "~%.0f ft", m * 3.28084)
    else -> "30 ft+"
}

private fun has(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun setTorch(context: Context, on: Boolean) {
    val camera = context.getSystemService(CameraManager::class.java) ?: return
    runCatching {
        val id = camera.cameraIdList.firstOrNull {
            camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return
        camera.setTorchMode(id, on)
    }
}
