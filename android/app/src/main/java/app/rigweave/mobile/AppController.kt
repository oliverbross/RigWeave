package app.rigweave.mobile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

enum class FieldProfile { DAY, NIGHT, FIELD }

data class RadioPreset(
    val slot: Int,
    val name: String,
    val frequencyHz: Long,
    val mode: String,
    val bandwidthHz: Int,
    val color: Long,
)

class AppController(private val context: Context) {
    private val prefs = context.getSharedPreferences("rigweave-app", Context.MODE_PRIVATE)
    var fieldProfile by mutableStateOf(runCatching { FieldProfile.valueOf(prefs.getString("profile", "DAY")!!) }.getOrDefault(FieldProfile.DAY)); private set
    var transmitArmed by mutableStateOf(false); private set
    var cwMacrosArmed by mutableStateOf(false); private set
    var stationCallsign by mutableStateOf(prefs.getString("station_call", "") ?: ""); private set
    var stationName by mutableStateOf(prefs.getString("station_name", "") ?: ""); private set
    var stationGrid by mutableStateOf(prefs.getString("station_grid", "") ?: ""); private set
    var activationProgram by mutableStateOf(prefs.getString("activation_program", "NONE") ?: "NONE"); private set
    var activationReference by mutableStateOf(prefs.getString("activation_reference", "") ?: ""); private set
    var autoDim by mutableStateOf(prefs.getBoolean("auto_dim", true)); private set
    var alertTones by mutableStateOf(prefs.getBoolean("alert_tones", false)); private set
    var quietAlerts by mutableStateOf(prefs.getBoolean("quiet_alerts", false)); private set
    var brightness by mutableStateOf(prefs.getInt("brightness", 82)); private set
    var cqRepeatSeconds by mutableStateOf(prefs.getInt("cq_repeat", 3).coerceIn(CQ_REPEAT_MIN_SECONDS, CQ_REPEAT_MAX_SECONDS)); private set
    var favoriteBands by mutableStateOf(prefs.getString("favorites", "7.020,7.030,7.100,7.200,14.060,21.060")!!.split(",")); private set
    val macroLabels = mutableStateListOf<String>().apply {
        repeat(CW_MACRO_COUNT) { index -> add(sanitizeCwMacroLabel(prefs.getString("macro_label_$index", defaultCwMacroLabel(index))
            ?: defaultCwMacroLabel(index))) }
    }
    val macroTexts = mutableStateListOf<String>().apply {
        repeat(CW_MACRO_COUNT) { index -> add(sanitizeCwMacroText(prefs.getString("macro_text_$index", "") ?: "")) }
    }
    val presets = mutableStateListOf<RadioPreset>().apply { addAll(loadPresets()) }

    fun setProfile(value: FieldProfile) {
        fieldProfile = value; prefs.edit().putString("profile", value.name).apply()
    }

    fun updateTransmitArmed(value: Boolean) { transmitArmed = value }
    fun updateCwMacrosArmed(value: Boolean) { cwMacrosArmed = value }

    fun saveLocalSettings(call: String, name: String, grid: String, repeat: Int,
        labels: List<String>, texts: List<String>) {
        stationCallsign = call.uppercase(); stationName = name; stationGrid = grid.uppercase()
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

    fun backupNow(): String = runCatching {
        val payload = JSONObject().put("version", 1).put("created_at", System.currentTimeMillis())
            .put("preferences", JSONObject(prefs.all))
        context.openFileOutput("rigweave-recovery.json", Context.MODE_PRIVATE).bufferedWriter().use { it.write(payload.toString(2)) }
        "Backup captured locally"
    }.getOrElse { "Backup failed: ${it.message}" }

    fun verifyBackup(): String = runCatching {
        val row = JSONObject(recoveryText())
        require(row.getInt("version") == 1 && row.has("preferences"))
        "Recovery data verified"
    }.getOrElse { "Backup verification failed: ${it.message}" }

    fun recoveryPath(): String = context.filesDir.resolve("rigweave-recovery.json").absolutePath

    fun recoveryText(): String = context.openFileInput("rigweave-recovery.json").bufferedReader().use { it.readText() }

    fun reviewRecovery(text: String): String = runCatching {
        val row = JSONObject(text); require(row.getInt("version") == 1)
        "Valid recovery · ${row.getJSONObject("preferences").length()} settings"
    }.getOrElse { "Invalid recovery: ${it.message}" }

    fun restoreRecovery(text: String): String = runCatching {
        val row = JSONObject(text); require(row.getInt("version") == 1); val values = row.getJSONObject("preferences")
        val editor = prefs.edit().clear(); values.keys().forEach { key -> when (val value = values.get(key)) {
            is Boolean -> editor.putBoolean(key, value); is Int -> editor.putInt(key, value); is Long -> editor.putLong(key, value)
            is Double -> editor.putFloat(key, value.toFloat()); is String -> editor.putString(key, value)
        } }; editor.commit(); "Recovery restored · restart app to load all settings"
    }.getOrElse { "Restore failed: ${it.message}" }

    private fun loadPresets(): List<RadioPreset> = runCatching {
        val rows = JSONArray(prefs.getString("presets", "[]"))
        List(rows.length()) { index ->
            rows.getJSONObject(index).let { row -> RadioPreset(row.getInt("slot"), row.getString("name"),
                row.getLong("frequency"), row.getString("mode"), row.getInt("bandwidth"), row.getLong("color")) }
        }
    }.getOrDefault(emptyList())

    private fun persistPresets() {
        val rows = JSONArray()
        presets.forEach { rows.put(JSONObject().put("slot", it.slot).put("name", it.name)
            .put("frequency", it.frequencyHz).put("mode", it.mode).put("bandwidth", it.bandwidthHz).put("color", it.color)) }
        prefs.edit().putString("presets", rows.toString()).apply()
    }

    companion object {
        val presetColors = listOf(0xFF704B12, 0xFF174F70, 0xFF245A43, 0xFF593C73, 0xFF713337, 0xFF37444C)
    }
}
