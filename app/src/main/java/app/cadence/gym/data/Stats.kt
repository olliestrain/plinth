package app.cadence.gym.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

/** Pure functions: everything gamified/derived is recomputed from raw data, so imports restore it for free. */
object Stats {
    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun epley(weight: Double, reps: Int): Double = if (reps <= 1) weight else weight * (1 + reps / 30.0)

    /** Best "score" of an exercise in one session, used for PR detection. */
    fun exerciseBest(e: ExerciseFull): Double {
        val sets = e.sets.filter { it.done }
        return when (e.ex.measure) {
            Measure.WEIGHT_REPS -> sets.maxOfOrNull { epley(it.weight ?: 0.0, it.reps ?: 0) } ?: 0.0
            Measure.REPS -> sets.maxOfOrNull { (it.reps ?: 0).toDouble() } ?: 0.0
            Measure.TIME -> sets.maxOfOrNull { (it.seconds ?: 0).toDouble() } ?: 0.0
        }
    }

    fun sessionVolume(s: SessionFull): Double = s.exercises.sumOf { e ->
        e.sets.filter { it.done }.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) }
    }

    /** exerciseId -> number of PRs hit per session id. First time doing an exercise is not a PR. */
    fun prsPerSession(done: List<SessionFull>): Map<Long, Int> {
        val best = HashMap<Long, Double>()
        val out = HashMap<Long, Int>()
        for (s in done.sortedWith(compareBy({ it.session.date }, { it.session.createdAt }))) {
            var n = 0
            for (e in s.exercises) {
                val b = exerciseBest(e)
                if (b <= 0.0) continue
                val prev = best[e.ex.exerciseId]
                if (prev != null && b > prev + 1e-6) n++
                if (prev == null || b > prev) best[e.ex.exerciseId] = b
            }
            out[s.session.id] = n
        }
        return out
    }

    data class WeekInfo(val start: LocalDate, val done: Int, val target: Int) { val met get() = done >= target && target > 0 }

    /** Consecutive weeks (ending now) that met their target; the running week never breaks a streak. */
    fun streakWeeks(done: List<SessionFull>, plans: List<DayPlan>, weeklyGoal: Int, today: LocalDate): Pair<Int, Int> {
        if (done.isEmpty()) return 0 to 0
        val doneByWeek = done.groupingBy { weekStart(LocalDate.parse(it.session.date)) }.eachCount()
        val plannedByWeek = plans.filter { it.type == DayType.WORKOUT }
            .groupingBy { weekStart(LocalDate.parse(it.date)) }.eachCount()
        fun met(w: LocalDate): Boolean {
            val target = plannedByWeek[w]?.takeIf { it > 0 } ?: weeklyGoal.coerceAtLeast(1)
            return (doneByWeek[w] ?: 0) >= target
        }
        val first = doneByWeek.keys.min()
        val thisWeek = weekStart(today)
        // best ever
        var best = 0; var run = 0; var w = first
        while (!w.isAfter(thisWeek)) {
            if (met(w)) { run++; best = max(best, run) } else if (w != thisWeek) run = 0
            w = w.plusWeeks(1)
        }
        // current
        var cur = if (met(thisWeek)) 1 else 0
        var p = thisWeek.minusWeeks(1)
        while (!p.isBefore(first) && met(p)) { cur++; p = p.minusWeeks(1) }
        return cur to max(best, cur)
    }

    data class Summary(
        val workouts: Int,
        val prs: Int,
        val volumeKg: Double,
        val streak: Int,
        val bestStreak: Int,
        val last7Volume: Double,
        val perWeek30: Float,
        val perMonth: Int,
        val doneDates: Set<LocalDate>,
        val totalSeconds: Long,
    )

    fun summarize(sessions: List<SessionFull>, plans: List<DayPlan>, weeklyGoal: Int, today: LocalDate = LocalDate.now()): Summary {
        val done = sessions.filter { it.session.status == SessionStatus.DONE }
        val prMap = prsPerSession(done)
        var prs = 0; var vol = 0.0; var l7 = 0.0; var secs = 0L
        for (s in done) {
            prs += prMap[s.session.id] ?: 0
            val d = LocalDate.parse(s.session.date)
            val v = sessionVolume(s); vol += v
            secs += s.session.durationSec
            if (ChronoUnit.DAYS.between(d, today) in 0..6) l7 += v
        }
        val (cur, best) = streakWeeks(done, plans, weeklyGoal, today)
        val dates = done.map { LocalDate.parse(it.session.date) }
        val last28 = dates.count { ChronoUnit.DAYS.between(it, today) in 0..27 }
        val month = dates.count { it.year == today.year && it.month == today.month }
        return Summary(done.size, prs, vol, cur, best, l7, last28 / 4f, month, dates.toSet(), secs)
    }

    // ---- per-exercise charts ----
    enum class Metric { MAX_LOAD, VOLUME, E1RM, AVG_REPS, MAX_REPS, TOTAL_REPS, BEST_TIME, TOTAL_TIME }

    data class Point(val date: LocalDate, val value: Double)

    fun series(done: List<SessionFull>, exerciseId: Long, metric: Metric): List<Point> =
        done.sortedBy { it.session.date }.mapNotNull { s ->
            val sets = s.exercises.filter { it.ex.exerciseId == exerciseId }.flatMap { it.sets }.filter { it.done }
            if (sets.isEmpty()) return@mapNotNull null
            val v = when (metric) {
                Metric.MAX_LOAD -> sets.maxOf { it.weight ?: 0.0 }
                Metric.VOLUME -> sets.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) }
                Metric.E1RM -> sets.maxOf { epley(it.weight ?: 0.0, it.reps ?: 0) }
                Metric.AVG_REPS -> sets.map { (it.reps ?: 0).toDouble() }.average()
                Metric.MAX_REPS -> sets.maxOf { (it.reps ?: 0).toDouble() }
                Metric.TOTAL_REPS -> sets.sumOf { (it.reps ?: 0).toDouble() }
                Metric.BEST_TIME -> sets.maxOf { (it.seconds ?: 0).toDouble() }
                Metric.TOTAL_TIME -> sets.sumOf { (it.seconds ?: 0).toDouble() }
            }
            Point(LocalDate.parse(s.session.date), v)
        }

    /** Average weeks-per-month style numbers for the stats card. */
    fun workoutsPerWeek(done: List<SessionFull>, weeks: Int, today: LocalDate = LocalDate.now()): List<Pair<LocalDate, Int>> {
        val ws = weekStart(today)
        val counts = done.groupingBy { weekStart(LocalDate.parse(it.session.date)) }.eachCount()
        return (weeks - 1 downTo 0).map { ws.minusWeeks(it.toLong()) }.map { it to (counts[it] ?: 0) }
    }
}

// ---- achievements ----
data class Achievement(val key: String, val unlocked: Boolean, val progress: Float)

object Achievements {
    val keys = listOf("first", "w10", "w50", "w100", "streak3", "streak8", "pr1", "pr10", "vol10k", "vol100k")
    fun evaluate(s: Stats.Summary): List<Achievement> {
        fun a(k: String, v: Number, t: Number) =
            Achievement(k, v.toDouble() >= t.toDouble(), (v.toFloat() / t.toFloat()).coerceIn(0f, 1f))
        return listOf(
            a("first", s.workouts, 1), a("w10", s.workouts, 10), a("w50", s.workouts, 50), a("w100", s.workouts, 100),
            a("streak3", s.bestStreak, 3), a("streak8", s.bestStreak, 8),
            a("pr1", s.prs, 1), a("pr10", s.prs, 10),
            a("vol10k", s.volumeKg, 10_000), a("vol100k", s.volumeKg, 100_000),
        )
    }
}
