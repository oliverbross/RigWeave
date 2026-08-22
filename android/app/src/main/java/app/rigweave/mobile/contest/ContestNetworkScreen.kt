package app.rigweave.mobile.contest

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable internal fun ContestNetworkScreen(state: ContestWorkspaceState, callbacks: ContestWorkspaceCallbacks) {
    Column(Modifier.fillMaxSize().padding(16.dp).semantics { contentDescription = "Contest network controls" }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("N1MM network", style = MaterialTheme.typography.headlineSmall)
        Text("Mode: ${state.networkMode} · default OFF · loopback only")
        Text("Peers: ${state.peers.size}")
        state.peers.forEach { Text("• $it") }
        Text("Incoming QSO changes are monitor-only unless a peer and matching contest are explicitly trusted.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(callbacks.onNetworkStart) { Text("START MONITOR") }
            OutlinedButton(callbacks.onNetworkStop) { Text("STOP") }
        }
        OutlinedButton(callbacks.onTrustedModeReview) { Text("REVIEW TRUSTED-LAN MODE") }
        Text("FREQMODE, FUNCTIONKEY, XMIT, TIME, FILE and radio-control XML never execute side effects.")
    }
}
