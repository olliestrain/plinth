package app.rowan.gym.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import app.rowan.gym.BuildConfig
import app.rowan.gym.LocalApp
import app.rowan.gym.R
import app.rowan.gym.data.BackupException
import app.rowan.gym.data.Prefs
import app.rowan.gym.net.Updates
import app.rowan.gym.ui.Actions
import app.rowan.gym.ui.components.*
import app.rowan.gym.ui.currentLang
import app.rowan.gym.ui.onboarding.AccountDialog
import app.rowan.gym.ui.onboarding.HubStatusChip
import app.rowan.gym.ui.theme.TC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(prefs: Prefs, actions: Actions) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val hub = app.hubManager
    val reachable by hub.reachable.collectAsState()
    val update by hub.update.collectAsState()
    val backup = rememberBackupActions()
    var name by remember(prefs.name) { mutableStateOf(prefs.name) }
    var url by remember(prefs.hubUrl) { mutableStateOf(prefs.hubUrl) }
    var account by remember { mutableStateOf(false) }
    var snapshots by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var checkedNone by remember { mutableStateOf(false) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Column(Modifier.fillMaxSize().background(TC.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconPill(Icons.AutoMirrored.Rounded.ArrowBack, { actions.back() })
            Text(stringResource(R.string.settings), Modifier.padding(start = 14.dp), style = MaterialTheme.typography.headlineMedium)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            SectionTitle(stringResource(R.string.set_profile))
            TCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TextBox(name, { name = it.take(24); scope.launch { app.settings.update { this.name = it.take(24).trim() } } }, stringResource(R.string.wiz_name_hint), singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.set_weekly_goal), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        IconPill(Icons.Rounded.Remove, { scope.launch { app.settings.update { weeklyGoal = (weeklyGoal - 1).coerceAtLeast(1) } } }, size = 38.dp)
                        Text("${prefs.weeklyGoal}", Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Black, fontSize = 22.sp, color = TC.Lime)
                        IconPill(Icons.Rounded.Add, { scope.launch { app.settings.update { weeklyGoal = (weeklyGoal + 1).coerceAtMost(7) } } }, size = 38.dp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.set_units), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        TChip("kg", !prefs.useLb, { scope.launch { app.settings.update { useLb = false } } })
                        TChip("lb", prefs.useLb, { scope.launch { app.settings.update { useLb = true } } })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.set_language), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        TChip("English", currentLang() == "en", { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) })
                        TChip("Italiano", currentLang() == "it", { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("it")) })
                    }
                }
            }

            SectionTitle(stringResource(R.string.set_data))
            TCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (prefs.lastExport == 0L) stringResource(R.string.backup_never) else stringResource(R.string.backup_last, fmt.format(Date(prefs.lastExport))),
                        color = TC.Dim, fontSize = 13.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton(stringResource(R.string.backup_export), backup.export, Modifier.weight(1f), enabled = !backup.busy, icon = Icons.Rounded.FileUpload)
                        GhostButton(stringResource(R.string.backup_import), backup.import, Modifier.weight(1f), icon = Icons.Rounded.FileDownload)
                    }
                    GhostButton(stringResource(R.string.backup_snapshots), { snapshots = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.History)
                    Text(stringResource(R.string.set_snapshots_hint), color = TC.Faint, fontSize = 12.sp)
                    Text(stringResource(R.string.set_reminder).uppercase(), style = MaterialTheme.typography.labelSmall, color = TC.Dim)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 3, 7, 14, 30).forEach { d ->
                            TChip(if (d == 0) stringResource(R.string.off) else stringResource(R.string.set_days_n, d), prefs.reminderDays == d, { scope.launch { app.settings.update { reminderDays = d } } }, color = TC.Sky)
                        }
                    }
                }
            }

            SectionTitle(stringResource(R.string.set_hub))
            TCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HubStatusChip(reachable)
                    if (prefs.loggedIn) {
                        Text(stringResource(R.string.set_signed_in, prefs.email), fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GhostButton(stringResource(R.string.hub_backup_to), backup.toHub, Modifier.weight(1f), icon = Icons.Rounded.CloudUpload)
                            GhostButton(stringResource(R.string.hub_backup_from), backup.fromHub, Modifier.weight(1f), icon = Icons.Rounded.CloudDownload)
                        }
                        GhostButton(stringResource(R.string.set_sign_out), { scope.launch { hub.logout() } }, Modifier.fillMaxWidth(), color = TC.Danger)
                    } else {
                        Text(stringResource(R.string.set_not_signed_in), color = TC.Dim)
                        PrimaryButton(stringResource(R.string.set_sign_in), { account = true }, Modifier.fillMaxWidth())
                    }
                    Text(stringResource(R.string.set_hub_address).uppercase(), style = MaterialTheme.typography.labelSmall, color = TC.Dim)
                    TextBox(url, { url = it }, BuildConfig.HUB_URL, singleLine = true)
                    GhostButton(stringResource(R.string.set_hub_test), {
                        scope.launch { hub.normalizeAndSaveUrl(url); val ok = hub.refresh(); Toast.makeText(ctx, if (ok) R.string.hub_found else R.string.hub_away, Toast.LENGTH_SHORT).show() }
                    }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Wifi)
                }
            }

            SectionTitle(stringResource(R.string.set_updates))
            TCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.set_version, BuildConfig.VERSION_NAME), fontWeight = FontWeight.Bold)
                    val u = update
                    if (u != null) {
                        Text(stringResource(R.string.update_available) + " · " + stringResource(R.string.update_version, u.version), color = TC.Lime, fontWeight = FontWeight.ExtraBold)
                        PrimaryButton(stringResource(R.string.update_get), {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.apkUrl ?: u.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }, Modifier.fillMaxWidth(), icon = Icons.Rounded.SystemUpdateAlt)
                    } else if (!Updates.configured) {
                        Text(stringResource(R.string.set_updates_unconfigured), color = TC.Ember, fontSize = 13.sp)
                    } else if (checkedNone) Text(stringResource(R.string.set_up_to_date), color = TC.Good, fontWeight = FontWeight.Bold)
                    GhostButton(stringResource(R.string.set_check_updates), {
                        scope.launch { checking = true; hub.checkUpdate(force = true); checkedNone = hub.update.value == null; checking = false }
                    }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Refresh)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (account) AccountDialog { account = false }
    if (snapshots) SnapshotsDialog { snapshots = false }
}

