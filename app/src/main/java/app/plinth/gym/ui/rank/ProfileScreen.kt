package app.plinth.gym.ui.rank

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import app.plinth.gym.data.Achievements
import app.plinth.gym.data.Prefs
import app.plinth.gym.data.Stats
import app.plinth.gym.ui.Actions
import app.plinth.gym.ui.components.BigNumber
import app.plinth.gym.ui.components.IconPill
import app.plinth.gym.ui.components.ScreenColumn
import app.plinth.gym.ui.components.SectionTitle
import app.plinth.gym.ui.components.TCard
import app.plinth.gym.ui.fmtBig
import app.plinth.gym.ui.theme.TC
import app.plinth.gym.ui.unitLabel
import androidx.compose.material.icons.rounded.Settings

/** Local profile: nothing here ever leaves the phone. */
@Composable
fun ProfileScreen(prefs: Prefs, actions: Actions) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val sessions by app.repo.sessions.collectAsState()
    val plans by app.repo.dayPlans.collectAsState()
    val summary = remember(sessions, plans, prefs.weeklyGoal) { Stats.summarize(sessions, plans.values.toList(), prefs.weeklyGoal) }
    val lb = prefs.useLb

    ScreenColumn {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tab_profile).uppercase(), style = MaterialTheme.typography.labelMedium, color = TC.Dim)
                Text(prefs.name.ifBlank { stringResource(R.string.profile_anon) }, style = MaterialTheme.typography.headlineLarge)
            }
            IconPill(Icons.Rounded.Settings, { actions.settings() })
        }
        Text(stringResource(R.string.profile_local_note), color = TC.Dim, style = MaterialTheme.typography.bodyMedium)

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(Modifier.weight(1f), stringResource(R.string.rank_workouts), "${summary.workouts}", null)
            Stat(Modifier.weight(1f), stringResource(R.string.pg_total_volume), fmtBig(summary.volumeKg, lb), unitLabel(lb))
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(Modifier.weight(1f), stringResource(R.string.pg_consistency), "${summary.streak}", stringResource(R.string.unit_weeks))
            Stat(Modifier.weight(1f), stringResource(R.string.pg_best_streak), "${summary.bestStreak}", stringResource(R.string.unit_weeks))
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(Modifier.weight(1f), stringResource(R.string.pg_prs), "${summary.prs}", null)
            Stat(Modifier.weight(1f), stringResource(R.string.profile_time), "${summary.totalSeconds / 3600}", "h")
        }

        SectionTitle(stringResource(R.string.rank_achievements))
        val ach = remember(summary) { Achievements.evaluate(summary) }
        ach.forEach { a ->
            val name = ctx.getString(ctx.resources.getIdentifier("ach_${a.key}", "string", ctx.packageName))
            val desc = ctx.getString(ctx.resources.getIdentifier("ach_${a.key}_desc", "string", ctx.packageName))
            TCard(Modifier.fillMaxWidth(), color = TC.Card, border = if (a.unlocked) TC.Accent.copy(alpha = 0.5f) else TC.Outline, radius = 12.dp, padding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(if (a.unlocked) TC.Accent else TC.CardHi), contentAlignment = Alignment.Center) {
                        Icon(if (a.unlocked) Icons.Rounded.Check else Icons.Rounded.Lock, null, tint = if (a.unlocked) Color.Black else TC.Faint, modifier = Modifier.size(18.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (a.unlocked) TC.Text else TC.Dim)
                        Text(desc, color = TC.Dim, fontSize = 12.sp)
                        if (!a.unlocked) Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(2.dp).background(TC.Outline)) {
                            Box(Modifier.fillMaxWidth(a.progress).height(2.dp).background(TC.Accent.copy(alpha = 0.8f)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(modifier: Modifier, label: String, value: String, unit: String?) {
    TCard(modifier.fillMaxHeight(), radius = 12.dp) {
        Column {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TC.Dim)
            Spacer(Modifier.height(8.dp))
            BigNumber(value, unit, size = 30)
        }
    }
}
