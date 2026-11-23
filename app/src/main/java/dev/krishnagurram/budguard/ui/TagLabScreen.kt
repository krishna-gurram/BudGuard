package dev.krishnagurram.budguard.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.krishnagurram.budguard.data.Format
import dev.krishnagurram.budguard.tracking.SamsungFind
import dev.krishnagurram.budguard.tracking.TagLab
import dev.krishnagurram.budguard.tracking.TagScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun TagLabScreen(modifier: Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { TagScanner(context.applicationContext) }
    val sightings by scanner.sightings.collectAsStateWithLifecycle()
    val scanning by scanner.scanning.collectAsStateWithLifecycle()

    var canScan by remember { mutableStateOf(has(context, Manifest.permission.BLUETOOTH_SCAN)) }
    val scanPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canScan = it }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var recording by remember { mutableStateOf(false) }
    var recordStart by remember { mutableLongStateOf(0L) }
    var linked by remember { mutableStateOf(TagScanner.linkedNow(context)) }
    var paired by remember { mutableStateOf(TagScanner.pairedTags(context)) }

    var signingIn by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(SamsungFind.isSignedIn()) }
    var samsungNote by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf<SamsungFind.Session?>(null) }
    var devices by remember { mutableStateOf<List<SamsungFind.Device>>(emptyList()) }
    val statuses = remember { mutableStateMapOf<String, SamsungFind.Status>() }
    val ringing = remember { mutableStateMapOf<String, Boolean>() }

    DisposableEffect(canScan) {
        if (canScan) scanner.start()
        onDispose { scanner.stop() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
            if (recording && now - recordStart > 10 * 60_000) {
                scanner.stopRecording()
                recording = false
            }
        }
    }

    if (signingIn) {
        SamsungSignIn(
            onCancel = { signingIn = false },
            onDone = {
                signingIn = false
                signedIn = SamsungFind.isSignedIn()
                samsungNote = "Signed in. Tap Fetch my tags."
            },
        )
        return
    }

    fun fetch(refresh: Boolean) {
        scope.launch {
            samsungNote = "Talking to Samsung…"
            val s = SamsungFind.connect().getOrElse {
                samsungNote = "Sign-in didn't stick: ${it.message}"
                signedIn = false
                return@launch
            }
            session = s
            devices = SamsungFind.devices(s).getOrElse {
                samsungNote = "Couldn't list devices: ${it.message}"
                return@launch
            }
            val tags = devices.filter { it.isTag }
            samsungNote = "Found ${devices.size} devices, ${tags.size} tags. ${if (refresh) "Asking tags for a fresh spot (about 6 s)…" else ""}"
            tags.forEach { d ->
                SamsungFind.status(s, d, refresh).onSuccess { statuses[d.id] = it }
                    .onFailure { samsungNote = "Couldn't read ${d.name}: ${it.message}" }
            }
            if (samsungNote?.startsWith("Couldn't") != true) samsungNote = "Updated ${Format.clock(System.currentTimeMillis())}"
        }
    }

    val likely = TagLab.likelyYours(sightings.values, now)
    val sorted = sightings.values.sortedByDescending { it.rssi }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenTitle("Tag Lab", "Phase 7: can BudGuard hear your wallet tag?")
        TextButton(onClick = onBack) { Text("‹ Back to Alerts") }

        SectionLabel("1 · Your phone's link to tags")
        Card {
            Text("Live Bluetooth links (any app)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
            Text(if (linked.isEmpty()) "None right now" else linked.joinToString("\n"), fontSize = 13.sp, color = Palette.Quiet)
            Text("Paired tags", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
            Text(if (paired.isEmpty()) "None in the paired list" else paired.joinToString("\n"), fontSize = 13.sp, color = Palette.Quiet)
            Button(
                onClick = {
                    linked = TagScanner.linkedNow(context)
                    paired = TagScanner.pairedTags(context)
                },
                colors = softButton,
                shape = RoundedCornerShape(50),
            ) { Text("Refresh") }
        }

        SectionLabel("2 · Tag broadcasts nearby")
        Card {
            if (!canScan) {
                Button(onClick = { scanPermission.launch(Manifest.permission.BLUETOOTH_SCAN) }, colors = blueButton, shape = RoundedCornerShape(50)) {
                    Text("Allow nearby devices")
                }
            }
            Text(
                if (scanning) "Listening. Hold the wallet next to the phone; the strongest steady one should be yours."
                else "Not listening",
                fontSize = 13.sp,
                color = Palette.Quiet,
            )
            if (sorted.isEmpty()) Text("No Samsung tag broadcasts yet", fontSize = 14.sp, color = Palette.Ink)
            sorted.take(8).forEach { s -> SightingRow(s, TagLab.label(s, likely, now)) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (recording) {
                            scanner.stopRecording()
                            recording = false
                        } else {
                            scanner.startRecording()
                            recordStart = System.currentTimeMillis()
                            recording = true
                        }
                    },
                    enabled = scanning,
                    colors = blueButton,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f),
                ) { Text(if (recording) "Stop (${(now - recordStart) / 1000}s)" else "Record 10 min") }
                Button(
                    onClick = { shareRecording(context, scanner) },
                    enabled = !recording && scanner.recordingFile != null,
                    colors = softButton,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f),
                ) { Text("Share file") }
            }
            if (recording) Text("${scanner.recordedRows} broadcasts saved", fontSize = 12.sp, color = Palette.Quiet)
        }

        SectionLabel("3 · Samsung Find (unofficial)")
        Card {
            Text(
                "Sign in to Samsung inside BudGuard to read your tags' last spot, battery and ring them. The session stays on this phone.",
                fontSize = 13.sp,
                color = Palette.Quiet,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!signedIn) {
                    Button(onClick = { signingIn = true }, colors = blueButton, shape = RoundedCornerShape(50)) { Text("Sign in to Samsung") }
                } else {
                    Button(onClick = { fetch(refresh = true) }, colors = blueButton, shape = RoundedCornerShape(50)) { Text("Fetch my tags") }
                    TextButton(onClick = {
                        SamsungFind.signOut()
                        signedIn = false
                        devices = emptyList()
                        statuses.clear()
                        samsungNote = "Signed out"
                    }) { Text("Sign out") }
                }
            }
            samsungNote?.let { Text(it, fontSize = 13.sp, color = Palette.Quiet) }
            devices.filter { it.isTag }.forEach { d ->
                val st = statuses[d.id]
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Bg).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(d.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                    Text(statusLine(st, now), fontSize = 13.sp, color = Palette.Quiet)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val fix = st?.fix
                        if (fix != null) {
                            Button(onClick = { navigateTo(context, fix.lat, fix.lng) }, colors = softButton, shape = RoundedCornerShape(50)) { Text("Directions") }
                        }
                        val on = ringing[d.id] == true
                        Button(
                            onClick = {
                                val s = session ?: return@Button
                                scope.launch {
                                    SamsungFind.ring(s, d, start = !on)
                                        .onSuccess { ringing[d.id] = !on; samsungNote = if (!on) "Ring sent to ${d.name}" else "Stop sent" }
                                        .onFailure { samsungNote = "Ring failed: ${it.message}" }
                                }
                            },
                            colors = if (on) blueButton else softButton,
                            shape = RoundedCornerShape(50),
                        ) { Text(if (on) "Stop ring" else "Ring") }
                    }
                }
            }
            val others = devices.count { !it.isTag }
            if (others > 0) Text("Also on your account: $others phones, watches or buds", fontSize = 12.sp, color = Palette.Quiet)
        }

        SectionLabel("4 · Samsung's own left-behind alert")
        Card {
            Text(
                "Owners report Samsung's alert only fires with SmartThings notifications fully on, including \"show live information\". Open its settings and turn everything on.",
                fontSize = 13.sp,
                color = Palette.Quiet,
            )
            Button(onClick = { openSmartThingsNotifications(context) }, colors = softButton, shape = RoundedCornerShape(50)) {
                Text("Open SmartThings notification settings")
            }
            Button(onClick = { openSamsungFind(context) }, colors = softButton, shape = RoundedCornerShape(50)) { Text("Open Samsung Find") }
        }

        Button(
            onClick = { copyTagReport(context, scanner, sightings.values, linked, paired, signedIn, devices, statuses.values, samsungNote) },
            colors = blueButton,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Copy Tag Lab report for Claude") }
        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun SightingRow(s: dev.krishnagurram.budguard.tracking.TagSighting, label: String) {
    val strength = ((s.rssi + 100) / 60.0).coerceIn(0.05, 1.0).toFloat()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("${s.rssi.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, modifier = Modifier.width(42.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Palette.GreySoft)) {
            Box(
                Modifier.fillMaxWidth(strength).height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (label == "Likely yours") Palette.Blue else Palette.Grey),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.width(128.dp)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (label == "Likely yours") Palette.Green else Palette.Ink)
            Text("…${s.address.takeLast(5)} · ${s.count}× · data ${s.dataChanges}Δ", fontSize = 11.sp, color = Palette.Quiet)
        }
    }
}

