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
                                OutlinedButton({ controller.bindStation(station) }, enabled = !controller.busy) {
                                    Text(if (selected) "BOUND" else "BIND THIS STATION")
                                }
                            }
                        }
                    }
                }
                binding?.let { active ->
                    item { HorizontalDivider() }
                    item {
                        Text(active.remoteStationName.ifBlank { "Station ${active.remoteStationId}" }, fontWeight = FontWeight.Bold)
                        Text(if (active.state == WavelogBindingState.READ_ONLY) "READ-ONLY LOCAL REPLICA · local changes cannot be pushed"
                            else "TWO-WAY LINK · downstream direct uploads remain governed by Sync Hub authority")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedButton({ controller.initialSync() }, enabled = !controller.busy) { Text("INITIAL") }
                            OutlinedButton({ controller.quickSync() }, enabled = !controller.busy) { Text("QUICK") }
                            OutlinedButton({ controller.fullReconcile() }, enabled = !controller.busy) { Text("FULL") }
                        }
                    }
                    controller.lastSummary?.let { summary -> item {
                        Text("${summary.imported} imported · ${summary.linked} linked · ${summary.merged} merged · ${summary.ambiguous} ambiguous")
                        Text("${controller.openConflicts} open conflicts", color = if (controller.openConflicts > 0) Color(0xFFE4544D) else Color(0xFF42C77B))
                    } }
                }
                item { Text(controller.status, color = Color(0xFFA5ADB2)) }
            }
        },
        confirmButton = { TextButton(dismiss) { Text("CLOSE") } },
    )
}
