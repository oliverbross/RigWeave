package app.rigweave.mobile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

enum class FieldProfile { DAY, NIGHT, FIELD }
enum class RadioFamily { ELECRAFT_KX3, ELECRAFT_KX2, FLEXRADIO }
enum class EqVisibilityPolicy { AUTO, SHOW, HIDE }

internal val RadioFamily.isElecraft: Boolean get() = this != RadioFamily.FLEXRADIO
internal val RadioFamily.displayName: String get() = when (this) {
    RadioFamily.ELECRAFT_KX3 -> "Elecraft KX3"
    RadioFamily.ELECRAFT_KX2 -> "Elecraft KX2"
    RadioFamily.FLEXRADIO -> "FlexRadio"
}

internal fun decodeRadioFamily(stored: String?): RadioFamily = when (stored) {
    null, "ELECRAFT_KX" -> RadioFamily.ELECRAFT_KX3
    else -> runCatching { RadioFamily.valueOf(stored) }.getOrDefault(RadioFamily.ELECRAFT_KX3)
}

internal fun legacyRadioFamily(profile: RadioConnectionProfile): RadioFamily = when (profile.backendKind) {
    RadioBackendKind.NATIVE_FLEX -> RadioFamily.FLEXRADIO
    RadioBackendKind.NATIVE_ELECRAFT -> if (profile.modelId.value == "KX2") RadioFamily.ELECRAFT_KX2 else RadioFamily.ELECRAFT_KX3
    else -> RadioFamily.ELECRAFT_KX3
}

internal fun eqDestinationVisible(policy: EqVisibilityPolicy, selectedRadio: RadioFamily): Boolean = when (policy) {
    EqVisibilityPolicy.AUTO -> selectedRadio.isElecraft
    EqVisibilityPolicy.SHOW -> true
    EqVisibilityPolicy.HIDE -> false
}

internal fun contestDestinationVisible(enabled: Boolean): Boolean = enabled

data class RadioPreset(
    val slot: Int,
    val name: String,
    val frequencyHz: Long,
    val mode: String,
    val bandwidthHz: Int,
    val color: Long,
)

internal const val SPOT_STATUS_CS = "CS"
internal const val SPOT_STATUS_DS = "DS"

internal val defaultSpotStatusColours = mapOf(
    "$SPOT_STATUS_CS:NC" to 0xFF43C7D9.toInt(),
    "$SPOT_STATUS_CS:NB" to 0xFFE9A72B.toInt(),
    "$SPOT_STATUS_CS:NM" to 0xFFC481D8.toInt(),
    "$SPOT_STATUS_CS:W" to 0xFFA5ADB2.toInt(),
    "$SPOT_STATUS_CS:C" to 0xFF42C77B.toInt(),
    "$SPOT_STATUS_DS:ATNO" to 0xFFE4544D.toInt(),
    "$SPOT_STATUS_DS:W/NB" to 0xFFE9A72B.toInt(),
    "$SPOT_STATUS_DS:C/NB" to 0xFF43C7D9.toInt(),
    "$SPOT_STATUS_DS:W" to 0xFFA5ADB2.toInt(),
    "$SPOT_STATUS_DS:C" to 0xFF42C77B.toInt(),
)

internal fun defaultSpotStatusColour(dimension: String, status: String): Int =
    defaultSpotStatusColours["$dimension:$status"] ?: 0xFFF4F0E7.toInt()

internal fun resolveSpotStatusColour(configured: Map<String, Int>, dimension: String, status: String?): Int =
    status?.takeIf(String::isNotBlank)?.let { configured["$dimension:$it"] ?: defaultSpotStatusColour(dimension, it) }
        ?: 0xFFF4F0E7.toInt()

