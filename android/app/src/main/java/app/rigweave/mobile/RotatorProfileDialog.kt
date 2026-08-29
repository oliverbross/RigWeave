package app.rigweave.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.rigweave.mobile.rotator.*
import java.util.UUID

private const val ROTATOR_USB = "USB"
private const val ROTATOR_TCP = "TCP"

@Composable
internal fun RotatorProfileDialog(
    runtime: AndroidRotatorRuntime,
    existing: RotatorDeviceProfile?,
    onDismiss: () -> Unit,
    onSave: (RotatorDeviceProfile) -> Unit,
) {
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var backend by remember(existing) { mutableStateOf(existing?.backend ?: RotatorBackend.NATIVE) }
    var protocol by remember(existing) { mutableStateOf(existing?.protocol ?: RotatorProtocolKind.GS232) }
    var connection by remember(existing) {
        mutableStateOf(if (existing?.backend == RotatorBackend.EMBEDDED_HAMLIB && existing.hamlibTcp != null) ROTATOR_TCP
            else if (existing?.transport == RotatorTransportKind.TCP || existing?.transport == RotatorTransportKind.ROTCTLD) ROTATOR_TCP else ROTATOR_USB)
    }
    var stableIdentity by remember(existing) { mutableStateOf(existing?.serial?.stableIdentityHash ?: existing?.hamlibSerial?.stableIdentityHash.orEmpty()) }
    var baudText by remember(existing) { mutableStateOf((existing?.serial?.baud ?: existing?.hamlibSerial?.baud ?: 9_600).toString()) }
    var host by remember(existing) { mutableStateOf(existing?.tcp?.host ?: existing?.hamlibTcp?.host ?: "127.0.0.1") }
    var portText by remember(existing) { mutableStateOf((existing?.tcp?.port ?: existing?.hamlibTcp?.port ?: 4_533).toString()) }
    var lanOptIn by remember(existing) { mutableStateOf(existing?.tcp?.lanOptIn ?: existing?.hamlibTcp?.lanOptIn ?: false) }
    var hamlibModelId by remember(existing) { mutableStateOf(existing?.hamlibModelId) }
    var hamlibSearch by remember { mutableStateOf("") }
    var pollText by remember(existing) { mutableStateOf((existing?.pollIntervalMs ?: 1_000).toString()) }
    var serialCandidates by remember { mutableStateOf(emptyList<RotatorSerialCandidate>()) }
    var hamlibModels by remember { mutableStateOf(emptyList<RotatorHamlibModelDescriptor>()) }

    LaunchedEffect(Unit) {
        serialCandidates = runtime.refreshSerialCandidates()
        hamlibModels = runCatching { runtime.hamlibModels() }.getOrDefault(emptyList())
    }
    val profileResult = remember(name, backend, protocol, connection, stableIdentity, baudText, host, portText,
        lanOptIn, hamlibModelId, pollText, existing) {
        runCatching { buildRotatorProfile(existing, name, backend, protocol, connection, stableIdentity,
            baudText.toIntOrNull(), host, portText.toIntOrNull(), lanOptIn, hamlibModelId, pollText.toIntOrNull()) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add rotator profile" else "Edit rotator profile") },
        text = {
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                item {
                    Text("Saving never connects, probes, parks, moves, or arms automation.", color = MaterialTheme.colorScheme.tertiary)
                    OutlinedTextField(name, { name = it.take(64) }, label = { Text("Profile name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                item {
                    Text("BACKEND", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        RotatorBackend.entries.forEach { value ->
                            FilterChip(backend == value, {
                                backend = value
                                protocol = when (value) {
                                    RotatorBackend.NATIVE -> RotatorProtocolKind.GS232
                                    RotatorBackend.REMOTE_ROTCTLD -> RotatorProtocolKind.ROTCTLD
                                    RotatorBackend.EMBEDDED_HAMLIB -> RotatorProtocolKind.HAMLIB
                                }
                                connection = if (value == RotatorBackend.REMOTE_ROTCTLD) ROTATOR_TCP else ROTATOR_USB
                            }, { Text(value.name.replace('_', ' ')) })
                        }
                    }
                }
                if (backend == RotatorBackend.NATIVE) item {
                    Text("PROTOCOL", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        nativeRotatorProtocols.forEach { value ->
                            FilterChip(protocol == value, { protocol = value }, { Text(value.name.replace('_', ' ')) })
                        }
                    }
                }
                if (backend != RotatorBackend.REMOTE_ROTCTLD) item {
                    Text("CONNECTION", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(ROTATOR_USB, ROTATOR_TCP).forEach { value ->
                            FilterChip(connection == value, { connection = value }, { Text(value) })
                        }
                    }
                }
                if (connection == ROTATOR_USB && backend != RotatorBackend.REMOTE_ROTCTLD) item {
                    Text("USB SERIAL DEVICE", fontWeight = FontWeight.Bold)
                    if (serialCandidates.isEmpty()) Text("No USB serial adapter is currently visible. Attach it, then refresh; a stable device identity is required.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        serialCandidates.forEach { candidate ->
                            FilterChip(stableIdentity == candidate.stableIdentityHash,
                                { stableIdentity = candidate.stableIdentityHash }, { Text(candidate.label) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ serialCandidates = runtime.refreshSerialCandidates() }) { Text("REFRESH USB") }
                        OutlinedTextField(baudText, { baudText = it.filter(Char::isDigit).take(6) },
                            label = { Text("Baud") }, singleLine = true, modifier = Modifier.width(150.dp))
                    }
                }
                if (connection == ROTATOR_TCP || backend == RotatorBackend.REMOTE_ROTCTLD) item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(host, { host = it.trim().take(253) }, label = { Text("Host") },
                            singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(portText, { portText = it.filter(Char::isDigit).take(5) }, label = { Text("Port") },
                            singleLine = true, modifier = Modifier.width(130.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Switch(lanOptIn, { lanOptIn = it })
                        Column {
                            Text("ALLOW TRUSTED LAN", fontWeight = FontWeight.Bold)
                            Text("Required for non-loopback endpoints. Saving still remains disconnected.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (backend == RotatorBackend.EMBEDDED_HAMLIB) item {
                    Text("HAMLIB ROTATOR MODEL", fontWeight = FontWeight.Bold)
                    OutlinedTextField(hamlibSearch, { hamlibSearch = it.take(80) }, label = { Text("Search ${hamlibModels.size} models") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    val matches = hamlibModels.filter { hamlibSearch.isBlank() ||
                        "${it.manufacturer} ${it.model} ${it.id}".contains(hamlibSearch, true) }.take(60)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        matches.forEach { model ->
                            FilterChip(hamlibModelId == model.id, { hamlibModelId = model.id },
                                { Text("${model.manufacturer} ${model.model} · ${model.id}") })
                        }
                    }
                }
                item {
                    OutlinedTextField(pollText, { pollText = it.filter(Char::isDigit).take(5) }, label = { Text("Poll interval (ms)") },
                        singleLine = true, modifier = Modifier.width(190.dp))
                    profileResult.exceptionOrNull()?.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { Button({ profileResult.getOrNull()?.let(onSave) }, enabled = profileResult.isSuccess) { Text("SAVE DISCONNECTED") } },
        dismissButton = { TextButton(onDismiss) { Text("CANCEL") } },
    )
}

private val nativeRotatorProtocols = listOf(
    RotatorProtocolKind.GS232, RotatorProtocolKind.DCU_ROTOREZ, RotatorProtocolKind.EASYCOMM,
    RotatorProtocolKind.SPID_ROT1, RotatorProtocolKind.SPID_ROT2,
)

internal fun buildRotatorProfile(
    existing: RotatorDeviceProfile?, name: String, backend: RotatorBackend,
    requestedProtocol: RotatorProtocolKind, connection: String, stableIdentity: String, baud: Int?,
    host: String, port: Int?, lanOptIn: Boolean, hamlibModelId: Int?, pollIntervalMs: Int?,
): RotatorDeviceProfile {
    val profileName = name.trim()
    require(profileName.isNotBlank()) { "Enter a profile name." }
    val poll = requireNotNull(pollIntervalMs) { "Enter a valid poll interval." }
    val protocol = when (backend) {
        RotatorBackend.NATIVE -> requestedProtocol.also { require(it in nativeRotatorProtocols) { "Choose a native protocol." } }
        RotatorBackend.REMOTE_ROTCTLD -> RotatorProtocolKind.ROTCTLD
        RotatorBackend.EMBEDDED_HAMLIB -> RotatorProtocolKind.HAMLIB
    }
    val useTcp = backend == RotatorBackend.REMOTE_ROTCTLD || connection == ROTATOR_TCP
    val serialSettings = if (!useTcp) SerialSettings(stableIdentity, requireNotNull(baud) { "Enter a valid baud rate." }) else null
    val tcpSettings = if (useTcp) TcpSettings(host.trim(), requireNotNull(port) { "Enter a valid port." }, lanOptIn = lanOptIn).also {
        require(lanOptIn || it.host in setOf("127.0.0.1", "localhost", "::1")) { "Enable trusted LAN for a non-loopback endpoint." }
    } else null
    if (backend == RotatorBackend.EMBEDDED_HAMLIB) require(hamlibModelId != null) { "Choose a Hamlib rotator model." }
    return RotatorDeviceProfile(
        id = existing?.id ?: UUID.randomUUID().toString(), name = profileName, backend = backend, protocol = protocol,
        transport = when (backend) {
            RotatorBackend.NATIVE -> if (useTcp) RotatorTransportKind.TCP else RotatorTransportKind.SERIAL
            RotatorBackend.REMOTE_ROTCTLD -> RotatorTransportKind.ROTCTLD
            RotatorBackend.EMBEDDED_HAMLIB -> RotatorTransportKind.EMBEDDED_HAMLIB
        },
        serial = serialSettings.takeIf { backend == RotatorBackend.NATIVE },
        tcp = tcpSettings.takeIf { backend != RotatorBackend.EMBEDDED_HAMLIB },
        hamlibModelId = hamlibModelId.takeIf { backend == RotatorBackend.EMBEDDED_HAMLIB },
        hamlibSerial = serialSettings.takeIf { backend == RotatorBackend.EMBEDDED_HAMLIB },
        hamlibTcp = tcpSettings.takeIf { backend == RotatorBackend.EMBEDDED_HAMLIB },
        connectOnForeground = existing?.connectOnForeground ?: false, pollIntervalMs = poll,
        limits = existing?.limits ?: RotatorLimits(), parkAzimuthDeg = existing?.parkAzimuthDeg,
        parkElevationDeg = existing?.parkElevationDeg, headingOffsetOwner = existing?.headingOffsetOwner ?: HeadingOffsetOwner.NONE,
        calibrationOffsetDeg = existing?.calibrationOffsetDeg ?: 0.0, allowFlipOver = existing?.allowFlipOver ?: false,
        forbiddenSectors = existing?.forbiddenSectors.orEmpty(), capabilityOverrides = existing?.capabilityOverrides.orEmpty(),
        capabilityOverrideProvenance = existing?.capabilityOverrideProvenance, presets = existing?.presets.orEmpty(),
    )
}
