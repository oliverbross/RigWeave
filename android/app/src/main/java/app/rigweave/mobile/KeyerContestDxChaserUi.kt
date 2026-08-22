// SPDX-License-Identifier: GPL-3.0-only
package app.rigweave.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.rigweave.mobile.bandmap.BandMapController
import app.rigweave.mobile.contest.*
import app.rigweave.mobile.dxchaser.DxChaserScreen
import app.rigweave.mobile.keyer.KeyerQueueSnapshot

enum class IntegratedDigiPage { DIGI, DX_CHASER }

@Composable
internal fun IntegratedContestWorkspace(
    runtime: ContestRuntime,
    keyer: KeyerQueueSnapshot,
    bandMaps: BandMapController,
    features: FeatureController,
    onOpenLogbook: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bandMapRows = bandMaps.snapshot.rankedSpots.take(40).map { ranked ->
        val spot = ranked.spot
        ContestBandMapRow(spot.id, spot.displayCallsign, spot.frequencyHz, spot.band,
            "${spot.modeFamily.name} · priority ${ranked.score} · ${ranked.explanation}")
    }
    val clusterRows = features.liveSpots.take(40).map { spot ->
        ContestBandMapRow(spot.id, spot.callsign, spot.frequencyHz, spot.band,
            listOf(spot.mode, spot.country, spot.reason).filter(String::isNotBlank).joinToString(" · "))
    }
    val state = runtime.workspaceState(bandMapRows, clusterRows)
    Column(modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("CONTEST · ${state.session.role.name.replace('_', ' ')} · ${state.session.state.name}",
                        style = MaterialTheme.typography.titleMedium)
                    Text("${state.operatingBand} ${state.operatingMode} · ${if (state.operatingFrequencyHz > 0) "%.3f MHz".format(state.operatingFrequencyHz / 1_000_000.0) else "frequency unavailable"} · " +
                        "Keyer ${keyer.state.name} · N1MM ${if (state.network.active) "ACTIVE" else state.network.mode}",
                        style = MaterialTheme.typography.bodySmall)
                    Text(state.statusMessage, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                OutlinedButton({ runtime.pause("OPERATOR STOP") }, enabled = state.session.state == ContestSessionState.RUNNING) { Text("STOP") }
            }
        }
        ContestWorkspace(state, ContestWorkspaceCallbacks(
            onPage = runtime::setPage,
            onDefinition = runtime::selectDefinition,
            onNewSession = runtime::newSession,
            onCloneSession = runtime::cloneSession,
            onSession = runtime::updateSession,
            onSaveSession = runtime::saveSession,
            onPauseSession = { runtime.pause() },
            onCloseSession = runtime::closeSession,
            onCallsign = runtime::updateCallsign,
            onRstSent = runtime::updateRstSent,
            onRstReceived = runtime::updateRstReceived,
            onExchange = runtime::setExchange,
            onExchangeField = runtime::setExchangeField,
            onLog = { runtime.logCurrent() },
            onClear = runtime::clearEntry,
            onEditQso = runtime::updateQso,
            onDeleteQso = runtime::deleteQso,
            onRole = runtime::changeRole,
            onStartSession = runtime::startSession,
            onKeyerIntent = { runtime.dispatchKeyer(it) },
            onEnterMessage = runtime::sendCurrentMessage,
            onLayout = runtime::updateLayout,
            onNetworkConfig = runtime::configureNetwork,
            onNetworkStart = { runtime.setNetworkArmed(true) },
            onNetworkStop = { runtime.setNetworkArmed(false) },
            onPeerTrust = runtime::setPeerTrust,
            onTrustedModeReview = runtime::reviewTrustedMode,
            onExport = runtime::previewExport,
            onOpenLogbook = onOpenLogbook,
            onOpenSettings = onOpenSettings,
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
