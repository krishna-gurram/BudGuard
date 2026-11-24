package dev.krishnagurram.budguard.shots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.navigationBarsPadding
import dev.krishnagurram.budguard.data.BudEvent
import dev.krishnagurram.budguard.data.EventSource
import dev.krishnagurram.budguard.data.EventType
import dev.krishnagurram.budguard.data.ItemStore
import dev.krishnagurram.budguard.data.Prefs
import dev.krishnagurram.budguard.data.SafePlace
import dev.krishnagurram.budguard.data.TagItem
import dev.krishnagurram.budguard.data.TagState
import dev.krishnagurram.budguard.data.TrackMode
import dev.krishnagurram.budguard.ui.AlertsScreen
import dev.krishnagurram.budguard.ui.BudGuardTheme
import dev.krishnagurram.budguard.ui.DiagnosticsScreen
import dev.krishnagurram.budguard.ui.FinderScreen
import dev.krishnagurram.budguard.ui.HomeScreen
import dev.krishnagurram.budguard.ui.ItemDetailScreen
import dev.krishnagurram.budguard.ui.ItemsScreen
import dev.krishnagurram.budguard.ui.LocalMapsLive
import dev.krishnagurram.budguard.ui.PillNav
import dev.krishnagurram.budguard.ui.Screen
import dev.krishnagurram.budguard.ui.SetupScreen
import dev.krishnagurram.budguard.ui.SetupStatus
import dev.krishnagurram.budguard.ui.TagLabScreen
import dev.krishnagurram.budguard.ui.TimelineScreen
import dev.krishnagurram.budguard.ui.TrackCard
import dev.krishnagurram.budguard.ui.TrackUi
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders screens to PNGs so they can be looked at without a phone.
 * Only runs with -Pshots; CI attaches the images to the release.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h960dp-xxhdpi")
class ScreenshotShots {

    private fun shoot(name: String, content: @Composable () -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        seed(activity)
        activity.setContent { BudGuardTheme { CompositionLocalProvider(LocalMapsLive provides false) { content() } } }
        shadowOf(Looper.getMainLooper()).idle()
        val view = activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        controller.pause().stop().destroy()
    }

    @Composable
    private fun OnMap(ui: TrackUi) {
        Box(Modifier.fillMaxSize().background(Color(0xFFE8E4DA))) {
            TrackCard(ui, Modifier.align(Alignment.BottomCenter).padding(10.dp))
        }
    }

    @Test
    fun trackWalletWithYou() = shoot("track_wallet_with_you") {
        OnMap(TrackUi("Wallet", "wallet", isTag = true, mode = TrackMode.WITH_YOU, seenAgo = "3 min ago", accuracyM = 12.0))
    }

    @Test
    fun trackWalletAway() = shoot("track_wallet_away") {
        OnMap(
            TrackUi(
                "Wallet", "wallet", isTag = true, mode = TrackMode.AWAY, distanceM = 420.0, bearingDeg = 40.0, turnDeg = 25.0,
                seenAgo = "6 min ago", place = "Tempe Marketplace", accuracyM = 15.0,
            ),
        )
    }

    @Test
    fun trackWalletNearby() = shoot("track_wallet_nearby") {
        OnMap(TrackUi("Wallet", "wallet", isTag = true, mode = TrackMode.NEARBY, distanceM = 18.0, seenAgo = "just now", place = "Home", playing = true))
    }

    @Test
    fun trackBudsConnected() = shoot("track_buds_connected") {
        OnMap(TrackUi("Galaxy Buds4 Pro", "case", isTag = false, mode = TrackMode.WITH_YOU, connected = true))
    }

    // ---------- Whole screens with realistic data ----------

    private val now = System.currentTimeMillis()
    private val min = 60_000L

