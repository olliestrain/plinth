package app.cadence.gym.ui.progress

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cadence.gym.LocalApp
import app.cadence.gym.R
import app.cadence.gym.data.*
import app.cadence.gym.ui.*
import app.cadence.gym.ui.components.*
import app.cadence.gym.ui.theme.Mono
import app.cadence.gym.ui.theme.TC
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private enum class Tab(val label: Int) { STATS(R.string.pg_stats), CHART(R.string.pg_chart), HISTORY(R.string.pg_history) }
private enum class Period(val label: Int, val days: Long?) {
    M1(R.string.period_1m, 30), M3(R.string.period_3m, 91), M6(R.string.period_6m, 182), Y1(R.string.period_1y, 365), ALL(R.string.period_all, null)
}

@Composable
fun ProgressScreen(prefs: Prefs, actions: Actions) {
    val app = LocalApp.current
    val plans by app.repo.dayPlans.collectAsState()
    val done by app.repo.doneSessions.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.STATS) }
    var period by rememberSaveable { mutableStateOf(Period.M3) }
    var exId by rememberSaveable { mutableLongStateOf(-1L) }
    var metricName by rememberSaveable { mutableStateOf("") }
    val lb = prefs.useLb
    val today = LocalDate.now()

    val from = period.days?.let { today.minusDays(it) }
    val inPeriod = remember(done, period) { done.filter { from == null || !LocalDate.parse(it.session.date).isBefore(from) } }

    // exercises that have history, most used first
    val exOptions = remember(done) {
        done.flatMap { s -> s.exercises.map { it.ex } }.groupBy { it.exerciseId }
            .map { (id, l) -> Triple(id, l.maxBy { it.id }.name, l.size) }.sortedByDescending { it.third }
    }
    val effectiveEx = if (exOptions.any { it.first == exId }) exId else exOptions.firstOrNull()?.first ?: -1L

    ScreenColumn {
        Text(stringResource(R.string.tab_progress), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TC.Card).padding(4.dp)) {
            Tab.entries.forEach { t ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (tab == t) TC.CardHi else Color.Transparent)
                    .pressable({ tab = t }).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(t.label), fontWeight = FontWeight.Bold, color = if (tab == t) TC.Accent else TC.Dim)
                }
            }
        }
        if (done.isEmpty()) {
            TCard(Modifier.fillMaxWidth(), radius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CadenceMark(40.dp)
                    Text(stringResource(R.string.pg_empty), fontWeight = FontWeight.SemiBold, fontSize = 20.sp, modifier = Modifier.padding(top = 16.dp))
                    Text(stringResource(R.string.pg_empty_sub), color = TC.Dim, modifier = Modifier.padding(top = 6.dp))
                }
            }
            return@ScreenColumn
        }
        if (tab != Tab.STATS) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Period.entries.forEach { p -> TChip(stringResource(p.label), period == p, { period = p }, color = TC.Sky) }
        }
        when (tab) {
            Tab.STATS -> StatsTab(done, plans.values.toList(), prefs)
            Tab.CHART -> ChartTab(inPeriod, exOptions, effectiveEx, metricName, { exId = it }, { metricName = it }, lb)
            Tab.HISTORY -> HistoryTab(inPeriod, exOptions, exId.takeIf { x -> exOptions.any { it.first == x } }, { exId = it }, lb, actions)
        }
    }
}

