package app.rigweave.mobile.contest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable internal fun ContestLoggingScreen(
    state: ContestWorkspaceState,
    callbacks: ContestWorkspaceCallbacks,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
) {
    var showLayout by remember { mutableStateOf(false) }
    val gap = when (state.layout.density) { ContestPanelDensity.DENSE -> 5.dp; ContestPanelDensity.COMPACT -> 8.dp; ContestPanelDensity.NORMAL -> 12.dp }
    Column(modifier.padding(10.dp).onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.Enter -> { callbacks.onEnterMessage(); true }
            Key.Escape -> { callbacks.onClear(); true }
            else -> false
        }
    }, verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("${state.definition.humanName} · ${state.session.state} · ${state.session.role.name.replace('_', ' ')}",
                    style = MaterialTheme.typography.titleMedium)
                Text("${state.operatingBand} · ${state.operatingMode} · " +
                    if (state.operatingFrequencyHz > 0) "%.3f MHz".format(state.operatingFrequencyHz / 1_000_000.0) else "FREQUENCY UNAVAILABLE",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ContestOperatingRole.entries.forEach { role ->
                FilterChip(state.session.role == role, { callbacks.onRole(role) }, { Text(if (role == ContestOperatingRole.RUN) "RUN" else "S&P") })
            }
            OutlinedButton({ showLayout = true }) { Text("PANELS") }
        }
        if (wide) {
            if (ContestPanel.QSO_ENTRY in state.layout.panels) ContestQsoEntryPanel(state, callbacks, true)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                ContestPanelColumn(state, callbacks, state.layout.panels.filter { it in setOf(ContestPanel.BAND_MAP, ContestPanel.CLUSTER) },
                    Modifier.weight(1.45f), gap, true)
                ContestPanelColumn(state, callbacks, state.layout.panels.filterNot { it in setOf(ContestPanel.QSO_ENTRY, ContestPanel.BAND_MAP, ContestPanel.CLUSTER) },
                    Modifier.weight(1f), gap, true)
            }
        } else ContestPanelColumn(state, callbacks, state.layout.panels, Modifier.fillMaxSize(), gap, false)
    }
    if (showLayout) ContestLayoutDialog(state.layout, callbacks.onLayout) { showLayout = false }
}

