package app.rigweave.mobile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.rigweave.mobile.rotator.RotatorAction
import app.rigweave.mobile.rotator.RotatorWorkspace
import kotlinx.coroutines.launch

private data class PendingRotatorMotion(val action: RotatorAction, val azimuth: Double?, val elevation: Double?)
private val RotatorInk = Color(0xFFF4F0E7)
private val RotatorMuted = Color(0xFFA5ADB2)

@Composable
fun IntegratedRotatorScreen(runtime: AndroidRotatorRuntime) {
    val scope = rememberCoroutineScope()
    var selectedId by remember(runtime.profiles) { mutableStateOf(runtime.state?.profileId ?: runtime.profiles.firstOrNull()?.id) }
    var pending by remember { mutableStateOf<PendingRotatorMotion?>(null) }
    val profiles = runtime.profiles
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (profiles.isEmpty()) {
            Text("NO ROTATOR PROFILE", color = RotatorInk, fontWeight = FontWeight.Bold)
            Text("Import or restore a Rotator configuration section in Settings; restoration never connects or moves hardware.", color = RotatorMuted)
            return@Column
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            profiles.forEach { profile -> FilterChip(selectedId == profile.id, { selectedId = profile.id }, { Text(profile.name) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ selectedId?.let { scope.launch { runtime.connect(it, readOnlyProbe = false) } } }, enabled = runtime.state?.connected != true) {
                Text("CONNECT")
            }
            OutlinedButton({ selectedId?.let { scope.launch { runtime.connect(it, readOnlyProbe = true) } } }, enabled = runtime.state?.connected != true) {
                Text("READ-ONLY TEST")
            }
            OutlinedButton({ scope.launch { runtime.disconnect() } }, enabled = runtime.state?.connected == true) { Text("DISCONNECT") }
        }
        RotatorWorkspace(
            state = runtime.state,
            capabilities = runtime.capabilities,
            assignment = runtime.store.snapshot().bandAssignments.firstOrNull { it.rotatorProfileId == runtime.state?.profileId },
            automation = runtime.automation,
            candidates = emptyList(),
            diagnostics = runtime.controller.diagnostics(),
            onAction = { action, azimuth, elevation ->
                if (action == RotatorAction.STOP) scope.launch { runtime.stopAndDisarm() }
                else if (action in setOf(RotatorAction.MOVE_ABSOLUTE, RotatorAction.PARK, RotatorAction.SELECT_PRESET, RotatorAction.JOG)) {
                    pending = PendingRotatorMotion(action, azimuth, elevation)
                } else scope.launch { runtime.submit(action, azimuth, elevation) }
            },
            modifier = Modifier.weight(1f),
        )
    }
    pending?.let { motion ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Confirm physical rotator action") },
            text = { Text("${motion.action}${motion.azimuth?.let { " · %.1f°".format(it) }.orEmpty()} will command the selected physical movement backend. Confirm only when the antenna system is clear.") },
            confirmButton = { Button({
                pending = null
                scope.launch { runtime.submit(motion.action, motion.azimuth, motion.elevation) }
            }) { Text("CONFIRM MOVEMENT") } },
            dismissButton = { TextButton({ pending = null }) { Text("CANCEL") } },
        )
    }
}
