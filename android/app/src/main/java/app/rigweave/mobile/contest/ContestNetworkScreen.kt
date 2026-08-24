package app.rigweave.mobile.contest

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable internal fun ContestNetworkScreen(state: ContestWorkspaceState, callbacks: ContestWorkspaceCallbacks) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        .semantics { contentDescription = "Contest network controls" }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        HorizontalDivider()
        Text("TRUSTED PEERS", style = MaterialTheme.typography.titleMedium)
        state.networkTrusts.forEach { trust ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${trust.station} · ${trust.expectedOperatorCall} · ${trust.subnet}${trust.pinnedAddress?.let { " · $it" }.orEmpty()}", Modifier.weight(1f))
                TextButton({ callbacks.onTrustRemove(trust.station) }) { Text("REMOVE") }
            }
        }
        OutlinedTextField(state.trustStation, callbacks.onTrustStation, Modifier.fillMaxWidth(), label = { Text("Peer station name") }, singleLine = true)
        OutlinedTextField(state.trustOperator, callbacks.onTrustOperator, Modifier.fillMaxWidth(), label = { Text("Expected operator callsign") }, singleLine = true)
        OutlinedTextField(state.trustSubnet, callbacks.onTrustSubnet, Modifier.fillMaxWidth(), label = { Text("Allowed subnet (CIDR)") }, singleLine = true)
        OutlinedTextField(state.trustPinnedAddress, callbacks.onTrustPinnedAddress, Modifier.fillMaxWidth(), label = { Text("Pinned IP address (optional)") }, singleLine = true)
        Button(callbacks.onTrustAdd, enabled = state.trustStation.isNotBlank() && state.trustOperator.isNotBlank() && state.trustSubnet.isNotBlank()) { Text("ADD TRUSTED PEER") }
        Text("Changing trust always stops N1MM. Review the list, then explicitly start monitoring again.", style = MaterialTheme.typography.bodySmall)
        Text("FREQMODE, FUNCTIONKEY, XMIT, TIME, FILE and radio-control XML never execute side effects.")
    }
}
