package app.rigweave.mobile.contest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun ContestSetupScreen(state: ContestWorkspaceState, callbacks: ContestWorkspaceCallbacks) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(state.definition.humanName, style = MaterialTheme.typography.headlineSmall)
        Text("Rules ${state.definition.version.value} · ${state.definition.officialSources.joinToString { it.edition }}")
        Text("UTC ${state.session.utcStart} – ${state.session.utcEnd}")
        Text("Station ${state.session.stationCallsign.ifBlank { "MISSING" }} · Grid ${state.session.stationGrid.ifBlank { "MISSING" }}")
        Text("Category ${state.session.category.operator} / ${state.session.category.mode} / ${state.session.category.power}")
        Text("Sent exchange: ${state.definition.sentExchange.joinToString()}")
        Text("Initial serial: ${state.session.initialSerial}")
        Text("Wavelog: canonical QSO outbox adapter supplied by integration")
        Text("N1MM: disabled until explicitly started")
        state.validation.forEach { AssistChip({}, { Text("${it.truth}: ${it.reason}") }) }
        Button(callbacks.onStartSession, enabled = state.validation.none { it.truth == ContestTruth.INVALID || it.truth == ContestTruth.INCOMPLETE }) { Text("START WITHOUT TRANSMITTING") }
    }
}
