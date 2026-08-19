package app.rigweave.mobile

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID

data class FastEntryDefaults(val date: LocalDate, val operatorCallsign: String = "", val stationCallsign: String = "",
    val stationProfileId: String = "", val myGrid: String = "")
data class FastEntryIssue(val line: Int, val message: String, val warning: Boolean = false)
data class FastEntryRow(val line: Int, val qso: Qso, val inherited: Set<String>)
data class FastEntryResult(val rows: List<FastEntryRow>, val issues: List<FastEntryIssue>) {
    val errors get() = issues.filterNot { it.warning }
    val warnings get() = issues.filter { it.warning }
}

object FastEntryParser {
    private val bandPattern = Regex("(?i)^(?:\\d{1,4}(?:m|cm|mm)|sat)$")
    private val callPattern = Regex("(?i)^(?:[A-Z0-9]{1,4}/)?[A-Z0-9]{1,3}\\d[A-Z0-9]{1,4}(?:/[A-Z0-9]{1,4})?$")
    private val gridPattern = Regex("(?i)^#?[A-R]{2}\\d{2}(?:[A-X]{2}(?:\\d{2}(?:[A-X]{2})?)?)?$")
    private val modePattern = Regex("(?i)^(?:AM|CW|CWR|SSB|USB|LSB|FM|RTTY|FT8|FT4|JS8|PSK31|PSK63|MFSK|DATA|DIGI|DV|DSTAR|FREEDV|SSTV)$")
    private val referencePattern = Regex("(?i)^(?:[A-Z0-9]{1,3}/[A-Z]{2}-\\d{3}|[A-Z]*[FNSUACA]-\\d{3}|[A-Z0-9]{1,3}-\\d{4,5}|[A-Z0-9]{1,3}FF-\\d{4})$")

