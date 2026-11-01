package app.plinth.gym.ui.settings

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
import app.plinth.gym.LocalApp
import app.plinth.gym.R
import app.plinth.gym.data.BackupException
import app.plinth.gym.ui.components.ConfirmDialog
import app.plinth.gym.ui.theme.TC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupActions(val export: () -> Unit, val import: () -> Unit, val busy: Boolean)

/** Export/import through the system file picker. Shows its own confirm dialogs. */
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
        export = { exporter.launch("plinth-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json") },
        import = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
        busy = busy,
    )
}
