package dev.krishnagurram.budguard.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Walking directions to a saved spot; falls back to any maps app. */
fun navigateTo(context: Context, lat: Double, lng: Double) {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng&mode=w"))
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(Buds)"))
    runCatching { context.startActivity(nav) }.onFailure { runCatching { context.startActivity(geo) } }
}

/** Opens Samsung's SmartThings Find, where the SmartTag2's arrow search lives. */
fun openSamsungFind(context: Context) {
    val packages = listOf("com.samsung.android.oneconnect", "com.samsung.android.app.smartthingsfind")
    val intent = packages.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
    if (intent != null) runCatching { context.startActivity(intent) }
}
