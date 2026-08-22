package app.rigweave.mobile

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.security.MessageDigest

private const val CONFIGURATION_FORMAT = "RIGWEAVE_CONFIGURATION_BUNDLE"
private const val CONFIGURATION_SCHEMA = 1

data class ConfigurationSectionPreview(
    val name: String,
    val changedKeys: List<String>,
    val mappingTasks: List<String>,
)

data class ConfigurationPreview(
    val schema: Int,
    val createdAt: Long,
    val sections: List<ConfigurationSectionPreview>,
) {
    val selectedByDefault: Set<String> get() = sections.map { it.name }.toSet()
    val changeCount: Int get() = sections.sumOf { it.changedKeys.size }
}

private data class PreferenceSection(
    val name: String,
    val store: String,
    val allow: (String) -> Boolean,
)

class ConfigurationRecovery(private val context: Context) {
    private val sections = listOf(
        PreferenceSection("app", "rigweave-app") { key -> !unsafeKey(key) },
        PreferenceSection("keyer", "rigweave-keyer") { key -> key in setOf(
            "keyer_document", "keyer_document_last_good", "hotkeys_enabled", "show_strip", "fallback_general", "active_profile",
            "repeat_interval_seconds", "repeat_maximum_cycles", "repeat_maximum_minutes", "repeat_stops_on_input"
        ) },
        PreferenceSection("navigation", "navigation") { true },
        PreferenceSection("home_hamclock", "rigweave-hamclock-layout") { key -> !unsafeKey(key) },
        PreferenceSection("digi", "rigweave-digi") { key -> !unsafeKey(key) },
        PreferenceSection("audio_routes", "rigweave-audio-routes") { key -> !unsafeKey(key) },
        PreferenceSection("usb_radio", "rigweave-usb") { key -> !unsafeKey(key) },
        PreferenceSection("cluster", "dx_cluster") { key -> key in setOf("host", "port", "callsign", "watchlist", "fallback_host", "fallback_port", "fallback2_host", "fallback2_port") },
        PreferenceSection("neural", "neural-dx-v12") { key -> key in setOf("notifications", "briefing_dx_mode", "briefing_order") || key.startsWith("display_") || key.startsWith("outlook_") },
        PreferenceSection("wavelog_binding", "wavelog") { key -> key in setOf("base_url", "station_id", "ntp_server", "log_mode") },
        PreferenceSection("groupsio", "rigweave-groupsio") { key -> key != "last_sync" && !unsafeKey(key) },
        PreferenceSection("portable", "rigweave-portable-filters") { key -> !unsafeKey(key) },
        PreferenceSection("pota", "rigweave-pota-filters") { key -> !unsafeKey(key) },
        PreferenceSection("satellite", "satellite_operations_v1") { key -> !unsafeKey(key) },
        PreferenceSection("log_intelligence", "rigweave-log-intelligence") { key -> !unsafeKey(key) },
        PreferenceSection("eq_profiles", "rigweave-eq-profiles") { key -> !unsafeKey(key) },
    )

    fun export(): String {
        val payload = JSONObject()
        sections.sortedBy { it.name }.forEach { section ->
            val values = JSONObject()
            preferences(section).all.toSortedMap().forEach { (key, value) ->
                if (section.allow(key) && supported(value)) values.put(key, encodeValue(value))
            }
            payload.put(section.name, values)
        }
        val canonical = payload.toString()
        return JSONObject()
            .put("format_signature", CONFIGURATION_FORMAT)
            .put("schema", CONFIGURATION_SCHEMA)
            .put("created_at", System.currentTimeMillis())
            .put("payload_sha256", configurationSha256(canonical))
            .put("sections", payload)
            .toString(2)
    }

    fun preview(text: String): ConfigurationPreview {
        val root = validated(text)
        val payload = root.getJSONObject("sections")
        val rows = sections.mapNotNull { section ->
            val incoming = payload.optJSONObject(section.name) ?: return@mapNotNull null
            val current = preferences(section).all
            val changed = incoming.keys().asSequence().filter { key ->
                section.allow(key) && current[key] != decodeComparable(incoming.getJSONObject(key))
            }.toList().sorted()
            val mappings = buildList {
                if (section.name == "wavelog_binding" && incoming.has("station_id") && current["station_id"] != decodeComparable(incoming.getJSONObject("station_id")))
                    add("Map the imported Wavelog station on this device")
                if (section.name == "app" && incoming.has("station_call") && current["station_call"] != decodeComparable(incoming.getJSONObject("station_call")))
                    add("Review the imported station profile identity")
            }
            ConfigurationSectionPreview(section.name, changed, mappings)
        }
        return ConfigurationPreview(root.getInt("schema"), root.getLong("created_at"), rows)
    }

