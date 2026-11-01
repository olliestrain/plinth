package app.rowan.gym.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rowan.gym.LocalApp
import app.rowan.gym.R
import app.rowan.gym.data.DayPlan
import app.rowan.gym.data.DayType
import app.rowan.gym.data.Note
import app.rowan.gym.data.Prefs
import app.rowan.gym.data.SessionStatus
import app.rowan.gym.data.TemplateFull
import app.rowan.gym.ui.Actions
import app.rowan.gym.ui.buzz
import app.rowan.gym.ui.components.DatePickerDialogT
import app.rowan.gym.ui.components.GhostButton
import app.rowan.gym.ui.components.IconPill
import app.rowan.gym.ui.components.PrimaryButton
import app.rowan.gym.ui.components.ScreenColumn
import app.rowan.gym.ui.components.SectionTitle
import app.rowan.gym.ui.components.TCard
import app.rowan.gym.ui.components.TChip
import app.rowan.gym.ui.components.TDialog
import app.rowan.gym.ui.components.TextBox
import app.rowan.gym.ui.components.pressable
import app.rowan.gym.ui.fmtDateLong
import app.rowan.gym.ui.fmtMonth
import app.rowan.gym.ui.theme.Mono
import app.rowan.gym.ui.theme.TC
import app.rowan.gym.ui.weekdayShort
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(prefs: Prefs, actions: Actions) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val sessions by app.repo.doneSessions.collectAsState()
    val plans by app.repo.dayPlans.collectAsState()
    val templates by app.repo.templates.collectAsState()
    var ym by rememberSaveable { mutableIntStateOf(YearMonth.now().year * 12 + YearMonth.now().monthValue - 1) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var planMonth by remember { mutableStateOf(false) }
    val month = YearMonth.of(ym / 12, ym % 12 + 1)
    val today = LocalDate.now()
    val doneByDate = remember(sessions) { sessions.groupBy { it.session.date } }

    var drag by remember { mutableStateOf(0f) }
    ScreenColumn(Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { drag = 0f },
            onHorizontalDrag = { _, d -> drag += d },
            onDragEnd = { if (drag > 140) ym-- else if (drag < -140) ym++ },
        )
    }) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(fmtMonth(month.atDay(1)), style = MaterialTheme.typography.headlineLarge, color = TC.Text, modifier = Modifier.weight(1f))
            IconPill(Icons.Rounded.AutoAwesomeMotion, { planMonth = true })
            Spacer(Modifier.size(8.dp))
            IconPill(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, { ym-- })
            Spacer(Modifier.size(8.dp))
            IconPill(Icons.AutoMirrored.Rounded.KeyboardArrowRight, { ym++ })
        }
        if (month != YearMonth.now()) Row { TChip(stringResource(R.string.cal_today), false, { ym = YearMonth.now().year * 12 + YearMonth.now().monthValue - 1 }) }

        TCard(Modifier.fillMaxWidth(), radius = 32.dp, padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    (1..7).forEach { d ->
                        Text(weekdayShort(d), Modifier.weight(1f), color = TC.Dim, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(6.dp))
                val first = month.atDay(1)
                val offset = first.dayOfWeek.value - 1
                val rows = (offset + month.lengthOfMonth() + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        for (c in 0..6) {
                            val dayNum = r * 7 + c - offset + 1
                            if (dayNum < 1 || dayNum > month.lengthOfMonth()) { Box(Modifier.weight(1f).aspectRatio(0.88f)); continue }
                            val date = month.atDay(dayNum)
                            DayCell(
                                date, today, plans[date.toString()], templates, doneByDate[date.toString()]?.isNotEmpty() == true,
                                Modifier.weight(1f).aspectRatio(0.88f).padding(2.dp),
                            ) { selected = date.toString(); buzz(ctx, 10) }
                        }
                    }
                }
            }
        }

        // ---- month summary ----
        val monthDays = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val planned = monthDays.count { plans[it.toString()]?.type == DayType.WORKOUT }
        val doneN = monthDays.sumOf { (doneByDate[it.toString()]?.size ?: 0) }
        val skipped = monthDays.count { it.isBefore(today) && plans[it.toString()]?.type == DayType.WORKOUT && doneByDate[it.toString()].isNullOrEmpty() }
        TCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SummaryStat(planned, stringResource(R.string.cal_planned), TC.Text)
                SummaryStat(doneN, stringResource(R.string.cal_done), TC.Lime)
                SummaryStat(skipped, stringResource(R.string.cal_skipped), if (skipped > 0) TC.Danger else TC.Dim)
            }
        }
        Text(stringResource(R.string.cal_hint), color = TC.Faint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
    }

    selected?.let { iso ->
        DaySheet(LocalDate.parse(iso), templates, plans[iso], doneByDate[iso].orEmpty().map { it.session.id to it.session.name }, actions, onDismiss = { selected = null })
    }
    if (planMonth) PlanMonthDialog(month, templates, prefs) { planMonth = false }
}

