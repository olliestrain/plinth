package app.rowan.gym.ui.onboarding

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import app.rowan.gym.LocalApp
import app.rowan.gym.R
import app.rowan.gym.net.HubResult
import app.rowan.gym.ui.buzz
import app.rowan.gym.ui.buzzPattern
import app.rowan.gym.ui.components.ConfettiBurst
import app.rowan.gym.ui.components.GhostButton
import app.rowan.gym.ui.components.GridBackground
import app.rowan.gym.ui.components.PrimaryButton
import app.rowan.gym.ui.components.TCard
import app.rowan.gym.ui.components.RowanLogo
import app.rowan.gym.ui.components.pressable
import app.rowan.gym.ui.currentLang
import app.rowan.gym.ui.theme.Mono
import app.rowan.gym.ui.theme.TC
import app.rowan.gym.ui.weekdayNarrow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Step { LANG, NAME, GOAL, LEVEL, DAYS, UNITS, ACCOUNT, VERIFY, FINALE }

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

@Composable
fun Wizard(onDone: () -> Unit) {
    val app = LocalApp.current
    val hub = app.hubManager
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val steps = Step.entries

    var stepIdx by rememberSaveable { mutableIntStateOf(0) }
    var prevIdx by rememberSaveable { mutableIntStateOf(0) }
    var lang by rememberSaveable { mutableStateOf(currentLang()) }
    var name by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableIntStateOf(-1) }
    var daysCsv by rememberSaveable { mutableStateOf("1,3,5") }
    var lb by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var loginMode by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var starter by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var pulse by remember { mutableIntStateOf(0) }
    var origin by remember { mutableStateOf(Offset(300f, 600f)) }
    var showForgot by remember { mutableStateOf(false) }
    var verifiedAccount by rememberSaveable { mutableStateOf(false) }

    val days = daysCsv.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    val step = steps[stepIdx]
    val reachable by hub.reachable.collectAsState()

    fun go(to: Int) { prevIdx = stepIdx; stepIdx = to.coerceIn(0, steps.lastIndex); error = null; pulse++ ; buzz(ctx, 12) }

    fun finish() {
        scope.launch {
            val tag = lang
            if (starter) app.repo.seedStarterPlan(tag, ctx.getString(R.string.starter_a), ctx.getString(R.string.starter_b))
            else app.repo.seedExercises(tag)
            app.settings.update {
                this.name = name.trim(); this.goal = goal.ifBlank { "consistency" }
                experience = level.coerceAtLeast(0); weeklyGoal = days.size.coerceAtLeast(1)
                trainingDays = days; useLb = lb
                onboarded = true
            }
            onDone()
        }
    }

    fun submitAccount() {
        if (busy) return
        val mail = email.trim()
        error = when {
            !EMAIL.matches(mail) -> R.string.err_invalid_email
            password.length < 8 -> R.string.err_invalid_password
            else -> null
        }
        if (error != null) return
        busy = true
        scope.launch {
            if (loginMode) {
                val (r, ok) = hub.login(mail, password, lang)
                if (ok != null) {
                    if (name.isBlank()) name = ok.name
                    verifiedAccount = true; go(Step.FINALE.ordinal)
                } else if (r is HubResult.Err && r.code == "unverified") { go(Step.VERIFY.ordinal) }
                else error = authError(r)
            } else {
                val r = hub.register(mail, password, name.trim(), lang)
                if (r is HubResult.Ok) go(Step.VERIFY.ordinal) else error = authError(r)
            }
            busy = false
        }
    }

    fun submitCode() {
        if (busy || code.length != 6) return
        busy = true
        scope.launch {
            val (r, ok) = hub.verify(email.trim(), code)
            if (ok != null) { verifiedAccount = true; go(Step.FINALE.ordinal) }
            else { error = authError(r); code = ""; buzz(ctx, 60, true) }
            busy = false
        }
    }

    LaunchedEffect(step) {
        if (step == Step.ACCOUNT) while (true) { hub.refresh(); delay(4000) }
    }
    LaunchedEffect(code) { if (code.length == 6 && step == Step.VERIFY) submitCode() }

    val canContinue = when (step) {
        Step.LANG -> true
        Step.NAME -> name.isNotBlank()
        Step.GOAL -> goal.isNotBlank()
        Step.LEVEL -> level >= 0
        Step.DAYS -> days.isNotEmpty()
        Step.UNITS -> true
        Step.ACCOUNT -> EMAIL.matches(email.trim()) && password.length >= 8 && !busy
        Step.VERIFY -> code.length == 6 && !busy
        Step.FINALE -> true
    }

    Box(
        Modifier.fillMaxSize().background(TC.Bg).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val c = e.changes.firstOrNull()
                    if (c != null && c.pressed && !c.previousPressed) { origin = c.position; pulse++ }
                }
            }
        },
    ) {
        GridBackground(progress = stepIdx / steps.lastIndex.toFloat() * 0.9f + 0.05f, pulse = pulse, origin = origin)

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 24.dp)) {
            // top bar: back + step progress
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                val canBack = stepIdx > 0 && step != Step.FINALE && !(step == Step.VERIFY && false)
                Box(Modifier.size(44.dp).clip(CircleShape).background(if (canBack) TC.Card.copy(alpha = 0.8f) else Color.Transparent)
                    .pressable({ go(if (step == Step.VERIFY) Step.ACCOUNT.ordinal else stepIdx - 1) }, canBack), contentAlignment = Alignment.Center) {
                    if (canBack) Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = TC.Text)
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    steps.forEachIndexed { i, _ ->
                        val on = i <= stepIdx
                        Box(Modifier.size(if (i == stepIdx) 18.dp else 9.dp, 9.dp).clip(RoundedCornerShape(3.dp))
                            .background(if (on) TC.Lime else TC.CardHi))
                    }
                }
                Spacer(Modifier.weight(1f)); Spacer(Modifier.size(44.dp))
            }

            AnimatedContent(
                targetState = stepIdx, modifier = Modifier.weight(1f).fillMaxWidth(), label = "step",
                transitionSpec = {
                    val fwd = targetState >= initialState
                    (slideInHorizontally(tween(420)) { if (fwd) it / 3 else -it / 3 } + fadeIn(tween(420))) togetherWith
                        (slideOutHorizontally(tween(300)) { if (fwd) -it / 3 else it / 3 } + fadeOut(tween(220)))
                },
            ) { idx ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 20.dp, bottom = 12.dp)) {
                    when (steps[idx]) {
                        Step.LANG -> LangStep(lang) { l ->
                            lang = l
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(l))
                        }
                        Step.NAME -> NameStep(name) { name = it.take(24) }
                        Step.GOAL -> GoalStep(goal) { goal = it; buzz(ctx, 15) }
                        Step.LEVEL -> LevelStep(level) { level = it; buzz(ctx, 15) }
                        Step.DAYS -> DaysStep(days) { d ->
                            daysCsv = (if (d in days) days - d else days + d).sorted().joinToString(","); buzz(ctx, 12)
                        }
                        Step.UNITS -> UnitsStep(lb) { lb = it; buzz(ctx, 12) }
                        Step.ACCOUNT -> AccountStep(
                            loginMode, { loginMode = it; error = null }, email, { email = it; error = null }, password, { password = it; error = null },
                            reachable, error, onForgot = { showForgot = true },
                        )
                        Step.VERIFY -> VerifyStep(email.trim(), code, { code = it.filter(Char::isDigit).take(6); error = null }, error,
                            onResend = { scope.launch { hub.resend(email.trim(), lang) } })
                        Step.FINALE -> FinaleStep(name, starter, { starter = it }, verifiedAccount)
                    }
                }
            }

            // bottom actions
            Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val label = when (step) {
                    Step.ACCOUNT -> stringResource(if (loginMode) R.string.wiz_login else R.string.wiz_create_account)
                    Step.VERIFY -> stringResource(R.string.wiz_verify)
                    Step.FINALE -> stringResource(R.string.wiz_lets_go)
                    else -> stringResource(R.string.wiz_continue)
                }
                PrimaryButton(label, modifier = Modifier.fillMaxWidth(), enabled = canContinue) {
                    when (step) {
                        Step.ACCOUNT -> submitAccount()
                        Step.VERIFY -> submitCode()
                        Step.FINALE -> finish()
                        else -> go(stepIdx + 1)
                    }
                }
                if (step == Step.ACCOUNT) GhostButton(stringResource(R.string.wiz_offline), { go(Step.FINALE.ordinal) }, Modifier.fillMaxWidth())
            }
        }
        if (showForgot) ForgotDialog(email, lang, onDismiss = { showForgot = false }, onDone = { verifiedAccount = true; showForgot = false; go(Step.FINALE.ordinal) })
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
private fun LangStep(lang: String, onPick: (String) -> Unit) {
    val lit = remember { Animatable(0f) }
    LaunchedEffect(Unit) { lit.animateTo(9f, tween(1600)) }
    RowanLogo(84.dp, lit.value)
    Spacer(Modifier.height(18.dp))
    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayLarge, color = TC.Text)
    Text(stringResource(R.string.wiz_tagline), style = MaterialTheme.typography.bodyLarge, color = TC.Dim, modifier = Modifier.padding(top = 8.dp))
    Spacer(Modifier.height(36.dp))
    Text(stringResource(R.string.wiz_pick_language).uppercase(), style = MaterialTheme.typography.labelMedium, color = TC.Dim)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("en" to "English", "it" to "Italiano").forEach { (tag, label) ->
            val sel = lang == tag
            TCard(Modifier.weight(1f), onClick = { onPick(tag) }, color = if (sel) TC.Lime else TC.Card, border = if (sel) TC.Lime else TC.Outline) {
                Column {
                    Text(tag.uppercase(), fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 30.sp, color = if (sel) Color.Black else TC.Text)
                    Text(label, fontWeight = FontWeight.Bold, color = if (sel) Color.Black.copy(alpha = 0.7f) else TC.Dim)
                }
            }
        }
    }
}

