package app.plinth.gym.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class Repository(ctx: Context, val db: PlinthDb, val settings: Settings, scope: CoroutineScope) {
    private val dao = db.dao()
    val backup = BackupManager(ctx, db, settings)

    val exercises = dao.exercises().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val notes = dao.notes().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val bodyWeights = dao.bodyWeights().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val dayPlans: StateFlow<Map<String, DayPlan>> =
        dao.dayPlans().map { l -> l.associateBy { it.date } }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val templates: StateFlow<List<TemplateFull>> =
        combine(dao.templates(), dao.templateExercises()) { t, te ->
            val by = te.groupBy { it.te.templateId }
            t.map { TemplateFull(it, by[it.id].orEmpty()) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** All sessions (drafts included) fully assembled. Shared by every screen. */
    val sessions: StateFlow<List<SessionFull>> =
        combine(dao.sessions(), dao.sessionExercises(), dao.sets()) { s, e, st ->
            val setsBy = st.groupBy { it.sessionExerciseId }
            val exBy = e.groupBy { it.sessionId }
            s.map { ses -> SessionFull(ses, exBy[ses.id].orEmpty().map { ExerciseFull(it, setsBy[it.id].orEmpty()) }) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val doneSessions: StateFlow<List<SessionFull>> =
        sessions.map { l -> l.filter { it.session.status == SessionStatus.DONE } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        // Reliability: rolling JSON snapshot a few seconds after any data change settles.
        scope.launch {
            combine(sessions, templates, dayPlans, notes, bodyWeights) { _, _, _, _, _ -> 0 }
                .drop(1).debounce(4000).collect { backup.snapshot("auto") }
        }
    }

    // ---------------- exercises ----------------
    suspend fun addExercise(name: String, measure: Measure): Exercise {
        val clean = name.trim()
        dao.exerciseByName(clean)?.let {
            if (it.archived) dao.upsert(it.copy(archived = false))
            return it.copy(archived = false)
        }
        val id = dao.upsert(Exercise(name = clean, measure = measure))
        return Exercise(id, clean, measure)
    }

    suspend fun archiveExercise(e: Exercise) = dao.upsert(e.copy(archived = true))
    suspend fun renameExercise(e: Exercise, name: String, measure: Measure) = dao.upsert(e.copy(name = name.trim(), measure = measure))

    // ---------------- templates ----------------
    data class TemplateExerciseDraft(
        val exerciseId: Long, val sets: Int, val repsMin: Int, val repsMax: Int, val weight: Double, val note: String,
    )

    suspend fun saveTemplate(id: Long?, name: String, colorIdx: Int, items: List<TemplateExerciseDraft>): Long = db.withTransaction {
        val tid = if (id == null || id == 0L) {
            val pos = (dao.allTemplates().maxOfOrNull { it.position } ?: -1) + 1
            dao.upsert(WorkoutTemplate(name = name.trim(), colorIdx = colorIdx, position = pos))
        } else {
            val old = dao.template(id)
            dao.update(WorkoutTemplate(id, name.trim(), colorIdx, old?.position ?: 0)); id
        }
        dao.clearTemplateExercises(tid)
        dao.insertTemplateExercises(items.mapIndexed { i, d ->
            TemplateExercise(0, tid, d.exerciseId, i, d.sets, d.repsMin, d.repsMax, d.weight, d.note)
        })
        tid
    }

    suspend fun duplicateTemplate(src: TemplateFull, copySuffix: String): Long =
        saveTemplate(null, src.template.name + " " + copySuffix, src.template.colorIdx,
            src.exercises.map { TemplateExerciseDraft(it.te.exerciseId, it.te.sets, it.te.repsMin, it.te.repsMax, it.te.weight, it.te.note) })

    suspend fun deleteTemplate(id: Long) = dao.deleteTemplate(id)

    suspend fun moveTemplate(id: Long, delta: Int) = db.withTransaction {
        val l = dao.allTemplates().sortedWith(compareBy({ it.position }, { it.id })).toMutableList()
        val i = l.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in l.indices) return@withTransaction
        val t = l.removeAt(i); l.add(j, t)
        l.forEachIndexed { idx, w -> dao.update(w.copy(position = idx)) }
    }

    // ---------------- calendar ----------------
    suspend fun setDay(date: LocalDate, type: DayType, templateId: Long?) {
        val cur = dao.dayPlan(date.toString())
        val next = DayPlan(date.toString(), type, if (type == DayType.WORKOUT) templateId else null, cur?.note ?: "")
        if (next.type == DayType.NONE && next.note.isEmpty()) dao.deleteDayPlan(next.date) else dao.upsert(next)
    }

    suspend fun setDayNote(date: LocalDate, note: String) {
        val cur = dao.dayPlan(date.toString()) ?: DayPlan(date.toString())
        val next = cur.copy(note = note.trim())
        if (next.type == DayType.NONE && next.note.isEmpty()) dao.deleteDayPlan(next.date) else dao.upsert(next)
    }

    /** Moves the planned workout/rest from one day to another. Notes stay with their own day. */
    suspend fun moveDay(from: LocalDate, to: LocalDate) = db.withTransaction {
        if (from == to) return@withTransaction
        val a = dao.dayPlan(from.toString()) ?: return@withTransaction
        val b = dao.dayPlan(to.toString())
        dao.upsert(DayPlan(to.toString(), a.type, a.templateId, b?.note ?: ""))
        val rest = DayPlan(from.toString(), DayType.NONE, null, a.note)
        if (rest.note.isEmpty()) dao.deleteDayPlan(rest.date) else dao.upsert(rest)
    }

    /** Copies a weekday pattern onto a month (independent copies; only empty days unless overwrite). templateId -1 = rest. */
    suspend fun planMonth(year: Int, month: Int, pattern: Map<Int, Long?>, overwrite: Boolean) = db.withTransaction {
        val first = LocalDate.of(year, month, 1)
        for (d in 0 until first.lengthOfMonth()) {
            val date = first.plusDays(d.toLong())
            val p = pattern[date.dayOfWeek.value] ?: continue
            val cur = dao.dayPlan(date.toString())
            if (cur != null && cur.type != DayType.NONE && !overwrite) continue
            val keepNote = cur?.note ?: ""
            dao.upsert(if (p == -1L) DayPlan(date.toString(), DayType.REST, null, keepNote) else DayPlan(date.toString(), DayType.WORKOUT, p, keepNote))
        }
    }

    // ---------------- sessions ----------------
    suspend fun startSession(date: LocalDate, templateId: Long?, emptyName: String): Long = db.withTransaction {
        dao.allSessions().firstOrNull { it.status == SessionStatus.DRAFT && it.date == date.toString() && it.templateId == templateId }
            ?.let { return@withTransaction it.id }
        val tpl = templateId?.let { dao.template(it) }
        val sid = dao.upsert(Session(date = date.toString(), templateId = tpl?.id, name = tpl?.name ?: emptyName))
        if (tpl != null) {
            for (e in exercisesFromTemplate(tpl.id, sid)) {
                val seId = dao.insert(e.ex.copy(sessionId = sid))
                dao.insertSets(e.sets.map { it.copy(sessionExerciseId = seId) })
            }
        }
        sid
    }


    /** Builds the exercise list for a workout from a template, pre-filling sets from the last time each exercise was done. */
    suspend fun exercisesFromTemplate(templateId: Long, excludeSession: Long): List<ExerciseFull> {
        val out = mutableListOf<ExerciseFull>()
        for (te in dao.templateExercises(templateId)) {
            val last = dao.lastExercise(te.te.exerciseId, excludeSession)
            val lastSets = last?.let { dao.setsOf(it.id) }.orEmpty()
            val ex = SessionExercise(
                sessionId = 0, exerciseId = te.te.exerciseId, name = te.exName, measure = te.exMeasure,
                position = te.te.position, note = te.te.note,
            )
            val n = te.te.sets.coerceAtLeast(1)
            val sets = (0 until n).map { i ->
                val ls = lastSets.getOrNull(i) ?: lastSets.lastOrNull()
                SetEntry(
                    sessionExerciseId = 0, position = i,
                    weight = if (te.exMeasure == Measure.WEIGHT_REPS) (ls?.weight ?: te.te.weight.takeIf { it > 0 }) else null,
                    reps = if (te.exMeasure != Measure.TIME) (ls?.reps ?: te.te.repsMin) else null,
                    seconds = if (te.exMeasure == Measure.TIME) (ls?.seconds ?: te.te.repsMin) else null,
                )
            }
            out += ExerciseFull(ex, sets)
        }
        return out
    }

    suspend fun loadSession(id: Long): SessionFull? = db.withTransaction {
        val s = dao.session(id) ?: return@withTransaction null
        SessionFull(s, dao.sessionExercises(id).map { ExerciseFull(it, dao.setsOf(it.id)) })
    }

    /** Replaces a session's full content atomically (used by autosave and by the final save). */
    suspend fun saveSession(sf: SessionFull) = db.withTransaction {
        if (dao.session(sf.session.id) == null) return@withTransaction
        dao.update(sf.session)
        dao.clearSessionExercises(sf.session.id)
        sf.exercises.forEachIndexed { i, e ->
            val id = dao.insert(e.ex.copy(id = 0, sessionId = sf.session.id, position = i))
            dao.insertSets(e.sets.mapIndexed { j, s -> s.copy(id = 0, sessionExerciseId = id, position = j) })
        }
    }

    suspend fun deleteSession(id: Long) = dao.deleteSession(id)

    data class LastTime(val date: String, val sets: List<SetEntry>, val nextTimeNote: String, val note: String)

    suspend fun lastTime(exerciseId: Long, excludeSession: Long): LastTime? {
        val e = dao.lastExercise(exerciseId, excludeSession) ?: return null
        val date = dao.session(e.sessionId)?.date ?: return null
        return LastTime(date, dao.setsOf(e.id).filter { it.done || it.reps != null || it.seconds != null }, e.nextTimeNote, e.note)
    }

    // ---------------- notes / weight ----------------
    suspend fun saveNote(n: Note) = dao.upsert(n)
    suspend fun deleteNote(n: Note) = dao.delete(n)
    suspend fun addBodyWeight(date: LocalDate, kg: Double) = dao.upsert(BodyWeight(date = date.toString(), kg = kg))
    suspend fun deleteBodyWeight(b: BodyWeight) = dao.delete(b)

    // ---------------- seeding ----------------
    private data class Seed(val en: String, val it: String, val m: Measure = Measure.WEIGHT_REPS)

    private val seeds = listOf(
        Seed("Bench press", "Panca piana"), Seed("Incline dumbbell press", "Panca inclinata manubri"),
        Seed("Chest fly", "Croci ai cavi"), Seed("Overhead press", "Lento avanti"),
        Seed("Lateral raise", "Alzate laterali"), Seed("Triceps pushdown", "Push down tricipiti"),
        Seed("Skull crusher", "French press"), Seed("Dips", "Dip", Measure.REPS),
        Seed("Push-ups", "Piegamenti", Measure.REPS), Seed("Pull-ups", "Trazioni", Measure.REPS),
        Seed("Lat pulldown", "Lat machine"), Seed("Barbell row", "Rematore bilanciere"),
        Seed("Seated cable row", "Pulley basso"), Seed("Face pull", "Face pull"),
        Seed("Biceps curl", "Curl bicipiti"), Seed("Hammer curl", "Curl a martello"),
        Seed("Squat", "Squat"), Seed("Front squat", "Front squat"), Seed("Leg press", "Leg press"),
        Seed("Lunges", "Affondi"), Seed("Leg extension", "Leg extension"), Seed("Leg curl", "Leg curl"),
        Seed("Romanian deadlift", "Stacco rumeno"), Seed("Deadlift", "Stacco da terra"),
        Seed("Hip thrust", "Hip thrust"), Seed("Calf raise", "Calf in piedi"),
        Seed("Crunch", "Crunch", Measure.REPS), Seed("Hanging leg raise", "Sollevamento gambe", Measure.REPS),
        Seed("Plank", "Plank", Measure.TIME), Seed("Side plank", "Plank laterale", Measure.TIME),
        Seed("Dead hang", "Hang alla sbarra", Measure.TIME), Seed("Farmer's walk", "Farmer's walk", Measure.TIME),
    )

    suspend fun seedExercises(lang: String) {
        if (dao.allExercises().isNotEmpty()) return
        dao.upsertExercises(seeds.map { Exercise(name = if (lang == "it") it.it else it.en, measure = it.m) })
    }

    /** Two ready-to-edit starter workouts so a new user is never staring at an empty app. */
    suspend fun seedStarterPlan(lang: String, nameA: String, nameB: String) {
        seedExercises(lang)
        if (dao.allTemplates().isNotEmpty()) return
        suspend fun ex(en: String): Long {
            val s = seeds.first { it.en == en }
            return dao.exerciseByName(if (lang == "it") s.it else s.en)?.id ?: addExercise(if (lang == "it") s.it else s.en, s.m).id
        }
        fun d(id: Long, sets: Int, a: Int, b: Int, note: String = "") = TemplateExerciseDraft(id, sets, a, b, 0.0, note)
        saveTemplate(null, nameA, 0, listOf(
            d(ex("Squat"), 4, 6, 8), d(ex("Bench press"), 4, 6, 10), d(ex("Barbell row"), 3, 8, 12),
            d(ex("Overhead press"), 3, 8, 12), d(ex("Plank"), 3, 30, 45),
        ))
        saveTemplate(null, nameB, 1, listOf(
            d(ex("Deadlift"), 3, 5, 6), d(ex("Incline dumbbell press"), 3, 8, 12), d(ex("Lat pulldown"), 3, 8, 12),
            d(ex("Lunges"), 3, 10, 12), d(ex("Biceps curl"), 3, 10, 12), d(ex("Triceps pushdown"), 3, 10, 12),
        ))
    }
}
