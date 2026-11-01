package app.rowan.gym.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.rowan.gym.LocalApp
import app.rowan.gym.R
import app.rowan.gym.data.Prefs
import app.rowan.gym.net.HubResult
import app.rowan.gym.ui.components.GhostButton
import app.rowan.gym.ui.components.PrimaryButton
import app.rowan.gym.ui.components.TCard
import app.rowan.gym.ui.components.TChip
import app.rowan.gym.ui.currentLang
import app.rowan.gym.ui.theme.TC
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Password reset: e-mail -> code + new password. Calls onDone once logged in. */
@Composable
fun ForgotDialog(initialEmail: String, lang: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val hub = LocalApp.current.hubManager
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(initialEmail) }
    var sent by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        TCard(Modifier.fillMaxWidth(), color = TC.Surface, radius = 28.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.forgot_title), style = MaterialTheme.typography.headlineSmall, color = TC.Text)
                Text(stringResource(if (sent) R.string.forgot_sent else R.string.forgot_sub), color = TC.Dim)
                AuthField(email, { email = it }, stringResource(R.string.field_email), KeyboardType.Email)
                if (sent) {
                    AuthField(code, { code = it.filter(Char::isDigit).take(6) }, stringResource(R.string.field_code), KeyboardType.Number)
                    AuthField(pw, { pw = it }, stringResource(R.string.field_new_password), KeyboardType.Password, secret = true)
                }
                if (err != null) Text(stringResource(err!!), color = TC.Danger, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                PrimaryButton(stringResource(if (sent) R.string.forgot_reset else R.string.forgot_send), Modifier.fillMaxWidth(), enabled = !busy && email.isNotBlank()) {
                    busy = true; err = null
                    scope.launch {
                        if (!sent) {
                            val r = hub.forgot(email.trim(), lang)
                            if (r is HubResult.Ok) sent = true else err = authError(r)
                        } else {
                            val (r, ok) = hub.reset(email.trim(), code, pw)
                            if (ok != null) onDone() else err = authError(r)
                        }
                        busy = false
                    }
                }
                GhostButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Log in / register from Settings after onboarding (full flow incl. e-mail code). */
@Composable
fun AccountDialog(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val hub = app.hubManager
    val scope = rememberCoroutineScope()
    val reachable by hub.reachable.collectAsState()
    val prefs by app.settings.flow.collectAsState(initial = Prefs())
    var login by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var forgot by remember { mutableStateOf(false) }
    val lang = currentLang()
    LaunchedEffect(Unit) { while (true) { hub.refresh(); delay(4000) } }

    Dialog(onDismissRequest = onDismiss) {
        TCard(Modifier.fillMaxWidth(), color = TC.Surface, radius = 28.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (verifying) R.string.wiz_verify_title else R.string.account_title), style = MaterialTheme.typography.headlineSmall, color = TC.Text)
                HubStatusChip(reachable)
                if (!verifying) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TChip(stringResource(R.string.wiz_tab_login), login, { login = true; err = null })
                        TChip(stringResource(R.string.wiz_tab_register), !login, { login = false; err = null })
                    }
                    AuthField(email, { email = it }, stringResource(R.string.field_email), KeyboardType.Email)
                    AuthField(pw, { pw = it }, stringResource(R.string.field_password), KeyboardType.Password, secret = true)
                } else {
                    Text(stringResource(R.string.wiz_verify_sub, email.trim()), color = TC.Dim)
                    AuthField(code, { code = it.filter(Char::isDigit).take(6) }, stringResource(R.string.field_code), KeyboardType.Number)
                }
                if (err != null) Text(stringResource(err!!), color = TC.Danger, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                PrimaryButton(
                    stringResource(if (verifying) R.string.wiz_verify else if (login) R.string.wiz_login else R.string.wiz_create_account),
                    Modifier.fillMaxWidth(), enabled = !busy && email.isNotBlank(),
                ) {
                    busy = true; err = null
                    scope.launch {
                        val name = prefs.name
                        if (verifying) {
                            val (r, ok) = hub.verify(email.trim(), code)
                            if (ok != null) onDismiss() else err = authError(r)
                        } else if (login) {
                            val (r, ok) = hub.login(email.trim(), pw, lang)
                            if (ok != null) onDismiss()
                            else if (r is HubResult.Err && r.code == "unverified") verifying = true else err = authError(r)
                        } else {
                            val r = hub.register(email.trim(), pw, name.ifBlank { email.substringBefore('@') }, lang)
                            if (r is HubResult.Ok) verifying = true else err = authError(r)
                        }
                        busy = false
                    }
                }
                if (login && !verifying) GhostButton(stringResource(R.string.forgot_password), { forgot = true }, Modifier.fillMaxWidth())
                GhostButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
            }
        }
    }
    if (forgot) ForgotDialog(email, lang, onDismiss = { forgot = false }, onDone = { forgot = false; onDismiss() })
}
