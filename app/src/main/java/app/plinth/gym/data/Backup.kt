package app.plinth.gym.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val BACKUP_FORMAT = "plinth-backup-v1"

class BackupException(message: String) : Exception(message)

class BackupManager(private val ctx: Context, private val db: PlinthDb, private val settings: Settings) {
    private val dao get() = db.dao()
    private val dir get() = File(ctx.filesDir, "autobackup").also { it.mkdirs() }

    // ---------- export ----------
    suspend fun toJson(): String = withContext(Dispatchers.IO) {
        val prefs = readPrefs()
        val root = JSONObject()
        root.put("format", BACKUP_FORMAT)
        root.put("createdAt", System.currentTimeMillis())
        root.put("profile", JSONObject().apply {
            put("name", prefs.name); put("goal", prefs.goal); put("experience", prefs.experience)
            put("weeklyGoal", prefs.weeklyGoal); put("trainingDays", JSONArray(prefs.trainingDays.toList()))
            put("useLb", prefs.useLb)
        })
        root.put("exercises", arr(dao.allExercises()) { JSONObject().put("id", it.id).put("name", it.name).put("measure", it.measure.name).put("archived", it.archived) })
        root.put("templates", arr(dao.allTemplates()) { JSONObject().put("id", it.id).put("name", it.name).put("colorIdx", it.colorIdx).put("position", it.position) })
        root.put("templateExercises", arr(dao.allTemplateExercises()) {
            JSONObject().put("id", it.id).put("templateId", it.templateId).put("exerciseId", it.exerciseId).put("position", it.position)
                .put("sets", it.sets).put("repsMin", it.repsMin).put("repsMax", it.repsMax).put("weight", it.weight).put("note", it.note)
        })
        root.put("dayPlans", arr(dao.allDayPlans()) {
            JSONObject().put("date", it.date).put("type", it.type.name).put("templateId", it.templateId ?: JSONObject.NULL).put("note", it.note)
        })
        root.put("sessions", arr(dao.allSessions()) {
            JSONObject().put("id", it.id).put("date", it.date).put("templateId", it.templateId ?: JSONObject.NULL).put("name", it.name)
                .put("status", it.status.name).put("startedAt", it.startedAt).put("durationSec", it.durationSec)
                .put("note", it.note).put("createdAt", it.createdAt)
        })
        root.put("sessionExercises", arr(dao.allSessionExercises()) {
            JSONObject().put("id", it.id).put("sessionId", it.sessionId).put("exerciseId", it.exerciseId).put("name", it.name)
                .put("measure", it.measure.name).put("position", it.position).put("note", it.note)
                .put("nextTimeNote", it.nextTimeNote).put("substitutedFrom", it.substitutedFrom ?: JSONObject.NULL)
        })
        root.put("sets", arr(dao.allSets()) {
            JSONObject().put("id", it.id).put("sessionExerciseId", it.sessionExerciseId).put("position", it.position)
                .put("weight", it.weight ?: JSONObject.NULL).put("reps", it.reps ?: JSONObject.NULL)
                .put("seconds", it.seconds ?: JSONObject.NULL).put("done", it.done)
        })
        root.put("notes", arr(dao.allNotes()) {
            JSONObject().put("id", it.id).put("text", it.text).put("createdAt", it.createdAt).put("archived", it.archived).put("source", it.source)
        })
        root.put("bodyWeights", arr(dao.allBodyWeights()) {
            JSONObject().put("id", it.id).put("date", it.date).put("kg", it.kg).put("createdAt", it.createdAt)
        })
        root.toString()
    }

    private fun <T> arr(list: List<T>, f: (T) -> JSONObject) = JSONArray().also { a -> list.forEach { a.put(f(it)) } }

    private suspend fun readPrefs(): Prefs = settings.flow.first()

