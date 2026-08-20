package app.rigweave.mobile.hamclock

import app.rigweave.mobile.GeoPoint
import app.rigweave.mobile.SignalDirection
import app.rigweave.mobile.SignalReport
import app.rigweave.mobile.bandForFrequency
import app.rigweave.mobile.maidenheadCenter
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

enum class HamClockRbnSourceKind { RBN }

data class HamClockRbnObservation(
    val id: String,
    val sourceKind: HamClockRbnSourceKind = HamClockRbnSourceKind.RBN,
    val skimmerCall: String,
    val dxCall: String,
    val frequencyHz: Long,
    val band: String,
    val mode: String,
    val snr: Int?,
    val wpm: Int?,
    val bps: Int?,
    val cq: Boolean,
    val test: Boolean,
    val observedEpoch: Long,
    val rawComment: String,
)

internal fun parseRbnClusterLine(line: String, nowEpoch: Long = Instant.now().epochSecond): HamClockRbnObservation? {
    val match = Regex("^DX\\s+de\\s+([^:]+):\\s*([0-9]+(?:\\.[0-9]+)?)\\s+([A-Z0-9/]+)\\s+(.+)$",
        setOf(RegexOption.IGNORE_CASE)).find(line.trim()) ?: return null
    val skimmer = match.groupValues[1].trim().uppercase(Locale.US)
    if (!skimmer.contains("#") && !skimmer.contains("SKIM", true)) return null
    val frequencyHz = (match.groupValues[2].toDoubleOrNull()?.times(1_000.0))?.toLong() ?: return null
    val dx = match.groupValues[3].uppercase(Locale.US)
    val comment = match.groupValues[4].trim().take(160)
    val mode = Regex("\\b(CW|RTTY|PSK|FT8|FT4|WSPR|BPSK|QPSK)\\b", RegexOption.IGNORE_CASE)
        .find(comment)?.value?.uppercase(Locale.US) ?: "CW"
    val snr = Regex("([+-]?\\d+)\\s*dB\\b", RegexOption.IGNORE_CASE).find(comment)?.groupValues?.get(1)?.toIntOrNull()
    val wpm = Regex("(\\d+)\\s*WPM\\b", RegexOption.IGNORE_CASE).find(comment)?.groupValues?.get(1)?.toIntOrNull()
    val bps = Regex("(\\d+)\\s*BPS\\b", RegexOption.IGNORE_CASE).find(comment)?.groupValues?.get(1)?.toIntOrNull()
    val identity = "$skimmer|$dx|$frequencyHz|$mode|$nowEpoch"
    return HamClockRbnObservation(identity, skimmerCall = skimmer, dxCall = dx, frequencyHz = frequencyHz,
        band = bandForFrequency(frequencyHz), mode = mode, snr = snr, wpm = wpm, bps = bps,
        cq = Regex("\\bCQ\\b", RegexOption.IGNORE_CASE).containsMatchIn(comment),
        test = Regex("\\b(TEST|BEACON)\\b", RegexOption.IGNORE_CASE).containsMatchIn(comment),
        observedEpoch = nowEpoch, rawComment = comment)
}

internal fun boundedRbnObservations(
    rows: List<HamClockRbnObservation>,
    preference: HamClockRbnPreference,
    watchlist: Set<String>,
    nowEpoch: Long = Instant.now().epochSecond,
): List<HamClockRbnObservation> {
    if (!preference.enabled) return emptyList()
    val cutoff = nowEpoch - preference.windowMinutes * 60L
    val bands = preference.bands.map { it.uppercase(Locale.US) }.toSet()
    val modes = preference.modes.map { it.uppercase(Locale.US) }.toSet()
    val deduped = LinkedHashMap<String, HamClockRbnObservation>()
    rows.asSequence().filter { it.observedEpoch >= cutoff }
        .filter { bands.isEmpty() || it.band.uppercase(Locale.US) in bands }
        .filter { modes.isEmpty() || it.mode.uppercase(Locale.US) in modes }
        .filter { preference.minimumSnr == null || (it.snr != null && it.snr >= preference.minimumSnr) }
        .filter { preference.skimmerCall.isBlank() || it.skimmerCall.contains(preference.skimmerCall, true) }
        .filter { preference.dxCall.isBlank() || it.dxCall.contains(preference.dxCall, true) }
        .filter { !preference.watchlistOnly || it.dxCall in watchlist }
        .sortedByDescending(HamClockRbnObservation::observedEpoch)
        .forEach { row -> deduped.putIfAbsent("${row.skimmerCall}|${row.dxCall}|${row.frequencyHz / 100}|${row.mode}", row) }
    return deduped.values.take(preference.maximumRows).toList()
}

