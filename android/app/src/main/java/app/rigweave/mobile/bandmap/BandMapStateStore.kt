// SPDX-License-Identifier: GPL-3.0-only
package app.rigweave.mobile.bandmap

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

internal const val BAND_MAP_SETTINGS_SCHEMA = 1
internal const val BAND_MAP_PREFERENCES = "rigweave-bandmaps-v1"

internal data class BandMapSettings(
    val schema: Int = BAND_MAP_SETTINGS_SCHEMA,
    val enabled: Boolean = true,
    val navigationVisible: Boolean = true,
    val selectedLayout: BandMapLayoutMode = BandMapLayoutMode.MULTI_VERTICAL,
    val selectedBands: List<String> = listOf("40m", "20m", "15m", "10m"),
    val activePresetId: String = "all-current",
    val labelDensity: Int = 2,
    val linkedZoom: Boolean = false,
    val palette: String = "COLOUR_VISION_FRIENDLY",
    val presets: List<BandMapPreset> = builtInBandMapPresets,
    val marks: List<BandMapMark> = emptyList(),
    val traversal: BandMapTraversal = BandMapTraversal.PRIORITY,
    val keyerContextVisible: Boolean = true,
    val chaserContextVisible: Boolean = true,
)

internal object BandMapSettingsCodec {
    fun encode(value: BandMapSettings): String = JSONObject()
        .put("schema", value.schema)
        .put("enabled", value.enabled)
        .put("navigation_visible", value.navigationVisible)
        .put("layout", value.selectedLayout.name)
        .put("bands", JSONArray(value.selectedBands))
        .put("active_preset", value.activePresetId)
        .put("label_density", value.labelDensity)
        .put("linked_zoom", value.linkedZoom)
        .put("palette", value.palette)
        .put("traversal", value.traversal.name)
        .put("keyer_context", value.keyerContextVisible)
        .put("chaser_context", value.chaserContextVisible)
        .put("presets", JSONArray().apply { value.presets.forEach { put(preset(it)) } })
        .put("marks", JSONArray().apply { value.marks.take(1_000).forEach { mark ->
            put(JSONObject().put("call", mark.callsign).put("band", mark.band).put("frequency_hz", mark.frequencyHz)
                .put("kinds", JSONArray(mark.kinds.map(Enum<*>::name))).put("created", mark.createdEpoch).put("note", mark.note.take(80)))
        } })
        .toString()

    fun decode(text: String): BandMapSettings {
        require(text.toByteArray().size <= 512_000) { "Band Map settings are too large" }
        val root = JSONObject(text)
        require(root.getInt("schema") == BAND_MAP_SETTINGS_SCHEMA) { "Unsupported Band Map settings schema" }
        val bands = root.getJSONArray("bands").strings().map { it.lowercase() }.distinct()
        require(bands.isNotEmpty() && bands.size <= bandMapBands.size && bands.all { name -> bandMapBands.any { it.name == name } }) { "Invalid selected bands" }
        val presets = root.optJSONArray("presets")?.objects()?.map(::decodePreset).orEmpty().ifEmpty { builtInBandMapPresets }
        require(presets.size <= 40 && presets.map(BandMapPreset::id).distinct().size == presets.size) { "Invalid Band Map preset collection" }
        val marks = root.optJSONArray("marks")?.objects()?.take(1_000)?.map { row ->
            val call = BandMapSpotCanonicalizer.callsign(row.getString("call")); require(call.isNotBlank()) { "Invalid marked callsign" }
            val band = row.getString("band"); require(bandMapBands.any { it.name == band }) { "Invalid marked band" }
            val frequency = row.getLong("frequency_hz"); require(frequency in 100_000L..500_000_000_000L) { "Invalid marked frequency" }
            BandMapMark(call, band, frequency, row.getJSONArray("kinds").strings().map { BandMapMarkKind.valueOf(it) }.toSet(),
                row.optLong("created", Instant.now().epochSecond), row.optString("note").take(80))
        }.orEmpty()
        val density = root.optInt("label_density", 2); require(density in 1..3) { "Invalid label density" }
        return BandMapSettings(
            enabled = root.optBoolean("enabled", true), navigationVisible = root.optBoolean("navigation_visible", true),
            selectedLayout = BandMapLayoutMode.valueOf(root.optString("layout", BandMapLayoutMode.MULTI_VERTICAL.name)),
            selectedBands = bands, activePresetId = root.optString("active_preset", "all-current").take(64),
            labelDensity = density, linkedZoom = root.optBoolean("linked_zoom"),
            palette = root.optString("palette", "COLOUR_VISION_FRIENDLY").take(40), presets = presets, marks = marks,
            traversal = BandMapTraversal.valueOf(root.optString("traversal", BandMapTraversal.PRIORITY.name)),
            keyerContextVisible = root.optBoolean("keyer_context", true), chaserContextVisible = root.optBoolean("chaser_context", true),
        )
    }

