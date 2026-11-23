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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.krishnagurram.budguard.data.Prefs

@Composable
fun AlertsScreen(modifier: Modifier, onDiagnostics: () -> Unit, onSetup: () -> Unit, onTagLab: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var walkAway by remember { mutableStateOf(prefs.leftBehindOn) }
    var radius by remember { mutableFloatStateOf(prefs.radiusM.toFloat()) }
    var battery by remember { mutableStateOf(prefs.batteryOn) }
    var moving by remember { mutableStateOf(prefs.movingOn) }
    var charge by remember { mutableStateOf(prefs.chargeOn) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenTitle("Alerts", "Your phone remembers, wherever you go")

        SectionLabel("Notify me when")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Palette.Card).padding(vertical = 4.dp)) {
            ToggleRow(Icons.Filled.LocationOn, Palette.Red, "I walk away without them", "Anywhere, not just home", walkAway) {
                walkAway = it
                prefs.leftBehindOn = it
            }
            if (walkAway) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 66.dp, end = 18.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Distance", fontSize = 13.sp, color = Palette.Quiet)
                    Slider(
                        value = radius,
                        onValueChange = { radius = (it / 25f).toInt() * 25f },
                        onValueChangeFinished = { prefs.radiusM = radius.toInt() },
                        valueRange = 75f..500f,
                        colors = SliderDefaults.colors(thumbColor = Palette.Blue, activeTrackColor = Palette.Blue),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text("${radius.toInt()} m", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                }
            }
            Divider()
            ToggleRow(Icons.Filled.Warning, Palette.Orange, "Battery runs low", "Save the spot at 15% and 5%", battery) {
                battery = it
                prefs.batteryOn = it
            }
            Divider()
            ToggleRow(Icons.Filled.Send, Color(0xFF7A3FD1), "They drop off in a car", "Check the seat before you get out", moving) {
                moving = it
                prefs.movingOn = it
            }
            Divider()
            ToggleRow(Icons.Filled.Refresh, Palette.Green, "They need charging", "Unseen 6 hours below 20%", charge) {
                charge = it
                prefs.chargeOn = it
            }
        }

        SectionLabel("More")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Palette.Card).padding(vertical = 4.dp)) {
            LinkRow(Icons.Filled.Search, "Tag Lab", "Phase 7 test for your wallet SmartTag2", onTagLab)
            Divider()
            LinkRow(Icons.Filled.Info, "Diagnostics", "Health checks and the report for Claude", onDiagnostics)
            Divider()
            LinkRow(Icons.Filled.Build, "Setup", "Permissions, buds, Android link", onSetup)
        }
        Text(
            "Map tiles load from OpenFreeMap. Your timeline and locations never leave this phone.",
            fontSize = 12.sp,
            color = Palette.Quiet,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
            Text(subtitle, fontSize = 13.sp, color = Palette.Quiet)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Blue, checkedThumbColor = Color.White),
        )
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, Palette.Blue)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
            Text(subtitle, fontSize = 13.sp, color = Palette.Quiet)
        }
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.Quiet)
    }
}

@Composable
private fun IconTile(icon: ImageVector, tint: Color) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tint), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(start = 66.dp, end = 18.dp).height(1.dp).background(Palette.Rule))
}
