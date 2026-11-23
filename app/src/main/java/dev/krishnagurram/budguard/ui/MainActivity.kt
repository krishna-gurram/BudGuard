package dev.krishnagurram.budguard.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.krishnagurram.budguard.data.EventStore
import dev.krishnagurram.budguard.data.Prefs
import dev.krishnagurram.budguard.tracking.BluetoothReceiver
import dev.krishnagurram.budguard.tracking.CompanionLink

enum class Screen { Buds, Timeline, Diagnostics, Setup }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CompanionLink.observe(this, Prefs(this).budsAddress)
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

    // While the app is open, also listen for battery reports (diagnostics for Spike A).
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

    Scaffold(
        containerColor = Palette.Bg,
        bottomBar = {
            if (screen != Screen.Setup) {
                NavigationBar(containerColor = Palette.Card) {
                    navItem(screen == Screen.Buds, "Buds", Icons.Filled.Home) { screen = Screen.Buds }
                    navItem(screen == Screen.Timeline, "Timeline", Icons.Filled.List) { screen = Screen.Timeline }
                    navItem(screen == Screen.Diagnostics, "Diagnostics", Icons.Filled.Info) { screen = Screen.Diagnostics }
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (screen) {
            Screen.Buds -> HomeScreen(modifier, events, status, onSetup = { screen = Screen.Setup }, onTimeline = { screen = Screen.Timeline })
            Screen.Timeline -> TimelineScreen(modifier, events)
            Screen.Diagnostics -> DiagnosticsScreen(modifier, events, status, refresh, onSetup = { screen = Screen.Setup }, onClear = { store.clear() })
            Screen.Setup -> SetupScreen(modifier, status, onRefresh = { refresh++ }, onDone = { screen = Screen.Buds })
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.navItem(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Palette.Blue,
            selectedTextColor = Palette.Blue,
            indicatorColor = Palette.BlueSoft,
            unselectedIconColor = Palette.Quiet,
            unselectedTextColor = Palette.Quiet,
        ),
    )
}
