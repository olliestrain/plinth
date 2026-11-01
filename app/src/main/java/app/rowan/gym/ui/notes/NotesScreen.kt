package app.rowan.gym.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rowan.gym.LocalApp
import app.rowan.gym.R
import app.rowan.gym.data.Note
import app.rowan.gym.ui.Actions
import app.rowan.gym.ui.components.*
import app.rowan.gym.ui.fmtDateShort
import app.rowan.gym.ui.theme.TC
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Notes live here: write new ones, archive them (kept forever) and consult the archive later. */
@Composable
fun NotesScreen(actions: Actions) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val notes by app.repo.notes.collectAsState()
    var archived by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Note?>(null) }
    val list = notes.filter { it.archived == archived }

    Column(Modifier.fillMaxSize().background(TC.Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconPill(Icons.AutoMirrored.Rounded.ArrowBack, { actions.back() })
            Text(stringResource(R.string.notes_title), Modifier.padding(start = 14.dp), style = MaterialTheme.typography.headlineMedium)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TChip(stringResource(R.string.notes_active), !archived, { archived = false })
                TChip(stringResource(R.string.notes_archive), archived, { archived = true }, color = TC.Sky)
            }
            if (!archived) {
                TextBox(draft, { draft = it }, stringResource(R.string.notes_hint), minHeight = 64.dp)
                if (draft.isNotBlank()) PrimaryButton(stringResource(R.string.save), {
                    scope.launch { app.repo.saveNote(Note(text = draft.trim())); draft = "" }
                }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
            }
            if (list.isEmpty()) Text(stringResource(if (archived) R.string.notes_archive_empty else R.string.notes_empty), color = TC.Dim, modifier = Modifier.padding(8.dp))
            list.forEach { n ->
                TCard(Modifier.fillMaxWidth(), radius = 22.dp) {
                    Column {
                        Text(n.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TC.Text)
                        val d = if (n.source.length == 10) LocalDate.parse(n.source) else Instant.ofEpochMilli(n.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(fmtDateShort(d), color = TC.Faint, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            IconPill(if (archived) Icons.Rounded.Unarchive else Icons.Rounded.Inventory2, { scope.launch { app.repo.saveNote(n.copy(archived = !n.archived)) } }, size = 36.dp)
                            Spacer(Modifier.size(8.dp))
                            IconPill(Icons.Rounded.Delete, { deleting = n }, size = 36.dp, tint = TC.Danger)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    deleting?.let { n ->
        ConfirmDialog(stringResource(R.string.notes_delete), stringResource(R.string.notes_delete_text), stringResource(R.string.delete), { deleting = null }, danger = true) {
            scope.launch { app.repo.deleteNote(n) }
        }
    }
}