class AppController(private val context: Context) {
    private val prefs = context.getSharedPreferences("rigweave-app", Context.MODE_PRIVATE)
    private val configurationRecovery = ConfigurationRecovery(context.applicationContext)
    private val needsDxccCountryColumnMigration = !prefs.getBoolean("logbook_dxcc_country_v1", false)
    var fieldProfile by mutableStateOf(runCatching { FieldProfile.valueOf(prefs.getString("profile", "DAY")!!) }.getOrDefault(FieldProfile.DAY)); private set
    var selectedRadioProfileId by mutableStateOf(RadioProfileCatalog.migrate(
        prefs.getString("radio_profile_id", null), prefs.getString("radio_family", null))); private set
    val selectedRadioProfile: RadioConnectionProfile get() = selectedProfile(selectedRadioProfileId)
    var radioFamily by mutableStateOf(legacyRadioFamily(selectedRadioProfile)); private set
    var preferredFlexStation by mutableStateOf(prefs.getString("flex_station", "").orEmpty()); private set
    var manualFlexIp by mutableStateOf(prefs.getString("flex_manual_ip", "").orEmpty().takeIf { it.isBlank() || manualFlexDiscovery(it) != null }.orEmpty()); private set
    var panadapterEnabled by mutableStateOf(prefs.getBoolean("panadapter_enabled", false)); private set
    var eqVisibilityPolicy by mutableStateOf(runCatching {
        EqVisibilityPolicy.valueOf(prefs.getString("eq_visibility_policy", EqVisibilityPolicy.AUTO.name).orEmpty())
    }.getOrDefault(EqVisibilityPolicy.AUTO)); private set
    var contestEnabled by mutableStateOf(prefs.getBoolean("contest_destination_enabled", true)); private set
    var rotatorEnabled by mutableStateOf(prefs.getBoolean("rotator_destination_enabled", false)); private set
    var transmitArmed by mutableStateOf(false); private set
    var cwMacrosArmed by mutableStateOf(false); private set
    var voiceMacrosArmed by mutableStateOf(false); private set
    var voiceTxLevel by mutableStateOf(prefs.getFloat("voice_tx_level", 0.20f).coerceIn(0.02f, 1f)); private set
    var stationCallsign by mutableStateOf(prefs.getString("station_call", RigWeaveDefaults.OPERATOR_CALLSIGN) ?: RigWeaveDefaults.OPERATOR_CALLSIGN); private set
    var stationName by mutableStateOf(prefs.getString("station_name", RigWeaveDefaults.OPERATOR_NAME) ?: RigWeaveDefaults.OPERATOR_NAME); private set
    var stationGrid by mutableStateOf(prefs.getString("station_grid", RigWeaveDefaults.OPERATOR_GRID) ?: RigWeaveDefaults.OPERATOR_GRID); private set
    var activationProgram by mutableStateOf(prefs.getString("activation_program", "NONE") ?: "NONE"); private set
    var activationReference by mutableStateOf(prefs.getString("activation_reference", "") ?: ""); private set
    var autoDim by mutableStateOf(prefs.getBoolean("auto_dim", true)); private set
    var alertTones by mutableStateOf(prefs.getBoolean("alert_tones", false)); private set
    var quietAlerts by mutableStateOf(prefs.getBoolean("quiet_alerts", false)); private set
    var brightness by mutableStateOf(prefs.getInt("brightness", 82)); private set
    var cqRepeatSeconds by mutableStateOf(prefs.getInt("cq_repeat", 3).coerceIn(CQ_REPEAT_MIN_SECONDS, CQ_REPEAT_MAX_SECONDS)); private set
    var favoriteBands by mutableStateOf(prefs.getString("favorites", "7.020,7.030,7.100,7.200,14.060,21.060")!!.split(",")); private set
    var spotStatusColours by mutableStateOf(loadSpotStatusColours()); private set
    val macroLabels = mutableStateListOf<String>().apply {
        repeat(CW_MACRO_COUNT) { index -> add(sanitizeCwMacroLabel(prefs.getString("macro_label_$index", defaultCwMacroLabel(index))
            ?: defaultCwMacroLabel(index))) }
    }
    val macroTexts = mutableStateListOf<String>().apply {
        repeat(CW_MACRO_COUNT) { index -> add(sanitizeCwMacroText(prefs.getString("macro_text_$index", "") ?: "")) }
    }
    val voiceMacroLabels = mutableStateListOf<String>().apply {
        repeat(VOICE_MACRO_COUNT) { index ->
            add(sanitizeVoiceMacroLabel(prefs.getString("voice_macro_label_$index", defaultVoiceMacroLabel(index)).orEmpty(), index))
        }
    }
    val presets = mutableStateListOf<RadioPreset>().apply { addAll(loadPresets()) }
    val visibleLogbookColumns = mutableStateListOf<LogbookColumn>().apply {
        val restored = decodeLogbookColumns(prefs.getString("logbook_columns", null))
        addAll(if (needsDxccCountryColumnMigration) ensureDxccCountryColumn(restored) else restored)
    }

    init {
        val defaults = prefs.edit()
        if (prefs.getString("radio_family", null) == "ELECRAFT_KX") defaults.putString("radio_family", radioFamily.name)
        defaults.putString("radio_profile_id", selectedRadioProfileId.value)
        if (!prefs.contains("station_call")) defaults.putString("station_call", stationCallsign)
        if (!prefs.contains("station_name")) defaults.putString("station_name", stationName)
        if (!prefs.contains("station_grid")) defaults.putString("station_grid", stationGrid)
        defaults.apply()
        if (needsDxccCountryColumnMigration) prefs.edit()
            .putString("logbook_columns", encodeLogbookColumns(visibleLogbookColumns))
            .putBoolean("logbook_dxcc_country_v1", true)
            .apply()
    }

