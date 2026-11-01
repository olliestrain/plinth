package app.plinth.gym.ui.workout

import app.plinth.gym.ui.components.Field
import app.plinth.gym.ui.components.backdrop
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plinth.gym.LocalApp
import app.plinth.gym.R
import app.plinth.gym.data.*
import app.plinth.gym.ui.*
import app.plinth.gym.ui.components.*
import app.plinth.gym.ui.plans.ExercisePickerSheet
import app.plinth.gym.ui.plans.measureLabel
import app.plinth.gym.ui.theme.Mono
import app.plinth.gym.ui.theme.TC
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.LocalDate

private class DSet(w: String, r: String, s: String, d: Boolean) {
    var weight by mutableStateOf(w); var reps by mutableStateOf(r); var seconds by mutableStateOf(s); var done by mutableStateOf(d)
    val filled get() = weight.isNotBlank() || reps.isNotBlank() || seconds.isNotBlank()
}

private class DEx(
    val key: Long, exerciseId: Long, name: String, measure: Measure, note: String, nextNote: String, subFrom: String?, sets: List<DSet>,
) {
    var exerciseId by mutableLongStateOf(exerciseId); var name by mutableStateOf(name); var measure by mutableStateOf(measure)
    var note by mutableStateOf(note); var nextNote by mutableStateOf(nextNote); var subFrom by mutableStateOf(subFrom)
    val sets = mutableStateListOf<DSet>().apply { addAll(sets) }
    var last by mutableStateOf<Repository.LastTime?>(null)
}

private var keyGen = 1L

