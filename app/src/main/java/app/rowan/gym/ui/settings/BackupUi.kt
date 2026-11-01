package app.rowan.gym.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.rowan.gym.LocalApp
import app.rowan.gym.R
import app.rowan.gym.data.BackupException
import app.rowan.gym.net.HubResult
import app.rowan.gym.ui.components.ConfirmDialog
import app.rowan.gym.ui.theme.TC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupActions(val export: () -> Unit, val import: () -> Unit, val toHub: () -> Unit, val fromHub: () -> Unit, val busy: Boolean)

/** Export/import through the system file picker + optional copy on the Rowan hub. Shows its own confirm dialogs. */
@Composable
fun rememberBackupActions(): BackupActions {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun toast(res: Int, vararg a: Any) = Toast.makeText(ctx, ctx.getString(res, *a), Toast.LENGTH_LONG).show()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            runCatching {
                val json = app.repo.backup.toJson()
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) } }
                app.settings.update { lastExport = System.currentTimeMillis() }
            }.onSuccess { toast(R.string.backup_exported) }.onFailure { toast(R.string.backup_failed) }
            busy = false
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } } }.getOrNull()
            if (text == null) toast(R.string.backup_failed) else pendingImport = text
        }
    }

    pendingImport?.let { text ->
        ConfirmDialog(stringResource(R.string.import_title), stringResource(R.string.import_warning), stringResource(R.string.import_confirm), { pendingImport = null }, danger = true) {
            scope.launch {
                busy = true
                runCatching { app.repo.backup.import(text) }
                    .onSuccess { toast(R.string.import_done) }
                    .onFailure { if (it is BackupException) toast(R.string.import_invalid) else toast(R.string.backup_failed) }
                busy = false
            }
        }
    }

    return BackupActions(
        export = { exporter.launch("rowan-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json") },
        import = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
        toHub = {
            scope.launch {
                busy = true
                when (val r = app.hubManager.uploadBackup()) {
                    is HubResult.Ok -> toast(R.string.hub_backup_saved)
                    is HubResult.Unreachable -> toast(R.string.err_unreachable)
                    is HubResult.Err -> toast(R.string.err_generic)
                }
                busy = false
            }
        },
        fromHub = {
            scope.launch {
                busy = true
                val (text, r) = app.hubManager.downloadBackup()
                if (r is HubResult.Ok && text != null) pendingImport = text
                else toast(if (r is HubResult.Unreachable) R.string.err_unreachable else R.string.hub_no_backup)
                busy = false
            }
        },
        busy = busy,
    )
}
