package app.rowan.gym.net

import android.content.Context
import android.net.ConnectivityManager
import app.rowan.gym.BuildConfig
import app.rowan.gym.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.Inet4Address
import java.net.HttpURLConnection
import java.net.URL

sealed class HubResult {
    data class Ok(val json: JSONObject) : HubResult()
    data class Err(val status: Int, val code: String, val message: String) : HubResult()
    data object Unreachable : HubResult()
}

/** Talks to the Rowan hub (the Raspberry Pi on the home LAN). Everything here may legitimately fail when away. */
class HubClient(private val ctx: Context) {
    @Volatile private var working: String? = null
    private var lastScan = 0L

    private fun candidates(prefs: Prefs): List<String> {
        val l = mutableListOf<String>()
        working?.let { l += it }
        if (prefs.hubUrl.isNotBlank()) l += normalize(prefs.hubUrl)
        l += BuildConfig.HUB_URL
        l += "http://mosaic:8787"
        return l.distinct()
    }

    fun normalize(raw: String): String {
        var u = raw.trim().trimEnd('/')
        if (!u.startsWith("http")) u = "http://$u"
        if (!Regex(":\\d+$").containsMatchIn(u.removePrefix("http://").removePrefix("https://"))) u += ":8787"
        return u
    }

    /** Pings [base]; bounded even when DNS resolution blocks (the blocking call is abandoned on timeout). */
    private suspend fun isHub(base: String, ms: Long = 2500): Boolean {
        val d = CoroutineScope(Dispatchers.IO).async { raw(base, "GET", "/api/ping", null, null, connectMs = 1800, readMs = 2000) }
        val r = withTimeoutOrNull(ms) { d.await() }
        return r is HubResult.Ok && r.json.optString("app") == "rowan"
    }

    /** Returns the reachable base URL, or null when we are not on the home network. */
    suspend fun discover(prefs: Prefs): String? {
        val found = coroutineScope {
            candidates(prefs).map { c -> async { if (isHub(c)) c else null } }.awaitAll().firstOrNull { it != null }
        }
        working = found ?: scanLan()
        android.util.Log.d("RowanHub", "discover candidates=${candidates(prefs)} found=$found working=$working prefix=${localPrefix()}")
        return working
    }

    /** Fallback when mDNS (`mosaic.local`) does not resolve on this phone: probe the local /24 for the hub. */
    private suspend fun scanLan(): String? {
        val now = System.currentTimeMillis()
        if (now - lastScan < 45_000) return null
        lastScan = now
        val prefix = localPrefix() ?: return null
        return coroutineScope {
            val gate = Semaphore(48)
            (1..254).map { host ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        val base = "http://$prefix.$host:8787"
                        val r = raw(base, "GET", "/api/ping", null, null, connectMs = 1200, readMs = 1500)
                        if (r is HubResult.Ok && r.json.optString("app") == "rowan") base else null
                    }
                }
            }.awaitAll().firstOrNull { it != null }
        }
    }

    private fun localPrefix(): String? = runCatching {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        val a = lp.linkAddresses.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress } ?: return null
        a.address.hostAddress?.substringBeforeLast('.')
    }.getOrNull()

    suspend fun call(prefs: Prefs, method: String, path: String, body: JSONObject? = null, auth: Boolean = false): HubResult {
        val base = working ?: discover(prefs) ?: return HubResult.Unreachable
        return raw(base, method, path, body?.toString(), if (auth) prefs.token else null).also { if (it is HubResult.Unreachable) working = null }
    }

    suspend fun putRaw(prefs: Prefs, path: String, json: String): HubResult {
        val base = working ?: discover(prefs) ?: return HubResult.Unreachable
        return raw(base, "PUT", path, json, prefs.token, readMs = 60_000)
    }

    suspend fun getRaw(prefs: Prefs, path: String): Pair<String?, HubResult> {
        val base = working ?: discover(prefs) ?: return null to HubResult.Unreachable
        var text: String? = null
        val r = raw(base, "GET", path, null, prefs.token, readMs = 60_000) { text = it }
        return text to r
    }

    private suspend fun raw(
        base: String, method: String, path: String, body: String?, token: String?,
        connectMs: Int = 4000, readMs: Int = 25_000, sink: ((String) -> Unit)? = null,
    ): HubResult = withContext(Dispatchers.IO) {
        var c: HttpURLConnection? = null
        try {
            c = (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectMs; readTimeout = readMs
                setRequestProperty("Accept", "application/json")
                if (token != null) setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    outputStream.use { it.write(body.toByteArray()) }
                }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            sink?.invoke(text)
            val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (code in 200..299) HubResult.Ok(json)
            else HubResult.Err(code, json.optString("error", "http_$code"), json.optString("message"))
        } catch (e: IOException) {
            HubResult.Unreachable
        } catch (e: Exception) {
            HubResult.Unreachable
        } finally {
            c?.disconnect()
        }
    }
}
