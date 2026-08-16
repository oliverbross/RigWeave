package app.rigweave.mobile

enum class LogbookSort { TIME, CALLSIGN, DXCC, MODE, BAND, FREQUENCY, DISTANCE, DURATION }
enum class LogbookSortDirection { DESCENDING, ASCENDING }

val LOGBOOK_PAGE_SIZES = listOf(25, 50, 100, 200, 500, 1_000)

fun normalizedLogbookPageSize(value: Int): Int = value.takeIf { it in LOGBOOK_PAGE_SIZES } ?: 50

fun logbookPageCount(total: Int, pageSize: Int): Int =
    maxOf(1, (total + normalizedLogbookPageSize(pageSize) - 1) / normalizedLogbookPageSize(pageSize))

data class LogbookFilter(
    val fromEpochSeconds: Long? = null,
    val toEpochSecondsExclusive: Long? = null,
    val callsign: String = "",
    val dxcc: String = "",
    val state: String = "",
    val grid: String = "",
    val mode: String = "",
    val band: String = "",
    val propagation: String = "",
    val county: String = "",
    val dok: String = "",
    val sota: String = "",
    val pota: String = "",
    val iota: String = "",
    val wwff: String = "",
    val operator: String = "",
    val contest: String = "",
    val continent: String = "",
    val comment: String = "",
    val distance: String = "",
    val duration: String = "",
    val qslSent: String = "",
    val qslReceived: String = "",
    val qslSentMethod: String = "",
    val qslReceivedMethod: String = "",
    val lotwSent: String = "",
    val lotwReceived: String = "",
    val clublogSent: String = "",
    val clublogReceived: String = "",
    val eqslSent: String = "",
    val eqslReceived: String = "",
    val dclSent: String = "",
    val dclReceived: String = "",
    val qrzSent: String = "",
    val qrzReceived: String = "",
    val qslVia: String = "",
    val qslImages: String = "",
    val sort: LogbookSort = LogbookSort.TIME,
    val direction: LogbookSortDirection = LogbookSortDirection.DESCENDING,
    val limit: Int = 50,
)

fun filterLogbook(records: List<Qso>, filter: LogbookFilter): List<Qso> {
    val matching = records.asSequence().filter { qso ->
        (filter.fromEpochSeconds == null || qso.createdAt >= filter.fromEpochSeconds) &&
            (filter.toEpochSecondsExclusive == null || qso.createdAt < filter.toEpochSecondsExclusive) &&
            textMatches(qso.callsign, filter.callsign) && textMatches(qso.dxcc, filter.dxcc) &&
            textMatches(qso.state, filter.state) && textMatches(qso.grid, filter.grid) &&
            choiceMatches(qso.mode, filter.mode) && choiceMatches(qso.band, filter.band) &&
            choiceMatches(qso.propagationMode, filter.propagation) && textMatches(qso.county, filter.county) &&
            textMatches(qso.dok, filter.dok) && textMatches(qso.sotaRef, filter.sota) &&
            textMatches(qso.potaRef, filter.pota) && textMatches(qso.iota, filter.iota) &&
            textMatches(qso.wwffRef, filter.wwff) && textMatches(qso.operatorCallsign, filter.operator) &&
            textMatches(qso.contestId, filter.contest) && choiceMatches(qso.continent, filter.continent) &&
            textMatches(qso.comment + " " + qso.notes, filter.comment) &&
            numericMatches(qso.distanceKm, filter.distance) && numericMatches(qso.durationSeconds / 60.0, filter.duration) &&
            statusMatches(qso.qslSent, filter.qslSent) && statusMatches(qso.qslReceived, filter.qslReceived) &&
            choiceMatches(qso.qslMethod, filter.qslSentMethod) && choiceMatches(qso.qslReceivedMethod, filter.qslReceivedMethod) &&
            statusMatches(qso.lotwSent, filter.lotwSent) && statusMatches(qso.lotwReceived, filter.lotwReceived) &&
            statusMatches(qso.clublogSent, filter.clublogSent) && statusMatches(qso.clublogReceived, filter.clublogReceived) &&
            statusMatches(qso.eqslSent, filter.eqslSent) && statusMatches(qso.eqslReceived, filter.eqslReceived) &&
            statusMatches(qso.dclSent, filter.dclSent) && statusMatches(qso.dclReceived, filter.dclReceived) &&
            statusMatches(qso.qrzSent, filter.qrzSent) && statusMatches(qso.qrzReceived, filter.qrzReceived) &&
            textMatches(qso.qslVia, filter.qslVia) && presenceMatches(qso.qslImages, filter.qslImages)
    }.toList()

    val comparator = when (filter.sort) {
        LogbookSort.TIME -> compareBy<Qso> { it.createdAt }
        LogbookSort.CALLSIGN -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.callsign }
        LogbookSort.DXCC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.dxcc }
        LogbookSort.MODE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.mode }
        LogbookSort.BAND -> compareBy<Qso> { bandRank(it.band) }.thenBy { it.frequencyHz }
        LogbookSort.FREQUENCY -> compareBy<Qso> { it.frequencyHz }
        LogbookSort.DISTANCE -> compareBy<Qso> { it.distanceKm }
        LogbookSort.DURATION -> compareBy<Qso> { it.durationSeconds }
    }
    val ordered = matching.sortedWith(if (filter.direction == LogbookSortDirection.DESCENDING) comparator.reversed() else comparator)
    return ordered.take(filter.limit.coerceIn(1, 1_000))
}