    fun parse(text: String, defaults: FastEntryDefaults): FastEntryResult {
        var date = defaults.date; var timezoneOffset = 0; var band = ""; var mode = ""
        var frequencyMhz: Double? = null; var time = ""
        val retained = linkedMapOf<String, String>(); val rows = mutableListOf<FastEntryRow>(); val issues = mutableListOf<FastEntryIssue>()
        text.lineSequence().forEachIndexed { zeroIndex, original ->
            val lineNumber = zeroIndex + 1; var line = original.trim()
            if (line.isBlank() || line.startsWith("//")) return@forEachIndexed
            Regex("(?i)^(?:TIMEZONE|TZOFS)\\s+([+-]\\d{1,2})$").matchEntire(line)?.let {
                timezoneOffset = it.groupValues[1].toInt().coerceIn(-14, 14); return@forEachIndexed }
            Regex("(?i)^date\\s+(\\d{4}-\\d{2}-\\d{2})$").matchEntire(line)?.let {
                val parsed = runCatching { LocalDate.parse(it.groupValues[1]) }.getOrNull()
                if (parsed == null) issues += FastEntryIssue(lineNumber, "Invalid date") else date = parsed
                return@forEachIndexed }
            Regex("(?i)^day\\s+(\\++)$").matchEntire(line)?.let { date = date.plusDays(it.groupValues[1].length.toLong()); return@forEachIndexed }
            val fields = linkedMapOf<String, String>()
            Regex("<([^>]*)>|\\[([^]]*)]").findAll(line).toList().forEach { match ->
                line = line.replace(match.value, " "); val angle = match.groupValues[1]; val bracket = match.groupValues[2]
                if (bracket.isNotBlank()) fields["QSLMSG"] = bracket.trim() else {
                    val pair = Regex("^([A-Za-z0-9_]+)\\s*:\\s*(.*)$").matchEntire(angle.trim())
                    if (pair == null) fields["COMMENT"] = listOfNotNull(fields["COMMENT"], angle.trim()).filter(String::isNotBlank).joinToString(" ")
                    else { val key = pair.groupValues[1].uppercase(Locale.US); val value = pair.groupValues[2].trim()
                        if (value.isBlank()) retained.remove(key) else { if (key.startsWith("MY_") || key == "TX_PWR") retained[key] = value; fields[key] = value } }
                }
            }
            val inherited = mutableSetOf<String>(); var callsign = ""; var grid = ""; var rstSent = ""; var rstReceived = ""; var name = ""; var reference = ""
            val tokens = line.split(Regex("\\s+")).filter(String::isNotBlank)
            tokens.forEachIndexed { index, token -> when {
                bandPattern.matches(token) -> { band = token.lowercase(Locale.US); frequencyMhz = null }
                modePattern.matches(token) -> { mode = token.uppercase(Locale.US); frequencyMhz = null }
                token.matches(Regex("^\\d+\\.\\d+$")) -> { frequencyMhz = token.toDouble(); band = bandForFrequency((frequencyMhz!! * 1_000_000).toLong()) }
                token.matches(Regex("^[0-2]\\d[0-5]\\d$")) -> time = token
                index == 0 && time.isNotBlank() && token.matches(Regex("^[0-9]$")) -> time = time.dropLast(1) + token
                index == 0 && time.isNotBlank() && token.matches(Regex("^[0-5]\\d$")) -> time = time.dropLast(2) + token
                referencePattern.matches(token) -> reference = token.uppercase(Locale.US)
                callsign.isBlank() && callPattern.matches(token) -> callsign = token.uppercase(Locale.US)
                gridPattern.matches(token) -> grid = token.removePrefix("#").uppercase(Locale.US)
                callsign.isNotBlank() && token.matches(Regex("^[-+]?\\d{1,3}$")) && rstSent.isBlank() -> rstSent = token
                callsign.isNotBlank() && token.matches(Regex("^[-+]?\\d{1,3}$")) && rstReceived.isBlank() -> rstReceived = token
                callsign.isNotBlank() && token.startsWith("@") -> name = token.drop(1)
                callsign.isNotBlank() && token.startsWith(",") -> parseExchange(token.drop(1), true, fields)
                callsign.isNotBlank() && token.startsWith(".") -> parseExchange(token.drop(1), false, fields)
                else -> issues += FastEntryIssue(lineNumber, "Unrecognized token: $token", warning = callsign.isNotBlank())
            } }
            if (callsign.isBlank()) return@forEachIndexed
            if (band.isBlank()) issues += FastEntryIssue(lineNumber, "Band or frequency is required") else if (tokens.none(bandPattern::matches) && frequencyMhz == null) inherited += "band"
            if (mode.isBlank()) issues += FastEntryIssue(lineNumber, "Mode is required") else if (tokens.none(modePattern::matches)) inherited += "mode"
            if (time.isBlank()) issues += FastEntryIssue(lineNumber, "UTC time is required") else if (tokens.none { it.matches(Regex("^[0-2]\\d[0-5]\\d$")) }) inherited += "time"
            val frequencyHz = frequencyMhz?.times(1_000_000)?.toLong() ?: defaultFrequency(band, mode)
            if (frequencyHz <= 0) issues += FastEntryIssue(lineNumber, "Frequency is unknown for $band $mode")
            if (issues.any { it.line == lineNumber && !it.warning }) return@forEachIndexed
            val local = LocalDateTime.of(date.year, date.month, date.dayOfMonth, time.take(2).toInt(), time.drop(2).take(2).toInt())
            val utc = local.minusHours(timezoneOffset.toLong()); val report = defaultReport(mode); val allFields = retained + fields
            val qso = Qso(id = UUID.randomUUID().toString(), callsign = callsign, frequencyHz = frequencyHz, mode = mainMode(mode),
                submode = mode.takeIf { mainMode(it) != it }.orEmpty(), rstSent = rstSent.ifBlank { report }, rstReceived = rstReceived.ifBlank { report },
                createdAt = utc.toEpochSecond(ZoneOffset.UTC), band = band, grid = grid, name = name,
                operatorCallsign = defaults.operatorCallsign, stationCallsign = defaults.stationCallsign, stationProfileId = defaults.stationProfileId,
                myGrid = allFields["MY_GRIDSQUARE"] ?: defaults.myGrid, txPowerW = allFields["TX_PWR"]?.toDoubleOrNull()?.toInt() ?: 0,
                comment = allFields["COMMENT"].orEmpty(), qslMessage = allFields["QSLMSG"].orEmpty(),
                sotaRef = reference.takeIf { it.contains('/') }.orEmpty(), iota = reference.takeIf { Regex("(?i)^[A-Z]*[FNSUACA]-\\d{3}$").matches(it) }.orEmpty(),
                wwffRef = reference.takeIf { it.contains("FF-") }.orEmpty(),
                potaRef = reference.takeIf { !it.contains('/') && !it.contains("FF-") && Regex("(?i)^[A-Z0-9]{1,3}-\\d{4,5}$").matches(it) }.orEmpty(),
                contestId = allFields["CONTEST_ID"].orEmpty(), extraAdifFields = allFields.filterKeys { it !in WavelogCanonicalizer.rigWeaveFields })
            rows += FastEntryRow(lineNumber, qso, inherited)
        }
        return FastEntryResult(rows, issues)
    }

    private fun parseExchange(value: String, sent: Boolean, fields: MutableMap<String, String>) {
        val parts = value.split(if (sent) '.' else ',')
        parts.firstOrNull()?.takeIf { it.all(Char::isDigit) }?.let { fields[if (sent) "STX" else "SRX"] = it }
        parts.drop(1).filter(String::isNotBlank).joinToString(" ").takeIf(String::isNotBlank)?.let { fields[if (sent) "STX_STRING" else "SRX_STRING"] = it }
    }
    private fun mainMode(mode: String) = when (mode.uppercase(Locale.US)) { "USB", "LSB" -> "SSB"; "FT8", "FT4", "JS8" -> "MFSK"; "PSK31", "PSK63" -> "PSK"; else -> mode.uppercase(Locale.US) }
    private fun defaultReport(mode: String) = when (mainMode(mode)) { "CW" -> "599"; "MFSK", "PSK", "RTTY", "DATA", "DIGI" -> "+0"; else -> "59" }
    private fun defaultFrequency(band: String, mode: String): Long = mapOf("160m" to 1_840_000L, "80m" to 3_650_000L, "60m" to 5_357_000L,
        "40m" to 7_100_000L, "30m" to 10_120_000L, "20m" to 14_200_000L, "17m" to 18_100_000L, "15m" to 21_250_000L,
        "12m" to 24_930_000L, "10m" to 28_500_000L, "6m" to 50_150_000L)[band.lowercase(Locale.US)]
        ?.let { if (mainMode(mode) in setOf("CW", "MFSK", "PSK", "RTTY")) it - 100_000 else it } ?: 0
}