enum class HamClockWsprRegionalState { DISABLED, UNAVAILABLE_POLICY }

internal data class HamClockWsprSnapshot(
    val callsign: String = "",
    val reports: List<SignalReport> = emptyList(),
    val beingHeardState: HamClockFeedState = HamClockFeedState.UNAVAILABLE,
    val hearingState: HamClockFeedState = HamClockFeedState.UNAVAILABLE,
    val fetchedEpoch: Long = 0,
    val regionalState: HamClockWsprRegionalState = HamClockWsprRegionalState.DISABLED,
    val error: String = "",
)

internal class HamClockWsprRepository(private val pskReporter: PskReporterRepository) {
    fun refreshPersonal(callsign: String, station: GeoPoint?, preference: HamClockWsprPreference,
        force: Boolean = false, nowEpoch: Long = Instant.now().epochSecond): HamClockWsprSnapshot {
        if (!preference.personalEnabled || callsign.isBlank()) return HamClockWsprSnapshot(
            callsign = callsign.trim().uppercase(Locale.US), regionalState = regionalState(preference))
        val loaded = pskReporter.refresh(callsign, station, preference.windowMinutes, force, mode = "WSPR", nowEpoch = nowEpoch)
        val reports = loaded.reports.asSequence()
            .filter { preference.direction == HamClockPskDirection.BOTH ||
                preference.direction == HamClockPskDirection.MUTUAL && it.mutual ||
                preference.direction == HamClockPskDirection.BEING_HEARD && it.direction == SignalDirection.BEING_HEARD ||
                preference.direction == HamClockPskDirection.HEARING && it.direction == SignalDirection.HEARING }
            .filter { preference.band == "ALL" || it.band.equals(preference.band, true) }
            .filter { preference.minimumSnr == null || (it.snr != null && it.snr >= preference.minimumSnr) }
            .take(preference.maximumPaths).toList()
        return HamClockWsprSnapshot(loaded.callsign, reports, loaded.beingHeard.state, loaded.hearing.state,
            maxOf(loaded.beingHeard.fetchedEpoch, loaded.hearing.fetchedEpoch), regionalState(preference),
            listOf(loaded.beingHeard.error, loaded.hearing.error).filter(String::isNotBlank).joinToString(" · "))
    }

    private fun regionalState(preference: HamClockWsprPreference) =
        if (preference.regionalEnabled) HamClockWsprRegionalState.UNAVAILABLE_POLICY else HamClockWsprRegionalState.DISABLED
}

internal data class HamClockIbpBeacon(val callsign: String, val grid: String, val point: GeoPoint)
internal data class HamClockIbpTransmission(val beacon: HamClockIbpBeacon, val band: String, val frequencyHz: Long,
    val slot: Int, val slotStartEpoch: Long, val slotEndEpoch: Long)
internal data class HamClockIbpSchedule(val cycleStartEpoch: Long, val slot: Int, val transmissions: List<HamClockIbpTransmission>,
    val manifestVersion: String, val manifestHash: String)

private val ibpManifestRows = listOf(
    "4U1UN" to "FN30AS", "VE8AT" to "CP38GH", "W6WX" to "CM97BD", "KH6RS" to "BL11BM",
    "ZL6B" to "RE78TW", "VK6RBP" to "OF87AV", "JA2IGY" to "PM84JK", "RR9O" to "NO14KX",
    "VR2B" to "OL72BG", "4S7B" to "MJ96WV", "ZS6DN" to "KG33XI", "5Z4B" to "KI88HR",
    "4X6TU" to "KM72JB", "OH2B" to "KP20EH", "CS3B" to "IM12JT", "LU4AA" to "GF05TJ",
    "OA4B" to "FH17MW", "YV5B" to "FK60ND",
)
internal val hamClockIbpManifest: List<HamClockIbpBeacon> = ibpManifestRows.map { (call, grid) ->
    HamClockIbpBeacon(call, grid, requireNotNull(maidenheadCenter(grid)))
}
internal const val HAMCLOCK_IBP_MANIFEST_VERSION = "NCDXF-2026-08-20"
internal val HAMCLOCK_IBP_MANIFEST_HASH: String = MessageDigest.getInstance("SHA-256")
    .digest(ibpManifestRows.joinToString("|") { "${it.first}:${it.second}" }.toByteArray())
    .joinToString("") { "%02x".format(it) }

