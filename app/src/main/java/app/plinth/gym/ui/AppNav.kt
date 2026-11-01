package app.plinth.gym.ui

import androidx.compose.ui.graphics.Color
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.plinth.gym.LocalApp
import app.plinth.gym.R
import app.plinth.gym.data.Prefs
import app.plinth.gym.ui.calendar.CalendarScreen
import app.plinth.gym.ui.components.pressable
import app.plinth.gym.ui.home.HomeScreen
import app.plinth.gym.ui.notes.NotesScreen
import app.plinth.gym.ui.plans.PlanEditorScreen
import app.plinth.gym.ui.plans.PlansScreen
import app.plinth.gym.ui.progress.ProgressScreen
import app.plinth.gym.ui.rank.ProfileScreen
import app.plinth.gym.ui.settings.SettingsScreen
import app.plinth.gym.ui.theme.TC
import app.plinth.gym.ui.workout.WorkoutScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Navigation actions every screen may need. */
class Actions(private val nav: NavHostController, private val scope: CoroutineScope, private val ctx: android.content.Context) {
    private val app get() = ctx.applicationContext as app.plinth.gym.PlinthApp
    fun back() { nav.popBackStack() }
    fun openSession(id: Long) = nav.navigate("workout/$id")
    fun startWorkout(date: LocalDate, templateId: Long?) {
        scope.launch {
            val id = app.repo.startSession(date, templateId, ctx.getString(R.string.workout_free))
            nav.navigate("workout/$id")
        }
    }
    fun editPlan(id: Long) = nav.navigate("plan/$id")
    fun notes() = nav.navigate("notes")
    fun settings() = nav.navigate("settings")
}

@Composable
fun AppNav(prefs: Prefs) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val actions = remember(nav) { Actions(nav, scope, ctx) }

    NavHost(nav, startDestination = "main", modifier = Modifier.fillMaxSize()) {
        composable("main") { MainShell(prefs, actions) }
        composable("workout/{id}") { e -> WorkoutScreen(e.arguments?.getString("id")?.toLongOrNull() ?: 0L, prefs, actions) }
        composable("plan/{id}") { e -> PlanEditorScreen(e.arguments?.getString("id")?.toLongOrNull() ?: 0L, prefs, actions) }
        composable("notes") { NotesScreen(actions) }
        composable("settings") { SettingsScreen(prefs, actions) }
    }
}

private data class TabDef(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: Int)

@Composable
private fun MainShell(prefs: Prefs, actions: Actions) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = remember {
        listOf(
            TabDef(Icons.Rounded.GridView, R.string.tab_home), TabDef(Icons.Rounded.CalendarMonth, R.string.tab_calendar),
            TabDef(Icons.Rounded.FitnessCenter, R.string.tab_plans), TabDef(Icons.Rounded.Insights, R.string.tab_progress),
            TabDef(Icons.Rounded.Person, R.string.tab_profile),
        )
    }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }

    Box(Modifier.fillMaxSize()) {
        Crossfade(tab, label = "tab") { t ->
            when (t) {
                0 -> HomeScreen(prefs, actions, goTab = { tab = it })
                1 -> CalendarScreen(prefs, actions)
                2 -> PlansScreen(prefs, actions)
                3 -> ProgressScreen(prefs, actions)
                else -> ProfileScreen(prefs, actions)
            }
        }
        // flat bottom bar with hairline
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xE60C080A))) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(TC.Outline))
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 6.dp)) {
                tabs.forEachIndexed { i, t ->
                    val sel = tab == i
                    Column(
                        Modifier.weight(1f).pressable({ tab = i }).padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(t.icon, null, tint = if (sel) TC.Text else TC.Faint, modifier = Modifier.size(22.dp))
                        Text(stringResource(t.label), fontSize = 10.sp, letterSpacing = 0.4.sp, fontWeight = FontWeight.Medium, color = if (sel) TC.Text else TC.Faint, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
    }
}
