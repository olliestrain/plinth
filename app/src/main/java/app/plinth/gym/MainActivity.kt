package app.plinth.gym

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import app.plinth.gym.data.Prefs
import app.plinth.gym.ui.AppNav
import app.plinth.gym.ui.onboarding.Wizard
import app.plinth.gym.ui.theme.TC
import app.plinth.gym.ui.theme.PlinthTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = dark, navigationBarStyle = dark)
        val app = application as PlinthApp
        setContent {
            CompositionLocalProvider(LocalApp provides app) {
                PlinthTheme {
                    val prefs: Prefs? by app.settings.flow.collectAsState(initial = null)
                    val scope = rememberCoroutineScope()
                    LaunchedEffect(Unit) {
                        app.repo.backup.snapshot("start")
                        app.updates.check()
                    }
                    Box(Modifier.fillMaxSize().background(TC.Bg)) {
                        val p = prefs
                        when {
                            p == null -> Unit
                            !p.onboarded -> Wizard(onDone = { /* settings flow flips onboarded */ })
                            else -> AppNav(p)
                        }
                    }
                    @Suppress("UNUSED_EXPRESSION") scope
                }
            }
        }
    }
}