    private fun preset(value: BandMapPreset) = JSONObject()
        .put("id", value.id).put("label", value.label).put("layout", value.layout.name).put("built_in", value.builtIn)
        .put("filter", JSONObject().put("bands", JSONArray(value.filter.bands.toList())).put("modes", JSONArray(value.filter.modes.map(Enum<*>::name)))
            .put("sources", JSONArray(value.filter.sources.map(Enum<*>::name))).put("maximum_age", value.filter.maximumAgeSeconds)
            .put("minimum_spotters", value.filter.minimumSpotters).put("minimum_diversity", value.filter.minimumSourceDiversity)
            .put("needs", JSONArray(value.filter.requiredNeeds.toList())).put("contest_only", value.filter.contestOnly)
            .put("multipliers_only", value.filter.multipliersOnly).put("hide_duplicates", value.filter.hideDuplicates)
            .put("chaser_eligible", value.filter.chaserEligibleOnly).put("portable", JSONArray(value.filter.portablePrograms.toList()))
            .put("show_unknown", value.filter.showUnknown).put("show_stale", value.filter.showStale))
        .put("weights", JSONObject().apply { weights(value.weights).forEach { (key, number) -> put(key, number) } })

    private fun decodePreset(row: JSONObject): BandMapPreset {
        val id = row.getString("id").trim(); val label = row.getString("label").trim()
        require(id.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")) && label.isNotBlank() && label.length <= 80) { "Invalid Band Map preset identity" }
        val f = row.getJSONObject("filter"); val bands = f.optJSONArray("bands")?.strings()?.toSet().orEmpty()
        require(bands.all { name -> bandMapBands.any { it.name == name } }) { "Invalid preset band" }
        val filter = BandMapFilter(
            bands = bands, segments = bands.map { BandMapSegment(it) },
            modes = f.optJSONArray("modes")?.strings()?.map { BandMapModeFamily.valueOf(it) }?.toSet().orEmpty(),
            sources = f.optJSONArray("sources")?.strings()?.map { BandMapSource.valueOf(it) }?.toSet().orEmpty(),
            maximumAgeSeconds = f.optLong("maximum_age", 3_600).also { require(it in 60..86_400) },
            minimumSpotters = f.optInt("minimum_spotters").also { require(it in 0..100) },
            minimumSourceDiversity = f.optInt("minimum_diversity").also { require(it in 0..BandMapSource.entries.size) },
            requiredNeeds = f.optJSONArray("needs")?.strings()?.toSet().orEmpty(), contestOnly = f.optBoolean("contest_only"),
            multipliersOnly = f.optBoolean("multipliers_only"), hideDuplicates = f.optBoolean("hide_duplicates"),
            chaserEligibleOnly = f.optBoolean("chaser_eligible"), portablePrograms = f.optJSONArray("portable")?.strings()?.toSet().orEmpty(),
            showUnknown = f.optBoolean("show_unknown", true), showStale = f.optBoolean("show_stale", true),
        )
        val weights = row.optJSONObject("weights")?.let { w -> BandMapRankingWeights(
            watch = w.int("watch", 30), pin = w.int("pin", 12), neededEntity = w.int("needed_entity", 28), neededSlot = w.int("needed_slot", 18),
            contestMultiplier = w.int("contest_multiplier", 24), contestNonDupe = w.int("contest_nondupe", 8), chaserPriority = w.int("chaser", 14),
            currentEvidence = w.int("current_evidence", 10), outlook = w.int("outlook", 5), diversity = w.int("diversity", 4), freshness = w.int("freshness", 8),
            duplicatePenalty = w.int("duplicate_penalty", -25), stalePenalty = w.int("stale_penalty", -12)) } ?: BandMapRankingWeights()
        return BandMapPreset(id, label, filter, BandMapLayoutMode.valueOf(row.getString("layout")), weights, row.optBoolean("built_in"))
    }

    private fun weights(value: BandMapRankingWeights) = mapOf(
        "watch" to value.watch, "pin" to value.pin, "needed_entity" to value.neededEntity, "needed_slot" to value.neededSlot,
        "contest_multiplier" to value.contestMultiplier, "contest_nondupe" to value.contestNonDupe, "chaser" to value.chaserPriority,
        "current_evidence" to value.currentEvidence, "outlook" to value.outlook, "diversity" to value.diversity,
        "freshness" to value.freshness, "duplicate_penalty" to value.duplicatePenalty, "stale_penalty" to value.stalePenalty,
    )

    private fun JSONObject.int(key: String, fallback: Int) = optInt(key, fallback).also { require(it in -100..100) { "Invalid ranking weight" } }
    private fun JSONArray.strings() = (0 until length()).map(::getString)
    private fun JSONArray.objects() = (0 until length()).map(::getJSONObject)
}

internal class BandMapStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(BAND_MAP_PREFERENCES, Context.MODE_PRIVATE)
    var recoveredLastGood = false
        private set

    fun load(): BandMapSettings {
        val current = preferences.getString("document_v1", null)
        val lastGood = preferences.getString("document_last_good", null)
        val decoded = current?.let { runCatching { BandMapSettingsCodec.decode(it) }.getOrNull() }
        if (decoded != null) return decoded
        val recovered = lastGood?.let { runCatching { BandMapSettingsCodec.decode(it) }.getOrNull() }
        if (recovered != null) { recoveredLastGood = true; save(recovered); return recovered }
        return BandMapSettings().also(::save)
    }

    fun save(value: BandMapSettings) {
        val encoded = BandMapSettingsCodec.encode(value)
        BandMapSettingsCodec.decode(encoded)
        require(preferences.edit().putString("document_v1", encoded).putString("document_last_good", encoded).commit()) { "Unable to store Band Map settings" }
    }

    fun importDocument(text: String): BandMapSettings {
        val decoded = BandMapSettingsCodec.decode(text)
        save(decoded)
        return decoded
    }
}
