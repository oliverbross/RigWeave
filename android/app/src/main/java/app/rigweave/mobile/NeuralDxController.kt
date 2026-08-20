package app.rigweave.mobile

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.rigweave.mobile.hamclock.DxNewsItem
import app.rigweave.mobile.hamclock.DxNewsSource
import app.rigweave.mobile.hamclock.DxNewsSnapshot
import app.rigweave.mobile.hamclock.HamClockDxpedition
import app.rigweave.mobile.hamclock.HamClockFeed
import app.rigweave.mobile.hamclock.HamClockFeedState
import app.rigweave.mobile.hamclock.HamClockPskDirection
import app.rigweave.mobile.hamclock.HamClockPskPreference
import app.rigweave.mobile.hamclock.HamClockPublicProviders
import app.rigweave.mobile.hamclock.HamClockWsprPreference
import app.rigweave.mobile.hamclock.HamClockWsprSnapshot
import app.rigweave.mobile.hamclock.PskReporterSnapshot
import app.rigweave.mobile.hamclock.filterPskReports
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.*

enum class NeuralDxPage(val label: String) {
    COCKPIT("Cockpit"), MAP("Map"), INSIGHT("AI Insight"), WORLD("World"),
    BRIEFING("Briefing"), OBSERVATIONS("RF Evidence"), SATELLITES("Satellites"), WEATHER("Weather")
}

enum class NeuralDxRefreshScope { HOME, FULL_DX }

internal fun NeuralDxRefreshScope.includesLegacySatelliteWork(): Boolean = this == NeuralDxRefreshScope.FULL_DX
internal fun NeuralDxRefreshScope.stopsLegacySatelliteTicker(): Boolean = this == NeuralDxRefreshScope.HOME

data class NeuralWeather(
    val available: Boolean = false, val updatedEpoch: Long = 0, val temperatureC: Double? = null,
    val pressureHpa: Double? = null, val humidityPercent: Int? = null, val windKmh: Double? = null,
    val windDirection: Int? = null, val precipitationMm: Double? = null, val weatherCode: Int? = null,
    val cape: Double? = null, val temperature850C: Double? = null, val wind300Kmh: Double? = null,
    val wind300Direction: Int? = null, val tropoIndex: Int? = null, val ductingRisk: String? = null,
    val pressureTrend: String = "—", val source: String = "Open-Meteo", val error: String = "",
)

data class WsprBandActivity(val band: String, val spots: Int, val averageSnr: Double?, val averageDistanceKm: Int?)
data class NeuralWspr(val available: Boolean = false, val updatedEpoch: Long = 0,
    val hf: List<WsprBandActivity> = emptyList(), val vhf: List<WsprBandActivity> = emptyList(), val error: String = "")

internal typealias BriefingItem = DxNewsItem
internal typealias BriefingSource = DxNewsSource

data class NeuralPrediction(val callsign: String, val country: String, val band: String, val mode: String,
    val probability: Int, val model: String, val reason: String, val startEpoch: Long, val endEpoch: Long,
    val samples: Int, val measuredReliability: Int?)
data class NeuralWorldCell(val row: Int, val column: Int, val latitude: Double, val longitude: Double,
    val observed: Int, val expected: Double?, val anomalyRatio: Double?, val confidence: String,
    val calls: List<String>, val greyline: Boolean)
data class NeuralInsight(val generatedEpoch: Long = 0, val title: String = "DX analysis is waiting for data",
    val report: String = "", val bullets: List<String> = emptyList(), val recommendations: List<String> = emptyList(),
    val log: NeuralLogSummary = NeuralLogSummary(), val source: String = "LOCAL", val error: String = "")

data class OrbitRecord(
    val norad: Int, val name: String, val epoch: Long, val inclination: Double, val raan: Double,
    val eccentricity: Double, val argumentPerigee: Double, val meanAnomaly: Double, val meanMotion: Double,
)

internal fun decodeHtmlText(value: String): String {
    var text = value.replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&#39;", "'").replace("&nbsp;", " ").replace("&ndash;", "–")
        .replace("&mdash;", "—").replace("&lt;", "<").replace("&gt;", ">")
    text = Regex("&#(\\d+);").replace(text) { match ->
        match.groupValues[1].toIntOrNull()?.takeIf(Character::isValidCodePoint)
            ?.let { String(Character.toChars(it)) } ?: match.value
    }
    text = Regex("&#x([0-9a-fA-F]+);").replace(text) { match ->
        match.groupValues[1].toIntOrNull(16)?.takeIf(Character::isValidCodePoint)
            ?.let { String(Character.toChars(it)) } ?: match.value
    }
    return text.replace(Regex("\\s+"), " ").trim()
}

internal fun parseTleOrbitRecords(text: String): List<OrbitRecord> {
    val lines = text.lines().map(String::trimEnd)
    val records = mutableListOf<OrbitRecord>()
    for (index in 0 until lines.lastIndex) {
        val line1 = lines[index].trim()
        if (!line1.startsWith("1 ")) continue
        val line2Index = (index + 1 until minOf(lines.size, index + 4)).firstOrNull { lines[it].trim().startsWith("2 ") } ?: continue
        val line2 = lines[line2Index].trim()
        runCatching {
            require(line1.length >= 32 && line2.length >= 63)
            val norad = line1.substring(2, 7).trim().toInt()
            val twoDigitYear = line1.substring(18, 20).trim().toInt()
            val year = if (twoDigitYear >= 57) 1900 + twoDigitYear else 2000 + twoDigitYear
            val day = line1.substring(20, 32).trim().toDouble()
            val epoch = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toEpochSecond() + ((day - 1.0) * 86_400.0).roundToLong()
            val name = (index - 1 downTo maxOf(0, index - 5)).map { lines[it].trim() }
                .firstOrNull { it.isNotBlank() && !it.startsWith("1 ") && !it.startsWith("2 ") && it.length <= 80 }
                ?.removePrefix("0 ")?.trim().orEmpty().ifBlank { "SAT-$norad" }
            records += OrbitRecord(norad, name, epoch, line2.substring(8, 16).trim().toDouble(),
                line2.substring(17, 25).trim().toDouble(), "0.${line2.substring(26, 33).trim()}".toDouble(),
                line2.substring(34, 42).trim().toDouble(), line2.substring(43, 51).trim().toDouble(),
                line2.substring(52, 63).trim().toDouble())
        }
    }
    return records.distinctBy(OrbitRecord::norad).sortedBy(OrbitRecord::name)
}
data class SatellitePosition(val norad: Int, val name: String, val latitude: Double, val longitude: Double,
    val altitudeKm: Double, val azimuth: Double, val elevation: Double, val rangeKm: Double,
    val footprintKm: Double, val visible: Boolean, val updatedEpoch: Long)
data class SatellitePass(val norad: Int, val name: String, val aosEpoch: Long, val tcaEpoch: Long,
    val losEpoch: Long, val maxElevation: Double, val aosAzimuth: Double, val losAzimuth: Double)
data class SatelliteTransmitter(val description: String, val uplink: String, val downlink: String,
    val mode: String, val status: String)
data class BeaconReception(val callsign: String, val band: String, val frequencyHz: Long, val country: String,
    val ageMinutes: Int, val known: Boolean, val spotter: String)
data class BeaconReference(val callsign: String, val band: String, val frequencyMHz: Double, val locator: String,
    val lastReport: String, val distanceKm: Int? = null, val bearing: String = "", val inTypicalRange: Boolean = false)
data class LightningStrike(val epoch: Long, val latitude: Double, val longitude: Double, val distanceKm: Double,
    val bearingDegrees: Int, val bearing: String)
data class NeuralLightning(val connected: Boolean = false, val updatedEpoch: Long = 0,
    val strikes: List<LightningStrike> = emptyList(), val source: String = "Blitzortung community MQTT", val error: String = "")
enum class SignalDirection { BEING_HEARD, HEARING }
data class SignalReport(val callsign:String,val locator:String,val latitude:Double?,val longitude:Double?,val frequencyHz:Long,
    val band:String,val mode:String,val snr:Int?,val distanceKm:Int?,val epoch:Long,
    val direction: SignalDirection = SignalDirection.BEING_HEARD, val localCallsign: String = "",
    val senderCallsign: String = localCallsign, val senderLocator: String = "",
    val receiverCallsign: String = callsign, val receiverLocator: String = locator, val mutual: Boolean = false,
    val continent: String = "")
internal fun signalReportReference(report: SignalReport): String =
    listOf(report.direction.name, report.callsign.uppercase(Locale.US), report.epoch.toString(), report.frequencyHz.toString(),
        report.locator.uppercase(Locale.US)).joinToString("|")
internal data class ConsumedSignalRequest(val report: SignalReport?, val message: String)
internal fun consumeSignalRequest(referenceId: String, reports: List<SignalReport>): ConsumedSignalRequest {
    val report = reports.firstOrNull { signalReportReference(it) == referenceId }
    return ConsumedSignalRequest(report, if (report == null) "PSK report expired or filtered; request consumed" else "")
}
internal data class NeuralMySignal(val available:Boolean=false,val callsign:String="",val fetchedEpoch:Long=0,
    val reports:List<SignalReport> = emptyList(),val source:String="PSK Reporter",val error:String="",
    val beingHeardState: HamClockFeedState = HamClockFeedState.UNAVAILABLE,
    val hearingState: HamClockFeedState = HamClockFeedState.UNAVAILABLE,
    val beingHeardCount: Int = 0, val hearingCount: Int = 0)

internal data class GeoPoint(val latitude: Double, val longitude: Double)

