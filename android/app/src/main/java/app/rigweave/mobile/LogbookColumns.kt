package app.rigweave.mobile

const val LOGBOOK_ROW_HEIGHT_DP = 48
const val LOGBOOK_HEADER_HEIGHT_DP = 48
const val LOGBOOK_ROW_FONT_SP = 23.4f

enum class LogbookColumn(val label: String, val width: Int, val sort: LogbookSort? = null) {
    DATE_TIME("DATE / TIME", 180, LogbookSort.TIME),
    CALLSIGN("DX", 134, LogbookSort.CALLSIGN),
    MODE("MODE", 100, LogbookSort.MODE),
    RST_SENT("RST S", 64),
    RST_RECEIVED("RST R", 64),
    BAND("BAND", 88, LogbookSort.BAND),
    FREQUENCY("FREQUENCY", 140, LogbookSort.FREQUENCY),
    GRID("GRID", 118),
    QSL("QSL", 56),
    EQSL("eQSL", 64),
    LOTW("LoTW", 64),
    CLUBLOG("CLUBLOG", 92),
    QRZ("QRZ", 56),
    DXCC("DXCC / COUNTRY", 230, LogbookSort.DXCC),
    STATE("STATE", 90),
    COUNTY("COUNTY", 160),
    IOTA("IOTA", 118),
    POTA("POTA", 136),
    SOTA("SOTA", 136),
    WWFF("WWFF", 136),
    REGION("REGION", 144),
}

fun decodeLogbookColumns(serialized: String?): List<LogbookColumn> {
    if (serialized.isNullOrBlank()) return LogbookColumn.entries
    val selected = serialized.split(',').mapNotNull { token ->
        LogbookColumn.entries.firstOrNull { it.name == token.trim() }
    }.toSet()
    return LogbookColumn.entries.filter { it in selected }.ifEmpty { LogbookColumn.entries }
}

fun encodeLogbookColumns(columns: Collection<LogbookColumn>): String =
    LogbookColumn.entries.filter { it in columns }.joinToString(",") { it.name }