@Composable
private fun NameStep(name: String, onChange: (String) -> Unit) {
    StepTitle(stringResource(R.string.wiz_name_title), stringResource(R.string.wiz_name_sub))
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { delay(450); runCatching { focus.requestFocus() } }
    BasicTextField(
        value = name, onValueChange = onChange, singleLine = true, cursorBrush = SolidColor(TC.Lime),
        textStyle = TextStyle(color = TC.Lime, fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
        keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        decorationBox = { inner ->
            Column {
                Box { if (name.isEmpty()) Text(stringResource(R.string.wiz_name_hint), color = TC.Faint, fontSize = 40.sp, fontWeight = FontWeight.Black); inner() }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(if (name.isEmpty()) TC.Outline else TC.Lime))
            }
        },
    )
}

@Composable
private fun GoalStep(goal: String, onPick: (String) -> Unit) {
    StepTitle(stringResource(R.string.wiz_goal_title), stringResource(R.string.wiz_goal_sub))
    val items = listOf(
        Triple("strength", Icons.Rounded.FitnessCenter, R.string.goal_strength),
        Triple("muscle", Icons.Rounded.Bolt, R.string.goal_muscle),
        Triple("endurance", Icons.Rounded.Timer, R.string.goal_endurance),
        Triple("consistency", Icons.Rounded.LocalFireDepartment, R.string.goal_consistency),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (key, icon, label) ->
            val sel = goal == key
            TCard(Modifier.fillMaxWidth(), onClick = { onPick(key) }, color = if (sel) TC.Lime else TC.Card, border = if (sel) TC.Lime else TC.Outline, radius = 24.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = if (sel) Color.Black else TC.Lime, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.size(14.dp))
                    Text(stringResource(label), fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = if (sel) Color.Black else TC.Text, modifier = Modifier.weight(1f))
                    if (sel) Icon(Icons.Rounded.Check, null, tint = Color.Black)
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
            TCard(Modifier.fillMaxWidth(), onClick = { onPick(i) }, color = if (sel) TC.CardHi else TC.Card, border = if (sel) TC.Lime else TC.Outline, radius = 24.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 4 squares fill up like a battery
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(4) { t ->
                            Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(if (t <= i) TC.Lime.copy(alpha = if (sel) 1f else 0.55f) else TC.Outline))
                        }
                    }
                    Spacer(Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(l), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = TC.Text)
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
                Modifier.size(42.dp).clip(CircleShape).background(if (on) TC.Lime else TC.Card)
                    .border(1.dp, if (on) TC.Lime else TC.Outline, CircleShape).pressable({ onToggle(d) }),
                contentAlignment = Alignment.Center,
            ) { Text(weekdayNarrow(d), fontWeight = FontWeight.Black, color = if (on) Color.Black else TC.Dim) }
        }
    }
    Spacer(Modifier.height(36.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text("${days.size}", fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 96.sp, color = TC.Lime, lineHeight = 90.sp)
        Text(stringResource(R.string.wiz_days_per_week), color = TC.Dim, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(start = 10.dp, bottom = 14.dp))
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
            TCard(Modifier.weight(1f), onClick = { onPick(isLb) }, color = if (sel) TC.Lime else TC.Card, border = if (sel) TC.Lime else TC.Outline) {
                Text(label, fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 44.sp, color = if (sel) Color.Black else TC.Text)
            }
        }
    }
    Spacer(Modifier.height(22.dp))
    TCard(Modifier.fillMaxWidth()) {
        Column {
            Text(stringResource(R.string.wiz_units_example).uppercase(), style = MaterialTheme.typography.labelSmall, color = TC.Dim)
            Text(if (lb) "185 lb × 8" else "85 kg × 8", fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 30.sp, color = TC.Text, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun AccountStep(
    loginMode: Boolean, onMode: (Boolean) -> Unit, email: String, onEmail: (String) -> Unit, password: String, onPassword: (String) -> Unit,
    reachable: Boolean?, error: Int?, onForgot: () -> Unit,
) {
    StepTitle(stringResource(if (loginMode) R.string.wiz_login_title else R.string.wiz_account_title), stringResource(R.string.wiz_account_sub))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(TC.Card).padding(4.dp)) {
        listOf(false to R.string.wiz_tab_register, true to R.string.wiz_tab_login).forEach { (m, l) ->
            Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (loginMode == m) TC.CardHi else Color.Transparent)
                .pressable({ onMode(m) }).padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(l), fontWeight = FontWeight.Bold, color = if (loginMode == m) TC.Lime else TC.Dim)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    HubStatusChip(reachable)
    Spacer(Modifier.height(16.dp))
    AuthField(email, onEmail, stringResource(R.string.field_email), KeyboardType.Email)
    Spacer(Modifier.height(10.dp))
    AuthField(password, onPassword, stringResource(R.string.field_password), KeyboardType.Password, secret = true)
    if (error != null) Text(stringResource(error), color = TC.Danger, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
    if (loginMode) Text(stringResource(R.string.forgot_password), color = TC.Lime, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp).pressable(onForgot))
}

@Composable
fun HubStatusChip(reachable: Boolean?) {
    val (color, text) = when (reachable) {
        true -> TC.Good to R.string.hub_found
        false -> TC.Danger to R.string.hub_away
        null -> TC.Ember to R.string.hub_searching
    }
    Row(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
        .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(stringResource(text), color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun AuthField(value: String, onChange: (String) -> Unit, hint: String, type: KeyboardType, secret: Boolean = false) {
    var show by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true, cursorBrush = SolidColor(TC.Lime),
        textStyle = TextStyle(color = TC.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        keyboardOptions = KeyboardOptions(keyboardType = type, autoCorrectEnabled = false),
        visualTransformation = if (secret && !show) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(Modifier.fillMaxWidth().clip(shape).background(TC.Card).border(1.dp, TC.Outline, shape).padding(horizontal = 18.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { if (value.isEmpty()) Text(hint, color = TC.Faint, fontSize = 17.sp, fontWeight = FontWeight.SemiBold); inner() }
                if (secret) Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = TC.Dim, modifier = Modifier.size(22.dp).pressable({ show = !show }))
            }
        },
    )
}

@Composable
private fun VerifyStep(email: String, code: String, onCode: (String) -> Unit, error: Int?, onResend: () -> Unit) {
    StepTitle(stringResource(R.string.wiz_verify_title), stringResource(R.string.wiz_verify_sub, email))
    Box {
        BasicTextField(
            value = code, onValueChange = onCode, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = TextStyle(color = Color.Transparent), cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier.fillMaxWidth().height(70.dp).alpha(0.02f),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(6) { i ->
                val ch = code.getOrNull(i)?.toString() ?: ""
                val active = i == code.length
                Box(
                    Modifier.weight(1f).height(70.dp).clip(RoundedCornerShape(18.dp)).background(if (ch.isNotEmpty()) TC.CardHi else TC.Card)
                        .border(if (active) 2.dp else 1.dp, if (active) TC.Lime else TC.Outline, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text(ch, fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 30.sp, color = TC.Lime) }
            }
        }
    }
    if (error != null) Text(stringResource(error), color = TC.Danger, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
    var left by remember { mutableIntStateOf(30) }
    LaunchedEffect(left) { if (left > 0) { delay(1000); left-- } }
    Spacer(Modifier.height(18.dp))
    if (left > 0) Text(stringResource(R.string.wiz_resend_in, left), color = TC.Dim)
    else Text(stringResource(R.string.wiz_resend), color = TC.Lime, fontWeight = FontWeight.Bold, modifier = Modifier.pressable({ onResend(); left = 30 }))
}

@Composable
private fun FinaleStep(name: String, starter: Boolean, onStarter: (Boolean) -> Unit, account: Boolean) {
    val ctx = LocalContext.current
    val lit = remember { Animatable(0f) }
    var confetti by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        lit.animateTo(9f, tween(1900))
        confetti++
        buzzPattern(ctx, longArrayOf(0, 40, 60, 40, 60, 120))
    }
    Box {
        Column(horizontalAlignment = Alignment.Start) {
            RowanLogo(120.dp, lit.value)
            Spacer(Modifier.height(24.dp))
            Text(if (name.isBlank()) stringResource(R.string.wiz_finale_title_anon) else stringResource(R.string.wiz_finale_title, name),
                style = MaterialTheme.typography.displayMedium, color = TC.Text)
            Text(stringResource(if (account) R.string.wiz_finale_sub_account else R.string.wiz_finale_sub_offline),
                style = MaterialTheme.typography.bodyLarge, color = TC.Dim, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(26.dp))
            TCard(Modifier.fillMaxWidth(), onClick = { onStarter(!starter) }, color = if (starter) TC.CardHi else TC.Card, border = if (starter) TC.Lime else TC.Outline) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.wiz_starter_title), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = TC.Text)
                        Text(stringResource(R.string.wiz_starter_sub), color = TC.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(if (starter) TC.Lime else TC.Outline), contentAlignment = Alignment.Center) {
                        if (starter) Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        ConfettiBurst(confetti, Modifier.fillMaxSize())
    }
}

// ---------------- helpers ----------------

fun authError(r: HubResult): Int = when (r) {
    is HubResult.Unreachable -> R.string.err_unreachable
    is HubResult.Err -> when (r.code) {
        "email_taken" -> R.string.err_email_taken
        "invalid_email" -> R.string.err_invalid_email
        "invalid_password" -> R.string.err_invalid_password
        "invalid_credentials" -> R.string.err_invalid_credentials
        "invalid_code" -> R.string.err_invalid_code
        "code_expired" -> R.string.err_code_expired
        "too_many_attempts", "rate_limited" -> R.string.err_rate_limited
        "mail_failed" -> R.string.err_mail_failed
        else -> R.string.err_generic
    }
    else -> R.string.err_generic
}
