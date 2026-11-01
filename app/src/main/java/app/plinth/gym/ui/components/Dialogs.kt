package app.plinth.gym.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.plinth.gym.R
import app.plinth.gym.ui.theme.TC
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun TDialog(
    title: String, onDismiss: () -> Unit, modifier: Modifier = Modifier,
    confirm: String? = null, onConfirm: (() -> Unit)? = null, confirmEnabled: Boolean = true,
    danger: Boolean = false, showCancel: Boolean = true, content: @Composable () -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        TCard(modifier.fillMaxWidth(), color = TC.Surface, radius = 14.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = TC.Text)
                content()
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (showCancel) GhostButton(stringResource(R.string.cancel), onDismiss, Modifier.weight(1f))
                    if (confirm != null && onConfirm != null) {
                        if (danger) GhostButton(confirm, onConfirm, Modifier.weight(1f), color = TC.Danger)
                        else PrimaryButton(confirm, onConfirm, Modifier.weight(1f), enabled = confirmEnabled)
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, danger: Boolean = false, onConfirm: () -> Unit) {
    TDialog(title, onDismiss, confirm = confirm, onConfirm = { onConfirm(); onDismiss() }, danger = danger) {
        Text(text, color = TC.Dim, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogT(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    TDialog(stringResource(R.string.pick_date), onDismiss, confirm = stringResource(R.string.ok), onConfirm = {
        state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
        onDismiss()
    }) {
        DatePicker(
            state, title = null, headline = null, showModeToggle = false,
            colors = DatePickerDefaults.colors(containerColor = TC.Surface, selectedDayContainerColor = TC.Accent, selectedDayContentColor = androidx.compose.ui.graphics.Color.Black,
                todayDateBorderColor = TC.Accent, todayContentColor = TC.Accent),
        )
    }
}
