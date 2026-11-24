package dev.krishnagurram.budguard.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.material3.Icon
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.krishnagurram.budguard.data.Prefs
import dev.krishnagurram.budguard.tracking.CompanionLink

@SuppressLint("BatteryLife")
@Composable
fun SetupScreen(modifier: Modifier, status: SetupStatus, onRefresh: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var linkMessage by remember { mutableStateOf<String?>(null) }

    val basicPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onRefresh() }
    val backgroundLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefresh() }
    val linkApproval = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { onRefresh() }
    val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        dev.krishnagurram.budguard.tracking.Driving.register(context)
        onRefresh()
    }

    val bonded = remember(status.bluetooth, status.budsAddress) {
        if (status.bluetooth) CompanionLink.bondedAudioFirst(context) else emptyList()
    }

    Column(
        modifier = modifier.fillMaxSize().background(Palette.Bg).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Set up BudGuard", "About 2 minutes, once")

        Step(
            n = 1,
            done = status.bluetooth && status.location && status.notifications,
            title = "Allow Bluetooth, location and notifications",
            body = "Bluetooth to know when the buds leave, location to save where, notifications to tell you.",
            action = "Allow",
        ) {
            basicPermissions.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS,
                ),
            )
        }

        Step(
            n = 2,
            done = status.backgroundLocation,
            title = "Location: Allow all the time",
            body = "The buds usually leave when the app is closed. On the next screen pick \"Allow all the time\".",
            action = "Open",
            enabled = status.location,
        ) { backgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }

        Card {
            StepHeader(3, status.budsAddress != null, "Pick your buds")
            if (!status.bluetooth) {
                Text("Finish step 1 first.", style = Type.Sub.copy(fontSize = 14.sp), modifier = Modifier.padding(start = 46.dp))
            } else if (bonded.isEmpty()) {
                Text("No paired devices found. Pair the buds in Bluetooth settings first.", style = Type.Sub.copy(fontSize = 14.sp), modifier = Modifier.padding(start = 46.dp))
            } else {
                Text(
                    "This is your phone's paired list. Wear the new buds, then pick the one marked Connected now.",
                    fontSize = 13.sp,
                    color = Palette.Quiet,
                )
                bonded.forEach { device ->
                    val name = device.name
                    val address = device.address
                    val selected = address.equals(status.budsAddress, ignoreCase = true)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                prefs.budsAddress = address
                                prefs.budsName = name
                                onRefresh()
                            }
                            .padding(vertical = 2.dp),
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Palette.Blue),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(name, fontSize = 15.sp, color = Palette.Ink)
                            Text(
                                "${device.kind} · ends ${address.takeLast(5)}",
                                fontSize = 12.sp,
                                color = Palette.Quiet,
                            )
                        }
                        if (device.connected) Pill("Connected now", Palette.Green, Palette.GreenSoft)
                    }
                }
            }
        }

        Step(
            n = 4,
            done = status.linked,
            title = "Link with Android (recommended)",
            body = linkMessage
                ?: "Lets Android wake BudGuard when the buds come and go. If it can't find them, open the case and try again.",
            action = "Link",
            enabled = status.budsAddress != null,
        ) {
            val address = status.budsAddress ?: return@Step
            CompanionLink.link(
                context,
                address,
                onApprovalNeeded = { sender -> linkApproval.launch(IntentSenderRequest.Builder(sender).build()) },
                onResult = { message ->
                    linkMessage = message
                    onRefresh()
                },
            )
        }

        Step(
            n = 5,
            done = status.unrestricted,
            title = "Let it run in the background",
            body = "Allow unrestricted battery. Then in Settings > Battery > Background usage limits, add BudGuard to Never sleeping apps.",
            action = "Allow",
        ) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                )
            }
        }

        Step(
            n = 6,
            done = status.activity,
            title = "Know when you're driving",
            body = "Allow physical activity so a disconnect in a car or ride share warns you to check the seat.",
            action = "Allow",
        ) { activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION) }

        PillButton(if (status.coreReady) "Done" else "Finish steps 1 to 3", Modifier.fillMaxWidth().height(56.dp), enabled = status.coreReady, onClick = onDone)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Step(
    n: Int,
    done: Boolean,
    title: String,
    body: String,
    action: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card {
        StepHeader(n, done, title)
        if (!done) {
            Text(body, style = Type.Sub.copy(fontSize = 14.sp), modifier = Modifier.padding(start = 46.dp))
            PillButton(action, Modifier.padding(start = 46.dp).height(44.dp), enabled = enabled, onClick = onClick)
        }
    }
}

@Composable
private fun StepHeader(n: Int, done: Boolean, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(if (done) Palette.Green else Palette.BlueSoft),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                androidx.compose.material3.Icon(Sym.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(18.dp))
            } else {
                Text("$n", style = Type.Small.copy(fontSize = 14.sp, color = Palette.Blue, fontWeight = FontWeight.Bold))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(title, style = Type.Row.copy(color = if (done) Palette.Quiet else Palette.Ink), modifier = Modifier.weight(1f))
    }
}
