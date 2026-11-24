package dev.krishnagurram.budguard.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.krishnagurram.budguard.data.EventStore
import dev.krishnagurram.budguard.data.Prefs
import dev.krishnagurram.budguard.tracking.BatteryWatch
import dev.krishnagurram.budguard.tracking.BluetoothReceiver
import dev.krishnagurram.budguard.tracking.CompanionLink
import dev.krishnagurram.budguard.tracking.Driving
import dev.krishnagurram.budguard.tracking.LeftBehind

enum class Screen { Buds, Items, ItemDetail, Track, Timeline, Find, Alerts, Diagnostics, Setup, TagLab }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CompanionLink.observe(this, Prefs(this).budsAddress)
        Driving.register(this)
        LeftBehind.rearm(this)
        BatteryWatch.schedule(this)
        dev.krishnagurram.budguard.tracking.TagWatch.schedule(this)
        dev.krishnagurram.budguard.tracking.TagWatch.checkSoon(this, "app opened")
        setContent { BudGuardTheme { AppRoot() } }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val store = remember { EventStore.get(context) }
    val events by store.observe().collectAsStateWithLifecycle(initialValue = emptyList())

    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val status = remember(refresh) { SetupStatus.read(context) }

    var screen by rememberSaveable { mutableStateOf(if (status.coreReady) Screen.Buds else Screen.Setup) }
    var openItem by rememberSaveable { mutableStateOf("") }
    var trackId by rememberSaveable { mutableStateOf(TRACK_BUDS) }
    var trackBack by rememberSaveable { mutableStateOf(Screen.Buds) }

    // While the app is open, also listen for battery reports.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                BluetoothReceiver.handleBatteryIntent(c, intent)
                refresh++
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothReceiver.ACTION_BATTERY_LEVEL_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    val dark = screen == Screen.Find
    Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF0A0C10) else Palette.Bg)) {
        val modifier = Modifier.fillMaxSize()
        when (screen) {
            Screen.Buds -> HomeScreen(
                modifier,
                events,
                status,
                refresh,
                onSetup = { screen = Screen.Setup },
                onTimeline = { screen = Screen.Timeline },
                onFind = { screen = Screen.Find },
                onTrack = {
                    trackId = TRACK_BUDS
                    trackBack = Screen.Buds
                    screen = Screen.Track
                },
            )
            Screen.Track -> TrackScreen(
                modifier,
                trackId,
                events,
                onBack = { screen = trackBack },
                onNearby = { screen = Screen.Find },
            )
            Screen.Items -> ItemsScreen(
                modifier,
                events,
                onBuds = { screen = Screen.Buds },
                onOpen = { id ->
                    openItem = id
                    screen = Screen.ItemDetail
                },
            )
            Screen.ItemDetail -> ItemDetailScreen(modifier, openItem, onBack = { screen = Screen.Items }, onTrack = { id ->
                trackId = id
                trackBack = Screen.ItemDetail
                screen = Screen.Track
            })
            Screen.Timeline -> TimelineScreen(modifier.statusBarsPadding(), events)
            Screen.Find -> FinderScreen(modifier.statusBarsPadding())
            Screen.Alerts -> AlertsScreen(
                modifier.statusBarsPadding(),
                onDiagnostics = { screen = Screen.Diagnostics },
                onSetup = { screen = Screen.Setup },
                onTagLab = { screen = Screen.TagLab },
            )
            Screen.TagLab -> TagLabScreen(modifier.statusBarsPadding(), onBack = { screen = Screen.Alerts })
            Screen.Diagnostics -> DiagnosticsScreen(
                modifier.statusBarsPadding(),
                events,
                status,
                refresh,
                onSetup = { screen = Screen.Setup },
                onClear = { store.clear() },
            )
            Screen.Setup -> SetupScreen(
                modifier.statusBarsPadding().navigationBarsPadding(),
                status,
                onRefresh = { refresh++ },
                onDone = { screen = Screen.Buds },
            )
        }

        if (screen != Screen.Setup && screen != Screen.Track) {
            PillNav(
                current = screen,
                dark = dark,
                onSelect = { screen = it },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
            )
        }
    }
}

@Composable
internal fun PillNav(current: Screen, dark: Boolean, onSelect: (Screen) -> Unit, modifier: Modifier) {
    val bg = if (dark) Color(0xFF1A1D24) else Color.White
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .shadow(18.dp, RoundedCornerShape(34.dp))
            .clip(RoundedCornerShape(34.dp))
            .background(bg)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        PillItem("Buds", Sym.Earbuds, current == Screen.Buds, dark) { onSelect(Screen.Buds) }
        PillItem("Items", Sym.GridView, current == Screen.Items || current == Screen.ItemDetail, dark) { onSelect(Screen.Items) }
        PillItem("Timeline", Sym.History, current == Screen.Timeline, dark) { onSelect(Screen.Timeline) }
        PillItem("Find", Sym.Radar, current == Screen.Find, dark) { onSelect(Screen.Find) }
        PillItem(
            "Alerts",
            Sym.Notifications,
            current == Screen.Alerts || current == Screen.Diagnostics || current == Screen.TagLab,
            dark,
        ) { onSelect(Screen.Alerts) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PillItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val selectedBg = if (dark) Color(0xFF2C313C) else Palette.BlueSoft
    val selectedFg = if (dark) Color(0xFF8AB4FF) else Palette.Blue
    val idle = if (dark) Color(0xFFA6A9B2) else Palette.Quiet
    Column(
        modifier = Modifier
            .weight(1f)
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(if (selected) selectedBg else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) selectedFg else idle, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = Type.Small.copy(fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) selectedFg else idle),
        )
    }
}
