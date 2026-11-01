package app.plinth.gym.ui.plans

import app.plinth.gym.ui.components.Field
import app.plinth.gym.ui.components.backdrop
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plinth.gym.LocalApp
import app.plinth.gym.R
import app.plinth.gym.data.Measure
import app.plinth.gym.data.Prefs
import app.plinth.gym.data.Repository
import app.plinth.gym.data.TemplateFull
import app.plinth.gym.ui.Actions
import app.plinth.gym.ui.components.ConfirmDialog
import app.plinth.gym.ui.components.GhostButton
import app.plinth.gym.ui.components.IconPill
import app.plinth.gym.ui.components.MiniField
import app.plinth.gym.ui.components.PrimaryButton
import app.plinth.gym.ui.components.ScreenColumn
import app.plinth.gym.ui.components.TCard
import app.plinth.gym.ui.components.TextBox
import app.plinth.gym.ui.components.pressable
import app.plinth.gym.ui.fmtWeight
import app.plinth.gym.ui.fromDisplay
import app.plinth.gym.ui.theme.TC
import app.plinth.gym.ui.unitLabel
import kotlinx.coroutines.launch

@Composable
fun PlansScreen(prefs: Prefs, actions: Actions) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val templates by app.repo.templates.collectAsState()
    var deleting by remember { mutableStateOf<TemplateFull?>(null) }
    val copySuffix = stringResource(R.string.plan_copy_suffix)

    ScreenColumn(field = Field.MOSS) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_plans), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            IconPill(Icons.Rounded.Add, { actions.editPlan(0) }, bg = TC.Accent, tint = Color.Black)
        }
        Text(stringResource(R.string.plans_sub), color = TC.Dim)
        if (templates.isEmpty()) {
            TCard(Modifier.fillMaxWidth(), onClick = { actions.editPlan(0) }, radius = 16.dp) {
                Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.plans_empty), fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                    Text(stringResource(R.string.plans_empty_sub), color = TC.Dim, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        templates.forEachIndexed { idx, t ->
            val color = TC.palette(t.template.colorIdx)
            TCard(Modifier.fillMaxWidth(), onClick = { actions.editPlan(t.template.id) }, radius = 14.dp) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(width = 6.dp, height = 40.dp).clip(CircleShape).background(color))
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(t.template.name, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = TC.Text)
                            Text(pluralStringResource(R.plurals.exercises_count, t.exercises.size, t.exercises.size), color = TC.Dim, fontSize = 13.sp)
                        }
                    }
                    if (t.exercises.isNotEmpty()) Text(
                        t.exercises.take(4).joinToString(" · ") { it.exName } + if (t.exercises.size > 4) " …" else "",
                        color = TC.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp), maxLines = 2,
                    )
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (idx > 0) IconPill(Icons.Rounded.ArrowUpward, { scope.launch { app.repo.moveTemplate(t.template.id, -1) } }, size = 38.dp)
                        if (idx < templates.lastIndex) IconPill(Icons.Rounded.ArrowDownward, { scope.launch { app.repo.moveTemplate(t.template.id, 1) } }, size = 38.dp)
                        Spacer(Modifier.weight(1f))
                        IconPill(Icons.Rounded.ContentCopy, { scope.launch { app.repo.duplicateTemplate(t, copySuffix) } }, size = 38.dp)
                        IconPill(Icons.Rounded.Delete, { deleting = t }, size = 38.dp, tint = TC.Danger)
                    }
                }
            }
        }
    }
    deleting?.let { t ->
        ConfirmDialog(stringResource(R.string.plan_delete_title, t.template.name), stringResource(R.string.plan_delete_text), stringResource(R.string.delete), { deleting = null }, danger = true) {
            scope.launch { app.repo.deleteTemplate(t.template.id) }
        }
    }
}