    private val gymEvents = listOf(
        BudEvent(6, now - 38 * min, EventType.DISCONNECTED, EventSource.BLUETOOTH, 33.38, -112.15, 9f, "Gym, by the lockers", null),
        BudEvent(5, now - 95 * min, EventType.CONNECTED, EventSource.BLUETOOTH, 33.37, -112.14, 6f, "Home", null),
        BudEvent(4, now - 120 * min, EventType.BATTERY, EventSource.BATTERY_BROADCAST, 33.37, -112.14, 6f, "Home", 15),
        BudEvent(3, now - 200 * min, EventType.TAG_LEFT, EventSource.SAMSUNG, 33.41, -112.10, 20f, "Rosa's Café", null, item = "Wallet"),
        BudEvent(2, now - 26 * 60 * min, EventType.DISCONNECTED, EventSource.BLUETOOTH, 33.37, -112.14, 5f, "Home", null, moving = true),
        BudEvent(1, now - 27 * 60 * min, EventType.CONNECTED, EventSource.BLUETOOTH, 33.37, -112.14, 5f, "Home", null),
    )
    private val withYouEvents = listOf(
        BudEvent(7, now - 4 * min, EventType.CONNECTED, EventSource.BLUETOOTH, 33.37, -112.14, 6f, "Home", null),
    ) + gymEvents

    private val ready = SetupStatus(true, true, true, true, "Galaxy Buds4 Pro", "C7:52", true, true, true)
    private val fresh = SetupStatus(true, true, false, true, null, null, false, false, false)

    private fun seed(context: android.content.Context) {
        Prefs(context).apply {
            budsName = "Galaxy Buds4 Pro"
            budsAddress = "C7:52"
            lastBattery = 64
        }
        ItemStore.saveItems(
            context,
            listOf(
                TagItem("w", "u", "Wallet", "Wallet", icon = "wallet", lat = 33.41, lng = -112.10, accuracyM = 25.0, fixTime = now - 6 * min,
                    place = "Rosa's Café", battery = 84, state = TagState.LEFT_BEHIND, stateSince = now - 6 * min),
                TagItem("c", "u", "Buds case", "Buds case tag", icon = "case", lat = 33.37, lng = -112.14, accuracyM = 10.0, fixTime = now - 4 * min,
                    place = "Home", battery = 92, state = TagState.WITH_YOU, stateSince = now - 60 * min),
            ),
        )
        ItemStore.saveSafePlaces(context, listOf(SafePlace("Home", 33.37, -112.14), SafePlace("Office", 33.42, -111.94)))
    }

    @Composable
    private fun WithNav(screen: Screen, dark: Boolean = false, content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            content()
            PillNav(screen, dark, {}, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
        }
    }

    @Test fun homeLeftBehind() = shoot("01_home_left_behind") { WithNav(Screen.Buds) { HomeScreen(Modifier.fillMaxSize(), gymEvents, ready, 0, {}, {}, {}) } }

    @Test fun homeWithYou() = shoot("02_home_with_you") { WithNav(Screen.Buds) { HomeScreen(Modifier.fillMaxSize(), withYouEvents, ready, 0, {}, {}, {}) } }

    @Test fun homeFirstRun() = shoot("03_home_first_run") { WithNav(Screen.Buds) { HomeScreen(Modifier.fillMaxSize(), emptyList(), fresh, 0, {}, {}, {}) } }

    @Test fun items() = shoot("04_items") { WithNav(Screen.Items) { ItemsScreen(Modifier.fillMaxSize(), gymEvents, {}, {}) } }

    @Test fun itemDetail() = shoot("05_wallet") { WithNav(Screen.ItemDetail) { ItemDetailScreen(Modifier.fillMaxSize(), "w", {}) } }

    @Test fun timeline() = shoot("06_timeline") { WithNav(Screen.Timeline) { TimelineScreen(Modifier.fillMaxSize(), gymEvents) } }

    @Test fun finder() = shoot("07_find") { WithNav(Screen.Find, dark = true) { FinderScreen(Modifier.fillMaxSize()) } }

    @Test fun alerts() = shoot("08_alerts") { WithNav(Screen.Alerts) { AlertsScreen(Modifier.fillMaxSize(), {}, {}, {}) } }

    @Test fun setup() = shoot("09_setup") { SetupScreen(Modifier.fillMaxSize(), fresh, {}, {}) }

    @Test fun diagnostics() = shoot("10_diagnostics") { WithNav(Screen.Diagnostics) { DiagnosticsScreen(Modifier.fillMaxSize(), gymEvents, ready, 0, {}, {}) } }

    @Test fun tagLab() = shoot("11_tag_lab") { WithNav(Screen.TagLab) { TagLabScreen(Modifier.fillMaxSize(), {}) } }
}