    fun restore(text: String, selectedSections: Set<String>): ConfigurationPreview {
        val preview = preview(text)
        require(selectedSections.all { selected -> sections.any { it.name == selected } }) { "Unknown configuration section" }
        val payload = validated(text).getJSONObject("sections")
        val backups = sections.associateWith { preferences(it).all.toMap() }
        try {
            sections.filter { it.name in selectedSections }.forEach { section ->
                val incoming = payload.optJSONObject(section.name) ?: return@forEach
                val editor = preferences(section).edit()
                incoming.keys().forEach { key ->
                    require(section.allow(key)) { "Unsafe or unknown key in ${section.name}" }
                    putPreference(editor, key, incoming.getJSONObject(key))
                }
                require(editor.commit()) { "Unable to commit ${section.name}" }
            }
            clearUnsafeRuntimePreferences()
        } catch (error: Throwable) {
            backups.forEach { (section, values) -> restoreAll(preferences(section), values) }
            throw error
        }
        return preview
    }

    private fun validated(text: String): JSONObject {
        require(text.toByteArray().size <= 2_000_000) { "Configuration bundle is too large" }
        val parsed = JSONObject(text)
        val root = if (parsed.optString("format_signature") == CONFIGURATION_FORMAT) parsed else migrateLegacy(parsed)
        require(root.getString("format_signature") == CONFIGURATION_FORMAT) { "Invalid configuration signature" }
        require(root.getInt("schema") == CONFIGURATION_SCHEMA) { "Unsupported configuration schema" }
        val payload = root.getJSONObject("sections")
        require(root.getString("payload_sha256") == configurationSha256(payload.toString())) { "Configuration hash mismatch" }
        payload.keys().forEach { name -> require(sections.any { it.name == name }) { "Unknown configuration section $name" } }
        return root
    }

    private fun migrateLegacy(legacy: JSONObject): JSONObject {
        require(legacy.optInt("version") in 1..2 && legacy.has("preferences")) { "Invalid configuration signature" }
        val payload = JSONObject()
        sections.sortedBy { it.name }.forEach { section ->
            val source = when (section.name) {
                "app" -> legacy.optJSONObject("preferences")
                "home_hamclock" -> legacy.optJSONObject("hamclock_layout")
                else -> null
            } ?: return@forEach
            val values = JSONObject()
            source.keys().forEach { key ->
                val value = source.opt(key)
                if (section.allow(key) && supported(value)) values.put(key, encodeValue(value))
            }
            payload.put(section.name, values)
        }
        val canonical = payload.toString()
        return JSONObject()
            .put("format_signature", CONFIGURATION_FORMAT)
            .put("schema", CONFIGURATION_SCHEMA)
            .put("created_at", legacy.optLong("created_at", System.currentTimeMillis()))
            .put("payload_sha256", configurationSha256(canonical))
            .put("sections", payload)
    }

    private fun preferences(section: PreferenceSection) = context.getSharedPreferences(section.store, Context.MODE_PRIVATE)

    private fun clearUnsafeRuntimePreferences() {
        listOf("rigweave-app", "rigweave-digi").forEach { name ->
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            prefs.all.keys.filter(::unsafeKey).forEach(editor::remove)
            editor.commit()
        }
    }

    private fun restoreAll(store: SharedPreferences, values: Map<String, *>) {
        val editor = store.edit().clear()
        values.forEach { (key, value) -> putRaw(editor, key, value) }
        editor.commit()
    }
}

private fun unsafeKey(key: String): Boolean {
    val value = key.lowercase()
    return listOf("password", "secret", "token", "api_key", "credential", "ptt", "tx_arm", "transmit_arm",
        "transmit_enabled", "continuous_tx", "sequencer_active", "temporary_command").any(value::contains)
}

private fun supported(value: Any?) = value is String || value is Boolean || value is Int || value is Long || value is Float

private fun encodeValue(value: Any?): JSONObject = when (value) {
    is String -> JSONObject().put("type", "string").put("value", value)
    is Boolean -> JSONObject().put("type", "boolean").put("value", value)
    is Int -> JSONObject().put("type", "int").put("value", value)
    is Long -> JSONObject().put("type", "long").put("value", value)
    is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
    else -> error("Unsupported preference type")
}

private fun decodeComparable(row: JSONObject): Any = when (row.getString("type")) {
    "string" -> row.getString("value")
    "boolean" -> row.getBoolean("value")
    "int" -> row.getInt("value")
    "long" -> row.getLong("value")
    "float" -> row.getDouble("value").toFloat()
    else -> error("Unsupported preference value type")
}

private fun putPreference(editor: SharedPreferences.Editor, key: String, row: JSONObject) = putRaw(editor, key, decodeComparable(row))

private fun putRaw(editor: SharedPreferences.Editor, key: String, value: Any?) {
    when (value) {
        is String -> editor.putString(key, value)
        is Boolean -> editor.putBoolean(key, value)
        is Int -> editor.putInt(key, value)
        is Long -> editor.putLong(key, value)
        is Float -> editor.putFloat(key, value)
    }
}

private fun configurationSha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