fun activeLogbookFilterCount(filter: LogbookFilter): Int = listOf(
    filter.fromEpochSeconds, filter.toEpochSecondsExclusive, filter.callsign, filter.dxcc, filter.state, filter.grid,
    filter.mode, filter.band, filter.propagation, filter.county, filter.dok, filter.sota, filter.pota, filter.iota,
    filter.wwff, filter.operator, filter.contest, filter.continent, filter.comment, filter.distance, filter.duration,
    filter.qslSent, filter.qslReceived, filter.qslSentMethod, filter.qslReceivedMethod, filter.lotwSent,
    filter.lotwReceived, filter.clublogSent, filter.clublogReceived, filter.eqslSent, filter.eqslReceived,
    filter.dclSent, filter.dclReceived, filter.qrzSent, filter.qrzReceived, filter.qslVia, filter.qslImages,
).count { value -> value != null && value.toString().isNotBlank() }

private fun textMatches(actual: String, expected: String) = expected.isBlank() || actual.contains(expected.trim(), ignoreCase = true)
private fun choiceMatches(actual: String, expected: String) = expected.isBlank() || actual.equals(expected, ignoreCase = true)

private fun statusMatches(actual: String, expected: String): Boolean {
    if (expected.isBlank()) return true
    val normalized = actual.trim().uppercase()
    return when (expected.uppercase()) {
        "Y" -> normalized in setOf("Y", "YES", "S", "SENT", "UPLOADED", "1", "TRUE")
        "N" -> normalized.isBlank() || normalized in setOf("N", "NO", "0", "FALSE")
        else -> normalized == expected.uppercase()
    }
}

private fun presenceMatches(actual: String, expected: String): Boolean = when (expected.uppercase()) {
    "Y" -> actual.isNotBlank() && actual.uppercase() !in setOf("N", "NO", "0", "FALSE")
    "N" -> actual.isBlank() || actual.uppercase() in setOf("N", "NO", "0", "FALSE")
    else -> true
}

internal fun numericMatches(actual: Double, expression: String): Boolean {
    val raw = expression.trim().replace(",", ".")
    if (raw.isBlank() || raw == "*") return true
    Regex("^(-?\\d+(?:\\.\\d+)?)\\s*(?:\\.\\.|-)\\s*(-?\\d+(?:\\.\\d+)?)$").matchEntire(raw)?.let {
        val low = it.groupValues[1].toDouble(); val high = it.groupValues[2].toDouble()
        return actual in minOf(low, high)..maxOf(low, high)
    }
    val match = Regex("^(>=|<=|>|<|=)?\\s*(-?\\d+(?:\\.\\d+)?)$").matchEntire(raw) ?: return false
    val value = match.groupValues[2].toDouble()
    return when (match.groupValues[1]) {
        ">" -> actual > value; ">=" -> actual >= value; "<" -> actual < value; "<=" -> actual <= value
        "=" -> actual == value; else -> actual >= value
    }
}

private fun bandRank(band: String) = listOf("2200m", "630m", "160m", "80m", "60m", "40m", "30m", "20m", "17m", "15m", "12m", "10m", "6m")
    .indexOf(band.lowercase()).let { if (it < 0) Int.MAX_VALUE else it }