@OptIn(FlowPreview::class)
@Composable
fun WorkoutScreen(id: Long, prefs: Prefs, actions: Actions) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val templates by app.repo.templates.collectAsState()
    val lb = prefs.useLb

    var base by remember { mutableStateOf<Session?>(null) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var title by remember { mutableStateOf("") }
    var templateId by remember { mutableStateOf<Long?>(null) }
    var sessionNote by remember { mutableStateOf("") }
    val exs = remember { mutableStateListOf<DEx>() }
    var loaded by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var pickerFor by remember { mutableStateOf<DEx?>(null) }      // swap target
    var addPicker by remember { mutableStateOf(false) }
    var cur by remember { mutableIntStateOf(0) }
    var sessionMenu by remember { mutableStateOf(false) }
    var overview by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<DEx?>(null) }
    var datePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmTemplate by remember { mutableStateOf<Long?>(null) }
    var uncheckedPrompt by remember { mutableStateOf(0) }
    var celebration by remember { mutableStateOf<Celebration?>(null) }

    // ---- rest timer ----
    var restLeft by remember { mutableIntStateOf(0) }
    var restTotal by remember { mutableIntStateOf(0) }
    var restRun by remember { mutableIntStateOf(0) }
    var restPreset by remember { mutableIntStateOf(prefs.restSeconds) }
    LaunchedEffect(restRun) {
        while (restLeft > 0) {
            delay(1000)
            if (restLeft > 0) { restLeft--; if (restLeft == 0) buzzPattern(ctx, longArrayOf(0, 200, 120, 200, 120, 400)) }
        }
    }
    fun startRest(sec: Int) { restTotal = sec; restLeft = sec; restRun++ }

    fun toDEx(e: ExerciseFull) = DEx(
        keyGen++, e.ex.exerciseId, e.ex.name, e.ex.measure, e.ex.note, e.ex.nextTimeNote, e.ex.substitutedFrom,
        e.sets.map { DSet(it.weight?.let { w -> fmtWeight(w, lb) } ?: "", it.reps?.toString() ?: "", it.seconds?.toString() ?: "", it.done) },
    )

    suspend fun refreshLast(e: DEx) { e.last = app.repo.lastTime(e.exerciseId, id) }

    LaunchedEffect(id) {
        val sf = app.repo.loadSession(id)
        if (sf == null) { actions.back(); return@LaunchedEffect }
        base = sf.session; date = LocalDate.parse(sf.session.date); title = sf.session.name
        templateId = sf.session.templateId; sessionNote = sf.session.note
        exs.clear(); exs.addAll(sf.exercises.map(::toDEx))
        exs.forEach { refreshLast(it) }
        loaded = true
    }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    fun snapshot(finish: Boolean = false, durationSec: Int? = null): SessionFull? {
        val b = base ?: return null
        val list = exs.mapIndexed { i, e ->
            ExerciseFull(
                SessionExercise(0, b.id, e.exerciseId, e.name, e.measure, i, e.note.trim(), e.nextNote.trim(), e.subFrom),
                e.sets.mapIndexed { j, s ->
                    SetEntry(
                        0, 0, j,
                        if (e.measure == Measure.WEIGHT_REPS) s.weight.replace(',', '.').toDoubleOrNull()?.let { v -> fromDisplay(v, lb) } else null,
                        if (e.measure != Measure.TIME) s.reps.toIntOrNull() else null,
                        if (e.measure == Measure.TIME) s.seconds.toIntOrNull() else null,
                        s.done,
                    )
                },
            )
        }
        return SessionFull(
            b.copy(
                date = date.toString(), name = title.ifBlank { b.name }, templateId = templateId, note = sessionNote.trim(),
                status = if (finish) SessionStatus.DONE else b.status, durationSec = durationSec ?: b.durationSec,
            ),
            list,
        )
    }

    // Live autosave: the draft is in the database a fraction of a second after every edit.
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        snapshotFlow { snapshot() }.debounce(500).collect { s -> if (s != null && !deleted) app.repo.saveSession(s) }
    }
    DisposableEffect(Unit) {
        onDispose { if (loaded && !deleted) snapshot()?.let { s -> app.scope.launch { app.repo.saveSession(s) } } }
    }
    BackHandler { actions.back() }

    fun finish(countUnchecked: Boolean) {
        val b = base ?: return
        exs.forEach { e ->
            if (countUnchecked) e.sets.forEach { if (!it.done && it.filled) it.done = true }
            e.sets.removeAll { !it.done }
        }
        exs.removeAll { it.sets.isEmpty() }
        if (exs.isEmpty()) { Toast.makeText(ctx, R.string.wk_nothing, Toast.LENGTH_SHORT).show(); return }
        val dur = if (b.status == SessionStatus.DRAFT) ((System.currentTimeMillis() - b.startedAt) / 1000).toInt().coerceAtLeast(60) else b.durationSec
        val sf = snapshot(finish = true, durationSec = dur) ?: return
        base = sf.session // from now on any pending autosave also writes DONE, never a stale DRAFT
        scope.launch {
            val before = app.repo.doneSessions.value.filter { it.session.id != b.id }
            app.repo.saveSession(sf)
            val sfFull = app.repo.loadSession(b.id) ?: sf
            val plans = app.repo.dayPlans.value.values.toList()
            val s0 = Stats.summarize(before, plans, prefs.weeklyGoal)
            val s1 = Stats.summarize(before + sfFull, plans, prefs.weeklyGoal)
            buzz(ctx, 35)
            base = sfFull.session
            celebration = Celebration(Stats.sessionVolume(sfFull), sfFull.doneSets, sfFull.session.durationSec, s1.prs - s0.prs, s1.workouts)
        }
    }

    fun requestFinish() {
        val unchecked = exs.sumOf { e -> e.sets.count { !it.done && it.filled } }
        val checked = exs.sumOf { e -> e.sets.count { it.done } }
        if (checked == 0 && unchecked == 0) { Toast.makeText(ctx, R.string.wk_nothing, Toast.LENGTH_SHORT).show(); return }
        // Sets are pre-filled from the plan / last time, so unchecked ones are normally "not done".
        // Only when nothing at all was checked do we ask (people who never tick boxes).
        if (checked == 0) uncheckedPrompt = unchecked else finish(false)
    }

    fun applyTemplate(tid: Long?) {
        scope.launch {
            val t = tid?.let { x -> templates.firstOrNull { it.template.id == x } }
            templateId = tid
            cur = 0
            if (t != null) { title = t.template.name }
            exs.clear()
            if (tid != null) {
                exs.addAll(app.repo.exercisesFromTemplate(tid, id).map(::toDEx))
                exs.forEach { refreshLast(it) }
            }
        }
    }

    val isDone = base?.status == SessionStatus.DONE
    val elapsed = base?.let { ((now - it.startedAt) / 1000).toInt().coerceAtLeast(0) } ?: 0
    val wkField = Field.forColor(templates.firstOrNull { it.template.id == templateId }?.template?.colorIdx ?: 5)
    val total = exs.size
    val idx = cur.coerceIn(0, (total - 1).coerceAtLeast(0))
    val curEx = exs.getOrNull(idx)

    Column(Modifier.fillMaxSize().backdrop(wkField).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // ---- top bar ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconPill(Icons.AutoMirrored.Rounded.ArrowBack, { actions.back() })
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, color = TC.Text)
                Text(if (isDone) fmtDate(date) else fmtClock(elapsed), color = TC.Dim, fontFamily = Mono, fontSize = 12.sp)
            }
            IconPill(Icons.Rounded.MoreHoriz, { sessionMenu = true })
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (curEx == null) {
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.workout_free), style = MaterialTheme.typography.displayMedium)
                Text(stringResource(R.string.wk_empty_sub), color = TC.Dim)
                PrimaryButton(stringResource(R.string.wk_add_exercise), { addPicker = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
            } else key(curEx.key) {
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.wk_exercise_n, idx + 1, total).uppercase(), style = MaterialTheme.typography.labelMedium, color = TC.Dim,
                        modifier = Modifier.weight(1f).pressable({ overview = true }),
                    )
                    IconPill(Icons.Rounded.SwapHoriz, { menuFor = curEx }, size = 36.dp)
                }
                ExerciseFocus(curEx, lb, onSetDone = { s -> if (s.done) { buzz(ctx, 25); startRest(restPreset) } })
            }
            Spacer(Modifier.height(16.dp))
        }

        // ---- rest timer + actions ----
        Column(Modifier.fillMaxWidth().background(Color(0xE60C080A)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(TC.Outline))
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (restLeft > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.wk_rest), color = TC.Dim, fontSize = 12.sp)
                        Text(fmtClock(restLeft), fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 26.sp)
                        Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.12f))) {
                            Box(Modifier.fillMaxWidth(restLeft / restTotal.coerceAtLeast(1).toFloat()).height(3.dp).background(TC.Sky))
                        }
                        Text("+15", fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.pressable({ restLeft += 15; restTotal += 15 }).padding(6.dp))
                        Text(stringResource(R.string.wk_skip), fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.pressable({ restLeft = 0 }).padding(6.dp))
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.wk_rest), color = TC.Dim, fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp))
                        listOf(60, 90, 120, 180).forEach { sec ->
                            TChip(fmtClock(sec), restPreset == sec, { restPreset = sec; startRest(sec); scope.launch { app.settings.update { restSeconds = sec } } }, color = TC.Sky)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (idx > 0) IconPill(Icons.AutoMirrored.Rounded.ArrowBack, { cur = idx - 1 }, size = 48.dp)
                    val nextName = exs.getOrNull(idx + 1)?.name
                    GhostButton(
                        if (nextName != null) stringResource(R.string.wk_next, nextName) else stringResource(R.string.wk_add_exercise),
                        { if (nextName != null) cur = idx + 1 else addPicker = true }, Modifier.weight(1f).height(48.dp),
                    )
                    PrimaryButton(stringResource(if (isDone) R.string.save else R.string.wk_finish_short), { requestFinish() }, Modifier.width(112.dp).height(48.dp))
                }
            }
        }
    }

    if (overview) TDialog(stringResource(R.string.wk_exercises), { overview = false }, confirm = stringResource(R.string.wk_done), onConfirm = { overview = false }, showCancel = false) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            exs.forEachIndexed { i, e ->
                TCard(Modifier.fillMaxWidth(), onClick = { cur = i; overview = false }, radius = 14.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), border = if (i == idx) TC.Accent else TC.Outline) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("%02d".format(i + 1), fontFamily = Mono, fontSize = 12.sp, color = TC.Faint, modifier = Modifier.width(28.dp))
                        Text(e.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text("${e.sets.count { it.done }}/${e.sets.size}", fontFamily = Mono, fontSize = 12.sp, color = TC.Dim)
                    }
                }
            }
            GhostButton(stringResource(R.string.wk_add_exercise), { overview = false; addPicker = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
        }
    }
    if (sessionMenu) TDialog(title, { sessionMenu = false }, confirm = stringResource(R.string.wk_done), onConfirm = { sessionMenu = false }, showCancel = false) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.wk_workout).uppercase(), style = MaterialTheme.typography.labelSmall, color = TC.Dim)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                templates.forEach { t ->
                    TChip(t.template.name, templateId == t.template.id, {
                        if (templateId == t.template.id) return@TChip
                        sessionMenu = false
                        if (exs.any { e -> e.sets.any { it.done } }) confirmTemplate = t.template.id else applyTemplate(t.template.id)
                    }, color = TC.palette(t.template.colorIdx))
                }
                TChip(stringResource(R.string.workout_free), templateId == null, { if (templateId != null) { templateId = null } })
            }
            GhostButton(fmtDate(date), { sessionMenu = false; datePicker = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.EditCalendar)
            GhostButton(stringResource(R.string.wk_add_exercise), { sessionMenu = false; addPicker = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
            Text(stringResource(R.string.wk_session_note).uppercase(), style = MaterialTheme.typography.labelSmall, color = TC.Dim)
            TextBox(sessionNote, { sessionNote = it }, stringResource(R.string.wk_session_note_hint), minHeight = 64.dp)
            GhostButton(stringResource(R.string.delete), { sessionMenu = false; confirmDelete = true }, Modifier.fillMaxWidth(), color = TC.Danger, icon = Icons.Rounded.Delete)
        }
    }

    // ---- dialogs / sheets ----
    if (datePicker) DatePickerDialogT(date, { datePicker = false }) { date = it }
    if (addPicker) ExercisePickerSheet({ addPicker = false }) { ex ->
        val d = DEx(keyGen++, ex.id, ex.name, ex.measure, "", "", null, listOf(blankSet(ex.measure)))
        exs.add(d); cur = exs.lastIndex; scope.launch { refreshLast(d); fillFromLast(d, lb) }
    }
    pickerFor?.let { target ->
        ExercisePickerSheet({ pickerFor = null }) { ex ->
            if (target.subFrom == null && ex.id != target.exerciseId) target.subFrom = target.name
            target.exerciseId = ex.id; target.name = ex.name
            if (target.measure != ex.measure) { target.measure = ex.measure; for (i in target.sets.indices) target.sets[i] = blankSet(ex.measure) }
            target.nextNote = ""
            scope.launch { refreshLast(target); fillFromLast(target, lb) }
        }
    }
    menuFor?.let { e ->
        TDialog(e.name, { menuFor = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(stringResource(R.string.wk_swap_today), { pickerFor = e; menuFor = null }, Modifier.fillMaxWidth(), icon = Icons.Rounded.SwapHoriz)
                if (e.subFrom != null) Text(stringResource(R.string.wk_swapped_from, e.subFrom ?: ""), color = TC.Dim, fontSize = 13.sp)
                Text(stringResource(R.string.wk_unit).uppercase(), style = MaterialTheme.typography.labelSmall, color = TC.Dim)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Measure.entries.forEach { m ->
                        TChip(measureLabel(m), e.measure == m, {
                            if (e.measure != m) { e.measure = m; for (i in e.sets.indices) e.sets[i] = blankSet(m).also { n -> n.done = e.sets[i].done } }
                        }, color = TC.Sky)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val i = exs.indexOf(e)
                    if (i > 0) GhostButton(stringResource(R.string.move_up), { exs.add(i - 1, exs.removeAt(i)); menuFor = null }, Modifier.weight(1f), icon = Icons.Rounded.ArrowUpward)
                    if (i < exs.lastIndex) GhostButton(stringResource(R.string.move_down), { exs.add(i + 1, exs.removeAt(i)); menuFor = null }, Modifier.weight(1f), icon = Icons.Rounded.ArrowDownward)
                }
                GhostButton(stringResource(R.string.wk_remove_exercise), { exs.remove(e); menuFor = null }, Modifier.fillMaxWidth(), color = TC.Danger, icon = Icons.Rounded.Delete)
            }
        }
    }
    if (confirmDelete) ConfirmDialog(stringResource(R.string.wk_delete_title), stringResource(R.string.wk_delete_text), stringResource(R.string.delete), { confirmDelete = false }, danger = true) {
        deleted = true
        app.scope.launch { app.repo.deleteSession(id) }
        actions.back()
    }
    confirmTemplate?.let { tid ->
        ConfirmDialog(stringResource(R.string.wk_switch_title), stringResource(R.string.wk_switch_text), stringResource(R.string.wk_switch_confirm), { confirmTemplate = null }) { applyTemplate(tid) }
    }
    if (uncheckedPrompt > 0) TDialog(stringResource(R.string.wk_unchecked_title), { uncheckedPrompt = 0 }) {
        Text(stringResource(R.string.wk_unchecked_text, uncheckedPrompt), color = TC.Dim)
        PrimaryButton(stringResource(R.string.wk_count_them), { uncheckedPrompt = 0; finish(true) }, Modifier.fillMaxWidth())
        GhostButton(stringResource(R.string.wk_discard_them), { uncheckedPrompt = 0; finish(false) }, Modifier.fillMaxWidth())
    }
    celebration?.let { c ->
        TDialog(stringResource(R.string.wk_saved_title), { celebration = null; actions.back() }, confirm = stringResource(R.string.wk_done), onConfirm = { celebration = null; actions.back() }, showCancel = false) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SavedStat(stringResource(R.string.wk_sets), "${c.sets}")
                    SavedStat(stringResource(R.string.pg_total_volume), fmtBig(c.volumeKg, lb) + " " + unitLabel(lb))
                    SavedStat(stringResource(R.string.wk_duration), fmtDuration(c.seconds))
                }
                if (c.prs > 0) Text(stringResource(R.string.wk_prs, c.prs), color = TC.Accent, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.wk_workout_n, c.workouts), color = TC.Dim)
            }
        }
    }
}

