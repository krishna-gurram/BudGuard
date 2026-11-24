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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.rememberCoroutineScope
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
import dev.krishnagurram.budguard.data.ItemStore
import dev.krishnagurram.budguard.data.Prefs
import dev.krishnagurram.budguard.data.SafePlace
import dev.krishnagurram.budguard.tracking.EventRecorder
import kotlinx.coroutines.launch

@Composable
fun AlertsScreen(modifier: Modifier, onDiagnostics: () -> Unit, onSetup: () -> Unit, onTagLab: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var walkAway by remember { mutableStateOf(prefs.leftBehindOn) }
    var radius by remember { mutableFloatStateOf(prefs.radiusM.toFloat()) }
    var battery by remember { mutableStateOf(prefs.batteryOn) }
    var moving by remember { mutableStateOf(prefs.movingOn) }
    var charge by remember { mutableStateOf(prefs.chargeOn) }
    var tagAlerts by remember { mutableStateOf(prefs.tagAlertsOn) }
    var tagBattery by remember { mutableStateOf(prefs.tagBatteryOn) }
    var places by remember { mutableStateOf(ItemStore.safePlaces(context)) }
    var naming by remember { mutableStateOf(false) }
    var placeName by remember { mutableStateOf("Home") }
    var placeNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (naming) {
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Name this safe place") },
            text = {
                OutlinedTextField(value = placeName, onValueChange = { placeName = it }, singleLine = true, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    naming = false
                    placeNote = "Finding where you are…"
                    scope.launch {
                        val here = EventRecorder.currentLocation(context)
                        if (here == null) {
                            placeNote = "Couldn't get your location. Try again outside."
                        } else {
                            places = places + SafePlace(placeName.trim().ifBlank { "Safe place" }, here.latitude, here.longitude, 150)
                            ItemStore.saveSafePlaces(context, places)
                            placeNote = null
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }

    Column(
        modifier = modifier.fillMaxSize().background(Palette.Bg).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Alerts", "Your phone remembers, wherever you go")

        // What an alert looks like, so the toggles below mean something.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Palette.Card).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Blue), contentAlignment = Alignment.Center) {
                    Icon(Sym.Earbuds, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("BudGuard · example", style = Type.Sub.copy(fontSize = 12.sp))
            }
            Text("You left your buds at the gym", style = Type.Row)
            Text("Disconnected by the lockers 3 min ago.", style = Type.Sub.copy(fontSize = 14.sp))
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Directions", "Play sound", "Got them").forEach {
                    Text(it, style = Type.Small.copy(fontSize = 13.sp), modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(Palette.GreySoft).padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }

        SectionLabel("Notify me when")
        Group {
            ToggleRow(Sym.DirectionsWalk, Palette.Red, "I walk away without them", "Anywhere, not just home", walkAway) {
                walkAway = it
                prefs.leftBehindOn = it
            }
            if (walkAway) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 70.dp, end = 18.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Distance", style = Type.Sub)
                    Slider(
                        value = radius,
                        onValueChange = { radius = (it / 25f).toInt() * 25f },
                        onValueChangeFinished = { prefs.radiusM = radius.toInt() },
                        valueRange = 75f..500f,
                        colors = SliderDefaults.colors(thumbColor = Palette.Blue, activeTrackColor = Palette.Blue, inactiveTrackColor = Palette.GreySoft),
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    )
                    Text(dev.krishnagurram.budguard.data.Geo.distanceText(radius.toDouble()), style = Type.Small.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
                }
            }
            RowDivider()
            ToggleRow(Sym.BatteryAlert, Palette.Orange, "Battery runs low", "Save the spot at 15% and 5%", battery) {
                battery = it
                prefs.batteryOn = it
            }
            RowDivider()
            ToggleRow(Sym.DirectionsCar, Palette.Purple, "They drop off in a car", "Check the seat before you get out", moving) {
                moving = it
                prefs.movingOn = it
            }
            RowDivider()
            ToggleRow(Sym.Bolt, Palette.Green, "They need charging", "Unseen 6 hours below 20%", charge) {
                charge = it
                prefs.chargeOn = it
            }
        }

        SectionLabel("Your tags")
        Group {
            ToggleRow(Sym.Sell, Palette.Red, "A tag is left behind", "Wallet and other SmartTags, via Samsung Find", tagAlerts) {
                tagAlerts = it
                prefs.tagAlertsOn = it
            }
            RowDivider()
            ToggleRow(Sym.BatteryAlert, Palette.Orange, "Tag battery is low", "At 15%, once per coin cell", tagBattery) {
                tagBattery = it
                prefs.tagBatteryOn = it
            }
        }

        SectionLabel("Safe places · tags may stay here")
        Group {
            places.forEach { place ->
                ListRow(
                    title = place.name,
                    detail = "${dev.krishnagurram.budguard.data.Geo.distanceText(place.radiusM.toDouble())} around",
                    leading = { IconTile(if (place.name.contains("work", true) || place.name.contains("office", true)) Sym.Work else Sym.Home, Palette.Green) },
                ) {
                    Text(
                        "Remove",
                        style = Type.Small.copy(color = Palette.Red),
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                            places = places - place
                            ItemStore.saveSafePlaces(context, places)
                        }.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
                RowDivider()
            }
            ListRow(
                title = placeNote ?: "Add where you are now",
                leading = { IconDot(Sym.Add, Palette.Blue, Palette.BlueSoft, size = 36) },
                onClick = { naming = true },
            )
        }

        SectionLabel("More")
        Group {
            LinkRow(Sym.Science, Palette.Purple, "Tag Lab", "Test how BudGuard hears your SmartTag2", onTagLab)
            RowDivider()
            LinkRow(Sym.BugReport, Palette.Grey, "Diagnostics", "Health checks and the report for Claude", onDiagnostics)
            RowDivider()
            LinkRow(Sym.Settings, Palette.Blue, "Setup", "Permissions, buds, Android link", onSetup)
        }
        Text(
            "Map tiles load from OpenFreeMap. Your timeline and locations never leave this phone.",
            style = Type.Sub.copy(fontSize = 12.sp),
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, tint: Color, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListRow(title = title, detail = subtitle, leading = { IconTile(icon, tint) }, onClick = { onChange(!checked) }) {
        Toggle(checked, onChange)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    ListRow(title = title, detail = subtitle, leading = { IconTile(icon, tint) }, onClick = onClick) { Chevron() }
}
