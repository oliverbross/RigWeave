package app.rigweave.mobile.contest

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.rigweave.mobile.n1mm.N1mmPeerTrust

enum class ContestWorkspacePage { SETUP, LOGGING, REVIEW, NETWORK }
data class ContestWorkspaceState(
    val session: ContestSession,
    val definition: ContestDefinition,
    val page: ContestWorkspacePage = ContestWorkspacePage.SETUP,
    val callsign: String = "",
    val exchange: String = "",
    val dupe: ContestDupeState = ContestDupeState.UNKNOWN,
    val newMultipliers: Set<ContestMultiplierType> = emptySet(),
    val score: ContestScoreSnapshot = ContestScoreSnapshot(),
    val networkMode: String = "OFF",
    val peers: List<String> = emptyList(),
    val networkTrusts: List<N1mmPeerTrust> = emptyList(),
    val trustStation: String = "",
    val trustOperator: String = "",
    val trustSubnet: String = "127.0.0.0/8",
    val trustPinnedAddress: String = "",
    val validation: List<ContestValidationIssue> = emptyList(),
)
data class ContestWorkspaceCallbacks(
    val onPage: (ContestWorkspacePage) -> Unit = {},
    val onCallsign: (String) -> Unit = {},
    val onExchange: (String) -> Unit = {},
    val onLog: () -> Unit = {},
    val onClear: () -> Unit = {},
    val onRole: (ContestOperatingRole) -> Unit = {},
    val onStartSession: () -> Unit = {},
    val onKeyerIntent: (ContestKeyerIntent) -> Unit = {},
    val onNetworkStart: () -> Unit = {},
    val onNetworkStop: () -> Unit = {},
    val onTrustedModeReview: () -> Unit = {},
    val onTrustStation: (String) -> Unit = {},
    val onTrustOperator: (String) -> Unit = {},
    val onTrustSubnet: (String) -> Unit = {},
    val onTrustPinnedAddress: (String) -> Unit = {},
    val onTrustAdd: () -> Unit = {},
    val onTrustRemove: (String) -> Unit = {},
    val onExport: (String) -> Unit = {},
)

@Composable fun ContestWorkspace(state: ContestWorkspaceState, callbacks: ContestWorkspaceCallbacks, modifier: Modifier = Modifier) {
    val wide = LocalConfiguration.current.screenWidthDp >= 900
    Column(modifier.fillMaxSize().semantics { contentDescription = "Contest workspace" }) {
        PrimaryScrollableTabRow(ContestWorkspacePage.entries.indexOf(state.page)) {
            ContestWorkspacePage.entries.forEach { page -> Tab(state.page == page, { callbacks.onPage(page) }, text = { Text(page.name) }) }
        }
        when (state.page) {
            ContestWorkspacePage.SETUP -> ContestSetupScreen(state, callbacks)
            ContestWorkspacePage.LOGGING -> if (wide) Row(Modifier.fillMaxSize()) {
                ContestLoggingScreen(state, callbacks, Modifier.weight(1.5f)); ContestScorePanel(state, Modifier.weight(1f))
            } else Column(Modifier.fillMaxSize()) { ContestLoggingScreen(state, callbacks, Modifier.weight(1f)); ContestScorePanel(state) }
            ContestWorkspacePage.REVIEW -> ContestReviewScreen(state, callbacks)
            ContestWorkspacePage.NETWORK -> ContestNetworkScreen(state, callbacks)
        }
    }
}

@Composable internal fun ContestScorePanel(state: ContestWorkspaceState, modifier: Modifier = Modifier) {
    Card(modifier.padding(12.dp).fillMaxWidth().semantics { contentDescription = "Claimed score and multipliers" }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CLAIMED SCORE", style = MaterialTheme.typography.labelMedium)
            Text(state.score.claimedScore.toString(), style = MaterialTheme.typography.headlineLarge)
            Text("${state.score.scoredQsos} scored · ${state.score.points} points · ${state.score.status.name}")
            state.score.multipliers.forEach { (type, count) -> Text("${type.name}: $count") }
            Text("10 min ${"%.1f".format(state.score.rate.last10MinutesPerHour)} Q/h · 60 min ${"%.1f".format(state.score.rate.last60MinutesPerHour)} Q/h")
        }
    }
}
