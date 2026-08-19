package app.rigweave.mobile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private enum class PortableMode { CHASE, ACTIVATE }

@Composable
internal fun PortableWorkspaceScreen(portable: PortableController, activation: PotaActivationController, radio: RadioState,
    stationGrid: String, foreground: Boolean, compact: Boolean, app: AppController, database: QsoDatabase,
    mutations: QsoMutationCoordinator, wavelog: WavelogController, callbook: CallbookController, cty: CtyController, onTune: (PortableSpot) -> Unit,
    onTuneAndLog: (PortableSpot) -> Unit, intelligenceNeeds: Map<String, List<String>> = emptyMap(), onOpenLogbook: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(if (activation.pendingP2p != null) PortableMode.ACTIVATE else PortableMode.CHASE) }
    LaunchedEffect(activation.pendingP2p?.token) { if (activation.pendingP2p != null) mode = PortableMode.ACTIVATE }
    LaunchedEffect(activation.openToken) { if (activation.openToken > 0) mode = PortableMode.ACTIVATE }
    Column(Modifier.fillMaxSize()) {
        PotaActivationStrip(activation, radio) { mode = PortableMode.ACTIVATE }
        SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            PortableMode.entries.forEachIndexed { index, item ->
                SegmentedButton(mode == item, { mode = item }, SegmentedButtonDefaults.itemShape(index, PortableMode.entries.size)) {
                    Text(item.name)
                }
            }
        }
        when (mode) {
            PortableMode.CHASE -> PortableChaseScreen(portable, radio, stationGrid, foreground, compact, onTune, onTuneAndLog,
                activation.session?.state == PotaActivationState.ACTIVE, intelligenceNeeds) { spot -> activation.prepareP2p(spot); mode = PortableMode.ACTIVATE }
            PortableMode.ACTIVATE -> PotaActivateScreen(activation, portable.pota, radio, app, database, mutations,
                wavelog, callbook, cty, compact, onOpenLogbook)
        }
    }
}
