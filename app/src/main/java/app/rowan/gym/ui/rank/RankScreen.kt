package app.rowan.gym.ui.rank

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.rowan.gym.LocalApp
import app.rowan.gym.R
import app.rowan.gym.data.Achievements
import app.rowan.gym.data.Prefs
import app.rowan.gym.data.Stats
import app.rowan.gym.net.BoardEntry
import app.rowan.gym.net.HubResult
import app.rowan.gym.net.SyncOutcome
import app.rowan.gym.ui.Actions
import app.rowan.gym.ui.buzz
import app.rowan.gym.ui.buzzPattern
import app.rowan.gym.ui.components.*
import app.rowan.gym.ui.fmtBig
import app.rowan.gym.ui.onboarding.AccountDialog
import app.rowan.gym.ui.theme.Mono
import app.rowan.gym.ui.theme.TC
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sin

@Composable
fun RankScreen(prefs: Prefs, actions: Actions) {
    val app = LocalApp.current
    val hub = app.hubManager
    val scope = rememberCoroutineScope()
    val sessions by app.repo.sessions.collectAsState()
    val plans by app.repo.dayPlans.collectAsState()
    val reachable by hub.reachable.collectAsState()
    val liveBoard by hub.board.collectAsState()
    val summary = remember(sessions, plans, prefs.weeklyGoal) { Stats.summarize(sessions, plans.values.toList(), prefs.weeklyGoal) }
    val titles = stringArrayResource(R.array.level_titles)
    var period by rememberSaveable { mutableStateOf("all") }
    var account by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<SyncOutcome?>(null) }
    val board = if (liveBoard.isNotEmpty()) liveBoard else hub.cachedBoard(prefs.boardCache)
    val online = reachable == true && prefs.loggedIn

    LaunchedEffect(Unit) { while (true) { hub.refresh(); delay(6000) } }
    LaunchedEffect(online, period) { if (online) hub.loadBoard(period) }

    ScreenColumn {
        Text(stringResource(R.string.tab_rank), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp))

        // ---- level hero ----
        TCard(Modifier.fillMaxWidth(), radius = 36.dp, color = TC.CardHi) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(springFloat(Stats.levelProgress(summary.xp)), 110.dp, 11.dp, track = TC.Outline) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.rank_level).uppercase(), style = MaterialTheme.typography.labelSmall, color = TC.Dim)
                        Text("${summary.level}", fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 38.sp, color = TC.Lime, lineHeight = 38.sp)
                    }
                }
                Column(Modifier.padding(start = 18.dp)) {
                    Text(titles[(summary.level - 1).coerceAtMost(titles.lastIndex)], fontWeight = FontWeight.Black, fontSize = 24.sp)
                    BigNumber("${summary.xp}", "XP", size = 30, color = TC.Lime)
                    Text(stringResource(R.string.xp_to_next, Stats.levelFloorXp(summary.level + 1) - summary.xp, summary.level + 1), color = TC.Dim, fontSize = 13.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat(Modifier.weight(1f), Icons.Rounded.FitnessCenter, "${summary.workouts}", stringResource(R.string.rank_workouts))
            MiniStat(Modifier.weight(1f), Icons.Rounded.LocalFireDepartment, "${summary.streak}", stringResource(R.string.rank_streak), TC.Ember)
            MiniStat(Modifier.weight(1f), Icons.Rounded.EmojiEvents, "${summary.prs}", stringResource(R.string.pg_prs), TC.Lime)
        }

        // ---- leaderboard ----
        SectionTitle(stringResource(R.string.rank_board))
        when {
            !prefs.loggedIn -> TCard(Modifier.fillMaxWidth(), radius = 32.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Leaderboard, null, tint = TC.Lime, modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.rank_join_title), fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.padding(top = 10.dp))
                    Text(stringResource(R.string.rank_join_sub), color = TC.Dim, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                    PrimaryButton(stringResource(R.string.rank_join_cta), { account = true }, Modifier.fillMaxWidth())
                }
            }
            online -> TCard(Modifier.fillMaxWidth(), radius = 32.dp, color = TC.Lime.copy(alpha = 0.10f), border = TC.Lime.copy(alpha = 0.4f)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(TC.Good))
                        Text(stringResource(R.string.rank_home), color = TC.Good, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    }
                    val pending = summary.xp - prefs.lastSyncXp
                    Text(
                        if (prefs.lastSync == 0L) stringResource(R.string.rank_never_synced)
                        else if (pending > 0) stringResource(R.string.rank_pending, pending) else stringResource(R.string.rank_synced),
                        fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
                    )
                    PrimaryButton(stringResource(R.string.rank_sync), { syncing = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.CloudUpload, enabled = summary.workouts > 0)
                }
            }
            else -> TCard(Modifier.fillMaxWidth(), radius = 32.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WifiOff, null, tint = TC.Ember, modifier = Modifier.size(32.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(stringResource(R.string.rank_away_title), fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(stringResource(R.string.rank_away_sub), color = TC.Dim, fontSize = 13.sp)
                    }
                    IconPill(Icons.Rounded.Refresh, { scope.launch { hub.refresh() } })
                }
            }
        }
        if (prefs.loggedIn) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TChip(stringResource(R.string.rank_all_time), period == "all", { period = "all" }, color = TC.Sky)
                TChip(stringResource(R.string.rank_this_week), period == "week", { period = "week" }, color = TC.Sky)
            }
            if (board.isEmpty()) Text(stringResource(R.string.rank_board_empty), color = TC.Dim, modifier = Modifier.padding(6.dp))
            Column(Modifier.alpha(if (online) 1f else 0.55f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                board.forEach { BoardRow(it) }
            }
        }

        // ---- achievements ----
        SectionTitle(stringResource(R.string.rank_achievements))
        val ctx = LocalContext.current
        val ach = remember(summary) { Achievements.evaluate(summary) }
        ach.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { a ->
                    val name = ctx.getString(ctx.resources.getIdentifier("ach_${a.key}", "string", ctx.packageName))
                    val desc = ctx.getString(ctx.resources.getIdentifier("ach_${a.key}_desc", "string", ctx.packageName))
                    TCard(Modifier.weight(1f).fillMaxHeight(), color = if (a.unlocked) TC.CardHi else TC.Card, border = if (a.unlocked) TC.Lime.copy(alpha = 0.6f) else TC.Outline.copy(alpha = 0.6f), radius = 22.dp, padding = PaddingValues(14.dp)) {
                        Column {
                            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(if (a.unlocked) TC.Lime else TC.Outline), contentAlignment = Alignment.Center) {
                                Icon(if (a.unlocked) Icons.Rounded.Check else Icons.Rounded.Lock, null, tint = if (a.unlocked) Color.Black else TC.Faint, modifier = Modifier.size(20.dp))
                            }
                            Text(name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp), color = if (a.unlocked) TC.Text else TC.Dim)
                            Text(desc, color = TC.Dim, fontSize = 12.sp, lineHeight = 15.sp)
                            if (!a.unlocked) Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(TC.Outline)) {
                                Box(Modifier.fillMaxWidth(a.progress).height(4.dp).clip(CircleShape).background(TC.Lime.copy(alpha = 0.7f)))
                            }
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    if (account) AccountDialog { account = false }
    if (syncing) SyncDialog(prefs, summary, onFinished = { syncing = false; scope.launch { hub.loadBoard(period) } })
}

