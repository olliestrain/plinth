package app.rowan.gym

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
import app.rowan.gym.data.Prefs
import app.rowan.gym.ui.AppNav
import app.rowan.gym.ui.onboarding.Wizard
import app.rowan.gym.ui.theme.TC
import app.rowan.gym.ui.theme.RowanTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = SystemBarStyle.dark(TC.Bg.toArgb())
        enableEdgeToEdge(statusBarStyle = dark, navigationBarStyle = dark)
        val app = application as RowanApp
        setContent {
            CompositionLocalProvider(LocalApp provides app) {
                RowanTheme {
                    val prefs: Prefs? by app.settings.flow.collectAsState(initial = null)
                    val scope = rememberCoroutineScope()
                    LaunchedEffect(Unit) {
                        app.repo.backup.snapshot("start")
                        app.hubManager.refresh()
                        app.hubManager.checkUpdate()
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