    // ---------- import ----------
    /** Parses and validates everything BEFORE touching the DB, then swaps data atomically. */
    suspend fun import(json: String) = withContext(Dispatchers.IO) {
        val root = try { JSONObject(json) } catch (e: Exception) { throw BackupException("not-json") }
        if (root.optString("format") != BACKUP_FORMAT) throw BackupException("bad-format")
        val ex = list(root, "exercises") { Exercise(it.getLong("id"), it.getString("name"), Measure.valueOf(it.getString("measure")), it.optBoolean("archived")) }
        val tp = list(root, "templates") { WorkoutTemplate(it.getLong("id"), it.getString("name"), it.optInt("colorIdx"), it.optInt("position")) }
        val te = list(root, "templateExercises") {
            TemplateExercise(it.getLong("id"), it.getLong("templateId"), it.getLong("exerciseId"), it.getInt("position"),
                it.optInt("sets", 3), it.optInt("repsMin", 8), it.optInt("repsMax", 12), it.optDouble("weight", 0.0), it.optString("note"))
        }
        val dp = list(root, "dayPlans") { DayPlan(it.getString("date"), DayType.valueOf(it.getString("type")), it.optLongOrNull("templateId"), it.optString("note")) }
        val ss = list(root, "sessions") {
            Session(it.getLong("id"), it.getString("date"), it.optLongOrNull("templateId"), it.getString("name"),
                SessionStatus.valueOf(it.getString("status")), it.optLong("startedAt"), it.optInt("durationSec"), it.optString("note"), it.optLong("createdAt"))
        }
        val se = list(root, "sessionExercises") {
            SessionExercise(it.getLong("id"), it.getLong("sessionId"), it.getLong("exerciseId"), it.getString("name"),
                Measure.valueOf(it.getString("measure")), it.getInt("position"), it.optString("note"), it.optString("nextTimeNote"),
                if (it.isNull("substitutedFrom")) null else it.getString("substitutedFrom"))
        }
        val st = list(root, "sets") {
            SetEntry(it.getLong("id"), it.getLong("sessionExerciseId"), it.getInt("position"),
                if (it.isNull("weight")) null else it.getDouble("weight"), if (it.isNull("reps")) null else it.getInt("reps"),
                if (it.isNull("seconds")) null else it.getInt("seconds"), it.optBoolean("done"))
        }
        val nt = list(root, "notes") { Note(it.getLong("id"), it.getString("text"), it.optLong("createdAt"), it.optBoolean("archived"), it.optString("source")) }
        val bw = list(root, "bodyWeights") { BodyWeight(it.getLong("id"), it.getString("date"), it.getDouble("kg"), it.optLong("createdAt")) }
        val profile = root.optJSONObject("profile")

        snapshot("pre-import")
        db.withTransaction {
            dao.wipeSessions(); dao.wipeTemplates(); dao.wipeExercises(); dao.wipeDayPlans(); dao.wipeNotes(); dao.wipeBodyWeights()
            dao.upsertExercises(ex); dao.upsertTemplates(tp); dao.insertTemplateExercises(te); dao.upsertDayPlans(dp)
            dao.upsertSessions(ss); dao.upsertSessionExercises(se); dao.upsertSets(st); dao.upsertNotes(nt); dao.upsertBodyWeights(bw)
        }
        if (profile != null) settings.update {
            name = profile.optString("name", name); goal = profile.optString("goal", goal)
            experience = profile.optInt("experience", experience); weeklyGoal = profile.optInt("weeklyGoal", weeklyGoal)
            useLb = profile.optBoolean("useLb", useLb)
            profile.optJSONArray("trainingDays")?.let { a -> trainingDays = (0 until a.length()).map { a.getInt(it) }.toSet() }
        }
    }

    private fun <T> list(root: JSONObject, key: String, f: (JSONObject) -> T): List<T> {
        val a = root.optJSONArray(key) ?: return emptyList()
        return try { (0 until a.length()).map { f(a.getJSONObject(it)) } } catch (e: Exception) { throw BackupException("corrupt:$key") }
    }

    private fun JSONObject.optLongOrNull(k: String): Long? = if (isNull(k) || !has(k)) null else getLong(k)

    // ---------- rolling local snapshots ----------
    suspend fun snapshot(tag: String = "auto"): File? = withContext(Dispatchers.IO) {
        runCatching {
            val json = toJson()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            writeAtomic(File(dir, "$tag-$stamp.json"), json)
            writeAtomic(File(dir, "daily-$day.json"), json)
            prune()
            File(dir, "$tag-$stamp.json")
        }.getOrNull()
    }

    private fun writeAtomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    private fun prune() {
        val files = dir.listFiles { f -> f.name.endsWith(".json") } ?: return
        files.filter { it.name.startsWith("daily-") }.sortedByDescending { it.name }.drop(14).forEach { it.delete() }
        files.filter { !it.name.startsWith("daily-") && !it.name.startsWith("pre-import") }
            .sortedByDescending { it.lastModified() }.drop(10).forEach { it.delete() }
        files.filter { it.name.startsWith("pre-import") }.sortedByDescending { it.lastModified() }.drop(3).forEach { it.delete() }
    }

    fun snapshots(): List<File> =
        (dir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()).sortedByDescending { it.lastModified() }
}
