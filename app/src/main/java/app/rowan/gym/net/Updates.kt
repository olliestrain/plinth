package app.rowan.gym.net

import app.rowan.gym.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: String, val notes: String, val pageUrl: String, val apkUrl: String?)

/** Looks at the latest GitHub release and says whether it is newer than the installed build. */
object Updates {
    private fun parts(v: String) = v.trim().removePrefix("v").split('.', '-').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    val configured get() = BuildConfig.GITHUB_REPO.contains('/') && !BuildConfig.GITHUB_REPO.startsWith("OWNER/")

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!configured) return@withContext null
        runCatching {
            val c = (URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest").openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000; readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Rowan-Android")
            }
            if (c.responseCode != 200) return@runCatching null
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = j.optString("tag_name").ifBlank { return@runCatching null }
            if (!isNewer(tag, BuildConfig.VERSION_NAME)) return@runCatching null
            var apk: String? = null
            j.optJSONArray("assets")?.let { a ->
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    if (o.optString("name").endsWith(".apk")) { apk = o.optString("browser_download_url"); break }
                }
            }
            UpdateInfo(tag.removePrefix("v"), j.optString("body"), j.optString("html_url"), apk)
        }.getOrNull()
    }
}