private fun statusLine(st: SamsungFind.Status?, now: Long): String {
    if (st == null) return "Not read yet"
    val parts = mutableListOf<String>()
    st.fix?.let { f ->
        parts += String.format(Locale.US, "%.5f, %.5f", f.lat, f.lng)
        parts += Format.ago(now, f.time)
        f.accuracyM?.let { parts += "±${it.toInt()} m" }
        parts += f.kind
    }
    st.battery?.let { parts += "battery $it%" }
    st.note?.let { parts += it }
    return parts.joinToString(" · ")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SamsungSignIn(onCancel: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    BackHandler(onBack = onCancel)
    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 40.dp, start = 8.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Text("Samsung sign-in", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, modifier = Modifier.weight(1f))
            if (checking) Text("Checking…", fontSize = 13.sp, color = Palette.Quiet)
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                CookieManager.getInstance().setAcceptCookie(true)
                WebView(ctx).apply {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = SamsungFind.USER_AGENT
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            if (!url.startsWith(SamsungFind.HOME) || url.contains("login", ignoreCase = true)) return
                            if (!SamsungFind.isSignedIn() || checking) return
                            checking = true
                            scope.launch {
                                val ok = SamsungFind.connect().isSuccess
                                checking = false
                                if (ok) onDone()
                            }
                        }
                    }
                    loadUrl(SamsungFind.HOME)
                }
            },
        )
    }
}

