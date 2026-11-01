package app.plinth.gym.ui.onboarding

import app.plinth.gym.ui.components.Field
import app.plinth.gym.ui.components.backdrop
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import app.plinth.gym.LocalApp
import app.plinth.gym.R
import app.plinth.gym.ui.buzz
import app.plinth.gym.ui.components.PlinthMark
import app.plinth.gym.ui.components.PrimaryButton
import app.plinth.gym.ui.components.TCard
import app.plinth.gym.ui.components.pressable
import app.plinth.gym.ui.currentLang
import app.plinth.gym.ui.theme.Mono
import app.plinth.gym.ui.theme.TC
import app.plinth.gym.ui.weekdayNarrow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Step { LANG, NAME, GOAL, LEVEL, DAYS, UNITS, FINALE }

@Composable
fun Wizard(onDone: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val steps = Step.entries

    var stepIdx by rememberSaveable { mutableIntStateOf(0) }
    var lang by rememberSaveable { mutableStateOf(currentLang()) }
    var name by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableIntStateOf(-1) }
    var daysCsv by rememberSaveable { mutableStateOf("1,3,5") }
    var lb by rememberSaveable { mutableStateOf(false) }
    var starter by rememberSaveable { mutableStateOf(true) }
    var finishing by remember { mutableStateOf(false) }

    val days = daysCsv.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    val step = steps[stepIdx]
    var forward by remember { mutableStateOf(true) }

    fun go(to: Int) { forward = to >= stepIdx; stepIdx = to.coerceIn(0, steps.lastIndex); buzz(ctx, 10) }

    fun finish() {
        if (finishing) return
        finishing = true
        scope.launch {
            if (starter) app.repo.seedStarterPlan(lang, ctx.getString(R.string.starter_a), ctx.getString(R.string.starter_b))
            else app.repo.seedExercises(lang)
            app.settings.update {
                this.name = name.trim(); this.goal = goal.ifBlank { "consistency" }
                experience = level.coerceAtLeast(0); weeklyGoal = days.size.coerceAtLeast(1)
                trainingDays = days; useLb = lb
                onboarded = true
            }
            onDone()
        }
    }

    val canContinue = when (step) {
        Step.NAME -> name.isNotBlank()
        Step.GOAL -> goal.isNotBlank()
        Step.LEVEL -> level >= 0
        Step.DAYS -> days.isNotEmpty()
        else -> true
    }

    Column(Modifier.fillMaxSize().backdrop(Field.EMBER).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            val canBack = stepIdx > 0 && step != Step.FINALE
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).pressable({ go(stepIdx - 1) }, canBack), contentAlignment = Alignment.CenterStart) {
                if (canBack) Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = TC.Dim)
            }
            Spacer(Modifier.weight(1f))
            Text("%02d / %02d".format(stepIdx + 1, steps.size), fontFamily = Mono, fontSize = 12.sp, color = TC.Dim, letterSpacing = 1.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            steps.indices.forEach { i ->
                Box(Modifier.weight(1f).height(2.dp).background(if (i <= stepIdx) TC.Accent else TC.Outline))
            }
        }

        AnimatedContent(
            targetState = stepIdx, modifier = Modifier.weight(1f).fillMaxWidth(), label = "step",
            transitionSpec = {
                (slideInHorizontally(tween(260)) { if (forward) it / 8 else -it / 8 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(180)) { if (forward) -it / 8 else it / 8 } + fadeOut(tween(140)))
            },
        ) { idx ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 32.dp, bottom = 12.dp)) {
                when (steps[idx]) {
                    Step.LANG -> LangStep(lang) { l ->
                        lang = l
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(l))
                    }
                    Step.NAME -> NameStep(name) { name = it.take(24) }
                    Step.GOAL -> GoalStep(goal) { goal = it; buzz(ctx, 10) }
                    Step.LEVEL -> LevelStep(level) { level = it; buzz(ctx, 10) }
                    Step.DAYS -> DaysStep(days) { d ->
                        daysCsv = (if (d in days) days - d else days + d).sorted().joinToString(","); buzz(ctx, 10)
                    }
                    Step.UNITS -> UnitsStep(lb) { lb = it; buzz(ctx, 10) }
                    Step.FINALE -> FinaleStep(name, starter) { starter = it }
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
            PrimaryButton(
                stringResource(if (step == Step.FINALE) R.string.wiz_lets_go else R.string.wiz_continue),
                modifier = Modifier.fillMaxWidth(), enabled = canContinue && !finishing,
            ) { if (step == Step.FINALE) finish() else go(stepIdx + 1) }
        }
    }
}