@Composable
private fun SavedStat(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TC.Dim)
        Text(value, fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 18.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

private data class Celebration(val volumeKg: Double, val sets: Int, val seconds: Int, val prs: Int, val workouts: Int)

private fun blankSet(m: Measure) = DSet("", if (m != Measure.TIME) "" else "", "", false)

private suspend fun fillFromLast(e: DEx, lb: Boolean) {
    val ls = e.last?.sets ?: return
    for (i in e.sets.indices) {
        val src = ls.getOrNull(i) ?: ls.lastOrNull() ?: continue
        val s = e.sets[i]
        if (s.filled) continue
        s.weight = src.weight?.let { fmtWeight(it, lb) } ?: ""
        s.reps = src.reps?.toString() ?: ""
        s.seconds = src.seconds?.toString() ?: ""
    }
}

@Composable
private fun ExerciseFocus(e: DEx, lb: Boolean, onSetDone: (DSet) -> Unit) {
    val last = e.last
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(e.name, style = MaterialTheme.typography.displayMedium, color = TC.Text)
        if (last != null) {
            val txt = last.sets.joinToString("  ·  ") { fmtSet(it, e.measure, lb) }
            Text(stringResource(R.string.wk_last_time, fmtDateShort(LocalDate.parse(last.date)), txt), color = TC.Dim, fontSize = 12.sp, fontFamily = Mono, lineHeight = 17.sp)
        } else Text(stringResource(R.string.wk_first_time), color = TC.Faint, fontSize = 13.sp)
        if (e.subFrom != null) Text(stringResource(R.string.wk_swapped_from, e.subFrom ?: ""), color = TC.Sky, fontSize = 12.sp)
        if (last != null && last.nextTimeNote.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xB30A1E22)).border(1.dp, TC.Sky.copy(alpha = 0.45f), RoundedCornerShape(16.dp)).padding(12.dp),
            ) {
                Box(Modifier.width(3.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(TC.Sky))
                Column(Modifier.padding(start = 10.dp)) {
                    Text(stringResource(R.string.wk_from_last_time).uppercase(), style = MaterialTheme.typography.labelSmall, color = TC.Sky)
                    Text(last.nextTimeNote, color = TC.Text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        TCard(Modifier.fillMaxWidth(), radius = 20.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
            Column {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColLabel(stringResource(R.string.wk_set), 32.dp)
                    when (e.measure) {
                        Measure.WEIGHT_REPS -> { ColLabel(unitLabel(lb), 92.dp); Spacer(Modifier.width(8.dp)); ColLabel(stringResource(R.string.plan_reps), 72.dp) }
                        Measure.REPS -> ColLabel(stringResource(R.string.plan_reps), 92.dp)
                        Measure.TIME -> ColLabel(stringResource(R.string.plan_seconds), 100.dp)
                    }
                }
                e.sets.forEachIndexed { i, s ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(TC.Outline))
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", Modifier.width(32.dp), fontFamily = Mono, color = if (s.done) TC.Faint else TC.Dim, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        when (e.measure) {
                            Measure.WEIGHT_REPS -> {
                                MiniField(s.weight, { v -> s.weight = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7) }, decimal = true, width = 92.dp, hint = "–", accent = s.done)
                                Spacer(Modifier.width(8.dp))
                                MiniField(s.reps, { v -> s.reps = v.filter(Char::isDigit).take(3) }, width = 72.dp, hint = "–", accent = s.done)
                            }
                            Measure.REPS -> MiniField(s.reps, { v -> s.reps = v.filter(Char::isDigit).take(3) }, width = 92.dp, hint = "–", accent = s.done)
                            Measure.TIME -> MiniField(s.seconds, { v -> s.seconds = v.filter(Char::isDigit).take(5) }, width = 100.dp, hint = "–", accent = s.done)
                        }
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (s.done) TC.Accent else Color.Transparent)
                                .border(1.dp, if (s.done) TC.Accent else Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .pressable({ s.done = !s.done; onSetDone(s) }),
                            contentAlignment = Alignment.Center,
                        ) { if (s.done) Icon(Icons.Rounded.Check, null, tint = TC.Bg) }
                        Icon(Icons.Rounded.Close, null, tint = TC.Faint, modifier = Modifier.padding(start = 8.dp).size(20.dp).pressable({ e.sets.remove(s) }))
                    }
                }
            }
        }
        GhostButton(stringResource(R.string.wk_add_set), {
            val prev = e.sets.lastOrNull()
            e.sets.add(DSet(prev?.weight ?: "", prev?.reps ?: "", prev?.seconds ?: "", false))
        }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
        TextBox(e.note, { e.note = it }, stringResource(R.string.wk_note_hint), minHeight = 44.dp)
        TextBox(e.nextNote, { e.nextNote = it }, stringResource(R.string.wk_next_note_hint), minHeight = 44.dp, accent = TC.Sky)
    }
}

@Composable
private fun ColLabel(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(text.uppercase(), Modifier.width(width), style = MaterialTheme.typography.labelSmall, color = TC.Faint, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

private fun fmtSet(s: SetEntry, m: Measure, lb: Boolean): String = when (m) {
    Measure.WEIGHT_REPS -> "${s.weight?.let { fmtWeight(it, lb) } ?: "–"}×${s.reps ?: "–"}"
    Measure.REPS -> "${s.reps ?: "–"}"
    Measure.TIME -> "${s.seconds ?: "–"}s"
}