private fun has(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun openSmartThingsNotifications(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, "com.samsung.android.oneconnect")
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "SmartThings isn't installed", Toast.LENGTH_SHORT).show() }
}

private fun shareRecording(context: Context, scanner: TagScanner) {
    val file = scanner.recordingFile ?: return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(send, "Share Tag Lab recording")) }
}

private fun copyTagReport(
    context: Context,
    scanner: TagScanner,
    sightings: Collection<dev.krishnagurram.budguard.tracking.TagSighting>,
    linked: List<String>,
    paired: List<String>,
    signedIn: Boolean,
    devices: List<SamsungFind.Device>,
    statuses: Collection<SamsungFind.Status>,
    note: String?,
) {
    val now = System.currentTimeMillis()
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    val text = buildString {
        appendLine("BudGuard Tag Lab report (v$version, ${Build.MODEL}, Android ${Build.VERSION.RELEASE})")
        appendLine()
        appendLine("## 1. Live Bluetooth links")
        appendLine(if (linked.isEmpty()) "none" else linked.joinToString("\n"))
        appendLine("Paired tags: ${if (paired.isEmpty()) "none" else paired.joinToString("; ")}")
        appendLine()
        appendLine("## 2. Tag broadcasts")
        appendLine(TagLab.summary(sightings, now))
        scanner.recordingFile?.let { appendLine("Recording: ${it.name}, ${scanner.recordedRows} rows") }
        appendLine()
        appendLine("## 3. Samsung Find")
        appendLine("Signed in: $signedIn · devices: ${devices.size} · tags: ${devices.count { it.isTag }}")
        devices.forEach { d -> appendLine("- ${d.name} (${d.type}, ${d.model})") }
        statuses.forEach { st -> appendLine("  ${st.device.name}: ${statusLine(st, now)}") }
        note?.let { appendLine("Last note: $it") }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Tag Lab report", text))
    Toast.makeText(context, "Copied. Paste it into the chat.", Toast.LENGTH_SHORT).show()
}