internal fun maidenheadCenter(raw: String): GeoPoint? {
    val grid = raw.trim().uppercase(Locale.US)
    if (grid.length < 4 || grid[0] !in 'A'..'R' || grid[1] !in 'A'..'R' ||
        grid[2] !in '0'..'9' || grid[3] !in '0'..'9') return null
    var lon = (grid[0] - 'A') * 20.0 - 180.0 + (grid[2] - '0') * 2.0 + 1.0
    var lat = (grid[1] - 'A') * 10.0 - 90.0 + (grid[3] - '0') + 0.5
    if (grid.length >= 6 && grid[4] in 'A'..'X' && grid[5] in 'A'..'X') {
        // Replace the four-character square centre with the six-character subsquare centre.
        lon += (grid[4] - 'A') / 12.0 - 23.0 / 24.0
        lat += (grid[5] - 'A') / 24.0 - 23.0 / 48.0
    }
    return GeoPoint(lat, lon)
}

internal fun dxDistanceKm(
    stationGrid: String,
    remoteGrid: String,
    remoteLatitude: String = "",
    remoteLongitude: String = "",
): Int? {
    val origin = maidenheadCenter(stationGrid) ?: return null
    val target = maidenheadCenter(remoteGrid) ?: run {
        val latitude = remoteLatitude.toDoubleOrNull()
        val longitude = remoteLongitude.toDoubleOrNull()
        if (latitude == null || longitude == null || latitude !in -90.0..90.0 || longitude !in -180.0..180.0 ||
            (latitude == 0.0 && longitude == 0.0)) return null
        GeoPoint(latitude, longitude)
    }
    val p1 = Math.toRadians(origin.latitude)
    val p2 = Math.toRadians(target.latitude)
    val deltaLatitude = p2 - p1
    val deltaLongitude = Math.toRadians(target.longitude - origin.longitude)
    val haversine = sin(deltaLatitude / 2).pow(2) + cos(p1) * cos(p2) * sin(deltaLongitude / 2).pow(2)
    return (6371.0 * 2 * atan2(sqrt(haversine), sqrt(1 - haversine))).roundToInt()
}

internal fun tropoIndex(surface: Double?, at850: Double?, humidity: Int?, cape: Double?): Pair<Int?, String?> {
    if (surface == null || at850 == null) return null to null
    val inversion = 8.5 - (surface - at850)
    var score = when { inversion >= 6 -> 4; inversion >= 3 -> 2; inversion >= 0 -> 1; else -> 0 }
    if ((humidity ?: 0) >= 80) score += 2
    if (cape != null && cape < 100) score += 1
    val index = min(score * 10 / 7, 10)
    return index to when { index >= 7 -> "HIGH"; index >= 4 -> "MODERATE"; else -> "LOW" }
}

internal fun extractCallsigns(text: String): List<String> = Regex("(?<![A-Z0-9/])(?:(?:[A-Z0-9]{1,4})/)?(?:[A-Z]{1,2}|[0-9][A-Z])[0-9][A-Z0-9]{1,4}(?:/[A-Z0-9]{1,4})?(?![A-Z0-9/])")
    .findAll(text.uppercase(Locale.US)).map { it.value }.filterNot { it in setOf("2024", "2025", "2026") }.distinct().take(24).toList()

