package app.rigweave.mobile

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

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
    val syncState: String = "local", val remoteId: String = "",
)

fun bandForFrequency(frequencyHz: Long): String = when (frequencyHz) {
    in 135_700L..137_800L -> "2200m"; in 472_000L..479_000L -> "630m"
    in 1_800_000L..2_000_000L -> "160m"; in 3_500_000L..4_000_000L -> "80m"
    in 5_250_000L..5_450_000L -> "60m"; in 7_000_000L..7_300_000L -> "40m"
    in 10_100_000L..10_150_000L -> "30m"; in 14_000_000L..14_350_000L -> "20m"
    in 18_068_000L..18_168_000L -> "17m"; in 21_000_000L..21_450_000L -> "15m"
    in 24_890_000L..24_990_000L -> "12m"; in 28_000_000L..29_700_000L -> "10m"
    in 50_000_000L..54_000_000L -> "6m"; else -> ""
}

class QsoDatabase(context: Context, databaseName: String = "rigweave.sqlite") : SQLiteOpenHelper(context, databaseName, null, 4) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE radio_profile(id TEXT PRIMARY KEY, model TEXT NOT NULL)")
        db.execSQL("CREATE TABLE qso(id TEXT PRIMARY KEY, callsign TEXT NOT NULL, frequency_hz INTEGER NOT NULL, mode TEXT NOT NULL, rst_sent TEXT NOT NULL, rst_received TEXT NOT NULL, created_at INTEGER NOT NULL, name TEXT NOT NULL DEFAULT '', qth TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', country TEXT NOT NULL DEFAULT '', details_json TEXT NOT NULL DEFAULT '{}')")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE qso ADD COLUMN name TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE qso ADD COLUMN qth TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE qso ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE qso ADD COLUMN country TEXT NOT NULL DEFAULT ''")
        if (oldVersion < 4) db.execSQL("ALTER TABLE qso ADD COLUMN details_json TEXT NOT NULL DEFAULT '{}'")
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
            qslMessage = remote.qslMessage.ifBlank { existing.qslMessage }, syncState = "synced",
            remoteId = remote.remoteId.ifBlank { existing.remoteId })
        update(merged); return false
    }

    fun markSynced(id: String) { findById(id)?.let { update(it.copy(syncState = "synced")) } }
    fun list(): List<Qso> = query(" LIMIT 100")
    fun all(): List<Qso> = query("")
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
            "QSL_VIA" to qso.qslVia, "QSLMSG" to qso.qslMessage)
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
            qslSent = field("QSL_SENT").ifBlank { "N" }, qslMethod = field("QSL_SENT_VIA"),
            qslVia = field("QSL_VIA"), qslMessage = field("QSLMSG"), syncState = if (remoteId.isBlank()) "local" else "synced",
            remoteId = remoteId)
    }

    private fun insert(qso: Qso) {
        writableDatabase.execSQL("INSERT INTO qso(id,callsign,frequency_hz,mode,rst_sent,rst_received,created_at,name,qth,notes,country,details_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any>(qso.id, qso.callsign, qso.frequencyHz, qso.mode, qso.rstSent, qso.rstReceived,
                qso.createdAt, qso.name, qso.qth, qso.notes, qso.country, details(qso).toString()))
    }
    private fun update(qso: Qso) {
        writableDatabase.execSQL("UPDATE qso SET callsign=?,frequency_hz=?,mode=?,rst_sent=?,rst_received=?,created_at=?,name=?,qth=?,notes=?,country=?,details_json=? WHERE id=?",
            arrayOf<Any>(qso.callsign, qso.frequencyHz, qso.mode, qso.rstSent, qso.rstReceived, qso.createdAt,
                qso.name, qso.qth, qso.notes, qso.country, details(qso).toString(), qso.id))
    }
    private fun findNatural(qso: Qso): Qso? = queryWhere("callsign=? AND frequency_hz=? AND mode=? AND created_at BETWEEN ? AND ? LIMIT 1",
        arrayOf(qso.callsign, qso.frequencyHz.toString(), qso.mode, (qso.createdAt - 15).toString(), (qso.createdAt + 15).toString())).firstOrNull()
    private fun findById(id: String): Qso? = queryWhere("id=? LIMIT 1", arrayOf(id)).firstOrNull()
    private fun query(limit: String): List<Qso> = queryWhere("1=1 ORDER BY created_at DESC$limit", null)
    private fun queryWhere(where: String, args: Array<String>?): List<Qso> = buildList {
        readableDatabase.rawQuery("SELECT id,callsign,frequency_hz,mode,rst_sent,rst_received,created_at,name,qth,notes,country,details_json FROM qso WHERE $where", args).use { cursor ->
            while (cursor.moveToNext()) add(fromRow(cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getString(3),
                cursor.getString(4), cursor.getString(5), cursor.getLong(6), cursor.getString(7), cursor.getString(8),
                cursor.getString(9), cursor.getString(10), cursor.getString(11)))
        }
    }
    private fun fromRow(id: String, call: String, frequency: Long, mode: String, sent: String, received: String,
        created: Long, name: String, qth: String, notes: String, country: String, raw: String): Qso {
        val row = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }; fun value(key: String) = row.optString(key)
        return Qso(id, call, frequency, mode, sent, received, created, name, qth, notes, country,
            value("band"), value("grid"), value("iota"), value("sotaRef"), value("wwffRef"), value("potaRef"), value("comment"),
            row.optLong("frequencyRxHz"), value("bandRx"), row.optInt("txPowerW"), value("operatorCallsign"), value("stationCallsign"),
            value("stationProfileId"), value("stationLocation"), value("myGrid"), value("myCountry"), value("myDxcc"),
            value("myCqZone"), value("myItuZone"), value("myState"), value("myIota"), value("mySotaRef"), value("myWwffRef"),
            value("myPotaRef"), value("radioModel"), value("dxcc"), value("continent"), value("region"), value("cqZone"),
            value("ituZone"), value("state"), value("email"), value("propagationMode"), value("antennaPath"),
            value("qslSent").ifBlank { "N" }, value("qslMethod"), value("qslVia"), value("qslMessage"),
            value("syncState").ifBlank { "local" }, value("remoteId"))
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
        put("syncState", qso.syncState); put("remoteId", qso.remoteId)
    }
}
