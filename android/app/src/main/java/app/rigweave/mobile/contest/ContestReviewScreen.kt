package app.rigweave.mobile.contest

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun ContestReviewScreen(state: ContestWorkspaceState, callbacks: ContestWorkspaceCallbacks) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Contest review", style = MaterialTheme.typography.headlineSmall)
        Text("${state.score.reviewQsos} review · ${state.score.duplicates} duplicates · ${state.score.zeroPointValidQsos} valid zero-point")
        Text("Edits and deletes are integration callbacks to the canonical mutation coordinator.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ callbacks.onExport("CABRILLO") }) { Text("VALIDATE CABRILLO") }
            OutlinedButton({ callbacks.onExport("ADIF") }) { Text("EXPORT ADIF") }
        }
    }
}