@Composable private fun ContestPanelColumn(
    state: ContestWorkspaceState,
    callbacks: ContestWorkspaceCallbacks,
    panels: List<ContestPanel>,
    modifier: Modifier,
    gap: androidx.compose.ui.unit.Dp,
    wide: Boolean,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        items(panels, key = ContestPanel::name) { panel ->
            when (panel) {
                ContestPanel.QSO_ENTRY -> ContestQsoEntryPanel(state, callbacks, wide)
                ContestPanel.BAND_MAP -> ContestRowsPanel("BAND MAP · READ ONLY", state.bandMapRows,
                    "No current Contest-band opportunities in the existing Band Map snapshot")
                ContestPanel.CLUSTER -> ContestRowsPanel("CLUSTER / SPOTS · EXISTING SNAPSHOT", state.clusterRows,
                    "No current provider rows")
                ContestPanel.MULTIPLIERS -> ContestPanelCard("MULTIPLIERS") {
                    Text("New now · ${state.newMultipliers.joinToString { it.name }.ifBlank { "none" }}")
                    state.score.multipliers.forEach { (type, count) -> Text("${type.name.replace('_', ' ')} · $count") }
                    if (state.score.multipliers.isEmpty()) Text("No multiplier totals yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ContestPanel.SCORE_RATE -> ContestScorePanel(state)
                ContestPanel.RECENT_QSOS -> ContestPanelCard("RECENT QSOS") {
                    if (state.reviewRows.isEmpty()) Text("No temporary Contest entries yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.reviewRows.take(8).forEach { row ->
                        Text("${utcTime(row.createdAt)} · ${row.callsign} · ${row.band} ${row.mode} · ${"%.3f".format(row.frequencyHz / 1_000_000.0)}")
                    }
                }
                ContestPanel.KEYER_HOTKEYS -> ContestPanelCard("KEYER / HOTKEY STRIP") {
                    Text(state.keyerStatus)
                    Text("Enter sends the rule-context message through the existing Keyer adapter. Esc clears/stops; F-key profiles remain owned by Keyer.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(callbacks.onEnterMessage, enabled = state.session.state == ContestSessionState.RUNNING) { Text("SEND CONTEXT MESSAGE") }
                }
                ContestPanel.NETWORK_STATUS -> ContestPanelCard("NETWORK STATUS") {
                    Text("${state.network.mode} · ${if (state.network.active) "ACTIVE" else "OFF"} · ${if (state.network.armed) "ARMED" else "SAFE"}")
                    Text("${state.network.peers.size} peers · ${state.network.counters.values.sum()} retained policy events")
                    if (state.network.lastError.isNotBlank()) Text(state.network.lastError, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable private fun ContestQsoEntryPanel(state: ContestWorkspaceState, callbacks: ContestWorkspaceCallbacks, wide: Boolean) {
    ContestPanelCard("QSO ENTRY") {
        val fields: @Composable RowScope.() -> Unit = {
            OutlinedTextField(state.callsign, callbacks.onCallsign, Modifier.weight(2.2f).semantics { contentDescription = "Contest callsign" },
                label = { Text("CALLSIGN") }, singleLine = true)
            OutlinedTextField(state.rstSent, callbacks.onRstSent, label = { Text("RST S") }, singleLine = true, modifier = Modifier.weight(.8f))
            OutlinedTextField(state.rstReceived, callbacks.onRstReceived, label = { Text("RST R") }, singleLine = true, modifier = Modifier.weight(.8f))
            state.definition.receivedExchange.filterNot { it == ContestExchangeField.RST }.forEach { field ->
                OutlinedTextField(state.receivedExchange[field].orEmpty(), { callbacks.onExchangeField(field, it) }, Modifier.weight(1.2f),
                    label = { Text(field.name.replace('_', ' ')) }, singleLine = true)
            }
            Button(callbacks.onLog, enabled = state.session.state == ContestSessionState.RUNNING && state.callsign.isNotBlank(), modifier = Modifier.heightIn(min = 56.dp)) { Text("LOG") }
        }
        if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), content = fields)
        else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(state.callsign, callbacks.onCallsign, Modifier.weight(2f), label = { Text("CALLSIGN") }, singleLine = true)
                Button(callbacks.onLog, enabled = state.session.state == ContestSessionState.RUNNING && state.callsign.isNotBlank()) { Text("LOG") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(state.rstSent, callbacks.onRstSent, label = { Text("RST S") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(state.rstReceived, callbacks.onRstReceived, label = { Text("RST R") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            state.definition.receivedExchange.filterNot { it == ContestExchangeField.RST }.forEach { field ->
                OutlinedTextField(state.receivedExchange[field].orEmpty(), { callbacks.onExchangeField(field, it) }, Modifier.fillMaxWidth(), label = { Text(field.name.replace('_', ' ')) }, singleLine = true)
            }
        }
        Text("${state.operatingBand} · ${state.operatingMode} · ${if (state.operatingFrequencyHz > 0) state.operatingFrequencyHz else "frequency unavailable"} · Dupe ${state.dupe.name}",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.scpSuggestions.isNotEmpty()) Text(state.scpSuggestions.take(8).joinToString(" · ") { "${it.callsign} ${it.state.name.replace('_', ' ')}" },
            color = MaterialTheme.colorScheme.tertiary)
        if (state.definition.receivedExchange.isEmpty()) {
            OutlinedTextField(state.exchange, callbacks.onExchange, Modifier.fillMaxWidth(), label = { Text("RECEIVED EXCHANGE") }, singleLine = true)
        }
        Text("Sent · ${state.definition.sentExchange.joinToString { it.name.replace('_', ' ') }} · temporary session log · serial commits only after canonical merge",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.validation.filter { it.field != null }.forEach { Text("${it.truth} · ${it.reason}", color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(callbacks.onClear) { Text("CLEAR / ESC") }
            OutlinedButton({ state.reviewRows.firstOrNull()?.let { callbacks.onEditQso(it.id, it.callsign, it.rstSent, it.rstReceived) } },
                enabled = state.reviewRows.isNotEmpty()) { Text("EDIT LAST") }
            OutlinedButton(callbacks.onOpenLogbook) { Text("OPEN LOGBOOK") }
        }
        Text(state.statusMessage, color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable private fun ContestRowsPanel(title: String, rows: List<ContestBandMapRow>, empty: String) {
    ContestPanelCard(title) {
        if (rows.isEmpty()) Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant)
        rows.take(8).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(row.callsign, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(92.dp))
                Text("${row.band} · ${"%.3f".format(row.frequencyHz / 1_000_000.0)}", modifier = Modifier.width(128.dp))
                Text(row.status, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (rows.size > 8) Text("+${rows.size - 8} more in the bounded snapshot", color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable private fun ContestPanelCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable private fun ContestLayoutDialog(layout: ContestPanelLayout, save: (ContestPanelLayout) -> Unit, dismiss: () -> Unit) {
    var panels by remember(layout) { mutableStateOf(layout.panels) }
    var density by remember(layout) { mutableStateOf(layout.density) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("CONTEST PANELS") }, text = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ContestPanelDensity.entries.forEach { value -> FilterChip(density == value, { density = value }, { Text(value.name) }) }
            }
            ContestPanel.entries.forEach { panel ->
                val visible = panel in panels
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Checkbox(visible, { checked -> panels = if (checked) panels + panel else panels - panel })
                    Text(panel.label, modifier = Modifier.weight(1f))
                    val index = panels.indexOf(panel)
                    TextButton({ if (index > 0) panels = panels.toMutableList().also { val item = it.removeAt(index); it.add(index - 1, item) } }, enabled = index > 0) { Text("↑") }
                    TextButton({ if (index in 0 until panels.lastIndex) panels = panels.toMutableList().also { val item = it.removeAt(index); it.add(index + 1, item) } },
                        enabled = index in 0 until panels.lastIndex) { Text("↓") }
                }
            }
        }
    }, confirmButton = { Button({ save(ContestPanelLayout(panels, density)); dismiss() }) { Text("SAVE") } },
        dismissButton = { Row { TextButton({ panels = ContestPanel.entries; density = ContestPanelDensity.NORMAL }) { Text("RESET") }; TextButton(dismiss) { Text("CANCEL") } } })
}

private fun utcTime(epoch: Long): String = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(epoch))