// ---------------- step bodies ----------------

@Composable
private fun LangStep(lang: String, onPick: (String) -> Unit) {
    PlinthMark(34.dp)
    Spacer(Modifier.height(18.dp))
    Text(stringResource(R.string.app_name).uppercase(), fontSize = 14.sp, letterSpacing = 5.sp, fontWeight = FontWeight.SemiBold, color = TC.Dim)
    Spacer(Modifier.height(36.dp))
    Text(stringResource(R.string.wiz_tagline), style = MaterialTheme.typography.displayMedium, color = TC.Text)
    Spacer(Modifier.height(44.dp))
    Text(stringResource(R.string.wiz_pick_language).uppercase(), style = MaterialTheme.typography.labelMedium, color = TC.Dim)
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("en" to "English", "it" to "Italiano").forEach { (tag, label) ->
            val sel = lang == tag
            TCard(Modifier.fillMaxWidth(), onClick = { onPick(tag) }, color = if (sel) TC.CardHi else TC.Card, border = if (sel) TC.Accent else TC.Outline, radius = 12.dp, padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = TC.Text, modifier = Modifier.weight(1f))
                    if (sel) Icon(Icons.Rounded.Check, null, tint = TC.Accent, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ---------------- step bodies ----------------

@Composable
private fun StepTitle(title: String, sub: String? = null) {
    Text(title, style = MaterialTheme.typography.displayMedium, color = TC.Text)
    if (sub != null) Text(sub, style = MaterialTheme.typography.bodyLarge, color = TC.Dim, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun NameStep(name: String, onChange: (String) -> Unit) {
    StepTitle(stringResource(R.string.wiz_name_title), stringResource(R.string.wiz_name_sub))
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { delay(450); runCatching { focus.requestFocus() } }
    BasicTextField(
        value = name, onValueChange = onChange, singleLine = true, cursorBrush = SolidColor(TC.Accent),
        textStyle = TextStyle(color = TC.Accent, fontSize = 30.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp),
        keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        decorationBox = { inner ->
            Column {
                Box { if (name.isEmpty()) Text(stringResource(R.string.wiz_name_hint), color = TC.Faint, fontSize = 30.sp, fontWeight = FontWeight.Medium); inner() }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(if (name.isEmpty()) TC.Outline else TC.Accent))
            }
        },
    )
}

@Composable
private fun GoalStep(goal: String, onPick: (String) -> Unit) {
    StepTitle(stringResource(R.string.wiz_goal_title), stringResource(R.string.wiz_goal_sub))
    val items = listOf("strength" to R.string.goal_strength, "muscle" to R.string.goal_muscle, "endurance" to R.string.goal_endurance, "consistency" to R.string.goal_consistency)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (key, label) ->
            val sel = goal == key
            TCard(Modifier.fillMaxWidth(), onClick = { onPick(key) }, color = if (sel) TC.CardHi else TC.Card, border = if (sel) TC.Accent else TC.Outline, radius = 12.dp, padding = PaddingValues(horizontal = 18.dp, vertical = 17.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(label), fontWeight = FontWeight.Medium, fontSize = 17.sp, color = TC.Text, modifier = Modifier.weight(1f))
                    if (sel) Icon(Icons.Rounded.Check, null, tint = TC.Accent, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun LevelStep(level: Int, onPick: (Int) -> Unit) {
    StepTitle(stringResource(R.string.wiz_level_title), stringResource(R.string.wiz_level_sub))
    val labels = listOf(R.string.lvl_beginner, R.string.lvl_intermediate, R.string.lvl_advanced, R.string.lvl_veteran)
    val subs = listOf(R.string.lvl_beginner_sub, R.string.lvl_intermediate_sub, R.string.lvl_advanced_sub, R.string.lvl_veteran_sub)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        labels.forEachIndexed { i, l ->
            val sel = level == i
            TCard(Modifier.fillMaxWidth(), onClick = { onPick(i) }, color = if (sel) TC.CardHi else TC.Card, border = if (sel) TC.Accent else TC.Outline, radius = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                        repeat(4) { t ->
                            Box(Modifier.size(width = 5.dp, height = (8 + t * 4).dp).background(if (t <= i) TC.Accent else TC.Outline))
                        }
                    }
                    Spacer(Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(l), fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = TC.Text)
                        Text(stringResource(subs[i]), color = TC.Dim, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DaysStep(days: Set<Int>, onToggle: (Int) -> Unit) {
    StepTitle(stringResource(R.string.wiz_days_title), stringResource(R.string.wiz_days_sub))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (1..7).forEach { d ->
            val on = d in days
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(if (on) TC.Accent else TC.Card)
                    .border(1.dp, if (on) TC.Accent else TC.Outline, RoundedCornerShape(10.dp)).pressable({ onToggle(d) }),
                contentAlignment = Alignment.Center,
            ) { Text(weekdayNarrow(d), fontWeight = FontWeight.SemiBold, color = if (on) Color.Black else TC.Dim) }
        }
    }
    Spacer(Modifier.height(36.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text("${days.size}", fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 64.sp, color = TC.Text, lineHeight = 64.sp)
        Text(stringResource(R.string.wiz_days_per_week), color = TC.Dim, fontWeight = FontWeight.Medium, fontSize = 16.sp, modifier = Modifier.padding(start = 10.dp, bottom = 10.dp))
    }
    val msg = when { days.size <= 2 -> R.string.wiz_days_msg_light; days.size <= 4 -> R.string.wiz_days_msg_solid; else -> R.string.wiz_days_msg_hard }
    Text(stringResource(msg), color = TC.Dim, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun UnitsStep(lb: Boolean, onPick: (Boolean) -> Unit) {
    StepTitle(stringResource(R.string.wiz_units_title), stringResource(R.string.wiz_units_sub))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(false to "kg", true to "lb").forEach { (isLb, label) ->
            val sel = lb == isLb
            TCard(Modifier.weight(1f), onClick = { onPick(isLb) }, color = if (sel) TC.Accent else TC.Card, border = if (sel) TC.Accent else TC.Outline) {
                Text(label, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, color = if (sel) Color.Black else TC.Text)
            }
        }
    }
    Spacer(Modifier.height(22.dp))
    TCard(Modifier.fillMaxWidth()) {
        Column {
            Text(stringResource(R.string.wiz_units_example).uppercase(), style = MaterialTheme.typography.labelSmall, color = TC.Dim)
            Text(if (lb) "185 lb × 8" else "85 kg × 8", fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, color = TC.Text, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun FinaleStep(name: String, starter: Boolean, onStarter: (Boolean) -> Unit) {
    PlinthMark(34.dp)
    Spacer(Modifier.height(28.dp))
    Text(if (name.isBlank()) stringResource(R.string.wiz_finale_title_anon) else stringResource(R.string.wiz_finale_title, name),
        style = MaterialTheme.typography.displayMedium, color = TC.Text)
    Text(stringResource(R.string.wiz_finale_sub), style = MaterialTheme.typography.bodyLarge, color = TC.Dim, modifier = Modifier.padding(top = 12.dp))
    Spacer(Modifier.height(32.dp))
    TCard(Modifier.fillMaxWidth(), onClick = { onStarter(!starter) }, color = TC.Card, border = if (starter) TC.Accent else TC.Outline, radius = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.wiz_starter_title), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TC.Text)
                Text(stringResource(R.string.wiz_starter_sub), color = TC.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(if (starter) TC.Accent else TC.Outline), contentAlignment = Alignment.Center) {
                if (starter) Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
            }
        }
    }
}