@Composable
private fun MiniStat(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, tint: Color = TC.Text) {
    TCard(modifier, radius = 22.dp, padding = PaddingValues(14.dp)) {
        Column {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Text(value, fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 26.sp, modifier = Modifier.padding(top = 6.dp))
            Text(label, color = TC.Dim, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun BoardRow(e: BoardEntry) {
    val medal = when (e.rank) { 1 -> TC.Lime; 2 -> Color(0xFFD0D0DA); 3 -> TC.Ember; else -> null }
    TCard(Modifier.fillMaxWidth(), color = if (e.me) TC.Lime.copy(alpha = 0.14f) else TC.Card, border = if (e.me) TC.Lime else TC.Outline.copy(alpha = 0.6f), radius = 20.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(medal ?: TC.CardHi), contentAlignment = Alignment.Center) {
                Text("${e.rank}", fontFamily = Mono, fontWeight = FontWeight.Black, color = if (medal != null) Color.Black else TC.Dim)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(e.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1)
                Text(stringResource(R.string.rank_row_sub, e.level, e.streakWeeks), color = TC.Dim, fontSize = 12.sp)
            }
            Text(fmtBig(e.xp.toDouble()), fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 18.sp, color = if (e.me) TC.Lime else TC.Text)
            Text(" XP", color = TC.Dim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * The reward moment: your progress streams from the phone into the Rowan hub, XP counts up, then your rank slams in.
 * Only reachable on the home network, which is the whole point.
 */
@Composable
private fun SyncDialog(prefs: Prefs, summary: Stats.Summary, onFinished: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    var phase by remember { mutableIntStateOf(0) } // 0 connecting, 1 uploading, 2 done, 3 error
    var errorRes by remember { mutableIntStateOf(R.string.err_generic) }
    var result by remember { mutableStateOf<SyncOutcome?>(null) }
    val stream = remember { Animatable(0f) }
    val xpShown = remember { Animatable(prefs.lastSyncXp.toFloat()) }
    val rankPop = remember { Animatable(0f) }
    var confetti by remember { mutableIntStateOf(0) }
    val prevRank = prefs.lastRank

    LaunchedEffect(Unit) {
        val call = async { app.hubManager.sync(summary, prefs.name.ifBlank { prefs.email.substringBefore('@') }) }
        delay(1100)
        val (r, o) = call.await()
        if (o == null) {
            errorRes = if (r is HubResult.Unreachable) R.string.err_unreachable else R.string.err_generic
            phase = 3; buzz(ctx, 80, true); return@LaunchedEffect
        }
        result = o; phase = 1
        val tick = launch { while (true) { buzz(ctx, 8); delay(90) } }
        val a = async { stream.animateTo(1f, tween(2300, easing = LinearEasing)) }
        val b = async { xpShown.animateTo(summary.xp.toFloat(), tween(2300, easing = FastOutSlowInEasing)) }
        a.await(); b.await(); tick.cancel()
        phase = 2
        buzzPattern(ctx, longArrayOf(0, 70, 40, 70, 40, 220))
        confetti++
        rankPop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }

    Dialog(onDismissRequest = { if (phase >= 2) onFinished() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, dismissOnBackPress = phase >= 2)) {
        Box(Modifier.fillMaxSize().background(TC.Bg.copy(alpha = 0.97f))) {
            // flow of squares: phone (bottom) -> hub (top)
            Canvas(Modifier.fillMaxSize()) {
                val hubY = size.height * 0.2f; val devY = size.height * 0.82f
                val cx = size.width / 2
                // hub board
                val bars = listOf(0.55f, 0.85f, 0.7f)
                bars.forEachIndexed { i, h ->
                    val pulse = if (phase == 1) 0.6f + 0.4f * sin((stream.value * 40f) + i) else 1f
                    val w = 34.dp.toPx(); val bh = 70.dp.toPx() * h * (0.7f + 0.3f * pulse)
                    drawRoundRect(TC.Lime.copy(alpha = 0.25f + 0.5f * stream.value), Offset(cx - w * 2 + i * w * 1.4f, hubY - bh / 2), Size(w, bh), CornerRadius(10.dp.toPx()))
                }
                // squares in flight
                if (phase == 1) repeat(28) { i ->
                    val t = ((stream.value * 1.6f - i * 0.035f) % 1f + 1f) % 1f
                    val inFlight = stream.value * 1.6f - i * 0.035f > 0f && stream.value < 0.98f
                    if (inFlight) {
                        val wob = sin(i * 1.7f + t * 9f) * 46.dp.toPx() * (1f - t)
                        val y = devY + (hubY - devY) * t
                        val s = 14.dp.toPx() * (1f - 0.55f * t)
                        drawRoundRect(TC.Lime.copy(alpha = 0.95f - 0.5f * t), Offset(cx + wob - s / 2, y - s / 2), Size(s, s), CornerRadius(4.dp.toPx()))
                    }
                }
                // device pad
                drawRoundRect(TC.CardHi, Offset(cx - 44.dp.toPx(), devY + 8.dp.toPx()), Size(88.dp.toPx(), 12.dp.toPx()), CornerRadius(6.dp.toPx()))
            }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(when (phase) { 0 -> R.string.sync_connecting; 1 -> R.string.sync_uploading; 2 -> R.string.sync_done; else -> R.string.sync_failed }).uppercase(),
                    style = MaterialTheme.typography.labelLarge, color = if (phase == 3) TC.Danger else TC.Dim,
                )
                Spacer(Modifier.weight(1f))
                when (phase) {
                    0, 1 -> {
                        BigNumber("${xpShown.value.toInt()}", "XP", size = 64, color = TC.Lime)
                        if (phase == 0) RowanLogo(56.dp, 4.5f, Modifier.padding(top = 18.dp))
                    }
                    2 -> {
                        val o = result
                        Text(stringResource(R.string.sync_your_rank).uppercase(), style = MaterialTheme.typography.labelMedium, color = TC.Dim)
                        Text("#${o?.rank ?: "-"}", fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 112.sp, color = TC.Lime, modifier = Modifier.scale(0.6f + 0.4f * rankPop.value))
                        if (o != null && prevRank > 0 && o.rank < prevRank) Text("▲ " + stringResource(R.string.sync_climbed, prevRank - o.rank), color = TC.Good, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        else if (o != null) Text(stringResource(R.string.sync_of, o.total), color = TC.Dim, fontSize = 16.sp)
                        Text("${summary.xp} XP", fontFamily = Mono, color = TC.Text, fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                    else -> Text(stringResource(errorRes), color = TC.Dim, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 12.dp))
                }
                Spacer(Modifier.weight(1.3f))
                if (phase >= 2) PrimaryButton(stringResource(if (phase == 2) R.string.wk_nice else R.string.cancel), onFinished, Modifier.fillMaxWidth())
            }
            ConfettiBurst(confetti, Modifier.fillMaxSize())
        }
    }
}
