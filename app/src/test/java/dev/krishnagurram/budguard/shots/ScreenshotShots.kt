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
import dev.krishnagurram.budguard.data.TrackMode
import dev.krishnagurram.budguard.ui.BudGuardTheme
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
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotShots {

    private fun shoot(name: String, content: @Composable () -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        activity.setContent { BudGuardTheme { content() } }
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
}
