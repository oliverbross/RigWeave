package app.rigweave.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun WavelogNativeDialog(controller: WavelogNativeController, wavelog: WavelogController, dismiss: () -> Unit) {
    val inspection = controller.inspection
    val binding = controller.binding
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("NATIVE WAVELOG LINK") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("Local SQLite remains authoritative. API v2 is an optional remote peer; one writable binding is allowed.")
                    Text(if (wavelog.apiKey.startsWith("wl2_")) "API v2 token is stored in Android Keystore-backed encrypted preferences."
                        else "Enter a wl2_ API v2 token in Settings. A legacy key is not treated as a bearer token.", color = Color(0xFFE9A72B))
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ controller.inspect() }, enabled = !controller.busy && wavelog.apiKey.startsWith("wl2_")) { Text("INSPECT TOKEN") }
                        inspection?.token?.let { token ->
                            AssistChip({}, { Text(if (token.capabilities.canWriteQsos) "READ / WRITE" else "READ ONLY") })
                        }
                    }
                }
                inspection?.let { result ->
                    item {
                        val scopes = result.token.scopes.sorted().joinToString(", ").ifBlank { "No scopes reported" }
                        Text("Owner ${result.token.owner.ifBlank { "unknown" }}", fontWeight = FontWeight.Bold)
                        Text("Scopes: $scopes")
                        result.token.expiresAt.takeIf(String::isNotBlank)?.let { Text("Expires: $it") }
                    }
                    item { HorizontalDivider() }
                    items(result.stations, key = { it.id }) { station ->
                        val selected = binding?.remoteStationId == station.id
                        Card(colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF314B3C) else Color(0xFF283139))) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(listOf(station.name, station.callsign).filter(String::isNotBlank).joinToString(" · "), fontWeight = FontWeight.Bold)
                                Text(listOf(station.grid, "ID ${station.id}", station.uuid).filter(String::isNotBlank).joinToString(" · "))
                                Text("Map one local station explicitly:", color = Color(0xFFA5ADB2))
                                controller.localStationIds.forEach { localId ->
                                    val mapped = selected && binding?.localStationProfileId == localId
                                    OutlinedButton({ controller.bindStation(station, localId) }, enabled = !controller.busy) {
                                        Text(if (mapped) "MAPPED · ${controller.localStationLabel(localId)}"
                                            else "MAP · ${controller.localStationLabel(localId)}")
                                    }
                                }
                            }
                        }
                    }
                }
                binding?.let { active ->
                    item { HorizontalDivider() }
                    item {
                        Text(active.remoteStationName.ifBlank { "Station ${active.remoteStationId}" }, fontWeight = FontWeight.Bold)
                        Text("Local ${controller.localStationLabel(active.localStationProfileId)} ↔ remote station ${active.remoteStationId}")
                        Text(if (active.state == WavelogBindingState.READ_ONLY) "READ-ONLY LOCAL REPLICA · local changes cannot be pushed"
                            else "TWO-WAY LINK · downstream direct uploads remain governed by Sync Hub authority")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedButton({ controller.initialSync() }, enabled = !controller.busy) { Text("INITIAL") }
                            OutlinedButton({ controller.quickSync() }, enabled = !controller.busy) { Text("QUICK") }
                            OutlinedButton({ controller.fullReconcile() }, enabled = !controller.busy) { Text("FULL") }
                        }
                        if (controller.busy) {
                            OutlinedButton({ controller.cancelSync() }) { Text("CANCEL AFTER CURRENT PAGE") }
                        }
                    }
                    controller.lastSummary?.let { summary -> item {
                        Text("${summary.imported} imported · ${summary.linked} linked · ${summary.merged} merged · ${summary.ambiguous} ambiguous")
                        Text("${summary.pages} pages · resumed at ${summary.resumedFromPage} · " +
                            when {
                                summary.cancelled -> "cancelled safely"
                                summary.scope == "QUICK" -> "bounded recent overlap complete · historic edits require FULL"
                                summary.completed -> "full available history complete"
                                else -> "paused, resumable"
                            })
                        Text("${controller.openConflicts} open conflicts", color = if (controller.openConflicts > 0) Color(0xFFE4544D) else Color(0xFF42C77B))
                    } }
                    items(controller.conflicts.filter { it.state == WavelogConflictState.OPEN }, key = { it.id }) { conflict ->
                        WavelogConflictCard(controller, conflict)
                    }
                }
                item { Text(controller.status, color = Color(0xFFA5ADB2)) }
            }
        },
        confirmButton = { TextButton(dismiss) { Text("CLOSE") } },
    )
}

@Composable
private fun WavelogConflictCard(controller: WavelogNativeController, conflict: WavelogConflict) {
    val local = remember(conflict.id) { CanonicalQso.decode(conflict.localCanonical) }
    val remote = remember(conflict.id) { CanonicalQso.decode(conflict.remoteCanonical) }
    val merged = remember(conflict.id) { mutableStateMapOf<String, String>().apply { putAll(local.fields) } }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4A2D2D))) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("CONFLICT · ${conflict.localQsoId}", fontWeight = FontWeight.Bold)
            conflict.conflictingFields.sorted().forEach { field ->
                if (field == "LOCAL_DELETE" || field == "REMOTE_DELETE") {
                    Text(if (field == "LOCAL_DELETE") "Deleted locally, but the remote QSO changed."
                        else "Deleted remotely, but the local QSO changed.")
                } else {
                    Text(field, fontWeight = FontWeight.Bold)
                    Text("Local: ${local.fields[field].orEmpty()}")
                    Text("Remote: ${remote.fields[field].orEmpty()}")
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton({ merged[field] = local.fields[field].orEmpty() }) { Text("USE LOCAL") }
                        OutlinedButton({ merged[field] = remote.fields[field].orEmpty() }) { Text("USE REMOTE") }
                    }
                    Text("Merged choice: ${merged[field].orEmpty()}", color = Color(0xFFA5ADB2))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton({ controller.resolveConflict(conflict, WavelogConflictState.KEEP_LOCAL) },
                    enabled = !controller.busy) { Text("KEEP LOCAL") }
                OutlinedButton({ controller.resolveConflict(conflict, WavelogConflictState.KEEP_REMOTE) },
                    enabled = !controller.busy) { Text("KEEP REMOTE") }
                Button({ controller.resolveConflict(conflict, WavelogConflictState.MERGED, merged.toMap()) },
                    enabled = !controller.busy && conflict.conflictingFields.none { it.endsWith("_DELETE") }) { Text("APPLY MERGED") }
            }
        }
    }
}
