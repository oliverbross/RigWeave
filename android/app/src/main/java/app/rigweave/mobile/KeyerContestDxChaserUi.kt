// SPDX-License-Identifier: GPL-3.0-only
package app.rigweave.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.rigweave.mobile.contest.*
import app.rigweave.mobile.dxchaser.DxChaserScreen
import app.rigweave.mobile.keyer.KeyerQueueSnapshot

enum class IntegratedDigiPage { DIGI, DX_CHASER }

@Composable
fun IntegratedContestWorkspace(
    runtime: ContestRuntime,
    keyer: KeyerQueueSnapshot,
    onOpenRadio: () -> Unit,
    onOpenLogbook: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CONTEST · ${runtime.activeSession.role.name.replace('_', ' ')} · ${runtime.activeSession.state.name}",
                    style = MaterialTheme.typography.titleMedium)
                Text("Keyer ${keyer.state.name} · N1MM ${if (runtime.n1mm.active) "ACTIVE" else "OFF"} · ${runtime.lastMessage}",
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onOpenRadio) { Text("FAST ENTRY / RADIO") }
                    OutlinedButton(onOpenLogbook) { Text("LOGBOOK") }
                    OutlinedButton(onOpenProgress) { Text("LOG INTELLIGENCE") }
                    OutlinedButton(onOpenSettings) { Text("SETTINGS") }
                }
            }
        }
        ContestWorkspace(runtime.workspaceState(), ContestWorkspaceCallbacks(
            onPage = runtime::setPage,
            onCallsign = runtime::updateCallsign,
            onExchange = runtime::setExchange,
            onLog = { runtime.logCurrent() },
            onClear = { runtime.updateCallsign(""); runtime.setExchange("") },
            onRole = runtime::changeRole,
            onStartSession = runtime::startSession,
            onKeyerIntent = { runtime.dispatchKeyer(it) },
            onNetworkStart = { runtime.setNetworkArmed(true) },
            onNetworkStop = { runtime.setNetworkArmed(false) },
            onTrustedModeReview = runtime::reviewTrustedMode,
            onExport = runtime::validateExport,
        ), Modifier.weight(1f))
    }
}

@Composable
fun IntegratedDigiWorkspace(
    page: IntegratedDigiPage,
    onPage: (IntegratedDigiPage) -> Unit,
    digi: DigiController,
    radio: RadioState,
    compact: Boolean,
    chaser: DxChaserRuntime,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            IntegratedDigiPage.entries.forEachIndexed { index, value ->
                SegmentedButton(page == value, { onPage(value) }, SegmentedButtonDefaults.itemShape(index, IntegratedDigiPage.entries.size)) {
                    Text(if (value == IntegratedDigiPage.DIGI) "Digi" else "DX Chaser")
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when (page) {
                IntegratedDigiPage.DIGI -> DigiScreen(digi, radio, compact)
                IntegratedDigiPage.DX_CHASER -> DxChaserScreen(chaser.snapshot,
                    onStartAssist = chaser::startAssist,
                    onStartChase = { chaser.startChase() },
                    onStartDryRun = chaser::startDryRun,
                    onStop = { chaser.stop() },
                    onCandidate = chaser::select,
                    onCrossBandReview = chaser::review,
                    settings = chaser.settings,
                    onSettingsChanged = chaser::updateSettings)
            }
        }
    }
}

@Composable
fun IntegratedOperationsSettings(
    contest: ContestRuntime,
    chaser: DxChaserRuntime,
    onOpenContest: () -> Unit,
    onOpenChaser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CONTEST · N1MM · DX CHASER", style = MaterialTheme.typography.titleMedium)
            Text("Contest ${contest.activeSession.state} · N1MM ${if (contest.snapshot().n1mmEnabled) "configured" else "disabled by default"} · " +
                "DX Chaser ${chaser.snapshot.session.state}")
            Text("Restored configuration never restores a running Contest, N1MM arm, Keyer arm, Digi TX enable, or Chaser session.",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onOpenContest) { Text("OPEN CONTEST") }
                OutlinedButton(onOpenChaser) { Text("OPEN DX CHASER") }
            }
        }
    }
}