@Composable
private fun StatsTab(done: List<SessionFull>, plans: List<DayPlan>, prefs: Prefs) {
    val s = remember(done, plans, prefs.weeklyGoal) { Stats.summarize(done, plans, prefs.weeklyGoal) }
    val weeks = remember(done) { Stats.workoutsPerWeek(done, 10) }
    val lb = prefs.useLb
    TCard(Modifier.fillMaxWidth(), radius = 16.dp) {
        Column {
            Text(stringResource(R.string.pg_per_week).uppercase(), style = MaterialTheme.typography.labelMedium, color = TC.Dim)
            Spacer(Modifier.height(14.dp))
            BarChart(weeks.map { fmtDateShort(it.first) to it.second.toFloat() }, Modifier.fillMaxWidth().height(120.dp))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtDateShort(weeks.first().first), color = TC.Faint, fontSize = 11.sp)
                Text(stringResource(R.string.pg_this_week), color = TC.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(Modifier.weight(1f), stringResource(R.string.pg_avg_week), "%.1f".format(s.perWeek30), null)
        StatCard(Modifier.weight(1f), stringResource(R.string.pg_this_month), "${s.perMonth}", null)
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(Modifier.weight(1f), stringResource(R.string.pg_consistency), "${s.streak}", pluralStringResource(R.plurals.weeks_short, s.streak), accent = TC.Ember)
        StatCard(Modifier.weight(1f), stringResource(R.string.pg_best_streak), "${s.bestStreak}", pluralStringResource(R.plurals.weeks_short, s.bestStreak))
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(Modifier.weight(1f), stringResource(R.string.pg_total_volume), fmtBig(s.volumeKg, lb), unitLabel(lb))
        StatCard(Modifier.weight(1f), stringResource(R.string.pg_prs), "${s.prs}", null, accent = TC.Accent)
    }
    Text(stringResource(R.string.pg_consistency_hint, prefs.weeklyGoal), color = TC.Faint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
}

@Composable
private fun StatCard(modifier: Modifier, label: String, value: String, unit: String?, accent: Color = TC.Text) {
    TCard(modifier.fillMaxHeight()) {
        Column {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TC.Dim)
            Spacer(Modifier.height(8.dp))
            BigNumber(value, unit, size = 34, color = accent)
        }
    }
}

@Composable
private fun ChartTab(
    sessions: List<SessionFull>, exOptions: List<Triple<Long, String, Int>>, exId: Long, metricName: String,
    onEx: (Long) -> Unit, onMetric: (String) -> Unit, lb: Boolean,
) {
    val measure = sessions.flatMap { it.exercises }.lastOrNull { it.ex.exerciseId == exId }?.ex?.measure
        ?: Measure.WEIGHT_REPS
    val metrics = when (measure) {
        Measure.WEIGHT_REPS -> listOf(Stats.Metric.MAX_LOAD to R.string.metric_max_load, Stats.Metric.VOLUME to R.string.metric_volume, Stats.Metric.E1RM to R.string.metric_e1rm, Stats.Metric.AVG_REPS to R.string.metric_avg_reps)
        Measure.REPS -> listOf(Stats.Metric.MAX_REPS to R.string.metric_max_reps, Stats.Metric.TOTAL_REPS to R.string.metric_total_reps, Stats.Metric.AVG_REPS to R.string.metric_avg_reps)
        Measure.TIME -> listOf(Stats.Metric.BEST_TIME to R.string.metric_best_time, Stats.Metric.TOTAL_TIME to R.string.metric_total_time)
    }
    val metric = metrics.firstOrNull { it.first.name == metricName }?.first ?: metrics.first().first
    val weighty = metric == Stats.Metric.MAX_LOAD || metric == Stats.Metric.VOLUME || metric == Stats.Metric.E1RM
    val pts = remember(sessions, exId, metric, lb) {
        Stats.series(sessions, exId, metric).map { Stats.Point(it.date, if (weighty) toDisplay(it.value, lb) else it.value) }
    }
    val unit = when { weighty -> unitLabel(lb); metric == Stats.Metric.BEST_TIME || metric == Stats.Metric.TOTAL_TIME -> "s"; else -> "" }

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        exOptions.forEach { (id, name, _) -> TChip(name, exId == id, { onEx(id) }) }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.forEach { (m, label) -> TChip(stringResource(label), metric == m, { onMetric(m.name) }, color = TC.Violet) }
    }
    TCard(Modifier.fillMaxWidth(), radius = 16.dp) {
        Column {
            if (pts.isEmpty()) {
                Text(stringResource(R.string.pg_no_data_period), color = TC.Dim, modifier = Modifier.padding(vertical = 40.dp))
            } else {
                val best = pts.maxOf { it.value }; val latest = pts.last().value; val first = pts.first().value
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text(stringResource(R.string.pg_latest).uppercase(), style = MaterialTheme.typography.labelSmall, color = TC.Dim)
                        BigNumber(num(latest), unit, size = 38, color = TC.Accent)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.pg_best).uppercase(), style = MaterialTheme.typography.labelSmall, color = TC.Dim)
                        Text(num(best) + " " + unit, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        if (pts.size > 1) {
                            val d = latest - first
                            Text((if (d >= 0) "+" else "") + num(d) + " " + unit, color = if (d >= 0) TC.Good else TC.Danger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                LineChart(pts.map { ChartPoint(it.date.toString(), it.value.toFloat()) }, Modifier.fillMaxWidth().height(190.dp))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmtDateShort(pts.first().date), color = TC.Faint, fontSize = 11.sp)
                    Text(fmtDateShort(pts.last().date), color = TC.Faint, fontSize = 11.sp)
                }
            }
        }
    }
    if (pts.isNotEmpty()) {
        Text(stringResource(R.string.pg_sessions_n, pts.size), color = TC.Dim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 6.dp))
    }
}

@Composable
private fun HistoryTab(
    sessions: List<SessionFull>, exOptions: List<Triple<Long, String, Int>>, exId: Long?, onEx: (Long) -> Unit, lb: Boolean, actions: Actions,
) {
    var selected by remember(exId) { mutableStateOf(exId) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TChip(stringResource(R.string.pg_all), selected == null, { selected = null; onEx(-1L) })
        exOptions.forEach { (id, name, _) -> TChip(name, selected == id, { selected = id; onEx(id) }) }
    }
    val list = sessions.filter { s -> selected == null || s.exercises.any { it.ex.exerciseId == selected } }
    if (list.isEmpty()) Text(stringResource(R.string.pg_no_data_period), color = TC.Dim, modifier = Modifier.padding(top = 20.dp))
    list.forEach { s ->
        TCard(Modifier.fillMaxWidth(), onClick = { actions.openSession(s.session.id) }, radius = 12.dp) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.session.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text(fmtDate(LocalDate.parse(s.session.date)), color = TC.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Text(
                    stringResource(R.string.pg_session_summary, s.exercises.size, s.doneSets, fmtBig(Stats.sessionVolume(s), lb), unitLabel(lb)) +
                        if (s.session.durationSec > 0) "  ·  " + fmtDuration(s.session.durationSec) else "",
                    color = TC.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                )
                s.exercises.filter { selected == null || it.ex.exerciseId == selected }.forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(e.ex.name, Modifier.weight(0.45f), color = TC.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            e.sets.filter { it.done }.joinToString("  ") {
                                when (e.ex.measure) {
                                    Measure.WEIGHT_REPS -> "${fmtWeight(it.weight ?: 0.0, lb)}×${it.reps ?: 0}"
                                    Measure.REPS -> "${it.reps ?: 0}"
                                    Measure.TIME -> "${it.seconds ?: 0}s"
                                }
                            },
                            Modifier.weight(0.55f), color = TC.Dim, fontSize = 12.sp, fontFamily = Mono,
                        )
                    }
                    if (e.ex.nextTimeNote.isNotBlank()) Text("⚡ " + e.ex.nextTimeNote, color = TC.Accent, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}
