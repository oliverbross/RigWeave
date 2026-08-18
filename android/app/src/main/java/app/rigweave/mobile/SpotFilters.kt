package app.rigweave.mobile

import java.util.Locale

internal enum class SpotFilterDimension(val title: String) {
    BAND("Band"), MODE("Mode"), CALL_STATUS("CS"), DXCC_STATUS("DS")
}

internal data class SpotFilters(
    val bands: Set<String> = emptySet(),
    val modes: Set<String> = emptySet(),
    val callStatuses: Set<String> = emptySet(),
    val dxccStatuses: Set<String> = emptySet(),
) {
    fun selected(dimension: SpotFilterDimension): Set<String> = when (dimension) {
        SpotFilterDimension.BAND -> bands
        SpotFilterDimension.MODE -> modes
        SpotFilterDimension.CALL_STATUS -> callStatuses
        SpotFilterDimension.DXCC_STATUS -> dxccStatuses
    }

    fun withSelection(dimension: SpotFilterDimension, values: Set<String>): SpotFilters = when (dimension) {
        SpotFilterDimension.BAND -> copy(bands = values)
        SpotFilterDimension.MODE -> copy(modes = values)
        SpotFilterDimension.CALL_STATUS -> copy(callStatuses = values)
        SpotFilterDimension.DXCC_STATUS -> copy(dxccStatuses = values)
    }

    fun count(dimension: SpotFilterDimension): Int = selected(dimension).size
}

internal val spotBandOptions = listOf("160m", "80m", "60m", "40m", "30m", "20m", "17m", "15m", "12m", "10m", "6m")
internal val spotModeOptions = listOf("CW", "SSB", "FT8", "FT4", "RTTY", "PSK", "DATA", "FM", "AM")
internal val spotCallStatusOptions = listOf("NC", "NB", "NM", "W", "C")
internal val spotDxccStatusOptions = listOf("ATNO", "W/NB", "C/NB", "W", "C")

internal fun spotMatchesFilters(spot: AndroidDXSpot, status: SpotLogStatus?, filters: SpotFilters): Boolean {
    val band = spot.band.trim().uppercase(Locale.US)
    val mode = canonicalSpotMode(spot.mode)
    return (filters.bands.isEmpty() || filters.bands.any { it.uppercase(Locale.US) == band }) &&
        (filters.modes.isEmpty() || mode in filters.modes.map(::canonicalSpotMode)) &&
        (filters.callStatuses.isEmpty() || status?.callStatus in filters.callStatuses) &&
        (filters.dxccStatuses.isEmpty() || status?.dxccStatus in filters.dxccStatuses)
}

internal fun spotMatchesSearch(
    spot: AndroidDXSpot,
    entityCountry: String,
    entityDxcc: String,
    query: String,
): Boolean {
    val needle = query.trim().uppercase(Locale.US)
    if (needle.isEmpty()) return true
    return listOf(spot.callsign, spot.country, entityCountry, entityDxcc)
        .any { needle in it.uppercase(Locale.US) }
}
