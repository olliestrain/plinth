package app.plinth.gym.ui.home

import androidx.compose.foundation.layout.fillMaxHeight
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
import app.plinth.gym.LocalApp
import app.plinth.gym.R
import app.plinth.gym.data.DayType
import app.plinth.gym.data.Prefs
import app.plinth.gym.data.SessionStatus
import app.plinth.gym.data.Stats
import app.plinth.gym.ui.Actions
import app.plinth.gym.ui.components.BigNumber
import app.plinth.gym.ui.components.DotMatrix
import app.plinth.gym.ui.components.GhostButton
import app.plinth.gym.ui.components.IconPill
import app.plinth.gym.ui.components.Field
import app.plinth.gym.ui.components.PlinthMark
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import app.plinth.gym.ui.components.MiniField
import app.plinth.gym.ui.components.PrimaryButton
import app.plinth.gym.ui.components.ProgressRing
import app.plinth.gym.ui.components.ScreenColumn
import app.plinth.gym.ui.components.TCard
import app.plinth.gym.ui.components.TDialog
import app.plinth.gym.ui.components.pressable
import app.plinth.gym.ui.components.springFloat
import app.plinth.gym.ui.fmtBig
import app.plinth.gym.ui.fmtDateLong
import app.plinth.gym.ui.fmtMonthShort
import app.plinth.gym.ui.fmtWeight
import app.plinth.gym.ui.fromDisplay
import app.plinth.gym.ui.settings.rememberBackupActions
import app.plinth.gym.ui.theme.Mono
import app.plinth.gym.ui.theme.TC
import app.plinth.gym.ui.unitLabel
import app.plinth.gym.ui.weekdayNarrow
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
    val update by app.updates.info.collectAsState()
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

    val heroTemplateId = draft?.session?.templateId ?: todayTemplate?.template?.id
    val heroField = if (todayPlan?.type == DayType.REST && draft == null) Field.SLATE
        else heroTemplateId?.let { id -> templates.firstOrNull { it.template.id == id } }?.let { Field.forColor(it.template.colorIdx) } ?: Field.EMBER

    ScreenColumn(field = heroField) {
        // ---- header ----
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            PlinthMark(20.dp)
            Text(stringResource(R.string.app_name), Modifier.padding(start = 10.dp).weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TC.Text)
            IconPill(Icons.Rounded.StickyNote2, { actions.notes() })
        }

        // ---- banners ----
        val upd = update
        if (upd != null && !hideUpdate && upd.version != prefs.skippedUpdate) {
            TCard(Modifier.fillMaxWidth(), color = TC.Accent.copy(alpha = 0.12f), border = TC.Accent.copy(alpha = 0.5f), radius = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SystemUpdateAlt, null, tint = TC.Accent)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(stringResource(R.string.update_available), fontWeight = FontWeight.SemiBold, color = TC.Text)
                        Text(stringResource(R.string.update_version, upd.version), color = TC.Dim, fontSize = 13.sp)
                    }
                    GhostButton(stringResource(R.string.update_get), {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(upd.apkUrl ?: upd.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, color = TC.Accent)
                    Icon(Icons.Rounded.Close, null, tint = TC.Dim, modifier = Modifier.padding(start = 8.dp).size(20.dp).pressable({
                        hideUpdate = true; scope.launch { app.settings.update { skippedUpdate = upd.version } }
                    }))
                }
            }
        }
        val backupAge = if (prefs.lastExport == 0L) Long.MAX_VALUE else TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - prefs.lastExport)
        if (done.isNotEmpty() && prefs.reminderDays > 0 && backupAge >= prefs.reminderDays) {
            TCard(Modifier.fillMaxWidth(), color = TC.Ember.copy(alpha = 0.12f), border = TC.Ember.copy(alpha = 0.5f), radius = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudDone, null, tint = TC.Ember)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(stringResource(R.string.backup_due_title), fontWeight = FontWeight.SemiBold, color = TC.Text)
                        Text(if (prefs.lastExport == 0L) stringResource(R.string.backup_never) else stringResource(R.string.backup_days_ago, backupAge.toInt()), color = TC.Dim, fontSize = 13.sp)
                    }
                    GhostButton(stringResource(R.string.backup_now), backup.export, color = TC.Ember)
                }
            }
        }

        // ---- hero ----
        val heroName = when {
            draft != null -> draft.session.name
            todayDone != null -> todayDone.session.name
            todayPlan?.type == DayType.REST -> stringResource(R.string.home_rest_day)
            todayTemplate != null -> todayTemplate.template.name
            else -> stringResource(R.string.home_free_day)
        }
        val heroLabel = when {
            draft != null -> stringResource(R.string.home_in_progress)
            todayDone != null -> stringResource(R.string.home_done_today)
            else -> stringResource(R.string.home_today)
        }
        val heroMeta = when {
            draft != null -> stringResource(R.string.home_sets_logged, draft.doneSets)
            todayDone != null -> stringResource(R.string.pg_session_summary, todayDone.exercises.size, todayDone.doneSets, fmtBig(Stats.sessionVolume(todayDone), prefs.useLb), unitLabel(prefs.useLb))
            todayPlan?.type == DayType.REST -> stringResource(R.string.home_rest_sub)
            todayTemplate != null -> pluralStringResource(R.plurals.exercises_count, todayTemplate.exercises.size, todayTemplate.exercises.size) +
                (if (todayTemplate.exercises.isNotEmpty()) " · " + todayTemplate.exercises.take(3).joinToString(", ") { it.exName } else "")
            else -> stringResource(R.string.home_free_sub)
        }
        Column(Modifier.padding(top = 26.dp)) {
            Text((fmtDateLong(today) + " · " + heroLabel).uppercase(), style = MaterialTheme.typography.labelMedium, color = TC.Dim)
            Text(heroName, style = MaterialTheme.typography.displayMedium, color = TC.Text, modifier = Modifier.padding(top = 10.dp))
            Text(heroMeta, style = MaterialTheme.typography.bodyMedium, color = TC.Dim, modifier = Modifier.padding(top = 10.dp), maxLines = 2)
        }
        when {
            draft != null -> PrimaryButton(stringResource(R.string.home_resume), { actions.openSession(draft.session.id) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
            todayDone != null -> GhostButton(stringResource(R.string.home_view_session), { actions.openSession(todayDone.session.id) }, Modifier.fillMaxWidth())
            todayPlan?.type == DayType.REST -> GhostButton(stringResource(R.string.home_train_anyway), { actions.startWorkout(today, null) }, Modifier.fillMaxWidth())
            else -> PrimaryButton(stringResource(R.string.start_workout), { actions.startWorkout(today, todayTemplate?.template?.id) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
        }

        // ---- week ----
        val ws = Stats.weekStart(today)
        val weekDays = (0..6).map { ws.plusDays(it.toLong()) }
        val dayVol = weekDays.map { d -> done.filter { it.session.date == d.toString() }.sumOf { Stats.sessionVolume(it) } }
        val maxVol = (dayVol.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
        val doneThisWeek = weekDays.count { it in summary.doneDates }
        TCard(Modifier.fillMaxWidth(), onClick = { goTab(1) }, radius = 20.dp) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_this_week), fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.home_week_progress, doneThisWeek, prefs.weeklyGoal), fontFamily = Mono, fontSize = 12.sp, color = TC.Dim)
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp).height(76.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    weekDays.forEachIndexed { i, d ->
                        val isToday = d == today
                        val upcoming = plans[d.toString()]?.type == DayType.WORKOUT && dayVol[i] == 0.0 && !d.isBefore(today)
                        val h = if (dayVol[i] > 0) (16 + 40 * (dayVol[i] / maxVol)).dp else if (upcoming) 40.dp else 6.dp
                        Column(Modifier.width(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val base = Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(if (dayVol[i] > 0 || upcoming) 6.dp else 3.dp))
                            Box(
                                if (upcoming) base.border(1.dp, if (isToday) TC.Accent else Color.White.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                else base.background(if (dayVol[i] > 0) TC.Accent.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.16f)),
                            )
                            Text(weekdayNarrow(d.dayOfWeek.value), fontSize = 11.sp, fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal, color = if (isToday) TC.Text else TC.Faint, modifier = Modifier.padding(top = 7.dp))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TCard(Modifier.weight(1f).fillMaxHeight(), onClick = { goTab(3) }) {
                Column {
                    Text(stringResource(R.string.pg_consistency), fontSize = 12.sp, color = TC.Dim)
                    BigNumber("${summary.streak}", stringResource(R.string.unit_weeks), size = 28, modifier = Modifier.padding(top = 6.dp))
                }
            }
            TCard(Modifier.weight(1f).fillMaxHeight()) {
                Column {
                    Text(stringResource(R.string.home_volume_7d), fontSize = 12.sp, color = TC.Dim)
                    BigNumber(fmtBig(summary.last7Volume, prefs.useLb), unitLabel(prefs.useLb), size = 28, modifier = Modifier.padding(top = 6.dp))
                }
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
                    else Text(stringResource(R.string.home_log_weight), color = TC.Accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (weights.size >= 2) {
                        val diff = weights[0].kg - weights[1].kg
                        Text((if (diff > 0) "+" else "") + fmtWeight(diff, prefs.useLb) + " " + unitLabel(prefs.useLb), color = if (diff > 0) TC.Ember else TC.Good, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            TCard(Modifier.weight(1f).fillMaxWidth()) {
                Column {
                    Text(stringResource(R.string.pg_prs).uppercase(), style = MaterialTheme.typography.labelSmall, color = TC.Dim)
                    Spacer(Modifier.height(8.dp))
                    BigNumber("${summary.prs}", size = 34)
                }
            }
        }

        // ---- plinth heatmap ----
        TCard(Modifier.fillMaxWidth(), radius = 16.dp) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_consistency).uppercase(), style = MaterialTheme.typography.labelMedium, color = TC.Dim, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.home_workouts_n, summary.workouts), color = TC.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                        DotMatrix(columns, dot = dot, gap = gap, lit = TC.Accent, dim = Color.White.copy(alpha = 0.14f))
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
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(20.dp)) {
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
