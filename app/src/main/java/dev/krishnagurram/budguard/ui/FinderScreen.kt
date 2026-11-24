package dev.krishnagurram.budguard.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
        !canScan -> "Allow nearby devices"
        fresh -> state.meters?.let { formatMeters(it) } ?: "Nearby"
        else -> "No signal"
    }
    val trendText = when {
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Text(prefs.budsName ?: "Your buds", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Dark.Ink)
        Text(
            if (state.scanning) "Searching nearby · walk slowly" else state.error ?: "Not searching",
            fontSize = 14.sp,
            color = Dark.Quiet,
        )

        Box(Modifier.size(240.dp).clip(CircleShape).background(Dark.Surface), contentAlignment = Alignment.Center) {
            Box(Modifier.size(170.dp).clip(CircleShape).background(orbColor.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(100.dp).clip(CircleShape).background(orbColor), contentAlignment = Alignment.Center) {
                    Text(
                        if (fresh) "${state.rssi?.toInt()}" else "–",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Text(headline, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Dark.Ink)
        Text(trendText, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Dark.BlueText, textAlign = TextAlign.Center)

        SignalBars(state.history, fresh)

        if (!canScan) {
            Button(
                onClick = { scanPermission.launch(Manifest.permission.BLUETOOTH_SCAN) },
                colors = ButtonDefaults.buttonColors(containerColor = Dark.Blue),
                shape = RoundedCornerShape(50),
            ) { Text("Allow nearby devices") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Top) {
            RoundAction(if (torch) "Light on" else "Flashlight", small = true, active = torch) {
                torch = !torch
                setTorch(context, torch)
            }
            RoundAction(if (playing) "Stop sound" else "Play sound", small = false, active = playing) {
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
            RoundAction("Case tag", small = true, active = false) { openSamsungFind(context) }
        }

        Text(
            soundStatus ?: "Play sound works when the buds are out of the case. Closed case? Tap Case tag to follow the SmartTag2.",
            fontSize = 13.sp,
            color = Dark.Quiet,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun SignalBars(history: List<Int>, fresh: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(40.dp)) {
        val bars = if (history.isEmpty()) List(8) { -100 } else history.takeLast(8)
        bars.forEach { rssi ->
            val h = ((rssi + 100).coerceIn(0, 60) / 60f * 34 + 6).dp
            Box(
                Modifier.width(8.dp).height(h).clip(RoundedCornerShape(4.dp))
                    .background(if (fresh) Dark.Blue else Dark.Raised),
            )
        }
    }
}

@Composable
private fun RoundAction(label: String, small: Boolean, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.height(if (small) 12.dp else 0.dp))
        Button(
            onClick = onClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (active || !small) Dark.Blue else Dark.Surface,
                contentColor = Color.White,
            ),
            modifier = Modifier.size(if (small) 56.dp else 80.dp),
        ) {}
        Text(label, fontSize = if (small) 12.sp else 13.sp, color = if (small) Dark.Quiet else Dark.Ink, fontWeight = if (small) FontWeight.Normal else FontWeight.Bold)
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