@Composable
private fun SnapshotsDialog(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val files = remember { app.repo.backup.snapshots() }
    var pending by remember { mutableStateOf<File?>(null) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    TDialog(stringResource(R.string.backup_snapshots), onDismiss) {
        if (files.isEmpty()) Text(stringResource(R.string.snap_none), color = TC.Dim)
        files.take(15).forEach { f ->
            TCard(Modifier.fillMaxWidth(), onClick = { pending = f }, radius = 18.dp, padding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(fmt.format(Date(f.lastModified())), fontWeight = FontWeight.Bold)
                        Text(f.name.substringBefore('-') + " · " + (f.length() / 1024) + " KB", color = TC.Dim, fontSize = 12.sp)
                    }
                    Text(stringResource(R.string.snap_restore), color = TC.Lime, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    pending?.let { f ->
        ConfirmDialog(stringResource(R.string.import_title), stringResource(R.string.import_warning), stringResource(R.string.import_confirm), { pending = null }, danger = true) {
            scope.launch {
                runCatching { app.repo.backup.import(withContext(Dispatchers.IO) { f.readText() }) }
                    .onSuccess { Toast.makeText(ctx, R.string.import_done, Toast.LENGTH_LONG).show(); onDismiss() }
                    .onFailure { Toast.makeText(ctx, if (it is BackupException) R.string.import_invalid else R.string.backup_failed, Toast.LENGTH_LONG).show() }
            }
        }
    }
}
