package app.plinth.gym.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plinth.gym.LocalApp
import app.plinth.gym.R
import app.plinth.gym.data.Exercise
import app.plinth.gym.data.Measure
import app.plinth.gym.ui.components.PrimaryButton
import app.plinth.gym.ui.components.TCard
import app.plinth.gym.ui.components.TChip
import app.plinth.gym.ui.components.TextBox
import app.plinth.gym.ui.theme.TC
import kotlinx.coroutines.launch

@Composable
fun measureLabel(m: Measure) = stringResource(
    when (m) { Measure.WEIGHT_REPS -> R.string.measure_weight_reps; Measure.REPS -> R.string.measure_reps; Measure.TIME -> R.string.measure_time },
)

/** Pick an existing exercise or create a new one (with its unit of measure: kg×reps, reps only, or seconds). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExercisePickerSheet(onDismiss: () -> Unit, onPick: (Exercise) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val all by app.repo.exercises.collectAsState()
    var q by remember { mutableStateOf("") }
    var measure by remember { mutableStateOf(Measure.WEIGHT_REPS) }
    val shown = all.filter { it.name.contains(q.trim(), ignoreCase = true) }
    val canCreate = q.isNotBlank() && all.none { it.name.equals(q.trim(), ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TC.Surface, contentColor = TC.Text) {
        Column(Modifier.fillMaxHeight(0.92f).navigationBarsPadding().imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.pick_exercise), style = MaterialTheme.typography.headlineMedium)
            TextBox(q, { q = it }, stringResource(R.string.search_or_create), singleLine = true)
            if (canCreate) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Measure.entries.forEach { m -> TChip(measureLabel(m), measure == m, { measure = m }, color = TC.Sky) }
                }
                PrimaryButton(stringResource(R.string.create_exercise, q.trim()), {
                    scope.launch { onPick(app.repo.addExercise(q, measure)); onDismiss() }
                }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(shown, key = { it.id }) { e ->
                    TCard(Modifier.fillMaxWidth(), onClick = { onPick(e); onDismiss() }, radius = 12.dp, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(measureLabel(e.measure), color = TC.Dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
