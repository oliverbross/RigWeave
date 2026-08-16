package app.rigweave.mobile

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class Qso(
    val id: String, val callsign: String, val frequencyHz: Long, val mode: String,
    val rstSent: String, val rstReceived: String, val createdAt: Long,
    val name: String = "", val qth: String = "", val notes: String = "", val country: String = "",
    val band: String = "", val grid: String = "", val iota: String = "", val sotaRef: String = "",
    val wwffRef: String = "", val potaRef: String = "", val comment: String = "",
    val frequencyRxHz: Long = 0, val bandRx: String = "", val txPowerW: Int = 0,
    val operatorCallsign: String = "", val stationCallsign: String = "", val stationProfileId: String = "",
    val stationLocation: String = "", val myGrid: String = "", val myCountry: String = "",
    val myDxcc: String = "", val myCqZone: String = "", val myItuZone: String = "", val myState: String = "",
    val myIota: String = "", val mySotaRef: String = "", val myWwffRef: String = "", val myPotaRef: String = "",
    val radioModel: String = "", val dxcc: String = "", val continent: String = "", val region: String = "",
    val cqZone: String = "", val ituZone: String = "", val state: String = "", val email: String = "",
    val propagationMode: String = "", val antennaPath: String = "", val qslSent: String = "N",
    val qslMethod: String = "", val qslVia: String = "", val qslMessage: String = "",
    val submode: String = "", val county: String = "", val dok: String = "", val contestId: String = "",
    val distanceKm: Double = 0.0, val durationSeconds: Long = 0,
    val qslReceived: String = "N", val qslReceivedMethod: String = "",
    val lotwSent: String = "N", val lotwReceived: String = "N",
    val clublogSent: String = "N", val clublogReceived: String = "N",
    val eqslSent: String = "N", val eqslReceived: String = "N",
    val dclSent: String = "N", val dclReceived: String = "N",
    val qrzSent: String = "N", val qrzReceived: String = "N", val qslImages: String = "",
    val syncState: String = "local", val remoteId: String = "",
)

data class QsoPage(val rows: List<Qso>, val total: Int, val page: Int, val pageSize: Int) {
    val pageCount get() = logbookPageCount(total, pageSize)
}

fun bandForFrequency(frequencyHz: Long): String = when (frequencyHz) {
    in 135_700L..137_800L -> "2200m"; in 472_000L..479_000L -> "630m"
    in 1_800_000L..2_000_000L -> "160m"; in 3_500_000L..4_000_000L -> "80m"
    in 5_250_000L..5_450_000L -> "60m"; in 7_000_000L..7_300_000L -> "40m"
    in 10_100_000L..10_150_000L -> "30m"; in 14_000_000L..14_350_000L -> "20m"
    in 18_068_000L..18_168_000L -> "17m"; in 21_000_000L..21_450_000L -> "15m"
    in 24_890_000L..24_990_000L -> "12m"; in 28_000_000L..29_700_000L -> "10m"
    in 50_000_000L..54_000_000L -> "6m"; else -> ""
}

class QsoDatabase(context: Context, databaseName: String = "rigweave.sqlite") : SQLiteOpenHelper(context, databaseName, null, 6) {
    private val changeRevision = AtomicLong(0)
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE radio_profile(id TEXT PRIMARY KEY, model TEXT NOT NULL)")
        db.execSQL("CREATE TABLE qso(id TEXT PRIMARY KEY, callsign TEXT NOT NULL, frequency_hz INTEGER NOT NULL, mode TEXT NOT NULL, rst_sent TEXT NOT NULL, rst_received TEXT NOT NULL, created_at INTEGER NOT NULL, name TEXT NOT NULL DEFAULT '', qth TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', country TEXT NOT NULL DEFAULT '', details_json TEXT NOT NULL DEFAULT '{}')")
        createPagingIndexes(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE qso ADD COLUMN name TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE qso ADD COLUMN qth TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE qso ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE qso ADD COLUMN country TEXT NOT NULL DEFAULT ''")
        if (oldVersion < 4) db.execSQL("ALTER TABLE qso ADD COLUMN details_json TEXT NOT NULL DEFAULT '{}'")
        if (oldVersion < 5) createPagingIndexes(db)
        if (oldVersion < 6) createSpotStatusIndexes(db)
    }

    private fun createPagingIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS qso_created_at_idx ON qso(created_at DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS qso_station_idx ON qso(json_extract(details_json,'$.stationProfileId'))")
        db.execSQL("CREATE INDEX IF NOT EXISTS qso_sync_state_idx ON qso(json_extract(details_json,'$.syncState'))")
        createSpotStatusIndexes(db)
    }

