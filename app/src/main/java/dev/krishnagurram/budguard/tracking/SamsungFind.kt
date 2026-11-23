package dev.krishnagurram.budguard.tracking

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Reads your own tags from the SmartThings Find website after you sign in to
 * Samsung inside BudGuard. Unofficial: the request shapes come from the
 * community HA SmartThings Find integration and can break if Samsung changes
 * the site. The session cookie stays in this app's WebView storage on the phone.
 */
object SamsungFind {

    const val HOME = "https://smartthingsfind.samsung.com/"
    private const val CSRF = "https://smartthingsfind.samsung.com/chkLogin.do"
    private const val DEVICES = "https://smartthingsfind.samsung.com/device/getDeviceList.do"
    private const val OPERATION = "https://smartthingsfind.samsung.com/dm/addOperation.do"
    private const val LAST = "https://smartthingsfind.samsung.com/device/setLastSelect.do"
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 16; SM-F971U1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    data class Device(val id: String, val userId: String, val name: String, val type: String, val model: String) {
        val isTag: Boolean get() = type.equals("TAG", ignoreCase = true)
    }

    data class Fix(val lat: Double, val lng: Double, val accuracyM: Double?, val time: Long, val kind: String)

    data class Status(val device: Device, val fix: Fix?, val battery: Int?, val note: String?)

    class Session(val csrf: String)

    fun isSignedIn(): Boolean =
        runCatching { CookieManager.getInstance().getCookie(HOME)?.contains("JSESSIONID") == true }.getOrDefault(false)

    fun signOut() {
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    suspend fun connect(): Result<Session> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open(CSRF, "GET")
            val code = conn.responseCode
            val token = conn.getHeaderField("_csrf")
            storeCookies(conn)
            conn.disconnect()
            if (code != 200 || token.isNullOrBlank()) error("Not signed in (HTTP $code)")
            Session(token)
        }
    }

    suspend fun devices(session: Session): Result<List<Device>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = post("$DEVICES?_csrf=${session.csrf}", form = true, payload = "")
            parseDevices(body)
        }
    }

    /** Asks Samsung to refresh the tag's location, waits, then reads what it has. */
    suspend fun status(session: Session, device: Device, refresh: Boolean): Result<Status> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (refresh) {
                    val ask = JSONObject()
                        .put("dvceId", device.id)
                        .put("operation", "CHECK_CONNECTION_WITH_LOCATION")
                        .put("usrId", device.userId)
                    runCatching { post("$OPERATION?_csrf=${session.csrf}", payload = ask.toString()) }
                    delay(6_000)
                }
                val read = JSONObject().put("dvceId", device.id).put("removeDevice", JSONArray())
                parseStatus(device, post("$LAST?_csrf=${session.csrf}", payload = read.toString()))
            }
        }

    suspend fun ring(session: Session, device: Device, start: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("dvceId", device.id)
                .put("operation", "RING")
                .put("usrId", device.userId)
                .put("status", if (start) "start" else "stop")
                .put("lockMessage", "BudGuard is ringing your tag")
            post("$OPERATION?_csrf=${session.csrf}", payload = payload.toString())
            Unit
        }
    }

    // ---- parsing (pure, unit-tested) ----

    fun parseDevices(json: String): List<Device> {
        val list = JSONObject(json).optJSONArray("deviceList") ?: return emptyList()
        return (0 until list.length()).map { i ->
            val d = list.getJSONObject(i)
            Device(
                id = d.optString("dvceID"),
                userId = d.optString("usrId"),
                name = unescape(unescape(d.optString("modelName"))),
                type = d.optString("deviceTypeCode"),
                model = d.optString("modelID"),
            )
        }
    }

    fun parseStatus(device: Device, json: String): Status {
        val ops = JSONObject(json).optJSONArray("operation") ?: return Status(device, null, null, "No data from Samsung")
        var best: Fix? = null
        var battery: Int? = null
        var encrypted = 0
        for (i in 0 until ops.length()) {
            val op = ops.getJSONObject(i)
            val type = op.optString("oprnType")
            if (type == "CHECK_CONNECTION" && op.has("battery")) battery = batteryLevel(op.optString("battery"))
            if (type !in setOf("LOCATION", "LASTLOC", "OFFLINE_LOC")) continue

            val src = when {
                op.has("latitude") -> op
                op.optJSONObject("encLocation")?.optBoolean("encrypted") == true -> { encrypted++; null }
                else -> op.optJSONObject("encLocation")
            } ?: continue
            val date = src.optString("gpsUtcDt").ifBlank { op.optJSONObject("extra")?.optString("gpsUtcDt").orEmpty() }
            val time = parseDate(date) ?: continue
            val lat = src.optString("latitude").toDoubleOrNull() ?: continue
            val lng = src.optString("longitude").toDoubleOrNull() ?: continue
            val accuracy = accuracy(src.optString("horizontalUncertainty"), src.optString("verticalUncertainty"))
            if (best == null || time > best.time) best = Fix(lat, lng, accuracy, time, type)
        }
        val note = when {
            best != null -> null
            encrypted > 0 -> "Samsung only returned encrypted locations ($encrypted)"
            else -> "No location in Samsung's answer"
        }
        return Status(device, best, battery, note)
    }

    fun batteryLevel(raw: String): Int? = when (raw.uppercase()) {
        "FULL" -> 100
        "MEDIUM" -> 50
        "LOW" -> 15
        "VERY_LOW" -> 5
        else -> raw.toIntOrNull()
    }

    fun parseDate(raw: String): Long? {
        if (raw.length != 14) return null
        val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return runCatching { fmt.parse(raw)?.time }.getOrNull()
    }

    fun accuracy(h: String, v: String): Double? {
        val hu = h.toDoubleOrNull() ?: return null
        val vu = v.toDoubleOrNull() ?: 0.0
        return Math.round(Math.sqrt(hu * hu + vu * vu) * 10) / 10.0
    }

    fun unescape(s: String): String {
        var out = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")
        out = Regex("&#(\\d+);").replace(out) { it.groupValues[1].toInt().toChar().toString() }
        return out
    }

    // ---- http ----

    private fun open(url: String, method: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/json")
        CookieManager.getInstance().getCookie(url)?.let { conn.setRequestProperty("Cookie", it) }
        return conn
    }

    private fun post(url: String, payload: String, form: Boolean = false): String {
        val conn = open(url, "POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", if (form) "application/x-www-form-urlencoded" else "application/json")
        conn.outputStream.use { it.write(payload.toByteArray()) }
        val code = conn.responseCode
        storeCookies(conn)
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) error("Samsung returned HTTP $code")
        return text
    }

    private fun storeCookies(conn: HttpURLConnection) {
        val cookies = conn.headerFields?.entries
            ?.firstOrNull { it.key.equals("Set-Cookie", ignoreCase = true) }?.value ?: return
        val manager = CookieManager.getInstance()
        cookies.forEach { manager.setCookie(HOME, it) }
        manager.flush()
    }
}