internal fun hamClockIbpSchedule(nowEpoch: Long = Instant.now().epochSecond): HamClockIbpSchedule {
    val cycleStart = nowEpoch - Math.floorMod(nowEpoch, 180L)
    val slot = ((nowEpoch - cycleStart) / 10L).toInt()
    val bands = listOf("20m" to 14_100_000L, "17m" to 18_110_000L, "15m" to 21_150_000L,
        "12m" to 24_930_000L, "10m" to 28_200_000L)
    val offsets = listOf(0, 17, 16, 15, 14)
    val transmissions = bands.mapIndexed { index, (band, frequency) ->
        val beacon = hamClockIbpManifest[(slot + offsets[index]) % hamClockIbpManifest.size]
        HamClockIbpTransmission(beacon, band, frequency, slot, cycleStart + slot * 10L, cycleStart + (slot + 1) * 10L)
    }
    return HamClockIbpSchedule(cycleStart, slot, transmissions, HAMCLOCK_IBP_MANIFEST_VERSION, HAMCLOCK_IBP_MANIFEST_HASH)
}

enum class HamClockEvidenceAvailability { CURRENT, STALE, UNAVAILABLE, DISABLED }
data class HamClockBandEvidence(val source: String, val band: String, val mode: String, val callsign: String,
    val receiver: String = "", val snr: Int? = null, val observedEpoch: Long)
data class HamClockBandHealthRow(val band: String, val state: String, val observations: Int, val uniqueCalls: Int,
    val uniqueReceivers: Int, val medianSnr: Int?, val trend: String, val diversity: Int, val confidence: String,
    val reasons: List<String>)

internal fun computeHamClockBandHealth(
    evidence: List<HamClockBandEvidence>,
    availability: Map<String, HamClockEvidenceAvailability>,
    preference: HamClockBandHealthPreference,
    nowEpoch: Long = Instant.now().epochSecond,
): List<HamClockBandHealthRow> {
    val liveSources = preference.enabledSources.filter {
        availability[it] in setOf(HamClockEvidenceAvailability.CURRENT, HamClockEvidenceAvailability.STALE)
    }
    val cutoff = nowEpoch - preference.windowMinutes * 60L
    return preference.visibleBands.sortedBy(::bandSortKey).map { band ->
        if (liveSources.isEmpty()) return@map HamClockBandHealthRow(band, "NO LIVE EVIDENCE", 0, 0, 0, null,
            "UNKNOWN", 0, "NONE", listOf("All selected evidence sources are disabled or unavailable"))
        val repeats = mutableMapOf<String, Int>()
        val rows = evidence.asSequence().filter { it.source in liveSources && it.band.equals(band, true) && it.observedEpoch >= cutoff }
            .filter { preference.mode == "ALL" || it.mode.equals(preference.mode, true) }
            .filter {
                val key = "${it.source}|${it.callsign}|${it.receiver}"
                (repeats.getOrDefault(key, 0) < 3).also { accepted -> if (accepted) repeats[key] = repeats.getOrDefault(key, 0) + 1 }
            }.toList()
        val calls = rows.map { it.callsign }.filter(String::isNotBlank).toSet().size
        val receivers = rows.map { it.receiver }.filter(String::isNotBlank).toSet().size
        val sources = rows.map { it.source }.toSet().size
        val median = rows.mapNotNull { it.snr }.sorted().let { values -> values.takeIf(List<Int>::isNotEmpty)?.get(values.size / 2) }
        val midpoint = nowEpoch - preference.windowMinutes * 30L
        val older = rows.count { it.observedEpoch < midpoint }; val newer = rows.size - older
        val trend = when { newer > older * 3 / 2 -> "RISING"; older > newer * 3 / 2 -> "FALLING"; else -> "STEADY" }
        val confidence = when { rows.size >= 12 && sources >= 2 -> "HIGH"; rows.size >= 4 -> "MEDIUM"; rows.isNotEmpty() -> "LOW"; else -> "NONE" }
        val state = when { rows.size >= 4 -> "ACTIVE"; rows.isNotEmpty() -> "QUIET"; else -> "NO RECENT EVIDENCE" }
        val reasons = if (rows.isEmpty()) listOf("No selected observations inside the ${preference.windowMinutes} minute window")
            else listOf("${rows.size} capped observations from $sources source${if (sources == 1) "" else "s"}",
                "$calls unique calls · $receivers unique receivers")
        HamClockBandHealthRow(band, state, rows.size, calls, receivers, median, trend, sources, confidence, reasons)
    }
}

private fun bandSortKey(band: String): Int = listOf("2200m", "630m", "160m", "80m", "60m", "40m", "30m",
    "20m", "17m", "15m", "12m", "10m", "6m", "4m", "2m", "70cm", "23cm")
    .indexOf(band.lowercase(Locale.US)).let { if (it < 0) 999 else it }