    private fun createSpotStatusIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS qso_callsign_upper_idx ON qso(UPPER(callsign))")
        db.execSQL("CREATE INDEX IF NOT EXISTS qso_country_upper_idx ON qso(UPPER(country))")
        db.execSQL("CREATE INDEX IF NOT EXISTS qso_dxcc_upper_idx ON qso(UPPER(COALESCE(json_extract(details_json,'$.dxcc'),'')))")
    }

    fun save(qso: Qso): Boolean {
        readableDatabase.rawQuery("SELECT 1 FROM qso WHERE callsign=? AND frequency_hz=? AND mode=? AND created_at BETWEEN ? AND ? LIMIT 1",
            arrayOf(qso.callsign, qso.frequencyHz.toString(), qso.mode, (qso.createdAt - 15).toString(), (qso.createdAt + 15).toString())).use {
            if (it.moveToFirst()) return false
        }
        insert(qso); return true
    }

    fun mergeRemote(remote: Qso): Boolean {
        val existing = findNatural(remote)
        if (existing == null) { insert(remote.copy(syncState = "synced")); return true }
        val merged = remote.copy(
            id = existing.id, name = remote.name.ifBlank { existing.name }, qth = remote.qth.ifBlank { existing.qth },
            notes = remote.notes.ifBlank { existing.notes }, country = remote.country.ifBlank { existing.country },
            grid = remote.grid.ifBlank { existing.grid }, comment = remote.comment.ifBlank { existing.comment },
            iota = remote.iota.ifBlank { existing.iota }, sotaRef = remote.sotaRef.ifBlank { existing.sotaRef },
            wwffRef = remote.wwffRef.ifBlank { existing.wwffRef }, potaRef = remote.potaRef.ifBlank { existing.potaRef },
            operatorCallsign = remote.operatorCallsign.ifBlank { existing.operatorCallsign },
            stationCallsign = remote.stationCallsign.ifBlank { existing.stationCallsign },
            stationProfileId = remote.stationProfileId.ifBlank { existing.stationProfileId },
            stationLocation = remote.stationLocation.ifBlank { existing.stationLocation },
            myGrid = remote.myGrid.ifBlank { existing.myGrid }, myCountry = remote.myCountry.ifBlank { existing.myCountry },
            radioModel = remote.radioModel.ifBlank { existing.radioModel }, dxcc = remote.dxcc.ifBlank { existing.dxcc },
            continent = remote.continent.ifBlank { existing.continent }, region = remote.region.ifBlank { existing.region },
            cqZone = remote.cqZone.ifBlank { existing.cqZone }, ituZone = remote.ituZone.ifBlank { existing.ituZone },
            state = remote.state.ifBlank { existing.state }, email = remote.email.ifBlank { existing.email },
            propagationMode = remote.propagationMode.ifBlank { existing.propagationMode },
            antennaPath = remote.antennaPath.ifBlank { existing.antennaPath },
            qslMethod = remote.qslMethod.ifBlank { existing.qslMethod }, qslVia = remote.qslVia.ifBlank { existing.qslVia },
            qslMessage = remote.qslMessage.ifBlank { existing.qslMessage }, submode = remote.submode.ifBlank { existing.submode },
            county = remote.county.ifBlank { existing.county }, dok = remote.dok.ifBlank { existing.dok },
            contestId = remote.contestId.ifBlank { existing.contestId },
            distanceKm = remote.distanceKm.takeIf { it > 0 } ?: existing.distanceKm,
            durationSeconds = remote.durationSeconds.takeIf { it > 0 } ?: existing.durationSeconds,
            qslReceived = remote.qslReceived.ifBlank { existing.qslReceived },
            qslReceivedMethod = remote.qslReceivedMethod.ifBlank { existing.qslReceivedMethod },
            lotwSent = remote.lotwSent.ifBlank { existing.lotwSent }, lotwReceived = remote.lotwReceived.ifBlank { existing.lotwReceived },
            clublogSent = remote.clublogSent.ifBlank { existing.clublogSent }, clublogReceived = remote.clublogReceived.ifBlank { existing.clublogReceived },
            eqslSent = remote.eqslSent.ifBlank { existing.eqslSent }, eqslReceived = remote.eqslReceived.ifBlank { existing.eqslReceived },
            dclSent = remote.dclSent.ifBlank { existing.dclSent }, dclReceived = remote.dclReceived.ifBlank { existing.dclReceived },
            qrzSent = remote.qrzSent.ifBlank { existing.qrzSent }, qrzReceived = remote.qrzReceived.ifBlank { existing.qrzReceived },
            qslImages = remote.qslImages.ifBlank { existing.qslImages }, syncState = "synced",
            remoteId = remote.remoteId.ifBlank { existing.remoteId })
        update(merged); return false
    }

    fun markSynced(id: String) { findById(id)?.let { update(it.copy(syncState = "synced")) } }
    fun changeToken(): Long = changeRevision.get()
    fun list(): List<Qso> = query(" LIMIT 100")
    fun all(): List<Qso> = query("")
    fun page(page: Int, pageSize: Int, filter: LogbookFilter = LogbookFilter(), stationId: String? = null): QsoPage {
        val size = normalizedLogbookPageSize(pageSize)
        val spec = pageQuery(filter, stationId)
        val total = readableDatabase.rawQuery("SELECT COUNT(*) FROM qso WHERE ${spec.where}", spec.args.toTypedArray()).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        val boundedPage = page.coerceIn(0, logbookPageCount(total, size) - 1)
        val args = (spec.args + listOf(size.toString(), (boundedPage * size).toString())).toTypedArray()
        val rows = queryWhere("${spec.where} ORDER BY ${spec.order} LIMIT ? OFFSET ?", args)
        return QsoPage(rows, total, boundedPage, size)
    }

    fun spotStatuses(spots: List<SpotLogIdentity>, stationId: String? = null): Map<String, SpotLogStatus> {
        if (spots.isEmpty()) return emptyMap()
        val calls = dimensions("UPPER(callsign)", spots.map { it.callsign }, stationId)
        val countries = dimensions("UPPER(country)", spots.map { it.country }, stationId)
        val dxccs = dimensions("UPPER(COALESCE(json_extract(details_json,'$.dxcc'),''))",
            spots.map { it.dxcc }, stationId)
        return spots.associate { spot ->
            val call = calls[spot.callsign.normalizedStatusKey()] ?: WorkedDimensions()
            val dxcc = spot.dxcc.normalizedStatusKey()
            val entity = if (dxcc.isNotBlank() && dxccs.containsKey(dxcc)) dxccs.getValue(dxcc)
                else countries[spot.country.normalizedStatusKey()] ?: WorkedDimensions()
            spot.id to classifySpotStatus(spot, call, entity)
        }
    }

    private fun dimensions(keyExpression: String, rawKeys: List<String>, stationId: String?): Map<String, WorkedDimensions> {
        val keys = rawKeys.map { it.normalizedStatusKey() }.filter(String::isNotBlank).distinct()
        if (keys.isEmpty()) return emptyMap()
        val placeholders = keys.joinToString(",") { "?" }
        val stationClause = if (stationId == null)
            " AND COALESCE(json_extract(details_json,'$.stationProfileId'),'') = ''"
        else " AND COALESCE(json_extract(details_json,'$.stationProfileId'),'') = ?"
        val bandExpression = "UPPER(COALESCE(json_extract(details_json,'$.band'),''))"
        val submodeExpression = "UPPER(COALESCE(json_extract(details_json,'$.submode'),''))"
        val confirmedExpression = listOf("qslReceived", "lotwReceived")
            .joinToString(" OR ") { "UPPER(COALESCE(json_extract(details_json,'$.$it'),'')) IN ('Y','V')" }
        val sql = """SELECT $keyExpression, $bandExpression, $submodeExpression, UPPER(mode),
            MAX(CASE WHEN $confirmedExpression THEN 1 ELSE 0 END)
            FROM qso WHERE $keyExpression IN ($placeholders)$stationClause
            GROUP BY $keyExpression, $bandExpression, $submodeExpression, UPPER(mode)""".trimIndent()
        val mutable = mutableMapOf<String, MutableWorkedDimensions>()
        val args = (keys + listOfNotNull(stationId)).toTypedArray()
        readableDatabase.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                val key = cursor.getString(0).orEmpty().normalizedStatusKey()
                val band = cursor.getString(1).orEmpty().normalizedStatusKey()
                val mode = canonicalSpotMode(cursor.getString(2).orEmpty().ifBlank { cursor.getString(3).orEmpty() })
                val confirmed = cursor.getInt(4) == 1
                mutable.getOrPut(key, ::MutableWorkedDimensions).add(band, mode, confirmed)
            }
        }
        return mutable.mapValues { it.value.freeze() }
    }

    private class MutableWorkedDimensions {
        var any = false
        var confirmedAny = false
        val bands = mutableSetOf<String>()
        val confirmedBands = mutableSetOf<String>()
        val bandModes = mutableSetOf<String>()
        val confirmedBandModes = mutableSetOf<String>()

        fun add(band: String, mode: String, confirmed: Boolean) {
            any = true
            if (confirmed) confirmedAny = true
            if (band.isNotBlank()) {
                bands += band
                if (confirmed) confirmedBands += band
                if (mode.isNotBlank()) {
                    val bandMode = "$band|$mode"
                    bandModes += bandMode
                    if (confirmed) confirmedBandModes += bandMode
                }
            }
        }

        fun freeze() = WorkedDimensions(any, confirmedAny, bands, confirmedBands, bandModes, confirmedBandModes)
    }

    private fun String.normalizedStatusKey() = trim().uppercase(java.util.Locale.US)
    fun <T> transaction(block: () -> T): T {
        val database = writableDatabase
        database.beginTransaction()
        return try {
            block().also { database.setTransactionSuccessful() }
        } finally {
            database.endTransaction()
        }
    }
    fun exportADIF(): String = all().asReversed().joinToString("") { toADIF(it) }

    fun toADIF(qso: Qso): String {
        val at = java.time.Instant.ofEpochSecond(qso.createdAt).atZone(ZoneOffset.UTC)
        var adif = NativeCore.adif(qso.id, qso.callsign, at.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
            at.format(DateTimeFormatter.ofPattern("HHmmss")), qso.frequencyHz, qso.mode, qso.rstSent, qso.rstReceived)
        val fields = listOf(
            "BAND" to qso.band.ifBlank { bandForFrequency(qso.frequencyHz) }, "NAME" to qso.name, "QTH" to qso.qth,
            "COUNTRY" to qso.country, "GRIDSQUARE" to qso.grid, "IOTA" to qso.iota, "SOTA_REF" to qso.sotaRef,
            "WWFF_REF" to qso.wwffRef, "POTA_REF" to qso.potaRef, "COMMENT" to qso.comment, "NOTES" to qso.notes,
            "FREQ_RX" to qso.frequencyRxHz.takeIf { it > 0 }?.let { "%.6f".format(java.util.Locale.US, it / 1_000_000.0) }.orEmpty(),
            "BAND_RX" to qso.bandRx, "TX_PWR" to qso.txPowerW.takeIf { it > 0 }?.toString().orEmpty(),
            "OPERATOR" to qso.operatorCallsign, "STATION_CALLSIGN" to qso.stationCallsign, "MY_GRIDSQUARE" to qso.myGrid,
            "MY_COUNTRY" to qso.myCountry, "MY_DXCC" to qso.myDxcc, "MY_CQ_ZONE" to qso.myCqZone,
            "MY_ITU_ZONE" to qso.myItuZone, "MY_STATE" to qso.myState, "MY_IOTA" to qso.myIota,
            "MY_SOTA_REF" to qso.mySotaRef, "MY_WWFF_REF" to qso.myWwffRef, "MY_POTA_REF" to qso.myPotaRef,
            "RIG" to qso.radioModel, "DXCC" to qso.dxcc, "CONT" to qso.continent,
            "APP_RIGWEAVE_REGION" to qso.region, "CQZ" to qso.cqZone, "ITUZ" to qso.ituZone,
            "STATE" to qso.state, "EMAIL" to qso.email, "PROP_MODE" to qso.propagationMode,
            "ANT_PATH" to qso.antennaPath, "QSL_SENT" to qso.qslSent, "QSL_SENT_VIA" to qso.qslMethod,
            "QSL_VIA" to qso.qslVia, "QSLMSG" to qso.qslMessage, "SUBMODE" to qso.submode,
            "CNTY" to qso.county, "DARC_DOK" to qso.dok, "CONTEST_ID" to qso.contestId,
            "DISTANCE" to qso.distanceKm.takeIf { it > 0 }?.toString().orEmpty(),
            "APP_RIGWEAVE_DURATION_SECONDS" to qso.durationSeconds.takeIf { it > 0 }?.toString().orEmpty(),
            "QSL_RCVD" to qso.qslReceived, "QSL_RCVD_VIA" to qso.qslReceivedMethod,
            "LOTW_QSL_SENT" to qso.lotwSent, "LOTW_QSL_RCVD" to qso.lotwReceived,
            "CLUBLOG_QSO_UPLOAD_STATUS" to qso.clublogSent, "CLUBLOG_QSO_DOWNLOAD_STATUS" to qso.clublogReceived,
            "EQSL_QSL_SENT" to qso.eqslSent, "EQSL_QSL_RCVD" to qso.eqslReceived,
            "DCL_QSL_SENT" to qso.dclSent, "DCL_QSL_RCVD" to qso.dclReceived,
            "QRZCOM_QSO_UPLOAD_STATUS" to qso.qrzSent, "QRZCOM_QSO_DOWNLOAD_STATUS" to qso.qrzReceived,
            "APP_RIGWEAVE_QSL_IMAGES" to qso.qslImages)
        fields.forEach { (name, value) -> if (value.isNotBlank())
            adif = adif.replace("<EOR>", "<$name:${value.toByteArray(Charsets.UTF_8).size}>$value<EOR>") }
        return adif
    }

    fun importADIF(text: String): Pair<Int, Int> {
        var added = 0; var skipped = 0
        Regex("(?is)(.*?<EOR>)").findAll(text.substringAfter("<EOH>", text)).forEach { match ->
            val record = match.value
            fun field(name: String): String {
                val tag = Regex("(?i)<${Regex.escape(name)}:(\\d+)(?::[^>]*)?>").find(record) ?: return ""
                val length = tag.groupValues[1].toIntOrNull() ?: return ""
                return record.substring(tag.range.last + 1).take(length)
            }
            val qso = qsoFromFields(::field, "", "")
            if (qso == null) skipped++ else if (save(qso)) added++ else skipped++
        }
        return added to skipped
    }

    fun qsoFromFields(field: (String) -> String, remoteId: String, stationProfileId: String): Qso? {
        val call = field("CALL").uppercase(); val mode = field("MODE").uppercase()
        val frequency = field("FREQ").toDoubleOrNull(); val date = field("QSO_DATE"); val time = field("TIME_ON")
        val epoch = runCatching { LocalDateTime.parse(date + time.padEnd(6, '0').take(6),
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).toEpochSecond(ZoneOffset.UTC) }.getOrNull()
        if (call.isBlank() || mode.isBlank() || frequency == null || epoch == null) return null
        val frequencyHz = (frequency * 1_000_000).toLong()
        val offDate = field("QSO_DATE_OFF").ifBlank { date }; val offTime = field("TIME_OFF")
        val offEpoch = offTime.takeIf { it.isNotBlank() }?.let { value -> runCatching {
            LocalDateTime.parse(offDate + value.padEnd(6, '0').take(6), DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                .toEpochSecond(ZoneOffset.UTC)
        }.getOrNull() }
        return Qso(
            id = field("APP_KX3TOUCH_UUID").ifBlank { if (remoteId.isBlank()) UUID.randomUUID().toString() else "wavelog-$stationProfileId-$remoteId" },
            callsign = call, frequencyHz = frequencyHz, mode = mode, rstSent = field("RST_SENT"),
            rstReceived = field("RST_RCVD"), createdAt = epoch, name = field("NAME"), qth = field("QTH"),
            notes = field("NOTES"), country = field("COUNTRY"), band = field("BAND").ifBlank { bandForFrequency(frequencyHz) },
            grid = field("GRIDSQUARE"), iota = field("IOTA"), sotaRef = field("SOTA_REF"),
            wwffRef = field("WWFF_REF"), potaRef = field("POTA_REF"), comment = field("COMMENT"),
            frequencyRxHz = field("FREQ_RX").toDoubleOrNull()?.let { (it * 1_000_000).toLong() } ?: 0,
            bandRx = field("BAND_RX"), txPowerW = field("TX_PWR").toDoubleOrNull()?.toInt() ?: 0,
            operatorCallsign = field("OPERATOR"), stationCallsign = field("STATION_CALLSIGN"),
            stationProfileId = stationProfileId, myGrid = field("MY_GRIDSQUARE"), myCountry = field("MY_COUNTRY"),
            myDxcc = field("MY_DXCC"), myCqZone = field("MY_CQ_ZONE"), myItuZone = field("MY_ITU_ZONE"),
            myState = field("MY_STATE"), myIota = field("MY_IOTA"), mySotaRef = field("MY_SOTA_REF"),
            myWwffRef = field("MY_WWFF_REF"), myPotaRef = field("MY_POTA_REF"), radioModel = field("RIG"),
            dxcc = field("DXCC"), continent = field("CONT"), region = field("APP_RIGWEAVE_REGION"),
            cqZone = field("CQZ"), ituZone = field("ITUZ"), state = field("STATE"), email = field("EMAIL"),
            propagationMode = field("PROP_MODE"), antennaPath = field("ANT_PATH"),
            qslSent = field("QSL_SENT"), qslMethod = field("QSL_SENT_VIA"), qslVia = field("QSL_VIA"),
            qslMessage = field("QSLMSG"), submode = field("SUBMODE"), county = field("CNTY"),
            dok = field("DARC_DOK"), contestId = field("CONTEST_ID"), distanceKm = field("DISTANCE").toDoubleOrNull() ?: 0.0,
            durationSeconds = field("APP_RIGWEAVE_DURATION_SECONDS").toLongOrNull()
                ?: offEpoch?.minus(epoch)?.coerceAtLeast(0) ?: 0,
            qslReceived = field("QSL_RCVD"), qslReceivedMethod = field("QSL_RCVD_VIA"),
            lotwSent = field("LOTW_QSL_SENT"), lotwReceived = field("LOTW_QSL_RCVD"),
            clublogSent = field("CLUBLOG_QSO_UPLOAD_STATUS"), clublogReceived = field("CLUBLOG_QSO_DOWNLOAD_STATUS"),
            eqslSent = field("EQSL_QSL_SENT"), eqslReceived = field("EQSL_QSL_RCVD"),
            dclSent = field("DCL_QSL_SENT"), dclReceived = field("DCL_QSL_RCVD"),
            qrzSent = field("QRZCOM_QSO_UPLOAD_STATUS"), qrzReceived = field("QRZCOM_QSO_DOWNLOAD_STATUS"),
            qslImages = field("APP_RIGWEAVE_QSL_IMAGES"), syncState = if (remoteId.isBlank()) "local" else "synced",
            remoteId = remoteId)
    }

    private fun insert(qso: Qso) {
        writableDatabase.execSQL("INSERT INTO qso(id,callsign,frequency_hz,mode,rst_sent,rst_received,created_at,name,qth,notes,country,details_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any>(qso.id, qso.callsign, qso.frequencyHz, qso.mode, qso.rstSent, qso.rstReceived,
                qso.createdAt, qso.name, qso.qth, qso.notes, qso.country, details(qso).toString()))
        changeRevision.incrementAndGet()
    }
    private fun update(qso: Qso) {
        writableDatabase.execSQL("UPDATE qso SET callsign=?,frequency_hz=?,mode=?,rst_sent=?,rst_received=?,created_at=?,name=?,qth=?,notes=?,country=?,details_json=? WHERE id=?",
            arrayOf<Any>(qso.callsign, qso.frequencyHz, qso.mode, qso.rstSent, qso.rstReceived, qso.createdAt,
                qso.name, qso.qth, qso.notes, qso.country, details(qso).toString(), qso.id))
        changeRevision.incrementAndGet()
    }
    private fun findNatural(qso: Qso): Qso? = queryWhere("callsign=? AND frequency_hz=? AND mode=? AND created_at BETWEEN ? AND ? LIMIT 1",
        arrayOf(qso.callsign, qso.frequencyHz.toString(), qso.mode, (qso.createdAt - 15).toString(), (qso.createdAt + 15).toString())).firstOrNull()
    private fun findById(id: String): Qso? = queryWhere("id=? LIMIT 1", arrayOf(id)).firstOrNull()
    private fun query(limit: String): List<Qso> = queryWhere("1=1 ORDER BY created_at DESC$limit", null)

    private data class PageQuery(val where: String, val args: List<String>, val order: String)

    private fun pageQuery(filter: LogbookFilter, stationId: String?): PageQuery {
        val clauses = mutableListOf<String>(); val args = mutableListOf<String>()
        fun json(key: String) = "COALESCE(json_extract(details_json,'$.${key}'),'')"
        fun text(expression: String, value: String) {
            if (value.isBlank()) return
            clauses += "$expression LIKE ? ESCAPE '\\' COLLATE NOCASE"
            args += "%" + value.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        }
        fun choice(expression: String, value: String) {
            if (value.isBlank()) return
            clauses += "$expression = ? COLLATE NOCASE"; args += value.trim()
        }
        fun status(expression: String, value: String) {
            if (value.isBlank()) return
            val normalized = "UPPER(TRIM($expression))"
            if (value.equals("Y", true)) clauses += "$normalized IN ('Y','YES','S','SENT','UPLOADED','1','TRUE')"
            else if (value.equals("N", true)) clauses += "($normalized = '' OR $normalized IN ('N','NO','0','FALSE'))"
            else { clauses += "$normalized = ?"; args += value.trim().uppercase() }
        }
        fun numeric(expression: String, value: String) {
            val raw = value.trim().replace(',', '.')
            if (raw.isBlank() || raw == "*") return
            Regex("^(-?\\d+(?:\\.\\d+)?)\\s*(?:\\.\\.|-)\\s*(-?\\d+(?:\\.\\d+)?)$").matchEntire(raw)?.let {
                val first = it.groupValues[1].toDouble(); val second = it.groupValues[2].toDouble()
                clauses += "CAST($expression AS REAL) BETWEEN ? AND ?"
                args += minOf(first, second).toString(); args += maxOf(first, second).toString(); return
            }
            val match = Regex("^(>=|<=|>|<|=)?\\s*(-?\\d+(?:\\.\\d+)?)$").matchEntire(raw) ?: return
            clauses += "CAST($expression AS REAL) ${match.groupValues[1].ifBlank { ">=" }} ?"
            args += match.groupValues[2]
        }

        clauses += "1=1"
        stationId?.takeIf { it.isNotBlank() }?.let { station ->
            clauses += "(${json("stationProfileId")} = ? OR ${json("syncState")} = 'pending' OR (${json("stationProfileId")} = '' AND ${json("remoteId")} = ''))"
            args += station
        }
        filter.fromEpochSeconds?.let { clauses += "created_at >= ?"; args += it.toString() }
        filter.toEpochSecondsExclusive?.let { clauses += "created_at < ?"; args += it.toString() }
        text("callsign", filter.callsign); text(json("dxcc"), filter.dxcc); text(json("state"), filter.state)
        text(json("grid"), filter.grid); choice("mode", filter.mode); choice(json("band"), filter.band)
        choice(json("propagationMode"), filter.propagation); text(json("county"), filter.county)
        text(json("dok"), filter.dok); text(json("sotaRef"), filter.sota); text(json("potaRef"), filter.pota)
        text(json("iota"), filter.iota); text(json("wwffRef"), filter.wwff); text(json("operatorCallsign"), filter.operator)
        text(json("contestId"), filter.contest); choice(json("continent"), filter.continent)
        text("(COALESCE(json_extract(details_json,'$.comment'),'') || ' ' || notes)", filter.comment)
        numeric(json("distanceKm"), filter.distance); numeric("(${json("durationSeconds")} / 60.0)", filter.duration)
        status(json("qslSent"), filter.qslSent); status(json("qslReceived"), filter.qslReceived)
        choice(json("qslMethod"), filter.qslSentMethod); choice(json("qslReceivedMethod"), filter.qslReceivedMethod)
        status(json("lotwSent"), filter.lotwSent); status(json("lotwReceived"), filter.lotwReceived)
        status(json("clublogSent"), filter.clublogSent); status(json("clublogReceived"), filter.clublogReceived)
        status(json("eqslSent"), filter.eqslSent); status(json("eqslReceived"), filter.eqslReceived)
        status(json("dclSent"), filter.dclSent); status(json("dclReceived"), filter.dclReceived)
        status(json("qrzSent"), filter.qrzSent); status(json("qrzReceived"), filter.qrzReceived)
        text(json("qslVia"), filter.qslVia)
        if (filter.qslImages.equals("Y", true)) clauses += "${json("qslImages")} <> '' AND UPPER(${json("qslImages")}) NOT IN ('N','NO','0','FALSE')"
        if (filter.qslImages.equals("N", true)) clauses += "(${json("qslImages")} = '' OR UPPER(${json("qslImages")}) IN ('N','NO','0','FALSE'))"

        val sortExpression = when (filter.sort) {
            LogbookSort.TIME -> "created_at"; LogbookSort.CALLSIGN -> "callsign COLLATE NOCASE"
            LogbookSort.DXCC -> "${json("dxcc")} COLLATE NOCASE"; LogbookSort.MODE -> "mode COLLATE NOCASE"
            LogbookSort.BAND -> "frequency_hz"; LogbookSort.FREQUENCY -> "frequency_hz"
            LogbookSort.DISTANCE -> "CAST(${json("distanceKm")} AS REAL)"
            LogbookSort.DURATION -> "CAST(${json("durationSeconds")} AS INTEGER)"
        }
        val direction = if (filter.direction == LogbookSortDirection.ASCENDING) "ASC" else "DESC"
        return PageQuery(clauses.joinToString(" AND "), args, "$sortExpression $direction, created_at DESC, id ASC")
    }

    private fun queryWhere(where: String, args: Array<String>?): List<Qso> = buildList {
        readableDatabase.rawQuery("SELECT id,callsign,frequency_hz,mode,rst_sent,rst_received,created_at,name,qth,notes,country,details_json FROM qso WHERE $where", args).use { cursor ->
            while (cursor.moveToNext()) add(fromRow(cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getString(3),
                cursor.getString(4), cursor.getString(5), cursor.getLong(6), cursor.getString(7), cursor.getString(8),
                cursor.getString(9), cursor.getString(10), cursor.getString(11)))
        }
    }
    private fun fromRow(id: String, call: String, frequency: Long, mode: String, sent: String, received: String,
        created: Long, name: String, qth: String, notes: String, country: String, raw: String): Qso {
        val row = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        fun value(key: String) = row.optString(key).takeUnless { it.equals("null", true) } ?: ""
        return Qso(
            id = id, callsign = call, frequencyHz = frequency, mode = mode, rstSent = sent, rstReceived = received,
            createdAt = created, name = name, qth = qth, notes = notes, country = country, band = value("band"),
            grid = value("grid"), iota = value("iota"), sotaRef = value("sotaRef"), wwffRef = value("wwffRef"),
            potaRef = value("potaRef"), comment = value("comment"), frequencyRxHz = row.optLong("frequencyRxHz"),
            bandRx = value("bandRx"), txPowerW = row.optInt("txPowerW"), operatorCallsign = value("operatorCallsign"),
            stationCallsign = value("stationCallsign"), stationProfileId = value("stationProfileId"),
            stationLocation = value("stationLocation"), myGrid = value("myGrid"), myCountry = value("myCountry"),
            myDxcc = value("myDxcc"), myCqZone = value("myCqZone"), myItuZone = value("myItuZone"),
            myState = value("myState"), myIota = value("myIota"), mySotaRef = value("mySotaRef"),
            myWwffRef = value("myWwffRef"), myPotaRef = value("myPotaRef"), radioModel = value("radioModel"),
            dxcc = value("dxcc"), continent = value("continent"), region = value("region"), cqZone = value("cqZone"),
            ituZone = value("ituZone"), state = value("state"), email = value("email"),
            propagationMode = value("propagationMode"), antennaPath = value("antennaPath"),
            qslSent = value("qslSent").ifBlank { "N" }, qslMethod = value("qslMethod"), qslVia = value("qslVia"),
            qslMessage = value("qslMessage"), submode = value("submode"), county = value("county"), dok = value("dok"),
            contestId = value("contestId"), distanceKm = row.optDouble("distanceKm", 0.0),
            durationSeconds = row.optLong("durationSeconds"), qslReceived = value("qslReceived").ifBlank { "N" },
            qslReceivedMethod = value("qslReceivedMethod"), lotwSent = value("lotwSent").ifBlank { "N" },
            lotwReceived = value("lotwReceived").ifBlank { "N" }, clublogSent = value("clublogSent").ifBlank { "N" },
            clublogReceived = value("clublogReceived").ifBlank { "N" }, eqslSent = value("eqslSent").ifBlank { "N" },
            eqslReceived = value("eqslReceived").ifBlank { "N" }, dclSent = value("dclSent").ifBlank { "N" },
            dclReceived = value("dclReceived").ifBlank { "N" }, qrzSent = value("qrzSent").ifBlank { "N" },
            qrzReceived = value("qrzReceived").ifBlank { "N" }, qslImages = value("qslImages"),
            syncState = value("syncState").ifBlank { "local" }, remoteId = value("remoteId"))
    }
    private fun details(qso: Qso) = JSONObject().apply {
        put("band", qso.band); put("grid", qso.grid); put("iota", qso.iota); put("sotaRef", qso.sotaRef)
        put("wwffRef", qso.wwffRef); put("potaRef", qso.potaRef); put("comment", qso.comment)
        put("frequencyRxHz", qso.frequencyRxHz); put("bandRx", qso.bandRx); put("txPowerW", qso.txPowerW)
        put("operatorCallsign", qso.operatorCallsign); put("stationCallsign", qso.stationCallsign)
        put("stationProfileId", qso.stationProfileId); put("stationLocation", qso.stationLocation)
        put("myGrid", qso.myGrid); put("myCountry", qso.myCountry); put("myDxcc", qso.myDxcc)
        put("myCqZone", qso.myCqZone); put("myItuZone", qso.myItuZone); put("myState", qso.myState)
        put("myIota", qso.myIota); put("mySotaRef", qso.mySotaRef); put("myWwffRef", qso.myWwffRef); put("myPotaRef", qso.myPotaRef)
        put("radioModel", qso.radioModel); put("dxcc", qso.dxcc); put("continent", qso.continent); put("region", qso.region)
        put("cqZone", qso.cqZone); put("ituZone", qso.ituZone); put("state", qso.state); put("email", qso.email)
        put("propagationMode", qso.propagationMode); put("antennaPath", qso.antennaPath)
        put("qslSent", qso.qslSent); put("qslMethod", qso.qslMethod); put("qslVia", qso.qslVia); put("qslMessage", qso.qslMessage)
        put("submode", qso.submode); put("county", qso.county); put("dok", qso.dok); put("contestId", qso.contestId)
        put("distanceKm", qso.distanceKm); put("durationSeconds", qso.durationSeconds)
        put("qslReceived", qso.qslReceived); put("qslReceivedMethod", qso.qslReceivedMethod)
        put("lotwSent", qso.lotwSent); put("lotwReceived", qso.lotwReceived)
        put("clublogSent", qso.clublogSent); put("clublogReceived", qso.clublogReceived)
        put("eqslSent", qso.eqslSent); put("eqslReceived", qso.eqslReceived)
        put("dclSent", qso.dclSent); put("dclReceived", qso.dclReceived)
        put("qrzSent", qso.qrzSent); put("qrzReceived", qso.qrzReceived); put("qslImages", qso.qslImages)
        put("syncState", qso.syncState); put("remoteId", qso.remoteId)
    }
}