    fun setProfile(value: FieldProfile) {
        fieldProfile = value; prefs.edit().putString("profile", value.name).apply()
    }

    fun selectRadioFamily(value: RadioFamily) {
        selectRadioProfile(when (value) {
            RadioFamily.ELECRAFT_KX3 -> RadioProfileCatalog.KX3
            RadioFamily.ELECRAFT_KX2 -> RadioProfileCatalog.KX2
            RadioFamily.FLEXRADIO -> RadioProfileCatalog.FLEX
        })
    }

    fun selectRadioProfile(value: RadioConnectionProfile) {
        disarmAll()
        selectedRadioProfileId = value.id
        radioFamily = legacyRadioFamily(value)
        prefs.edit().putString("radio_profile_id", value.id.value)
            .putString("radio_family", radioFamily.name)
            .apply()
    }

    fun selectHamlibModel(modelId: Int, manufacturer: String, model: String, network: Boolean = false) {
        require(modelId > 0)
        prefs.edit().putInt("hamlib_model_id", modelId)
            .putString("hamlib_manufacturer", manufacturer.take(80))
            .putString("hamlib_model", model.take(96))
            .putBoolean("hamlib_network", network)
            .apply()
        selectRadioProfile(selectedProfile(RadioProfileId("hamlib.${if (network) "network" else "embedded"}.$modelId")))
    }

    fun savePreferredFlexStation(value: String) {
        preferredFlexStation = value.take(64); prefs.edit().putString("flex_station", preferredFlexStation).apply()
    }

