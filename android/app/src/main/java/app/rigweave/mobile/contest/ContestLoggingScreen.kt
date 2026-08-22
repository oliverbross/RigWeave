package app.rigweave.mobile.contest

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable internal fun ContestLoggingScreen(state: ContestWorkspaceState, callbacks: ContestWorkspaceCallbacks, modifier: Modifier = Modifier) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ContestOperatingRole.entries.forEach { role -> FilterChip(state.session.role == role, { callbacks.onRole(role) }, { Text(if (role == ContestOperatingRole.RUN) "RUN" else "S&P") }) }
        }
        OutlinedTextField(state.callsign, callbacks.onCallsign, Modifier.fillMaxWidth().semantics { contentDescription = "Contest callsign" }, label={Text("CALLSIGN")}, singleLine=true)
        OutlinedTextField(state.exchange, callbacks.onExchange, Modifier.fillMaxWidth().semantics { contentDescription = "Contest exchange" }, label={Text("RECEIVED EXCHANGE")}, singleLine=true)
        Text("Dupe: ${state.dupe.name} · New multipliers: ${state.newMultipliers.joinToString().ifBlank { "none" }}")
        Text("Band/mode/frequency are read-only operating-context inputs")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(callbacks.onLog) { Text("LOG QSO") }
            OutlinedButton(callbacks.onClear) { Text("CLEAR / ESC") }
        }
        Text("ESM emits keyer intents through the supplied callback; this workspace cannot transmit.")
    }
}
