package app.rigweave.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun FastEntryDialog(database: QsoDatabase, wavelog: WavelogController, operatorCallsign: String,
    onImported: (Int, Int) -> Unit, dismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }; var result by remember { mutableStateOf(FastEntryResult(emptyList(), emptyList())) }
    var validOnly by remember { mutableStateOf(false) }; var status by remember { mutableStateOf("") }
    fun parse() { result = FastEntryParser.parse(text, FastEntryDefaults(LocalDate.now(ZoneOffset.UTC), operatorCallsign,
        operatorCallsign, wavelog.stationId, wavelog.selectedStation?.grid.orEmpty())); status = "${result.rows.size} parsed · ${result.errors.size} errors · ${result.warnings.size} warnings" }
    AlertDialog(onDismissRequest = dismiss, title = { Text("NATIVE FAST ENTRY") }, text = {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 650.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("One QSO per line; context is inherited. Example: 20m ssb / 2134 OM0RX / 6 VK8ABC 57 59. Commands: date YYYY-MM-DD, day ++, TIMEZONE +2. Use <FIELD:value>, <comment>, [QSL message].") }
            item { OutlinedTextField(text, { text = it }, label = { Text("Fast Entry lines") }, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp)) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ parse() }) { Text("PARSE / PREVIEW") }; Row { Checkbox(validOnly, { validOnly = it }); Text("Import valid lines only") } } }
            item { Text(status) }
            if (result.issues.isNotEmpty()) items(result.issues, key = { "${it.line}-${it.message}" }) { issue -> Text("Line ${issue.line}: ${if (issue.warning) "warning" else "error"} · ${issue.message}") }
            if (result.rows.isNotEmpty()) { item { HorizontalDivider(); Text("PREVIEW", fontWeight = FontWeight.Bold) }
                items(result.rows, key = { it.qso.id }) { row -> Text("${row.line} · ${java.time.Instant.ofEpochSecond(row.qso.createdAt)} · ${row.qso.callsign} · ${row.qso.band} ${row.qso.mode} · ${row.qso.rstSent}/${row.qso.rstReceived}${row.inherited.takeIf(Set<String>::isNotEmpty)?.joinToString(prefix = " · inherited ").orEmpty()}") } }
        }
    }, confirmButton = { Button({ parse(); if (result.errors.isNotEmpty() && !validOnly) { status = "Nothing imported · fix errors or explicitly choose valid lines only"; return@Button }
        var added = 0; var skipped = 0; database.transaction { result.rows.forEach { if (database.save(it.qso, QsoOrigin.IMPORT)) added++ else skipped++ } }; onImported(added, skipped); dismiss()
    }, enabled = result.rows.isNotEmpty()) { Text("IMPORT") } }, dismissButton = { TextButton(dismiss) { Text("CANCEL") } })
}
