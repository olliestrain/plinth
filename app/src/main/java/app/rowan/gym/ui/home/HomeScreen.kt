package app.rowan.gym.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rowan.gym.LocalApp
import app.rowan.gym.R
import app.rowan.gym.data.DayType
import app.rowan.gym.data.Prefs
import app.rowan.gym.data.SessionStatus
import app.rowan.gym.data.Stats
import app.rowan.gym.ui.Actions
import app.rowan.gym.ui.components.BigNumber
import app.rowan.gym.ui.components.DotMatrix
import app.rowan.gym.ui.components.GhostButton
import app.rowan.gym.ui.components.IconPill
import app.rowan.gym.ui.components.MiniField
import app.rowan.gym.ui.components.PrimaryButton
import app.rowan.gym.ui.components.ProgressRing
import app.rowan.gym.ui.components.ScreenColumn
import app.rowan.gym.ui.components.TCard
import app.rowan.gym.ui.components.TDialog
import app.rowan.gym.ui.components.pressable
import app.rowan.gym.ui.components.springFloat
import app.rowan.gym.ui.fmtBig
import app.rowan.gym.ui.fmtDateLong
import app.rowan.gym.ui.fmtMonthShort
import app.rowan.gym.ui.fmtWeight
import app.rowan.gym.ui.fromDisplay
import app.rowan.gym.ui.settings.rememberBackupActions
import app.rowan.gym.ui.theme.Mono
import app.rowan.gym.ui.theme.TC
import app.rowan.gym.ui.unitLabel
import app.rowan.gym.ui.weekdayNarrow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(prefs: Prefs, actions: Actions, goTab: (Int) -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessions by app.repo.sessions.collectAsState()
    val plans by app.repo.dayPlans.collectAsState()
    val templates by app.repo.templates.collectAsState()
    val weights by app.repo.bodyWeights.collectAsState()
    val update by app.hubManager.update.collectAsState()
    val backup = rememberBackupActions()
    var weightDialog by remember { mutableStateOf(false) }
    var hideUpdate by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val done = sessions.filter { it.session.status == SessionStatus.DONE }
    val draft = sessions.firstOrNull { it.session.status == SessionStatus.DRAFT }
    val summary = remember(sessions, plans, prefs.weeklyGoal) { Stats.summarize(sessions, plans.values.toList(), prefs.weeklyGoal) }
    val todayPlan = plans[today.toString()]
    val todayDone = done.firstOrNull { it.session.date == today.toString() }
    val todayTemplate = todayPlan?.templateId?.let { id -> templates.firstOrNull { it.template.id == id } }

    ScreenColumn {
        // ---- header ----
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fmtDateLong(today).uppercase(), style = MaterialTheme.typography.labelMedium, color = TC.Dim)
                Text(
                    if (prefs.name.isBlank()) stringResource(R.string.home_hello_anon) else stringResource(R.string.home_hello, prefs.name),
                    style = MaterialTheme.typography.headlineLarge, color = TC.Text,
                )
            }
            IconPill(Icons.Rounded.StickyNote2, { actions.notes() })
            Spacer(Modifier.size(8.dp))
            IconPill(Icons.Rounded.Settings, { actions.settings() })
        }

        // ---- banners ----
        val upd = update
        if (upd != null && !hideUpdate && upd.version != prefs.skippedUpdate) {
            TCard(Modifier.fillMaxWidth(), color = TC.Lime.copy(alpha = 0.12f), border = TC.Lime.copy(alpha = 0.5f), radius = 24.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SystemUpdateAlt, null, tint = TC.Lime)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(stringResource(R.string.update_available), fontWeight = FontWeight.ExtraBold, color = TC.Text)
                        Text(stringResource(R.string.update_version, upd.version), color = TC.Dim, fontSize = 13.sp)
                    }
                    GhostButton(stringResource(R.string.update_get), {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(upd.apkUrl ?: upd.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, color = TC.Lime)
                    Icon(Icons.Rounded.Close, null, tint = TC.Dim, modifier = Modifier.padding(start = 8.dp).size(20.dp).pressable({
                        hideUpdate = true; scope.launch { app.settings.update { skippedUpdate = upd.version } }
                    }))
                }
            }
        }
        val backupAge = if (prefs.lastExport == 0L) Long.MAX_VALUE else TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - prefs.lastExport)
        if (done.isNotEmpty() && prefs.reminderDays > 0 && backupAge >= prefs.reminderDays) {
            TCard(Modifier.fillMaxWidth(), color = TC.Ember.copy(alpha = 0.12f), border = TC.Ember.copy(alpha = 0.5f), radius = 24.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudDone, null, tint = TC.Ember)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(stringResource(R.string.backup_due_title), fontWeight = FontWeight.ExtraBold, color = TC.Text)
                        Text(if (prefs.lastExport == 0L) stringResource(R.string.backup_never) else stringResource(R.string.backup_days_ago, backupAge.toInt()), color = TC.Dim, fontSize = 13.sp)
                    }
                    GhostButton(stringResource(R.string.backup_now), backup.export, color = TC.Ember)
                }
            }
        }

        // ---- resume draft / today ----
        if (draft != null) {
            TCard(Modifier.fillMaxWidth(), onClick = { actions.openSession(draft.session.id) }, color = TC.Lime) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_in_progress).uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.Black.copy(alpha = 0.6f))
                        Text(draft.session.name, fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color.Black)
                    }
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(36.dp))
                }
            }
        } else {
            TodayCard(
                done = todayDone != null, restDay = todayPlan?.type == DayType.REST,
                title = todayTemplate?.template?.name, exercises = todayTemplate?.exercises?.size ?: 0,
                color = todayTemplate?.let { TC.palette(it.template.colorIdx) } ?: TC.Lime,
                onStart = { actions.startWorkout(today, todayTemplate?.template?.id) },
                onView = { todayDone?.let { actions.openSession(it.session.id) } },
            )
        }

        // ---- week + streak ----
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val ws = Stats.weekStart(today)
            val weekDone = (0..6).map { ws.plusDays(it.toLong()) }
            val doneThisWeek = weekDone.count { it in summary.doneDates }
            TCard(Modifier.weight(1.25f).fillMaxWidth(), onClick = { goTab(1) }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(springFloat(doneThisWeek / prefs.weeklyGoal.coerceAtLeast(1).toFloat()), 58.dp, 7.dp) {
                            Text("$doneThisWeek", fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 22.sp, color = TC.Text)
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(stringResource(R.string.home_this_week), fontWeight = FontWeight.ExtraBold, color = TC.Text)
                            Text(stringResource(R.string.home_of_goal, prefs.weeklyGoal), color = TC.Dim, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        weekDone.forEach { d ->
                            val on = d in summary.doneDates
                            val isToday = d == today
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(weekdayNarrow(d.dayOfWeek.value), fontSize = 10.sp, color = if (isToday) TC.Lime else TC.Faint, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                androidx.compose.foundation.layout.Box(
                                    Modifier.size(15.dp).clip(CircleShape).background(if (on) TC.Lime else Color.Transparent)
                                        .border(1.5.dp, if (on) TC.Lime else if (isToday) TC.Lime.copy(alpha = 0.6f) else TC.Outline, CircleShape),
                                )
                            }
                        }
                    }
                }
            }
            TCard(Modifier.weight(1f).fillMaxWidth(), onClick = { goTab(4) }) {
                Column {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = if (summary.streak > 0) TC.Ember else TC.Faint, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(8.dp))
                    BigNumber("${summary.streak}", size = 38)
                    Text(pluralStringResource(R.plurals.weeks_streak, summary.streak), color = TC.Dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ---- level ----
        val titles = stringArrayResource(R.array.level_titles)
        TCard(Modifier.fillMaxWidth(), onClick = { goTab(4) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(springFloat(Stats.levelProgress(summary.xp)), 72.dp, 8.dp) {
                    Text("${summary.level}", fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 26.sp, color = TC.Lime)
                }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(titles[(summary.level - 1).coerceAtMost(titles.lastIndex)], fontWeight = FontWeight.Black, fontSize = 20.sp, color = TC.Text)
                    Text(stringResource(R.string.xp_to_next, Stats.levelFloorXp(summary.level + 1) - summary.xp, summary.level + 1), color = TC.Dim, fontSize = 13.sp)
                }
                Text("${summary.xp}", fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 20.sp, color = TC.Lime)
                Text(" XP", color = TC.Dim, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // ---- body weight + volume ----
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TCard(Modifier.weight(1f).fillMaxWidth(), onClick = { weightDialog = true }) {
                Column {
                    Text(stringResource(R.string.home_body_weight).uppercase(), style = MaterialTheme.typography.labelSmall, color = TC.Dim)
                    Spacer(Modifier.height(8.dp))
                    val w = weights.firstOrNull()
                    if (w != null) BigNumber(fmtWeight(w.kg, prefs.useLb), unitLabel(prefs.useLb), size = 34)
                    else Text(stringResource(R.string.home_log_weight), color = TC.Lime, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (weights.size >= 2) {
                        val diff = weights[0].kg - weights[1].kg
                        Text((if (diff > 0) "+" else "") + fmtWeight(diff, prefs.useLb) + " " + unitLabel(prefs.useLb), color = if (diff > 0) TC.Ember else TC.Good, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            TCard(Modifier.weight(1f).fillMaxWidth()) {
                Column {
                    Text(stringResource(R.string.home_volume_7d).uppercase(), style = MaterialTheme.typography.labelSmall, color = TC.Dim)
                    Spacer(Modifier.height(8.dp))
                    BigNumber(fmtBig(summary.last7Volume, prefs.useLb), unitLabel(prefs.useLb), size = 34)
                }
            }
        }

        // ---- rowan heatmap ----
        TCard(Modifier.fillMaxWidth(), radius = 32.dp) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_consistency).uppercase(), style = MaterialTheme.typography.labelMedium, color = TC.Dim, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.home_workouts_n, summary.workouts), color = TC.Lime, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(14.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val dot = 7.dp; val gap = 11.dp
                    val cols = ((maxWidth + gap) / (dot + gap)).toInt().coerceIn(4, 30)
                    val ws0 = Stats.weekStart(today).minusWeeks((cols - 1).toLong())
                    val columns = (0 until cols).map { c ->
                        (0..6).map { r ->
                            val d = ws0.plusWeeks(c.toLong()).plusDays(r.toLong())
                            when { d.isAfter(today) -> -1f; d in summary.doneDates -> 1f; else -> 0f }
                        }
                    }
                    Column {
                        Box_(cols, ws0, dot, gap)
                        Spacer(Modifier.height(8.dp))
                        DotMatrix(columns, dot = dot, gap = gap, lit = TC.Lime, dim = Color.White.copy(alpha = 0.14f))
                    }
                }
            }
        }
    }

    if (weightDialog) {
        var text by remember { mutableStateOf(weights.firstOrNull()?.let { fmtWeight(it.kg, prefs.useLb) } ?: "") }
        TDialog(stringResource(R.string.home_body_weight), { weightDialog = false },
            confirm = stringResource(R.string.save), confirmEnabled = text.replace(',', '.').toDoubleOrNull() != null,
            onConfirm = {
                text.replace(',', '.').toDoubleOrNull()?.let { v -> scope.launch { app.repo.addBodyWeight(today, fromDisplay(v, prefs.useLb)) } }
                weightDialog = false
            }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniField(text, { text = it }, decimal = true, width = 120.dp, hint = "0.0", accent = true)
                Text(" " + unitLabel(prefs.useLb), color = TC.Dim, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Month labels positioned above their first matching column. */
@Composable
private fun Box_(cols: Int, start: LocalDate, dot: androidx.compose.ui.unit.Dp, gap: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(14.dp)) {
        var lastMonth = -1
        for (c in 0 until cols) {
            val d = start.plusWeeks(c.toLong())
            if (d.monthValue != lastMonth && (c == 0 || d.dayOfMonth <= 7)) {
                lastMonth = d.monthValue
                Text(fmtMonthShort(d), color = TC.Dim, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(x = (dot + gap) * c))
            }
        }
    }
}

@Composable
private fun TodayCard(done: Boolean, restDay: Boolean, title: String?, exercises: Int, color: Color, onStart: () -> Unit, onView: () -> Unit) {
    TCard(Modifier.fillMaxWidth(), radius = 32.dp, color = TC.CardHi) {
        Column {
            Text(stringResource(R.string.home_today).uppercase(), style = MaterialTheme.typography.labelMedium, color = TC.Dim)
            Spacer(Modifier.height(6.dp))
            when {
                done -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Check, null, tint = TC.Lime, modifier = Modifier.size(30.dp))
                        Text(stringResource(R.string.home_done_today), style = MaterialTheme.typography.headlineMedium, color = TC.Lime, modifier = Modifier.padding(start = 8.dp))
                    }
                    Spacer(Modifier.height(14.dp)); GhostButton(stringResource(R.string.home_view_session), onView)
                }
                restDay -> {
                    Text(stringResource(R.string.home_rest_day), style = MaterialTheme.typography.headlineMedium, color = TC.Text)
                    Text(stringResource(R.string.home_rest_sub), color = TC.Dim, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(14.dp)); GhostButton(stringResource(R.string.home_train_anyway), onStart)
                }
                title != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.headlineMedium, color = color)
                            Text(pluralStringResource(R.plurals.exercises_count, exercises, exercises), color = TC.Dim, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp)); PrimaryButton(stringResource(R.string.start_workout), onStart, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
                }
                else -> {
                    Text(stringResource(R.string.home_free_day), style = MaterialTheme.typography.headlineMedium, color = TC.Text)
                    Text(stringResource(R.string.home_free_sub), color = TC.Dim, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(16.dp)); PrimaryButton(stringResource(R.string.start_workout), onStart, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
                }
            }
        }
    }
}
