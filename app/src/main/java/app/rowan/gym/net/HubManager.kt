package app.rowan.gym.net

import app.rowan.gym.BuildConfig
import app.rowan.gym.RowanApp
import app.rowan.gym.data.Stats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

data class BoardEntry(val rank: Int, val name: String, val xp: Int, val level: Int, val streakWeeks: Int, val me: Boolean)
data class SyncOutcome(val rank: Int, val total: Int, val xp: Int)

/** Stateful facade over [HubClient]: reachability, auth, leaderboard, cloud backup and update checks. */
class HubManager(private val app: RowanApp) {
    private val client get() = app.hub
    private val settings get() = app.settings

    private val _reachable = MutableStateFlow<Boolean?>(null)
    val reachable: StateFlow<Boolean?> = _reachable
    private val _update = MutableStateFlow<UpdateInfo?>(null)
    val update: StateFlow<UpdateInfo?> = _update
    private val _board = MutableStateFlow<List<BoardEntry>>(emptyList())
    val board: StateFlow<List<BoardEntry>> = _board
    private var lastUpdateCheck = 0L

    suspend fun refresh(): Boolean {
        val p = settings.flow.first()
        val found = client.discover(p)
        // remember an address found by the LAN scan so next time it is instant
        if (found != null && found != p.hubUrl && found != app.hub.normalize(BuildConfig.HUB_URL) && p.hubUrl.isBlank()) settings.update { hubUrl = found }
        _reachable.value = found != null
        return found != null
    }

    suspend fun normalizeAndSaveUrl(raw: String) {
        val v = if (raw.isBlank()) "" else client.normalize(raw)
        settings.update { hubUrl = v }
    }

    // ---------- auth ----------
    data class AuthOk(val token: String, val email: String, val name: String)

    private suspend fun post(path: String, body: JSONObject, auth: Boolean = false): HubResult =
        client.call(settings.flow.first(), "POST", path, body, auth)

    suspend fun register(email: String, password: String, name: String, lang: String): HubResult =
        post("/api/register", JSONObject().put("email", email).put("password", password).put("name", name).put("lang", lang))

    suspend fun resend(email: String, lang: String): HubResult =
        post("/api/resend", JSONObject().put("email", email).put("lang", lang))

    suspend fun verify(email: String, code: String): Pair<HubResult, AuthOk?> = authed(post("/api/verify", JSONObject().put("email", email).put("code", code)))
    suspend fun login(email: String, password: String, lang: String): Pair<HubResult, AuthOk?> =
        authed(post("/api/login", JSONObject().put("email", email).put("password", password).put("lang", lang)))
    suspend fun forgot(email: String, lang: String): HubResult = post("/api/forgot", JSONObject().put("email", email).put("lang", lang))
    suspend fun reset(email: String, code: String, password: String): Pair<HubResult, AuthOk?> =
        authed(post("/api/reset", JSONObject().put("email", email).put("code", code).put("password", password)))

    private suspend fun authed(r: HubResult): Pair<HubResult, AuthOk?> {
        if (r !is HubResult.Ok) return r to null
        val token = r.json.optString("token")
        val u = r.json.optJSONObject("user")
        if (token.isBlank() || u == null) return HubResult.Err(500, "bad_response", "") to null
        val ok = AuthOk(token, u.optString("email"), u.optString("name"))
        settings.update { this.token = ok.token; this.email = ok.email }
        _reachable.value = true
        return r to ok
    }

    suspend fun logout() = settings.update { token = ""; email = ""; boardCache = "" }.also { _board.value = emptyList() }

    // ---------- leaderboard ----------
    suspend fun sync(s: Stats.Summary, name: String): Pair<HubResult, SyncOutcome?> {
        val body = JSONObject().put("xp", s.xp).put("weekXp", s.weekXp).put("level", s.level).put("workouts", s.workouts)
            .put("streakWeeks", s.streak).put("volume", s.volumeKg.toLong()).put("prs", s.prs).put("name", name)
        val r = client.call(settings.flow.first(), "POST", "/api/sync", body, auth = true)
        if (r is HubResult.Err && r.status == 401) logout()
        if (r !is HubResult.Ok) return r to null
        val o = SyncOutcome(r.json.optInt("rank"), r.json.optInt("total"), r.json.optInt("xp", s.xp))
        settings.update { lastSync = System.currentTimeMillis(); lastSyncXp = s.xp; lastRank = o.rank }
        _reachable.value = true
        return r to o
    }

    suspend fun loadBoard(period: String): HubResult {
        val r = client.call(settings.flow.first(), "GET", "/api/leaderboard?period=$period", null, auth = true)
        if (r is HubResult.Err && r.status == 401) logout()
        if (r is HubResult.Ok) {
            val a = r.json.optJSONArray("entries") ?: JSONArray()
            _board.value = (0 until a.length()).map {
                val o = a.getJSONObject(it)
                BoardEntry(o.optInt("rank"), o.optString("name"), o.optInt("xp"), o.optInt("level", 1), o.optInt("streakWeeks"), o.optBoolean("me"))
            }
            settings.update { boardCache = a.toString() }
            _reachable.value = true
        }
        return r
    }

    fun cachedBoard(raw: String): List<BoardEntry> = runCatching {
        val a = JSONArray(raw)
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            BoardEntry(o.optInt("rank"), o.optString("name"), o.optInt("xp"), o.optInt("level", 1), o.optInt("streakWeeks"), o.optBoolean("me"))
        }
    }.getOrDefault(emptyList())

    // ---------- cloud backup (to the Pi) ----------
    suspend fun uploadBackup(): HubResult {
        val json = app.repo.backup.toJson()
        val r = client.putRaw(settings.flow.first(), "/api/backup", json)
        if (r is HubResult.Ok) settings.update { lastExport = System.currentTimeMillis() }
        return r
    }

    suspend fun downloadBackup(): Pair<String?, HubResult> = client.getRaw(settings.flow.first(), "/api/backup")

    // ---------- updates ----------
    suspend fun checkUpdate(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUpdateCheck < 6 * 3600_000L && _update.value == null) return
        lastUpdateCheck = now
        _update.value = Updates.check()
    }
}