@Composable
private fun SummaryStat(n: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$n", fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 34.sp, color = color)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TC.Dim)
    }
}

@Composable
private fun DayCell(date: LocalDate, today: LocalDate, plan: DayPlan?, templates: List<TemplateFull>, done: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val planned = plan?.type == DayType.WORKOUT
    val rest = plan?.type == DayType.REST
    val skipped = planned && !done && date.isBefore(today)
    val due = planned && !done && date == today
    val tColor = plan?.templateId?.let { id -> templates.firstOrNull { it.template.id == id }?.template?.colorIdx }?.let { TC.palette(it) } ?: TC.Lime
    val shape = RoundedCornerShape(14.dp)
    val bg = when { done -> TC.Lime; skipped -> TC.Danger.copy(alpha = 0.12f); rest -> TC.Surface; else -> TC.CardHi.copy(alpha = 0.55f) }
    val borderColor = when {
        done -> TC.Lime; skipped -> TC.Danger.copy(alpha = 0.7f); due -> TC.Lime; date == today -> TC.Text.copy(alpha = 0.7f)
        planned -> tColor.copy(alpha = 0.7f); else -> Color.Transparent
    }
    Box(modifier.clip(shape).background(bg).border(if (due || date == today) 2.dp else 1.dp, borderColor, shape).pressable(onClick)) {
        Text("${date.dayOfMonth}", Modifier.align(Alignment.TopStart).padding(start = 7.dp, top = 5.dp), fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 13.sp,
            color = when { done -> Color.Black; skipped -> TC.Danger; rest -> TC.Faint; else -> TC.Text })
        when {
            done -> Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(18.dp))
            skipped -> Icon(Icons.Rounded.Close, null, tint = TC.Danger, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(16.dp))
            planned -> Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp).size(7.dp).clip(CircleShape).background(tColor))
            rest -> Icon(Icons.Rounded.NightsStay, null, tint = TC.Faint, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(14.dp))
        }
        if (!plan?.note.isNullOrBlank()) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(6.dp).clip(CircleShape).background(if (done) Color.Black else TC.Sky))
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DaySheet(
    date: LocalDate, templates: List<TemplateFull>, plan: DayPlan?, doneSessions: List<Pair<Long, String>>,
    actions: Actions, onDismiss: () -> Unit,
) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var note by remember(date, plan?.note) { mutableStateOf(plan?.note ?: "") }
    var moving by remember { mutableStateOf(false) }
    val type = plan?.type ?: DayType.NONE

    ModalBottomSheet(onDismissRequest = { scope.launch { app.repo.setDayNote(date, note) }; onDismiss() }, sheetState = state, containerColor = TC.Surface, contentColor = TC.Text) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(fmtDateLong(date), style = MaterialTheme.typography.headlineMedium)

            if (doneSessions.isNotEmpty()) {
                SectionTitle(stringResource(R.string.cal_trained))
                doneSessions.forEach { (id, name) ->
                    TCard(Modifier.fillMaxWidth(), onClick = { onDismiss(); actions.openSession(id) }, color = TC.Lime.copy(alpha = 0.12f), border = TC.Lime.copy(alpha = 0.5f), radius = 20.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Check, null, tint = TC.Lime)
                            Text(name, Modifier.weight(1f).padding(start = 10.dp), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                            Text(stringResource(R.string.cal_open), color = TC.Lime, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            SectionTitle(stringResource(R.string.cal_plan_day))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                templates.forEach { t ->
                    TChip(t.template.name, type == DayType.WORKOUT && plan?.templateId == t.template.id,
                        { scope.launch { app.repo.setDay(date, DayType.WORKOUT, t.template.id) } }, color = TC.palette(t.template.colorIdx))
                }
                TChip(stringResource(R.string.cal_rest), type == DayType.REST, { scope.launch { app.repo.setDay(date, DayType.REST, null) } }, color = TC.Sky)
                if (type != DayType.NONE) TChip(stringResource(R.string.cal_clear), false, { scope.launch { app.repo.setDay(date, DayType.NONE, null) } })
            }
            if (templates.isEmpty()) Text(stringResource(R.string.cal_no_templates), color = TC.Dim, fontSize = 13.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (type != DayType.NONE) GhostButton(stringResource(R.string.cal_move), { moving = true }, Modifier.weight(1f), icon = Icons.Rounded.SwapHoriz)
                PrimaryButton(stringResource(R.string.start_workout), {
                    scope.launch { app.repo.setDayNote(date, note) }
                    onDismiss(); actions.startWorkout(date, plan?.templateId)
                }, Modifier.weight(1f), icon = Icons.Rounded.PlayArrow, enabled = !date.isAfter(LocalDate.now()))
            }

            SectionTitle(stringResource(R.string.cal_note))
            TextBox(note, { note = it }, stringResource(R.string.cal_note_hint), minHeight = 84.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(stringResource(R.string.save), { scope.launch { app.repo.setDayNote(date, note) } ; onDismiss() }, Modifier.weight(1f))
                if (note.isNotBlank()) GhostButton(stringResource(R.string.note_archive), {
                    scope.launch {
                        app.repo.saveNote(Note(text = note.trim(), archived = true, source = date.toString()))
                        app.repo.setDayNote(date, ""); note = ""
                    }
                }, Modifier.weight(1f), icon = Icons.Rounded.Inventory2)
            }
        }
    }
    if (moving) DatePickerDialogT(date.plusDays(1), { moving = false }) { to ->
        scope.launch { app.repo.moveDay(date, to) }; onDismiss()
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlanMonthDialog(month: YearMonth, templates: List<TemplateFull>, prefs: Prefs, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    // value: null = leave alone, -1 = rest, else template id. Seeded from the weekdays picked in the wizard.
    val pattern = remember {
        mutableStateOf(
            (1..7).associateWith { d -> if (d in prefs.trainingDays && templates.isNotEmpty()) templates[prefs.trainingDays.sorted().indexOf(d) % templates.size].template.id else null as Long? },
        )
    }
    var overwrite by remember { mutableStateOf(false) }
    TDialog(stringResource(R.string.cal_plan_month), onDismiss, confirm = stringResource(R.string.cal_apply), onConfirm = {
        scope.launch { app.repo.planMonth(month.year, month.monthValue, pattern.value, overwrite) }; onDismiss()
    }) {
        Text(stringResource(R.string.cal_plan_month_sub), color = TC.Dim, fontSize = 13.sp)
        (1..7).forEach { d ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(weekdayShort(d), fontWeight = FontWeight.Bold, color = TC.Text)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TChip("—", pattern.value[d] == null, { pattern.value = pattern.value + (d to null) })
                    templates.forEach { t -> TChip(t.template.name, pattern.value[d] == t.template.id, { pattern.value = pattern.value + (d to t.template.id) }, color = TC.palette(t.template.colorIdx)) }
                    TChip(stringResource(R.string.cal_rest), pattern.value[d] == -1L, { pattern.value = pattern.value + (d to -1L) }, color = TC.Sky)
                }
            }
        }
        Row(Modifier.pressable({ overwrite = !overwrite }), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(if (overwrite) TC.Lime else TC.Outline), contentAlignment = Alignment.Center) {
                if (overwrite) Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
            }
            Text(stringResource(R.string.cal_overwrite), Modifier.padding(start = 10.dp), color = TC.Dim)
        }
    }
}