private class NeuralDxStore(context: Context) : SQLiteOpenHelper(context, "neural-dx.sqlite", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE spot(id TEXT PRIMARY KEY, ts INTEGER NOT NULL, call TEXT NOT NULL, spotter TEXT NOT NULL,
            frequency_hz INTEGER NOT NULL, band TEXT NOT NULL, mode TEXT NOT NULL, country TEXT NOT NULL,
            continent TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, score INTEGER NOT NULL,
            watchlisted INTEGER NOT NULL, comment TEXT NOT NULL)""")
        db.execSQL("CREATE INDEX spot_ts_idx ON spot(ts DESC)")
        db.execSQL("CREATE INDEX spot_band_ts_idx ON spot(band,ts DESC)")
        db.execSQL("CREATE INDEX spot_call_ts_idx ON spot(call,ts DESC)")
        createPredictionTable(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS prediction_result")
            createPredictionTable(db)
        }
    }

    fun ingest(rows: List<AndroidDXSpot>): List<AndroidDXSpot> {
        val inserted = mutableListOf<AndroidDXSpot>(); val db = writableDatabase
        db.beginTransaction()
        try {
            rows.forEach { row ->
                val values = ContentValues().apply {
                    put("id", row.id); put("ts", row.receivedEpoch); put("call", row.callsign); put("spotter", row.spotter)
                    put("frequency_hz", row.frequencyHz); put("band", row.band); put("mode", row.mode); put("country", row.country)
                    put("continent", row.continent); put("latitude", row.latitude); put("longitude", row.longitude)
                    put("score", row.score); put("watchlisted", if (row.watchlisted) 1 else 0); put("comment", row.comment)
                }
                if (db.insertWithOnConflict("spot", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L) inserted += row
            }
            db.delete("spot", "ts<?", arrayOf((Instant.now().epochSecond - 90L * 86400L).toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return inserted
    }

    fun bandActivity(hours: Int = 24): Map<String, Int> {
        val out = linkedMapOf<String, Int>(); val since = Instant.now().epochSecond - hours * 3600L
        readableDatabase.rawQuery("SELECT band,COUNT(*) FROM spot WHERE ts>=? GROUP BY band ORDER BY COUNT(*) DESC",
            arrayOf(since.toString())).use { while (it.moveToNext()) out[it.getString(0)] = it.getInt(1) }
        return out
    }

    fun heatmap6m(): List<List<Int>> {
        val out = List(7) { MutableList(24) { 0 } }; val since = Instant.now().epochSecond - 7L * 86400L
        readableDatabase.rawQuery("SELECT ts FROM spot WHERE ts>=? AND band='6m'", arrayOf(since.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val z = Instant.ofEpochSecond(cursor.getLong(0)).atZone(ZoneOffset.UTC)
                val day = ((z.dayOfWeek.value - 1) % 7); out[day][z.hour]++
            }
        }
        return out
    }

    fun worldCells(windowMinutes: Int, band: String): List<NeuralWorldCell> {
        val now = Instant.now().epochSecond; val currentSince = now - windowMinutes * 60L
        val historySince = currentSince - 14L * 86400L
        data class Bucket(var now: Int = 0, var historical: Int = 0, val calls: MutableSet<String> = linkedSetOf())
        val buckets = mutableMapOf<Pair<Int, Int>, Bucket>()
        val whereBand = if (band == "ALL") "" else " AND band=?"
        val args = if (band == "ALL") arrayOf(historySince.toString()) else arrayOf(historySince.toString(), band)
        readableDatabase.rawQuery("SELECT ts,call,latitude,longitude FROM spot WHERE ts>=? AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180$whereBand", args).use { c ->
            while (c.moveToNext()) {
                val lat = c.getDouble(2); val lon = c.getDouble(3)
                if (lat == 0.0 && lon == 0.0) continue
                val row = floor((75.0 - lat) / 30.0).toInt().coerceIn(0, 5)
                val col = floor((lon + 180.0) / 30.0).toInt().coerceIn(0, 11)
                val b = buckets.getOrPut(row to col) { Bucket() }
                if (c.getLong(0) >= currentSince) { b.now++; if (b.calls.size < 8) b.calls += c.getString(1) } else b.historical++
            }
        }
        val historyWindows = max(1.0, (14.0 * 24.0 * 60.0) / windowMinutes)
        return buckets.map { (key, value) ->
            val expected = value.historical / historyWindows
            val ratio = if (expected >= 0.35) value.now / expected else null
            val lat = 75.0 - key.first * 30.0 - 15.0; val lon = -180.0 + key.second * 30.0 + 15.0
            NeuralWorldCell(key.first, key.second, lat, lon, value.now, expected.takeIf { value.historical > 0 }, ratio,
                when { value.historical >= 30 -> "HIGH"; value.historical >= 8 -> "MEDIUM"; else -> "LOW" }, value.calls.toList(),
                isGreyline(lon, now))
        }.filter { it.observed > 0 }.sortedByDescending { it.anomalyRatio ?: it.observed.toDouble() }
    }

    fun predictions(spots: List<AndroidDXSpot>): List<NeuralPrediction> {
        val now = Instant.now().epochSecond
        verifyPredictions(now)
        val measured = reliability(now)
        return spots.asSequence().filter { it.score >= 45 }.distinctBy { "${it.callsign}|${it.band}|${it.mode}" }.take(12).map { spot ->
            val probability = (spot.score * 0.72 + spot.confidence * 0.28).roundToInt().coerceIn(1, 99)
            val model = if (spot.band in setOf("6m", "4m", "2m")) "es" else "hf"
            val prediction = NeuralPrediction(spot.callsign, spot.country, spot.band, spot.mode, probability,
                if (spot.band in setOf("6m", "4m", "2m")) "Es/tropo" else "HF empirical",
                spot.reason.ifBlank { "Recent activity, path and solar context" }, now, now + 3 * 3600L,
                spot.samples, measured)
            logPrediction(prediction, model, now)
            prediction
        }.toList()
    }

    fun recentBandCount(band: String, minutes: Int): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM spot WHERE band=? AND ts>=?", arrayOf(band, (Instant.now().epochSecond - minutes * 60L).toString())
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun logPrediction(row: NeuralPrediction, model: String, now: Long) {
        val hour = row.startEpoch / 3600L
        val values = ContentValues().apply {
            put("key", "${row.callsign}|${row.band}|$hour"); put("created", now); put("start_ts", row.startEpoch)
            put("end_ts", row.endEpoch); put("band", row.band); put("prefix", row.callsign.uppercase(Locale.US))
            put("model", model); put("probability", row.probability); put("checked", 0); put("correct", 0)
        }
        writableDatabase.insertWithOnConflict("prediction_result", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun verifyPredictions(now: Long) {
        val db = writableDatabase
        db.rawQuery("SELECT key,band,prefix,start_ts,end_ts FROM prediction_result WHERE checked=0 AND end_ts<?",
            arrayOf(now.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val found = db.rawQuery("SELECT 1 FROM spot WHERE band=? AND call LIKE ? AND ts BETWEEN ? AND ? LIMIT 1",
                    arrayOf(cursor.getString(1), cursor.getString(2) + "%", cursor.getLong(3).toString(), cursor.getLong(4).toString()))
                    .use { it.moveToFirst() }
                db.execSQL("UPDATE prediction_result SET checked=1,correct=? WHERE key=?", arrayOf(if (found) 1 else 0, cursor.getString(0)))
            }
        }
        db.delete("prediction_result", "created<?", arrayOf((now - 90L * 86400L).toString()))
    }

    private fun reliability(now: Long): Int? = readableDatabase.rawQuery(
        "SELECT SUM(correct),COUNT(*) FROM prediction_result WHERE checked=1 AND created>=?",
        arrayOf((now - 30L * 86400L).toString())
    ).use {
        if (!it.moveToFirst() || it.getInt(1) < 5) null else (it.getInt(0) * 100.0 / it.getInt(1)).roundToInt()
    }

    companion object {
        private fun createPredictionTable(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS prediction_result(
                key TEXT PRIMARY KEY, created INTEGER NOT NULL, start_ts INTEGER NOT NULL, end_ts INTEGER NOT NULL,
                band TEXT NOT NULL, prefix TEXT NOT NULL, model TEXT NOT NULL, probability INTEGER NOT NULL,
                checked INTEGER NOT NULL, correct INTEGER NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS prediction_pending_idx ON prediction_result(checked,end_ts)")
        }
        private fun isGreyline(longitude: Double, epoch: Long): Boolean {
            val utc = Instant.ofEpochSecond(epoch).atZone(ZoneOffset.UTC)
            val localSolarHour = (utc.hour + utc.minute / 60.0 + longitude / 15.0 + 24.0) % 24.0
            return localSolarHour < 1.0 || localSolarHour > 23.0 || localSolarHour in 5.0..7.0 || localSolarHour in 17.0..19.0
        }
    }
}

class NeuralDxController internal constructor(private val context: Context, private val database: QsoDatabase,
    private val publicProviders: HamClockPublicProviders) {
    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private val store = NeuralDxStore(context)
    private val prefs = context.getSharedPreferences("neural-dx-v12", Context.MODE_PRIVATE)
    private val cacheDir = File(context.filesDir, "neural-dx-cache").apply { mkdirs() }
    private var refreshJob: Job? = null
    private var lightningJob: Job? = null
    private var lightningPoint: GeoPoint? = null
    private var satelliteJob: Job? = null
    private var satellitePoint: GeoPoint? = null
    private var pskPreference = HamClockPskPreference()
    private var pskSnapshot = PskReporterSnapshot()
    private var pskJob: Job? = null
    private var pskGeneration = 0
    private var lastPskCall = ""
    private var lastPskPoint: GeoPoint? = null
    private var lastPskAttemptEpoch = 0L
    private var wsprPreference = HamClockWsprPreference(personalEnabled = false)
    private var wsprJob: Job? = null
    private var wsprGeneration = 0
    private var lastWsprCall = ""
    private var lastWsprPoint: GeoPoint? = null
    private var lastWsprAttemptEpoch = 0L
    private var dxpeditionFeed: HamClockFeed<List<HamClockDxpedition>>? = null
    private var ctyController: CtyController? = null
    private val lightningBuffer = ArrayDeque<LightningStrike>()
    private var lastIngestIds = emptySet<String>()
    private var lastIngestStation: String? = null
    private var lastCtyRevision = Long.MIN_VALUE

    var weather by mutableStateOf(loadWeatherCache()); private set
    var wspr by mutableStateOf(NeuralWspr()); private set
    internal var wsprPersonal by mutableStateOf(HamClockWsprSnapshot()); private set
    internal var briefing by mutableStateOf(loadBriefingCache()); private set
    internal var dxNewsSnapshot by mutableStateOf(DxNewsSnapshot()); private set
    var satelliteCatalogue by mutableStateOf(loadOrbitCache()); private set
    var satellites by mutableStateOf(emptyList<SatellitePosition>()); private set
    var passes by mutableStateOf(emptyList<SatellitePass>()); private set
    var transmitters by mutableStateOf(emptyMap<Int, List<SatelliteTransmitter>>()); private set
    var insight by mutableStateOf(NeuralInsight()); private set
    var predictions by mutableStateOf(emptyList<NeuralPrediction>()); private set
    var world by mutableStateOf(emptyList<NeuralWorldCell>()); private set
    var heatmap6m by mutableStateOf(List(7) { List(24) { 0 } }); private set
    var bandActivity by mutableStateOf(emptyMap<String, Int>()); private set
    var beacons by mutableStateOf(emptyList<BeaconReception>()); private set
    var beaconReference by mutableStateOf(loadBeaconReference()); private set
    var beaconStatus by mutableStateOf(if (beaconReference.isEmpty()) "Beacon reference not downloaded" else "${beaconReference.size} beacon references cached"); private set
    var lightning by mutableStateOf(NeuralLightning()); private set
    internal var mySignal by mutableStateOf(loadMySignalCache()); private set
    var requestedSignalReportId by mutableStateOf<String?>(null); private set
    var requestedPage by mutableStateOf<NeuralDxPage?>(null); private set
    var requestedRfEvidenceId by mutableStateOf<String?>(null); private set
    var signalSelectionMessage by mutableStateOf(""); private set
    val alerts = mutableStateListOf<String>()
    var status by mutableStateOf("Neural DX cache ready"); private set
    var refreshing by mutableStateOf(false); private set
    var lastRefreshEpoch by mutableStateOf(0L); private set
    var worldWindowMinutes by mutableStateOf(180); private set
    var worldBand by mutableStateOf("ALL"); private set
    var followedNorads by mutableStateOf(decodeIds(prefs.getString("satellites", null))); private set
    var notificationsEnabled by mutableStateOf(prefs.getBoolean("notifications", false)); private set
    var ntfyUrl by mutableStateOf(prefs.getString("ntfy_url", "") ?: ""); private set
    var ntfyToken by mutableStateOf(readSecret("ntfy_token")); private set
    var perplexityKey by mutableStateOf(readSecret("perplexity_key")); private set
    var briefingDxMode by mutableStateOf(prefs.getBoolean("briefing_dx_mode", true)); private set
    var briefingOrder by mutableStateOf(loadBriefingOrder()); private set
    var enrichedSpots by mutableStateOf(emptyList<AndroidDXSpot>()); private set

    init { createNotificationChannel() }

    fun ingest(spots: List<AndroidDXSpot>, stationId: String?, cty: CtyController, stationCall: String = "") {
        ctyController = cty
        if (spots.isEmpty()) return
        val ids = spots.mapTo(linkedSetOf()) { it.id }
        if (ids == lastIngestIds && stationId == lastIngestStation && cty.dataRevision == lastCtyRevision) return
        lastIngestIds = ids; lastIngestStation = stationId; lastCtyRevision = cty.dataRevision
        scope.launch {
            val enriched = spots.map { row -> cty.lookup(row.callsign)?.let { entity -> row.copy(
                country = entity.country.ifBlank { row.country }, continent = entity.continent.ifBlank { row.continent },
                cqZone = entity.cqZone.toIntOrNull() ?: row.cqZone, ituZone = entity.ituZone.toIntOrNull() ?: row.ituZone,
                latitude = entity.latitude.takeUnless { it == 0.0 } ?: row.latitude,
                longitude = entity.longitude.takeUnless { it == 0.0 } ?: row.longitude) } ?: row }
            withContext(Dispatchers.Main) { enrichedSpots = enriched }
            val fresh = store.ingest(enriched)
            val statuses = database.spotStatuses(enriched.map { it.toSpotLogIdentity(cty.lookup(it.callsign)) }, stationId)
            fresh.forEach { spot ->
                val state = statuses[spot.id]
                if (spot.watchlisted) deliverAlert("Watchlist · ${spot.callsign}", "${spot.band} ${spot.mode} · ${spot.country}", "watch:${spot.callsign}")
                if (state?.dxccStatus == "ATNO") deliverAlert("New DXCC · ${spot.country}", "${spot.callsign} on ${spot.band} ${spot.mode}", "dxcc:${spot.country}:${spot.band}")
            }
            if (store.recentBandCount("6m", 10) >= 8) deliverAlert("6m opening", "${store.recentBandCount("6m", 10)} spots in the last 10 minutes", "surge:6m")
            publishDerived(enriched, stationId, stationCall)
        }
    }

    fun setWorldFilter(windowMinutes: Int, band: String) {
        worldWindowMinutes = windowMinutes.coerceIn(15, 360); worldBand = band
        scope.launch { val rows = store.worldCells(worldWindowMinutes, worldBand); withContext(Dispatchers.Main) { world = rows } }
    }

    fun saveSettings(notifications: Boolean, ntfy: String, token: String, perplexity: String, dxMode: Boolean) {
        notificationsEnabled = notifications; ntfyUrl = ntfy.trim(); ntfyToken = token.trim(); perplexityKey = perplexity.trim()
        briefingDxMode = dxMode
        prefs.edit().putBoolean("notifications", notifications).putString("ntfy_url", ntfyUrl)
            .putString("ntfy_token", encrypt(ntfyToken)).putString("perplexity_key", encrypt(perplexityKey))
            .putBoolean("briefing_dx_mode", dxMode).apply()
    }

    fun setFollowed(norad: Int, followed: Boolean) {
        followedNorads = followedNorads.toMutableSet().apply { if (followed) add(norad) else remove(norad) }.toList().take(24)
        prefs.edit().putString("satellites", followedNorads.joinToString(",")).apply()
    }

    fun requestSignalReport(referenceId: String) { requestedSignalReportId = referenceId }
    fun requestPage(page: NeuralDxPage) { requestedPage = page }
    fun requestRfEvidence(referenceId: String) { requestedRfEvidenceId = referenceId }
    fun consumeRequestedRfEvidence() { requestedRfEvidenceId = null }
    fun consumeRequestedPage() { requestedPage = null }
    fun consumeRequestedSignalReport(message: String = "") {
        requestedSignalReportId = null
        signalSelectionMessage = message.take(160)
    }

    fun stopLegacySatelliteTicker() { satelliteJob?.cancel(); satelliteJob = null; satellitePoint = null }

    internal fun updateDxNewsCalendar(feed: HamClockFeed<List<HamClockDxpedition>>) {
        dxpeditionFeed = feed
        scope.launch { refreshBriefing(false) }
    }

    internal fun applyPskPreference(value: HamClockPskPreference) {
        val providerChanged = pskPreference.enabled != value.enabled || pskPreference.windowMinutes != value.windowMinutes
        pskPreference = value
        if (!value.enabled) {
            pskGeneration++; pskJob?.cancel(); pskJob = null
            pskSnapshot = PskReporterSnapshot(lastPskCall)
            publishPskSnapshot(pskSnapshot)
        } else if (providerChanged && lastPskCall.isNotBlank()) {
            val generation = ++pskGeneration
            pskJob?.cancel()
            pskJob = scope.launch { runCatching { refreshMySignal(lastPskCall, lastPskPoint, false, generation) } }
        } else publishPskSnapshot(pskSnapshot)
    }

    internal fun applyWsprPreference(value: HamClockWsprPreference) {
        val providerChanged = wsprPreference != value
        wsprPreference = value
        if (!value.personalEnabled) {
            wsprGeneration++; wsprJob?.cancel(); wsprJob = null
            publishWsprSnapshot(HamClockWsprSnapshot(lastWsprCall,
                regionalState = if (value.regionalEnabled)
                    app.rigweave.mobile.hamclock.HamClockWsprRegionalState.UNAVAILABLE_POLICY
                else app.rigweave.mobile.hamclock.HamClockWsprRegionalState.DISABLED))
        } else if (providerChanged && lastWsprCall.isNotBlank()) {
            lastWsprAttemptEpoch = 0
            refreshWspr(lastWsprCall, "", false)
        }
    }

    fun refreshWspr(call: String, grid: String, force: Boolean = false) {
        val normalized = call.trim().uppercase(Locale.US)
        val point = maidenheadCenter(grid) ?: lastWsprPoint
        lastWsprCall = normalized
        lastWsprPoint = point
        if (!wsprPreference.personalEnabled || normalized.isBlank()) {
            applyWsprPreference(wsprPreference)
            return
        }
        val now = Instant.now().epochSecond
        if (!force && now - lastWsprAttemptEpoch < 300L) return
        lastWsprAttemptEpoch = now
        val generation = ++wsprGeneration
        wsprJob?.cancel()
        wsprJob = scope.launch {
            val loaded = publicProviders.wspr.refreshPersonal(normalized, point, wsprPreference, force)
            if (generation == wsprGeneration) withContext(Dispatchers.Main) { publishWsprSnapshot(loaded) }
        }
    }

    private fun publishWsprSnapshot(snapshot: HamClockWsprSnapshot) {
        wsprPersonal = snapshot
        val rows = snapshot.reports.groupBy(SignalReport::band).map { (band, reports) ->
            WsprBandActivity(band, reports.size, reports.mapNotNull(SignalReport::snr).average().takeIf(Double::isFinite),
                reports.mapNotNull(SignalReport::distanceKm).average().takeIf(Double::isFinite)?.roundToInt())
        }
        val vhfBands = setOf("6m", "4m", "2m", "70cm", "23cm")
        wspr = NeuralWspr(
            available = snapshot.beingHeardState != HamClockFeedState.UNAVAILABLE ||
                snapshot.hearingState != HamClockFeedState.UNAVAILABLE,
            updatedEpoch = snapshot.fetchedEpoch,
            hf = rows.filter { it.band !in vhfBands },
            vhf = rows.filter { it.band in vhfBands },
            error = snapshot.error,
        )
    }

    fun refreshPsk(call: String, grid: String, force: Boolean = false) {
        val normalized = call.trim().uppercase(Locale.US)
        if (!pskPreference.enabled || normalized.isBlank()) { publishPskSnapshot(PskReporterSnapshot(normalized)); return }
        val now = Instant.now().epochSecond
        if (!force && now - lastPskAttemptEpoch < pskPreference.refreshSeconds) {
            lastPskPoint = maidenheadCenter(grid)
            publishPskSnapshot(pskSnapshot)
            return
        }
        lastPskAttemptEpoch = now
        val point = maidenheadCenter(grid)
        val generation = ++pskGeneration
        pskJob?.cancel()
        pskJob = scope.launch { runCatching { refreshMySignal(call, point, force, generation) }
            .onFailure { error -> withContext(Dispatchers.Main) { mySignal = mySignal.copy(error = safeError(error)) } } }
    }

    fun clearPskDisplay() {
        pskGeneration++; pskJob?.cancel(); pskJob = null
        pskSnapshot = PskReporterSnapshot(lastPskCall)
        publishPskSnapshot(pskSnapshot)
        consumeRequestedSignalReport("PSK display cleared")
    }

    fun moveBriefingSource(id: String, direction: Int) {
        val order = briefingOrder.toMutableList(); val from = order.indexOf(id); val to = (from + direction).coerceIn(0, order.lastIndex)
        if (from < 0 || from == to) return
        order.add(to, order.removeAt(from)); briefingOrder = order
        prefs.edit().putString("briefing_order", order.joinToString(",")).apply()
        briefing = order.mapNotNull { key -> briefing.firstOrNull { it.id == key } } + briefing.filter { it.id !in order }
    }

    fun refresh(call: String, grid: String, stationId: String?, live: List<AndroidDXSpot>, force: Boolean = false,
        refreshScope: NeuralDxRefreshScope = NeuralDxRefreshScope.FULL_DX) {
        if (refreshScope.stopsLegacySatelliteTicker()) stopLegacySatelliteTicker()
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            withContext(Dispatchers.Main) { refreshing = true; status = "Refreshing Neural DX sources…" }
            val point = maidenheadCenter(grid)
            val errors = mutableListOf<String>()
            try {
                if (point == null) errors += "Set a valid station gridsquare for map, weather and satellites"
                else {
                    ensureLightning(point)
                    runCatching { refreshWeather(point, force) }.onFailure { errors += "Weather unavailable" }
                    if (refreshScope.includesLegacySatelliteWork()) {
                        runCatching { refreshSatellites(point, force) }.onFailure { errors += "Satellites unavailable" }
                    }
                    runCatching { refreshBeaconReference(point, force) }.onFailure { errors += "Beacon reference unavailable" }
                }
                runCatching { refreshBriefing(force) }.onFailure { errors += "Briefing unavailable" }
                val log = database.neuralLogSummary(stationId)
                val localInsight = buildInsight(call, log, live)
                val finalInsight = if (perplexityKey.isNotBlank()) runCatching { enrichInsight(localInsight) }.getOrElse { localInsight.copy(error = "AI provider unavailable; local analysis shown") } else localInsight
                val derivedPredictions = store.predictions(live)
                val derivedWorld = store.worldCells(worldWindowMinutes, worldBand)
                val derivedBands = store.bandActivity(); val derivedHeatmap = store.heatmap6m()
                val derivedBeacons = buildBeaconReception(live)
                withContext(Dispatchers.Main) {
                    insight = finalInsight; predictions = derivedPredictions; world = derivedWorld; bandActivity = derivedBands
                    heatmap6m = derivedHeatmap; beacons = derivedBeacons; lastRefreshEpoch = Instant.now().epochSecond
                    status = if (errors.isEmpty()) "All Neural DX sources current" else errors.joinToString(" · ") + " · cached data retained"
                }
            } finally { withContext(Dispatchers.Main) { refreshing = false } }
        }
    }

    fun refreshSatelliteTransmitters(norad: Int) = scope.launch {
        val result = runCatching { fetchTransmitters(norad) }.getOrDefault(emptyList())
        withContext(Dispatchers.Main) { transmitters = transmitters + (norad to result) }
    }

    fun testNtfy() = scope.launch { deliverAlert("RigWeave Neural DX", "Notification test successful", "test:${Instant.now().epochSecond}", force = true) }
    fun close() { refreshJob?.cancel(); pskJob?.cancel(); wsprJob?.cancel(); lightningJob?.cancel(); satelliteJob?.cancel(); scope.cancel(); store.close() }

    private suspend fun publishDerived(rows: List<AndroidDXSpot>, stationId: String?, stationCall: String) {
        val p = store.predictions(rows); val w = store.worldCells(worldWindowMinutes, worldBand)
        val b = store.bandActivity(); val h = store.heatmap6m(); val be = buildBeaconReception(rows)
        val currentInsight = buildInsight(stationCall, database.neuralLogSummary(stationId), rows)
        withContext(Dispatchers.Main) { predictions = p; world = w; bandActivity = b; heatmap6m = h; beacons = be; insight = currentInsight }
    }

    private fun ensureLightning(point: GeoPoint) {
        if (lightningJob?.isActive == true && lightningPoint?.let { greatCircleKm(it, point) < 5.0 } == true) return
        lightningJob?.cancel(); lightningPoint = point
        lightningJob = scope.launch {
            while (isActive) {
                try {
                    listenForLightning(point)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    withContext(Dispatchers.Main) { lightning = lightning.copy(connected=false,error=safeError(error)) }
                    delay(30_000)
                }
            }
        }
    }

    private suspend fun listenForLightning(point: GeoPoint) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("blitzortung.ha.sed.pl",1883),15_000);socket.soTimeout=20_000
            val input=BufferedInputStream(socket.getInputStream());val output=BufferedOutputStream(socket.getOutputStream())
            mqttWrite(output,0x10,mqttConnectPayload("RigWeave-${System.currentTimeMillis().toString(16).takeLast(10)}"))
            val connAck=mqttRead(input);require(connAck.first ushr 4==2&&connAck.second.size>=2&&connAck.second[1].toInt()==0){"Lightning broker rejected connection"}
            val topics=lightningGeohashNeighbors(point).map{"blitzortung/1.1/${it.toList().joinToString("/")}/#"}
            val subscribe=ByteArrayOutputStream().apply{write(0);write(1);topics.forEach{topic->writeMqttString(topic);write(0)}}.toByteArray()
            mqttWrite(output,0x82,subscribe)
            scope.launch(Dispatchers.Main){lightning=lightning.copy(connected=true,error="")}
            while(currentCoroutineContext().isActive){
                try {
                    val packet=mqttRead(input);if(packet.first ushr 4==3) consumeLightningPublish(packet.first,packet.second,point)
                } catch(timeout:SocketTimeoutException){mqttWrite(output,0xC0,byteArrayOf())}
            }
        }
    }

    private fun consumeLightningPublish(header:Int,body:ByteArray,point:GeoPoint){
        if(body.size<3)return;val topicLength=((body[0].toInt() and 255) shl 8)+(body[1].toInt() and 255)
        var offset=2+topicLength;if((header and 0x06)!=0)offset+=2;if(offset>=body.size)return
        val row=runCatching{JSONObject(body.copyOfRange(offset,body.size).toString(Charsets.UTF_8))}.getOrNull()?:return
        val lat=row.optionalDouble("lat")?:return;val lon=row.optionalDouble("lon")?:return;val target=GeoPoint(lat,lon)
        val distance=greatCircleKm(point,target);if(distance>300)return
        val rawTime=row.optDouble("time",0.0);val epoch=when{rawTime>1e14->(rawTime/1e9).toLong();rawTime>1e11->(rawTime/1000).toLong();rawTime>1e9->rawTime.toLong();else->Instant.now().epochSecond}
        val bearing=initialBearing(point,target);val strike=LightningStrike(epoch,lat,lon,(distance*10).roundToInt()/10.0,bearing.roundToInt(),compass(bearing))
        val snapshot=synchronized(lightningBuffer){lightningBuffer.addLast(strike);val cutoff=Instant.now().epochSecond-3600;while(lightningBuffer.firstOrNull()?.epoch?.let{it<cutoff}==true)lightningBuffer.removeFirst();while(lightningBuffer.size>500)lightningBuffer.removeFirst();lightningBuffer.toList().asReversed()}
        scope.launch(Dispatchers.Main){lightning=NeuralLightning(true,Instant.now().epochSecond,snapshot)}
    }

    private fun mqttConnectPayload(clientId:String):ByteArray=ByteArrayOutputStream().apply{
        writeMqttString("MQTT");write(4);write(2);write(0);write(60);writeMqttString(clientId)
    }.toByteArray()
    private fun mqttWrite(output:BufferedOutputStream,header:Int,payload:ByteArray){output.write(header);var n=payload.size;do{var digit=n%128;n/=128;if(n>0)digit= digit or 128;output.write(digit)}while(n>0);output.write(payload);output.flush()}
    private fun mqttRead(input:BufferedInputStream):Pair<Int,ByteArray>{val header=input.read();if(header<0)error("Lightning broker closed connection");var multiplier=1;var remaining=0;var loops=0;do{val digit=input.read();if(digit<0)error("Incomplete MQTT packet");remaining+=(digit and 127)*multiplier;multiplier*=128;loops++;require(loops<=4){"Invalid MQTT length"}}while((digit and 128)!=0);require(remaining<=1_000_000){"MQTT packet too large"};return header to input.readNBytes(remaining).also{require(it.size==remaining){"Incomplete MQTT packet"}}}
    private fun ByteArrayOutputStream.writeMqttString(value:String){val bytes=value.toByteArray();write((bytes.size ushr 8) and 255);write(bytes.size and 255);write(bytes)}
    private fun lightningGeohashNeighbors(point:GeoPoint):Set<String>{val latBits=7;val lonBits=8;val latStep=180.0/(1 shl latBits);val lonStep=360.0/(1 shl lonBits);return buildSet{for(dLat in -1..1)for(dLon in -1..1)add(geohash((point.latitude+dLat*latStep).coerceIn(-89.999,89.999),((point.longitude+dLon*lonStep+180)%360+360)%360-180,3))}}
    private fun geohash(latitude:Double,longitude:Double,precision:Int):String{val alphabet="0123456789bcdefghjkmnpqrstuvwxyz";var minLat=-90.0;var maxLat=90.0;var minLon=-180.0;var maxLon=180.0;var even=true;var bit=0;var value=0;val out=StringBuilder();while(out.length<precision){val mid=if(even)(minLon+maxLon)/2 else(minLat+maxLat)/2;val high=if(even)longitude>mid else latitude>mid;if(high){value=value or (16 shr bit);if(even)minLon=mid else minLat=mid}else if(even)maxLon=mid else maxLat=mid;even=!even;if(bit<4)bit++ else{out.append(alphabet[value]);bit=0;value=0}};return out.toString()}

    private fun refreshWeather(point: GeoPoint, force: Boolean) {
        val cache = cacheFile("weather.json")
        val text = cachedFetch(cache, 30 * 60L, force) {
            val params = "latitude=${point.latitude}&longitude=${point.longitude}" +
                "&current=temperature_2m,pressure_msl,relative_humidity_2m,wind_speed_10m,wind_direction_10m,precipitation,weather_code" +
                "&hourly=cape,temperature_850hPa,wind_speed_300hPa,wind_direction_300hPa,pressure_msl&forecast_days=1&timezone=UTC"
            readUrl("https://api.open-meteo.com/v1/forecast?$params")
        }
        val root = JSONObject(text); val current = root.optJSONObject("current") ?: error("No current weather")
        val hourly = root.optJSONObject("hourly"); val times = hourly?.optJSONArray("time")
        val currentTime = current.optString("time"); var index = 0
        if (times != null) for (i in 0 until times.length()) if (times.optString(i) == currentTime) { index = i; break }
        fun number(name: String): Double? = hourly?.optJSONArray(name)?.optDouble(index)?.takeIf(Double::isFinite)
        val temp = current.optionalDouble("temperature_2m"); val humidity = current.optionalDouble("relative_humidity_2m")?.roundToInt()
        val cape = number("cape"); val at850 = number("temperature_850hPa"); val tropo = tropoIndex(temp, at850, humidity, cape)
        val pressures = hourly?.optJSONArray("pressure_msl"); val trend = if (pressures != null && index >= 2) {
            val delta = pressures.optDouble(index) - pressures.optDouble(index - 2)
            when { delta <= -2.0 -> "FALLING FAST"; delta <= -0.6 -> "FALLING"; delta >= 2.0 -> "RISING FAST"; delta >= 0.6 -> "RISING"; else -> "STEADY" }
        } else "—"
        val parsed = NeuralWeather(true, cache.lastModified() / 1000, temp, current.optionalDouble("pressure_msl"), humidity,
            current.optionalDouble("wind_speed_10m"), current.optionalDouble("wind_direction_10m")?.roundToInt(),
            current.optionalDouble("precipitation"), current.optionalDouble("weather_code")?.roundToInt(), cape, at850,
            number("wind_speed_300hPa"), number("wind_direction_300hPa")?.roundToInt(), tropo.first, tropo.second, trend)
        scope.launch(Dispatchers.Main) { weather = parsed }
    }

    private fun refreshMySignal(call: String, point: GeoPoint?, force: Boolean, generation: Int = pskGeneration) {
        val normalized = call.trim().uppercase(Locale.US)
        lastPskCall = normalized; lastPskPoint = point
        if (normalized.isBlank() || !pskPreference.enabled) {
            pskSnapshot = PskReporterSnapshot(normalized)
            publishPskSnapshot(pskSnapshot)
            return
        }
        val loaded = publicProviders.pskReporter.refresh(normalized, point, pskPreference.windowMinutes, force)
        if (generation != pskGeneration) return
        pskSnapshot = loaded
        publishPskSnapshot(pskSnapshot)
    }

    private fun refreshBriefing(force: Boolean) {
        val snapshot = publicProviders.dxNews.refresh(dxpeditionFeed, force)
        val ordered = briefingOrder.mapNotNull { key -> snapshot.sources.firstOrNull { it.id == key } } +
            snapshot.sources.filter { it.id !in briefingOrder }
        scope.launch(Dispatchers.Main) { dxNewsSnapshot = snapshot.copy(sources = ordered); briefing = ordered }
    }

    private fun publishPskSnapshot(snapshot: PskReporterSnapshot) {
        val enriched = snapshot.reports.map { report ->
            val distance = lastPskPoint?.let { local ->
                report.latitude?.let { latitude -> report.longitude?.let { longitude ->
                    greatCircleKm(local, GeoPoint(latitude, longitude)).roundToInt()
                } }
            }
            report.copy(continent = ctyController?.lookup(report.callsign)?.continent.orEmpty(), distanceKm = distance)
        }
        val rows = filterPskReports(enriched, pskPreference)
        val error = listOf(snapshot.beingHeard.error, snapshot.hearing.error).filter(String::isNotBlank).joinToString(" · ")
        scope.launch(Dispatchers.Main) {
            mySignal = NeuralMySignal(
                available = snapshot.beingHeard.state != HamClockFeedState.UNAVAILABLE || snapshot.hearing.state != HamClockFeedState.UNAVAILABLE,
                callsign = snapshot.callsign,
                fetchedEpoch = maxOf(snapshot.beingHeard.fetchedEpoch, snapshot.hearing.fetchedEpoch),
                reports = rows,
                error = error,
                beingHeardState = snapshot.beingHeard.state,
                hearingState = snapshot.hearing.state,
                beingHeardCount = snapshot.beingHeard.reports.size,
                hearingCount = snapshot.hearing.reports.size,
            )
        }
    }

    private fun refreshSatellites(point: GeoPoint, force: Boolean) {
        val cache = cacheFile("satellites.json")
        var text = cachedFetch(cache, 2 * 3600L, force) {
            fetchOrbitCatalogue()
        }
        var catalogue = parseOrbits(text)
        if (catalogue.isEmpty()) {
            text = fetchOrbitCatalogue()
            cache.writeText(text)
            catalogue = parseOrbits(text)
        }
        if (catalogue.isEmpty()) error("No valid orbital elements")
        val selected = catalogue.filter { it.norad in followedNorads }.ifEmpty { catalogue.take(8) }
        val now = Instant.now().epochSecond
        val positions = selected.map { satellitePosition(it, now, point) }
        val passRows = selected.flatMap { calculatePasses(it, point, now, 24) }.sortedBy { it.aosEpoch }.take(80)
        scope.launch(Dispatchers.Main) { satelliteCatalogue = catalogue; satellites = positions; passes = passRows }
        ensureSatelliteTicker(point)
    }

    private fun fetchOrbitCatalogue(): String {
        val merged = linkedMapOf<Int, OrbitRecord>()
        listOf(
            "https://celestrak.org/NORAD/elements/gp.php?GROUP=amateur&FORMAT=json",
            "https://celestrak.org/NORAD/elements/gp.php?GROUP=stations&FORMAT=json",
        ).forEach { url ->
            runCatching { parseOrbits(readUrl(url, 5_000_000)) }.getOrDefault(emptyList()).forEach { merged.putIfAbsent(it.norad, it) }
        }
        runCatching {
            parseTleOrbitRecords(readUrl("https://www.amsat.org/amsat/ftp/keps/current/nasa.all", 5_000_000))
        }.getOrDefault(emptyList()).forEach { merged.putIfAbsent(it.norad, it) }
        if (merged.isEmpty()) error("No orbital source available")
        return JSONArray(merged.values.sortedBy(OrbitRecord::name).map(::orbitToJson)).toString()
    }

    private fun ensureSatelliteTicker(point:GeoPoint){if(satelliteJob?.isActive==true&&satellitePoint?.let{greatCircleKm(it,point)<5}==true)return;satelliteJob?.cancel();satellitePoint=point;satelliteJob=scope.launch{while(isActive){delay(30_000);val catalogue=satelliteCatalogue;if(catalogue.isNotEmpty()){val selected=catalogue.filter{it.norad in followedNorads}.ifEmpty{catalogue.take(8)};val now=Instant.now().epochSecond;val positions=selected.map{satellitePosition(it,now,point)};withContext(Dispatchers.Main){satellites=positions}}}}}

    private fun buildInsight(call: String, log: NeuralLogSummary, live: List<AndroidDXSpot>): NeuralInsight {
        val now = Instant.now().epochSecond; val hot = live.sortedByDescending { it.score }.take(5)
        val activeBands = bandActivity.entries.sortedByDescending { it.value }.take(4)
        val solarSentence = "Live cluster has ${live.size} ranked calls; ${activeBands.joinToString { "${it.key} ${it.value}" }.ifBlank { "history is still learning" }}."
        val logSentence = "Configured log: ${log.qsos} QSOs, ${log.calls} calls, ${log.dxccs} DXCC (${log.confirmedDxccs} confirmed by QSL/LoTW)."
        val weatherSentence = if (weather.available) "Weather: ${weather.temperatureC?.roundToInt()}°C, ${weather.pressureHpa?.roundToInt()} hPa, tropo ${weather.ductingRisk ?: "unknown"}." else "Weather context unavailable."
        val recommendations = buildList {
            hot.forEach { add("${it.callsign} · ${it.band} ${it.mode} · score ${it.score} · ${it.country}") }
            if (hot.isEmpty()) add("Keep the cluster connected while the local model builds a sample history.")
        }
        return NeuralInsight(now, "Tactical DX report for ${call.ifBlank { "this station" }}",
            "$solarSentence $logSentence $weatherSentence", listOf(logSentence, solarSentence, weatherSentence), recommendations, log)
    }

    private fun enrichInsight(base: NeuralInsight): NeuralInsight {
        val payload = JSONObject().put("model", "sonar-pro").put("max_tokens", 300).put("messages", JSONArray()
            .put(JSONObject().put("role", "system").put("content", "You are a concise amateur-radio propagation analyst. Never invent missing measurements."))
            .put(JSONObject().put("role", "user").put("content", base.report + " Opportunities: " + base.recommendations.joinToString())))
        val text = readUrl("https://api.perplexity.ai/chat/completions", 1_000_000, "POST", payload.toString(),
            mapOf("Authorization" to "Bearer $perplexityKey", "Content-Type" to "application/json"))
        val report = JSONObject(text).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
        return if (report.isBlank()) base else base.copy(report = report, source = "PERPLEXITY SONAR-PRO + LOCAL")
    }

    private fun refreshBeaconReference(point: GeoPoint, force: Boolean) {
        val file = cacheFile("beacons.csv")
        val text = cachedFetch(file, 30L * 24L * 3600L, force) {
            readUrl("https://dl0tud.tu-dresden.de/beacons/csv.php", 4_000_000)
        }
        val parsed = parseBeaconCsv(text)
        require(parsed.size >= 10) { "Beacon reference response was incomplete" }
        val ranged = parsed.map { row ->
            val location = maidenheadCenter(row.locator)
            if (location == null) row else {
                val distance = greatCircleKm(point, location).roundToInt()
                row.copy(distanceKm = distance, bearing = compass(initialBearing(point, location)),
                    inTypicalRange = distance <= beaconRangeKm(row.band))
            }
        }.sortedWith(compareBy<BeaconReference> { it.distanceKm ?: Int.MAX_VALUE }.thenBy { it.band }.thenBy { it.callsign })
        cacheFile("beacons.json").writeText(JSONArray(ranged.map { beaconToJson(it) }).toString())
        scope.launch(Dispatchers.Main) {
            beaconReference = ranged
            beaconStatus = "${ranged.size} beacon references · updated ${DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(file.lastModified()))}"
        }
    }

    private fun parseBeaconCsv(text: String): List<BeaconReference> {
        val lines = text.lineSequence().filter(String::isNotBlank).toList(); if (lines.isEmpty()) return emptyList()
        val header = parseSemicolonRow(lines.first()).map { it.trim().lowercase(Locale.US) }
        fun index(name: String) = header.indexOf(name)
        val callIndex=index("call"); val freqIndex=index("qrg"); val locatorIndex=index("loc")
        val remarkIndex=index("rem"); val dateIndex=index("date")
        if (callIndex < 0 || freqIndex < 0 || locatorIndex < 0) return emptyList()
        val qrt = listOf("qrt", "license cancelled", "not qrv", "dismantled", "off air", "switched off", "permanently off")
        val byCall = linkedMapOf<String, BeaconReference>()
        lines.drop(1).forEach { line ->
            val cells=parseSemicolonRow(line); fun cell(i:Int)=cells.getOrNull(i)?.trim().orEmpty()
            val call=cell(callIndex).uppercase(Locale.US).replace(" ",""); val locator=cell(locatorIndex).uppercase(Locale.US).take(6)
            val mhz=cell(freqIndex).replace(',','.').toDoubleOrNull(); val remark=cell(remarkIndex); val date=cell(dateIndex)
            val band=mhz?.let(::beaconBand).orEmpty()
            if(call.isBlank()||mhz==null||locator.length<4||band.isBlank()||qrt.any{remark.contains(it,true)}) return@forEach
            val row=BeaconReference(call,band,mhz,locator,date)
            if(byCall[call]?.lastReport.orEmpty() < date) byCall[call]=row
        }
        return byCall.values.sortedWith(compareBy<BeaconReference>{it.band}.thenBy{it.frequencyMHz})
    }

    private fun parseSemicolonRow(line: String): List<String> {
        val out=mutableListOf<String>(); val cell=StringBuilder(); var quoted=false; var i=0
        while(i<line.length){val c=line[i];when{c=='"'&&quoted&&i+1<line.length&&line[i+1]=='"'->{cell.append('"');i++};c=='"'->quoted=!quoted;c==';'&&!quoted->{out+=cell.toString();cell.clear()};else->cell.append(c)};i++}
        out+=cell.toString();return out
    }

    private fun beaconBand(mhz: Double): String = when(mhz){in 50.0..54.0->"6m";in 70.0..71.0->"4m";in 144.0..148.0->"2m";in 430.0..440.0->"70cm";in 1240.0..1300.0->"23cm";in 2300.0..2450.0->"13cm";in 3300.0..3500.0->"9cm";in 5650.0..5850.0->"6cm";in 10000.0..10500.0->"3cm";in 24000.0..24250.0->"12mm";in 47000.0..47200.0->"6mm";in 75500.0..81000.0->"4mm";else->""}
    private fun beaconRangeKm(band:String)=mapOf("6m" to 3000,"4m" to 1500,"2m" to 800,"70cm" to 500,"23cm" to 300,"13cm" to 200,"9cm" to 150,"6cm" to 100,"3cm" to 80,"12mm" to 50,"6mm" to 30,"4mm" to 20)[band]?:1000
    private fun beaconToJson(row:BeaconReference)=JSONObject().put("call",row.callsign).put("band",row.band).put("frequency",row.frequencyMHz).put("locator",row.locator).put("report",row.lastReport).put("distance",row.distanceKm).put("bearing",row.bearing).put("range",row.inTypicalRange)

    private fun buildBeaconReception(rows: List<AndroidDXSpot>): List<BeaconReception> {
        val now = Instant.now().epochSecond
        val known = beaconReference.associateBy { it.callsign.substringBefore('/') }
        return rows.filter { it.band in setOf("6m", "4m", "2m", "70cm", "23cm", "13cm") &&
            (known.containsKey(it.callsign.substringBefore('/')) || it.callsign.contains("/B") || it.comment.contains("BEACON", true)) }
            .map { BeaconReception(it.callsign, it.band, it.frequencyHz, it.country,
                ((now - it.receivedEpoch).coerceAtLeast(0) / 60).toInt(), known.containsKey(it.callsign.substringBefore('/')), it.spotter) }
            .distinctBy { it.callsign }.take(40)
    }

    private fun deliverAlert(title: String, message: String, key: String, force: Boolean = false) {
        val cooldownKey = "alert_${key.hashCode()}"; val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(cooldownKey, 0L) < 15 * 60_000L) return
        prefs.edit().putLong(cooldownKey, now).apply()
        scope.launch(Dispatchers.Main) { alerts.add(0, "$title · $message"); while (alerts.size > 20) alerts.removeLast() }
        if (notificationsEnabled && (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(key.hashCode(), NotificationCompat.Builder(context, NOTIFICATION_CHANNEL).setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build())
        }
        if (ntfyUrl.startsWith("https://")) scope.launch { runCatching {
            readUrl(ntfyUrl, 100_000, "POST", message, buildMap { put("Title", title); put("Tags", "radio")
                if (ntfyToken.isNotBlank()) put("Authorization", "Bearer $ntfyToken") })
        } }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL, "Neural DX alerts", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun parseBriefing(text: String, base: String): List<BriefingItem> {
        val blocks = Regex("<item[\\s\\S]*?</item>", RegexOption.IGNORE_CASE).findAll(text).map { it.value }.toList()
        val candidates = if (blocks.isNotEmpty()) blocks else Regex("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)
            .findAll(text).map { "<link>${it.groupValues[1]}</link><title>${it.groupValues[2]}</title>" }.toList()
        return candidates.mapNotNull { block ->
            val title = xmlValue(block, "title").htmlText(); if (title.length < 4) return@mapNotNull null
            var link = xmlValue(block, "link").htmlText().ifBlank { Regex("href=[\"']([^\"']+)", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1).orEmpty() }
            if (link.startsWith('/')) link = URL(URL(base), link).toString()
            val summary = (xmlValue(block, "description").ifBlank { title }).htmlText().take(360)
            val image = sequenceOf(
                Regex("<media:(?:content|thumbnail)[^>]+url=[\"']([^\"']+)", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1),
                Regex("<enclosure[^>]+url=[\"']([^\"']+)[\"'][^>]+type=[\"']image/", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1),
                Regex("<img[^>]+src=[\"']([^\"']+)", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1),
            ).filterNotNull().firstOrNull().orEmpty().let { raw ->
                when { raw.startsWith("https://", true) -> raw; raw.startsWith('/') -> runCatching { URL(URL(base), raw).toString() }.getOrDefault(""); else -> "" }
            }
            BriefingItem(title.take(180), link, xmlValue(block, "pubDate").htmlText(), summary, extractCallsigns("$title $summary"), image)
        }.distinctBy { it.title }.take(20)
    }

    private fun parseOrbits(text: String): List<OrbitRecord> {
        val rows = JSONArray(text); val out = mutableListOf<OrbitRecord>()
        for (i in 0 until rows.length()) runCatching {
            val r = rows.getJSONObject(i); val norad = r.optInt("NORAD_CAT_ID"); if (norad <= 0) return@runCatching
            val epoch = Instant.parse(r.getString("EPOCH")).epochSecond
            out += OrbitRecord(norad, r.optString("OBJECT_NAME", "SAT-$norad"), epoch, r.getDouble("INCLINATION"),
                r.getDouble("RA_OF_ASC_NODE"), r.getDouble("ECCENTRICITY"), r.getDouble("ARG_OF_PERICENTER"),
                r.getDouble("MEAN_ANOMALY"), r.getDouble("MEAN_MOTION"))
        }
        return out.distinctBy { it.norad }.sortedBy { it.name }
    }

    private fun satellitePosition(orbit: OrbitRecord, epoch: Long, observer: GeoPoint): SatellitePosition {
        val mu = 398600.4418; val earth = 6378.137; val n = orbit.meanMotion * 2.0 * Math.PI / 86400.0
        val a = cbrt(mu / (n * n)); val mean = Math.toRadians(orbit.meanAnomaly) + n * (epoch - orbit.epoch)
        var eccentric = mean
        repeat(8) { eccentric -= (eccentric - orbit.eccentricity * sin(eccentric) - mean) / (1 - orbit.eccentricity * cos(eccentric)) }
        val xOrb = a * (cos(eccentric) - orbit.eccentricity); val yOrb = a * sqrt(1 - orbit.eccentricity.pow(2)) * sin(eccentric)
        val arg = Math.toRadians(orbit.argumentPerigee); val inc = Math.toRadians(orbit.inclination); val raan = Math.toRadians(orbit.raan)
        val x1 = xOrb * cos(arg) - yOrb * sin(arg); val y1 = xOrb * sin(arg) + yOrb * cos(arg)
        val xEci = x1 * cos(raan) - y1 * cos(inc) * sin(raan); val yEci = x1 * sin(raan) + y1 * cos(inc) * cos(raan); val zEci = y1 * sin(inc)
        val theta = gmst(epoch); val x = xEci * cos(theta) + yEci * sin(theta); val y = -xEci * sin(theta) + yEci * cos(theta); val z = zEci
        val lon = Math.toDegrees(atan2(y, x)); val radius = sqrt(x*x + y*y + z*z); val lat = Math.toDegrees(asin(z / radius)); val alt = radius - earth
        val obsLat = Math.toRadians(observer.latitude); val obsLon = Math.toRadians(observer.longitude)
        val ox = earth * cos(obsLat) * cos(obsLon); val oy = earth * cos(obsLat) * sin(obsLon); val oz = earth * sin(obsLat)
        val dx=x-ox; val dy=y-oy; val dz=z-oz
        val east=-sin(obsLon)*dx+cos(obsLon)*dy; val north=-sin(obsLat)*cos(obsLon)*dx-sin(obsLat)*sin(obsLon)*dy+cos(obsLat)*dz
        val up=cos(obsLat)*cos(obsLon)*dx+cos(obsLat)*sin(obsLon)*dy+sin(obsLat)*dz; val range=sqrt(east*east+north*north+up*up)
        val az=(Math.toDegrees(atan2(east,north))+360)%360; val el=Math.toDegrees(asin(up/range)); val footprint=earth*acos((earth/(earth+max(0.0,alt))).coerceIn(-1.0,1.0))
        return SatellitePosition(orbit.norad, orbit.name, lat, lon, alt, az, el, range, footprint, el > 0, epoch)
    }

    private fun calculatePasses(orbit: OrbitRecord, observer: GeoPoint, start: Long, hours: Int): List<SatellitePass> {
        val out=mutableListOf<SatellitePass>(); var active=false; var aos=0L; var aosAz=0.0; var best=-90.0; var tca=0L; var previous=satellitePosition(orbit,start,observer)
        var t=start+60; val end=start+hours*3600L
        while(t<=end){ val p=satellitePosition(orbit,t,observer)
            if(!active && p.elevation>=0 && previous.elevation<0){ active=true; aos=t; aosAz=p.azimuth; best=p.elevation; tca=t }
            if(active && p.elevation>best){best=p.elevation;tca=t}
            if(active && p.elevation<0){out+=SatellitePass(orbit.norad,orbit.name,aos,tca,t,best,aosAz,p.azimuth);active=false}
            previous=p;t+=60
        }
        return out
    }

    private fun fetchTransmitters(norad: Int): List<SatelliteTransmitter> {
        val text = readUrl("https://db.satnogs.org/api/transmitters/?satellite__norad_cat_id=$norad", 1_000_000)
        val rows = JSONArray(text); return buildList { for (i in 0 until rows.length()) {
            val r=rows.getJSONObject(i); add(SatelliteTransmitter(r.optString("description"), frequencyLabel(r.optLong("uplink_low")),
                frequencyLabel(r.optLong("downlink_low")), r.optString("mode"), r.optString("status")))
        } }.filter { it.status.lowercase() !in setOf("inactive", "invalid") }
    }

    private fun readUrl(url: String, limit: Int = 3_000_000, method: String = "GET", body: String? = null,
        headers: Map<String,String> = emptyMap()): String {
        require(url.startsWith("https://"))
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod=method; connection.connectTimeout=15_000; connection.readTimeout=30_000
            connection.instanceFollowRedirects=true; connection.setRequestProperty("User-Agent","RigWeave-NeuralDX/12.1 Android")
            headers.forEach(connection::setRequestProperty)
            if(body!=null){connection.doOutput=true;connection.outputStream.use{it.write(body.toByteArray())}}
            val code=connection.responseCode; if(code !in 200..299) error("HTTP $code")
            if(connection.contentLengthLong>limit) error("Response too large")
            val bytes=connection.inputStream.use{it.readNBytes(limit+1)}; if(bytes.size>limit) error("Response too large")
            return bytes.toString(Charsets.UTF_8)
        } finally { connection.disconnect() }
    }

    private fun cachedFetch(file: File, ttlSeconds: Long, force: Boolean, fetch: () -> String): String {
        if(!force && file.exists() && System.currentTimeMillis()-file.lastModified()<ttlSeconds*1000) return file.readText()
        return try { val value=fetch(); val tmp=File(file.parentFile,file.name+".tmp");tmp.writeText(value);if(file.exists())file.delete();require(tmp.renameTo(file));value }
        catch(cancelled:CancellationException){throw cancelled}
        catch(error:Exception){if(file.exists())file.readText() else throw error}
    }

    private fun loadWeatherCache(): NeuralWeather = runCatching {
        val file=cacheFile("weather.json"); if(!file.exists()) return@runCatching NeuralWeather()
        val c=JSONObject(file.readText()).optJSONObject("current") ?: return@runCatching NeuralWeather()
        NeuralWeather(true,file.lastModified()/1000,c.optionalDouble("temperature_2m"),c.optionalDouble("pressure_msl"),
            c.optionalDouble("relative_humidity_2m")?.roundToInt(),c.optionalDouble("wind_speed_10m"),
            c.optionalDouble("wind_direction_10m")?.roundToInt(),c.optionalDouble("precipitation"),c.optionalDouble("weather_code")?.roundToInt())
    }.getOrDefault(NeuralWeather())

    private fun loadBriefingCache(): List<BriefingSource> = runCatching {
        val file=cacheFile("briefing.json"); if(!file.exists()) return@runCatching emptyList()
        val rows=JSONArray(file.readText());buildList{for(i in 0 until rows.length()){val r=rows.getJSONObject(i);val items=r.optJSONArray("items")?:JSONArray()
            add(BriefingSource(r.optString("id"),r.optString("name"),r.optString("site"),buildList{for(j in 0 until items.length()){val q=items.getJSONObject(j);add(BriefingItem(q.optString("title"),q.optString("link"),q.optString("published"),q.optString("summary"),extractCallsigns(q.optString("title")+" "+q.optString("summary")),q.optString("image")))}},r.optLong("updated"),true))}}
    }.getOrDefault(emptyList())

    private fun loadOrbitCache(): List<OrbitRecord> = runCatching { val f=cacheFile("satellites.json");if(f.exists())parseOrbits(f.readText()) else emptyList() }.getOrDefault(emptyList())
    private fun loadMySignalCache():NeuralMySignal=runCatching{val f=cacheFile("my-signal.json");if(!f.exists())return@runCatching NeuralMySignal();val root=JSONObject(f.readText());val rows=root.optJSONArray("reports")?:JSONArray();NeuralMySignal(true,root.optString("call"),root.optLong("fetched"),buildList{for(i in 0 until rows.length()){val r=rows.getJSONObject(i);add(SignalReport(r.optString("call"),r.optString("locator"),r.optionalDouble("lat"),r.optionalDouble("lon"),r.optLong("frequency"),r.optString("band"),r.optString("mode"),r.optInt("snr").takeIf{r.has("snr")&&!r.isNull("snr")},r.optInt("distance").takeIf{r.has("distance")&&!r.isNull("distance")},r.optLong("epoch")))}})}.getOrDefault(NeuralMySignal())
    private fun loadBeaconReference(): List<BeaconReference> = runCatching {
        val file=cacheFile("beacons.json");if(!file.exists()) return@runCatching emptyList()
        val rows=JSONArray(file.readText());buildList{for(i in 0 until rows.length()){val r=rows.getJSONObject(i);add(BeaconReference(
            r.optString("call"),r.optString("band"),r.optDouble("frequency"),r.optString("locator"),r.optString("report"),
            r.optInt("distance").takeIf{r.has("distance")&&!r.isNull("distance")},r.optString("bearing"),r.optBoolean("range")))}}
    }.getOrDefault(emptyList())
    private fun loadBriefingOrder(): List<String> {
        val canonical=listOf("dxworld","dxnews","ng3k")
        val saved=prefs.getString("briefing_order","").orEmpty().split(',').filter{it in canonical}.distinct()
        return saved + canonical.filter{it !in saved}
    }
    private fun sourceToJson(s:BriefingSource)=JSONObject().put("id",s.id).put("name",s.name).put("site",s.site).put("updated",s.updatedEpoch).put("items",JSONArray(s.items.map{JSONObject().put("title",it.title).put("link",it.link).put("published",it.published).put("summary",it.summary).put("image",it.imageUrl)}))
    private fun orbitToJson(r:OrbitRecord)=JSONObject().put("NORAD_CAT_ID",r.norad).put("OBJECT_NAME",r.name)
        .put("EPOCH",Instant.ofEpochSecond(r.epoch).toString()).put("INCLINATION",r.inclination).put("RA_OF_ASC_NODE",r.raan)
        .put("ECCENTRICITY",r.eccentricity).put("ARG_OF_PERICENTER",r.argumentPerigee).put("MEAN_ANOMALY",r.meanAnomaly).put("MEAN_MOTION",r.meanMotion)
    private fun signalToJson(r:SignalReport)=JSONObject().put("call",r.callsign).put("locator",r.locator).put("lat",r.latitude).put("lon",r.longitude).put("frequency",r.frequencyHz).put("band",r.band).put("mode",r.mode).put("snr",r.snr).put("distance",r.distanceKm).put("epoch",r.epoch)
    private fun cacheFile(name:String)=File(cacheDir,name)
    private fun JSONObject.optionalDouble(name:String):Double?=if(has(name)&&!isNull(name))optDouble(name).takeIf(Double::isFinite) else null
    private fun normalizeWsprBand(raw:String)=when(raw.trim().lowercase()){ "-1"->"2200m";"0"->"630m";"1"->"160m";"3"->"80m";"5"->"60m";"7"->"40m";"10"->"30m";"14"->"20m";"18"->"17m";"21"->"15m";"24"->"12m";"28"->"10m";"50"->"6m";"70"->"4m";"144"->"2m";"432"->"70cm";else->raw }
    private fun xmlValue(block:String,tag:String)=Regex("<$tag[^>]*>([\\s\\S]*?)</$tag>",RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1).orEmpty().removePrefix("<![CDATA[").removeSuffix("]]>")
    private fun String.htmlText()=decodeHtmlText(this)
    private fun safeError(error:Throwable)=when(error){is java.net.SocketTimeoutException->"Timed out";is java.net.UnknownHostException->"Offline";else->error.message?.takeIf{!it.contains("http",true)}?:"Unavailable"}
    private fun readSecret(name: String): String {
        val stored = prefs.getString(name, "").orEmpty()
        if (stored.isBlank()) return ""
        if (stored.startsWith("v1:")) return decrypt(stored.removePrefix("v1:"))
        // One-time migration from early development builds that stored these optional values as plain text.
        prefs.edit().putString(name, encrypt(stored)).apply()
        return stored
    }
    private fun secret(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(SECRET_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(SECRET_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(value: String): String = if (value.isBlank()) "" else runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, secret())
        "v1:" + Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }.getOrDefault("")
    private fun decrypt(value: String): String = if (value.isBlank()) "" else runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP); require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
    }.getOrDefault("")
    private fun decodeIds(raw:String?)=raw?.split(',')?.mapNotNull(String::toIntOrNull)?.distinct()?.take(24)?.takeIf(List<Int>::isNotEmpty)?:listOf(25544,39444,43017,27607,44909,24278,43678)
    private fun frequencyLabel(hz:Long)=if(hz<=0)"—" else "%.6f MHz".format(Locale.US,hz/1_000_000.0)
    private fun frequencyBand(hz:Long):String=when(hz){in 1_800_000..2_000_000->"160m";in 3_500_000..4_000_000->"80m";in 5_000_000..5_500_000->"60m";in 7_000_000..7_300_000->"40m";in 10_100_000..10_150_000->"30m";in 14_000_000..14_350_000->"20m";in 18_068_000..18_168_000->"17m";in 21_000_000..21_450_000->"15m";in 24_890_000..24_990_000->"12m";in 28_000_000..29_700_000->"10m";in 50_000_000..54_000_000->"6m";in 70_000_000..71_000_000->"4m";in 144_000_000..148_000_000->"2m";in 430_000_000..440_000_000->"70cm";else->"—"}
    private fun greatCircleKm(a:GeoPoint,b:GeoPoint):Double{val p1=Math.toRadians(a.latitude);val p2=Math.toRadians(b.latitude);val dp=p2-p1;val dl=Math.toRadians(b.longitude-a.longitude);val h=sin(dp/2).pow(2)+cos(p1)*cos(p2)*sin(dl/2).pow(2);return 6371.0*2*atan2(sqrt(h),sqrt(1-h))}
    private fun initialBearing(a:GeoPoint,b:GeoPoint):Double{val p1=Math.toRadians(a.latitude);val p2=Math.toRadians(b.latitude);val dl=Math.toRadians(b.longitude-a.longitude);return(Math.toDegrees(atan2(sin(dl)*cos(p2),cos(p1)*sin(p2)-sin(p1)*cos(p2)*cos(dl)))+360)%360}
    private fun compass(degrees:Double)=listOf("N","NE","E","SE","S","SW","W","NW")[((degrees+22.5)/45).toInt()%8]
    private fun gmst(epoch:Long):Double{val jd=epoch/86400.0+2440587.5;val d=jd-2451545.0;return Math.toRadians((280.46061837+360.98564736629*d)%360.0)}

    companion object {
        private const val NOTIFICATION_CHANNEL="neural_dx_alerts"
        private const val SECRET_ALIAS="app.rigweave.mobile.neural_dx"
    }
}