private class EditItem(
    val exerciseId: Long, val name: String, val measure: Measure,
    sets: String, repsMin: String, repsMax: String, weight: String, note: String,
) {
    var sets by mutableStateOf(sets); var repsMin by mutableStateOf(repsMin); var repsMax by mutableStateOf(repsMax)
    var weight by mutableStateOf(weight); var note by mutableStateOf(note)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanEditorScreen(id: Long, prefs: Prefs, actions: Actions) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val templates by app.repo.templates.collectAsState()
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(templates.size) }
    val items = remember { mutableStateListOf<EditItem>() }
    var loaded by remember { mutableStateOf(id == 0L) }
    var picker by remember { mutableStateOf(false) }
    val defaultName = stringResource(R.string.plan_default_name)

    LaunchedEffect(templates) {
        if (!loaded) templates.firstOrNull { it.template.id == id }?.let { t ->
            name = t.template.name; color = t.template.colorIdx
            items.clear()
            t.exercises.forEach { e ->
                items += EditItem(e.te.exerciseId, e.exName, e.exMeasure, "${e.te.sets}", "${e.te.repsMin}", "${e.te.repsMax}",
                    if (e.te.weight > 0) fmtWeight(e.te.weight, prefs.useLb) else "", e.te.note)
            }
            loaded = true
        }
    }

    fun save() {
        val finalName = name.trim().ifBlank { defaultName }
        val drafts = items.map {
            val a = it.repsMin.toIntOrNull() ?: 8
            Repository.TemplateExerciseDraft(
                it.exerciseId, (it.sets.toIntOrNull() ?: 3).coerceIn(1, 20), a, (it.repsMax.toIntOrNull() ?: a).coerceAtLeast(a),
                it.weight.replace(',', '.').toDoubleOrNull()?.let { w -> fromDisplay(w, prefs.useLb) } ?: 0.0, it.note.trim(),
            )
        }
        // fire-and-forget on the app scope so leaving the screen can never cancel the write
        app.scope.launch { app.repo.saveTemplate(if (id == 0L) null else id, finalName, color, drafts) }
        actions.back()
    }
    BackHandler { if (loaded) save() else actions.back() }

    Column(Modifier.fillMaxSize().backdrop(Field.MOSS).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconPill(Icons.AutoMirrored.Rounded.ArrowBack, { if (loaded) save() else actions.back() })
            Text(stringResource(if (id == 0L) R.string.plan_new else R.string.plan_edit), Modifier.weight(1f).padding(start = 14.dp), style = MaterialTheme.typography.titleLarge)
            PrimaryButton(stringResource(R.string.save), { save() }, Modifier.height(44.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextBox(name, { name = it }, stringResource(R.string.plan_name_hint), singleLine = true, accent = TC.palette(color))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TC.Palette.indices.forEach { i ->
                    Box(Modifier.size(34.dp).clip(CircleShape).background(TC.palette(i)).pressable({ color = i }), contentAlignment = Alignment.Center) {
                        if (i == color % TC.Palette.size) Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                }
            }
            items.forEachIndexed { i, it ->
                TCard(Modifier.fillMaxWidth(), radius = 12.dp, padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(it.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                Text(measureLabel(it.measure), color = TC.Dim, fontSize = 12.sp)
                            }
                            if (i > 0) IconPill(Icons.Rounded.ArrowUpward, { items.add(i - 1, items.removeAt(i)) }, size = 36.dp)
                            Spacer(Modifier.size(6.dp))
                            if (i < items.lastIndex) IconPill(Icons.Rounded.ArrowDownward, { items.add(i + 1, items.removeAt(i)) }, size = 36.dp)
                            Spacer(Modifier.size(6.dp))
                            IconPill(Icons.Rounded.Delete, { items.removeAt(i) }, size = 36.dp, tint = TC.Danger)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            Labeled(stringResource(R.string.plan_sets)) { MiniField(it.sets, { v -> it.sets = v.filter(Char::isDigit).take(2) }, width = 56.dp, hint = "3") }
                            Labeled(stringResource(if (it.measure == Measure.TIME) R.string.plan_seconds else R.string.plan_reps)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MiniField(it.repsMin, { v -> it.repsMin = v.filter(Char::isDigit).take(3) }, width = 56.dp, hint = "8")
                                    Text("–", Modifier.padding(horizontal = 4.dp), color = TC.Dim)
                                    MiniField(it.repsMax, { v -> it.repsMax = v.filter(Char::isDigit).take(3) }, width = 56.dp, hint = "12")
                                }
                            }
                            if (it.measure == Measure.WEIGHT_REPS) Labeled(unitLabel(prefs.useLb)) {
                                MiniField(it.weight, { v -> it.weight = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) }, decimal = true, width = 76.dp, hint = "—")
                            }
                        }
                        TextBox(it.note, { v -> it.note = v }, stringResource(R.string.plan_note_hint), singleLine = true, minHeight = 44.dp)
                    }
                }
            }
            GhostButton(stringResource(R.string.plan_add_exercise), { picker = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
            Spacer(Modifier.height(30.dp))
        }
    }
    if (picker) ExercisePickerSheet({ picker = false }) { e ->
        items += EditItem(e.id, e.name, e.measure, "3", if (e.measure == Measure.TIME) "30" else "8", if (e.measure == Measure.TIME) "60" else "12", "", "")
    }
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TC.Dim, modifier = Modifier.padding(start = 4.dp, bottom = 3.dp))
        content()
    }
}