    fun saveManualFlexIp(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isNotEmpty() && manualFlexDiscovery(normalized) == null) return false
        manualFlexIp = normalized
        prefs.edit().putString("flex_manual_ip", manualFlexIp).apply()
        return true
    }

    fun updatePanadapterEnabled(value: Boolean) {
        panadapterEnabled = value
        prefs.edit().putBoolean("panadapter_enabled", value).apply()
    }

    fun updateEqVisibilityPolicy(value: EqVisibilityPolicy) {
        eqVisibilityPolicy = value
        prefs.edit().putString("eq_visibility_policy", value.name).apply()
    }

    fun updateContestEnabled(value: Boolean) {
        contestEnabled = value
        prefs.edit().putBoolean("contest_destination_enabled", value).apply()
    }

    fun updateRotatorEnabled(value: Boolean) {
        rotatorEnabled = value
        prefs.edit().putBoolean("rotator_destination_enabled", value).apply()
    }

    fun updateTransmitArmed(value: Boolean) { transmitArmed = value }
    fun updateCwMacrosArmed(value: Boolean) { cwMacrosArmed = value }
    fun updateVoiceMacrosArmed(value: Boolean) { voiceMacrosArmed = value }
    fun updateVoiceTxLevel(value: Float) {
        voiceTxLevel = value.coerceIn(0.02f, 1f)
        prefs.edit().putFloat("voice_tx_level", voiceTxLevel).apply()
    }

    fun spotStatusColour(dimension: String, status: String?): Int = resolveSpotStatusColour(spotStatusColours, dimension, status)

    fun setSpotStatusColour(dimension: String, status: String, colour: Int) {
        val key = "$dimension:$status"
        spotStatusColours = spotStatusColours + (key to (colour or 0xFF000000.toInt()))
        prefs.edit().putInt(spotStatusColourPreferenceKey(dimension, status), spotStatusColours.getValue(key)).apply()
    }

    fun resetSpotStatusColours() {
        val editor = prefs.edit()
        defaultSpotStatusColours.keys.forEach { key ->
            val (dimension, status) = key.split(':', limit = 2)
            editor.remove(spotStatusColourPreferenceKey(dimension, status))
        }
        editor.apply()
        spotStatusColours = defaultSpotStatusColours
    }
    fun saveVoiceMacroLabels(labels: List<String>) {
        val editor = prefs.edit()
        repeat(VOICE_MACRO_COUNT) { index ->
            voiceMacroLabels[index] = sanitizeVoiceMacroLabel(labels.getOrNull(index).orEmpty(), index)
            editor.putString("voice_macro_label_$index", voiceMacroLabels[index])
        }
        editor.apply()
    }
    fun disarmAll() {
        transmitArmed = false; cwMacrosArmed = false; voiceMacrosArmed = false
    }

    fun updateStationIdentity(call: String, name: String, grid: String) {
        stationCallsign = call.trim().uppercase(); stationName = name.trim(); stationGrid = grid.trim().uppercase()
        prefs.edit().putString("station_call", stationCallsign).putString("station_name", stationName)
            .putString("station_grid", stationGrid).apply()
    }

    fun saveLocalSettings(call: String, name: String, grid: String, repeat: Int,
        labels: List<String>, texts: List<String>) {
        updateStationIdentity(call, name, grid)
        cqRepeatSeconds = repeat.coerceIn(CQ_REPEAT_MIN_SECONDS, CQ_REPEAT_MAX_SECONDS)
        repeat(CW_MACRO_COUNT) { index ->
            macroLabels[index] = sanitizeCwMacroLabel(labels.getOrNull(index).orEmpty())
            macroTexts[index] = sanitizeCwMacroText(texts.getOrNull(index).orEmpty())
        }
        val editor = prefs.edit().putString("station_call", stationCallsign).putString("station_name", stationName)
            .putString("station_grid", stationGrid).putInt("cq_repeat", cqRepeatSeconds)
        repeat(CW_MACRO_COUNT) { index -> editor.putString("macro_label_$index", macroLabels[index])
            .putString("macro_text_$index", macroTexts[index]) }
        editor.apply()
    }

    fun saveFieldSettings(profile: FieldProfile, brightnessPercent: Int, dim: Boolean,
        tones: Boolean, quiet: Boolean, program: String, reference: String) {
        setProfile(profile); brightness = brightnessPercent.coerceIn(10, 100); autoDim = dim
        alertTones = tones; quietAlerts = quiet
        activationProgram = program.uppercase().let { if (it in listOf("NONE", "POTA", "SOTA", "WWFF")) it else "NONE" }
        activationReference = if (activationProgram == "NONE") "" else reference.uppercase()
        prefs.edit().putInt("brightness", brightness).putBoolean("auto_dim", autoDim)
            .putBoolean("alert_tones", alertTones).putBoolean("quiet_alerts", quietAlerts)
            .putString("activation_program", activationProgram).putString("activation_reference", activationReference).apply()
    }

    fun savePreset(slot: Int, state: RadioState, name: String) {
        if (!state.connected || state.frequencyHz <= 0) return
        presets.removeAll { it.slot == slot }
        presets += RadioPreset(slot, name.ifBlank { "Memory ${slot + 1}" }, state.frequencyHz, state.mode,
            state.bandwidthHz, presetColors[slot % presetColors.size])
        presets.sortBy { it.slot }; persistPresets()
    }

    fun deletePreset(slot: Int) {
        val remaining = presets.filterNot { it.slot == slot }.sortedBy { it.slot }.mapIndexed { index, item -> item.copy(slot = index) }
        presets.clear(); presets.addAll(remaining); persistPresets()
    }

    fun savePreset(slot: Int, frequencyHz: Long, mode: String, bandwidthHz: Int, colorIndex: Int) {
        if (slot !in 0 until 12 || !isValidRadioPreset(frequencyHz, mode, bandwidthHz)) return
        presets.removeAll { it.slot == slot }
        presets += RadioPreset(slot, "", frequencyHz, mode, bandwidthHz, presetColors[colorIndex.coerceIn(0, 5)])
        presets.sortBy { it.slot }; persistPresets()
    }

    fun movePreset(slot: Int, delta: Int) {
        val ordered = presets.sortedBy { it.slot }.toMutableList()
        val from = ordered.indexOfFirst { it.slot == slot }; val to = from + delta
        if (from !in ordered.indices || to !in ordered.indices) return
        val moved = ordered.removeAt(from); ordered.add(to, moved)
        presets.clear(); presets.addAll(ordered.mapIndexed { index, item -> item.copy(slot = index) }); persistPresets()
    }

    fun nextPresetSlot(): Int? = (0 until 12).firstOrNull { slot -> presets.none { it.slot == slot } }

    fun setLogbookColumnVisible(column: LogbookColumn, visible: Boolean) {
        if (!visible && column in visibleLogbookColumns && visibleLogbookColumns.size == 1) return
        val updated = visibleLogbookColumns.toMutableSet().apply {
            if (visible) add(column) else remove(column)
        }
        visibleLogbookColumns.clear()
        visibleLogbookColumns.addAll(LogbookColumn.entries.filter { it in updated })
        persistLogbookColumns()
    }

    fun showAllLogbookColumns() {
        visibleLogbookColumns.clear(); visibleLogbookColumns.addAll(LogbookColumn.entries); persistLogbookColumns()
    }

    fun backupNow(): String = runCatching {
        disarmAll()
        context.openFileOutput("rigweave-recovery.json", Context.MODE_PRIVATE).bufferedWriter().use {
            it.write(configurationRecovery.export())
        }
        "Backup captured locally"
    }.getOrElse { "Backup failed: ${it.message}" }

    fun verifyBackup(): String = runCatching {
        val preview = configurationRecovery.preview(recoveryText())
        "Recovery data verified · ${preview.sections.size} sections · ${preview.changeCount} changes"
    }.getOrElse { "Backup verification failed: ${it.message}" }

    fun recoveryPath(): String = context.filesDir.resolve("rigweave-recovery.json").absolutePath

    fun recoveryText(): String = context.openFileInput("rigweave-recovery.json").bufferedReader().use { it.readText() }

    fun reviewRecovery(text: String): String = runCatching {
        val preview = configurationRecovery.preview(text)
        "Valid recovery · ${preview.sections.size} sections · ${preview.changeCount} changes" +
            preview.sections.flatMap { it.mappingTasks }.takeIf(List<String>::isNotEmpty)?.joinToString("\n", prefix = "\nMapping tasks · ").orEmpty()
    }.getOrElse { "Invalid recovery: ${it.message}" }

    fun previewRecovery(text: String): ConfigurationPreview = configurationRecovery.preview(text)

    fun restoreRecovery(text: String, sections: Set<String> = previewRecovery(text).selectedByDefault): String = runCatching {
        disarmAll()
        val preview = configurationRecovery.restore(text, sections)
        disarmAll()
        "Recovery restored · ${sections.size}/${preview.sections.size} sections · restart required"
    }.getOrElse { "Restore failed: ${it.message}" }

    private fun selectedProfile(id: RadioProfileId): RadioConnectionProfile {
        RadioProfileCatalog.find(id)?.let { return it }
        val modelId = prefs.getInt("hamlib_model_id", 0)
        if (id.value.startsWith("hamlib.") && modelId > 0) {
            val network = id.value.startsWith("hamlib.network.")
            return RadioConnectionProfile(
                id = id,
                name = prefs.getString("hamlib_model", "Hamlib model $modelId").orEmpty().ifBlank { "Hamlib model $modelId" },
                backendKind = if (network) RadioBackendKind.HAMLIB_NETWORK else RadioBackendKind.HAMLIB_EMBEDDED,
                modelId = RadioModelId("HAMLIB:$modelId"),
                manufacturer = prefs.getString("hamlib_manufacturer", "Hamlib").orEmpty().ifBlank { "Hamlib" },
                model = prefs.getString("hamlib_model", "Model $modelId").orEmpty().ifBlank { "Model $modelId" },
                transport = if (network) RadioTransportType.RIGCTLD else RadioTransportType.USB_SERIAL,
                host = if (network) "127.0.0.1" else null,
                port = if (network) 4_532 else null,
                hamlibModelId = modelId,
                readOnly = true,
            )
        }
        return RadioProfileCatalog.UNKNOWN
    }

    private fun loadPresets(): List<RadioPreset> = runCatching {
        val rows = JSONArray(prefs.getString("presets", "[]"))
        List(rows.length()) { index ->
            rows.getJSONObject(index).let { row -> RadioPreset(row.getInt("slot"), row.getString("name"),
                row.getLong("frequency"), row.getString("mode"), row.getInt("bandwidth"), row.getLong("color")) }
        }
    }.getOrDefault(emptyList())

    private fun loadSpotStatusColours(): Map<String, Int> = defaultSpotStatusColours.mapValues { (key, fallback) ->
        val (dimension, status) = key.split(':', limit = 2)
        prefs.getInt(spotStatusColourPreferenceKey(dimension, status), fallback)
    }

    private fun spotStatusColourPreferenceKey(dimension: String, status: String) =
        "spot_status_colour_${dimension.lowercase()}_${status.replace('/', '_').lowercase()}"

    private fun persistPresets() {
        val rows = JSONArray()
        presets.forEach { rows.put(JSONObject().put("slot", it.slot).put("name", it.name)
            .put("frequency", it.frequencyHz).put("mode", it.mode).put("bandwidth", it.bandwidthHz).put("color", it.color)) }
        prefs.edit().putString("presets", rows.toString()).apply()
    }

    private fun persistLogbookColumns() {
        prefs.edit().putString("logbook_columns", encodeLogbookColumns(visibleLogbookColumns)).apply()
    }

    companion object {
        val presetColors = listOf(0xFF704B12, 0xFF174F70, 0xFF245A43, 0xFF593C73, 0xFF713337, 0xFF37444C)
    }
}
