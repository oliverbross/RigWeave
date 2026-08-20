package app.rigweave.mobile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.time.Instant
import java.time.YearMonth

data class AndroidDXSpot(
    val id: String, val callsign: String, val spotter: String, val frequencyHz: Long,
    val receivedEpoch: Long, val band: String, val mode: String, val country: String, val continent: String,
    val cqZone: Int, val ituZone: Int, val latitude: Double, val longitude: Double, val comment: String,
    val score: Int, val confidence: Int, val samples: Int, val watchlisted: Boolean,
    val workedCountry: Boolean, val workedCall: Boolean, val workedBand: Boolean,
    val workedMode: Boolean, val workedBandMode: Boolean, val recentDupe: Boolean,
    val distanceKm: Int, val bearingDegrees: Int, val pathState: String, val reason: String,
)

data class AndroidSolar(val valid: Boolean = false, val flux: Float = 0f, val aIndex: Float = 0f,
    val kpIndex: Float = 0f, val observedEpoch: Long = 0L)

data class AndroidDXBand(val band: String, val spots5m: Int, val spots60m: Int, val uniqueCalls: Int,
    val surgePercent: Int, val surge: Boolean)
data class AndroidDXRegion(val region: String, val spots15m: Int, val spots60m: Int,
    val uniqueCalls: Int, val activityPercent: Int, val anomaly: Boolean)

internal fun parseNoaaSummaryValue(text: String, keys: List<String>): Float? {
    val parsed = JSONTokener(text).nextValue()
    val rows = when(parsed) { is JSONObject -> listOf(parsed); is JSONArray -> (0 until parsed.length()).mapNotNull(parsed::optJSONObject); else -> emptyList() }
    rows.asReversed().forEach { root ->
        val names = root.keys().asSequence().toList()
        keys.forEach { key -> names.firstOrNull { it.equals(key, true) }?.let { actual -> root.optString(actual).toFloatOrNull()?.let { return it } } }
    }
    return null
}

internal fun parseLatestNoaaSunspot(body: String): Pair<Float, String> {
    val rows = JSONArray(body)
    val latest = (rows.length() - 1 downTo 0).asSequence().mapNotNull(rows::optJSONObject).firstOrNull { row ->
        runCatching { YearMonth.parse(row.optString("time-tag")) }.isSuccess &&
            row.optDouble("observed_swpc_ssn", Double.NaN).let { it.isFinite() && it >= 0.0 }
    } ?: error("NOAA returned no usable observed sunspot number")
    return latest.getDouble("observed_swpc_ssn").toFloat() to latest.getString("time-tag")
}

class FeatureController(private val context: Context) {
    private val handle = NativeCore.featureCreate()
    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private var clusterSocket: Socket? = null
    private var clusterGeneration = 0
    private val prefs = context.getSharedPreferences("dx_cluster", Context.MODE_PRIVATE)

    var clusterHost by mutableStateOf(prefs.getString("host", RigWeaveDefaults.CLUSTER_HOST) ?: RigWeaveDefaults.CLUSTER_HOST)
    var clusterPort by mutableStateOf(prefs.getInt("port", RigWeaveDefaults.CLUSTER_PORT))
    var fallbackHost by mutableStateOf(prefs.getString("fallback_host", "") ?: "")
    var fallbackPort by mutableStateOf(prefs.getInt("fallback_port", 7300))
    var fallback2Host by mutableStateOf(prefs.getString("fallback2_host", "") ?: "")
    var fallback2Port by mutableStateOf(prefs.getInt("fallback2_port", 7300))
    var clusterCallsign by mutableStateOf(prefs.getString("callsign", RigWeaveDefaults.CLUSTER_LOGIN) ?: RigWeaveDefaults.CLUSTER_LOGIN)
    var watchlistText by mutableStateOf(prefs.getString("watchlist", "") ?: ""); private set

    var clusterStatus by mutableStateOf("DX cluster disconnected"); private set
    var spots by mutableStateOf(emptyList<AndroidDXSpot>()); private set
    var liveSpots by mutableStateOf(emptyList<AndroidDXSpot>()); private set
    var watchSpots by mutableStateOf(emptyList<AndroidDXSpot>()); private set
    var dxBands by mutableStateOf(emptyList<AndroidDXBand>()); private set
    var dxRegions by mutableStateOf(emptyList<AndroidDXRegion>()); private set
    var dxTimeline by mutableStateOf(emptyList<List<Int>>()); private set
    var dxWorld by mutableStateOf(emptyList<List<Int>>()); private set
    var dxSummary by mutableStateOf("No live DX data"); private set
    var solar by mutableStateOf(AndroidSolar()); private set
    var sunspotNumber by mutableStateOf(prefs.getFloat("noaa_sunspot_number", Float.NaN).takeIf(Float::isFinite)); private set
    var sunspotObservedMonth by mutableStateOf(prefs.getString("noaa_sunspot_month", "").orEmpty()); private set
    var sunspotError by mutableStateOf(""); private set
    var learnedSpots by mutableStateOf(0); private set
    var duplicateSpots by mutableStateOf(0); private set
    var newestSpotEpoch by mutableStateOf(0L); private set
    var requestedSpotId by mutableStateOf<String?>(null); private set

    init {
        val defaults = prefs.edit()
        if (!prefs.contains("host")) defaults.putString("host", clusterHost)
        if (!prefs.contains("port")) defaults.putInt("port", clusterPort)
        if (!prefs.contains("callsign")) defaults.putString("callsign", clusterCallsign)
        defaults.apply()
        NativeCore.featureWatchlist(handle, watchlistText)
    }

    fun setWatchlist(value: String) {
        watchlistText = value.lineSequence().flatMap { it.split(',', ' ', ';').asSequence() }
            .map(String::trim).filter(String::isNotBlank).map(String::uppercase).distinct().take(32).joinToString("\n")
        prefs.edit().putString("watchlist", watchlistText).apply()
        NativeCore.featureWatchlist(handle, watchlistText)
    }

    fun requestSpot(id: String) { requestedSpotId = id }
    fun consumeRequestedSpot() { requestedSpotId = null }

    fun connectConfiguredCluster() {
        if (clusterHost.isNotBlank() && clusterPort in 1..65535 && clusterCallsign.isNotBlank()) {
            connectCluster(clusterHost, clusterPort, clusterCallsign,
                fallbackHost, fallbackPort, fallback2Host, fallback2Port)
        }
    }

    fun saveClusterConfiguration(host: String, port: Int, callsign: String,
        fallbackHost: String = "", fallbackPort: Int = RigWeaveDefaults.CLUSTER_PORT,
        fallback2Host: String = "", fallback2Port: Int = RigWeaveDefaults.CLUSTER_PORT) {
        clusterHost = host.trim().lowercase(); clusterPort = port.coerceIn(1, 65535)
        clusterCallsign = callsign.trim().uppercase()
        this.fallbackHost = fallbackHost.trim().lowercase(); this.fallbackPort = fallbackPort.coerceIn(1, 65535)
        this.fallback2Host = fallback2Host.trim().lowercase(); this.fallback2Port = fallback2Port.coerceIn(1, 65535)
        prefs.edit().putString("host", clusterHost).putInt("port", clusterPort).putString("callsign", clusterCallsign)
            .putString("fallback_host", this.fallbackHost).putInt("fallback_port", this.fallbackPort)
            .putString("fallback2_host", this.fallback2Host).putInt("fallback2_port", this.fallback2Port).apply()
    }

    fun connectCluster(host: String, port: Int, callsign: String,
        fallbackHost: String = "", fallbackPort: Int = 7300,
        fallback2Host: String = "", fallback2Port: Int = 7300) {
        saveClusterConfiguration(host, port, callsign, fallbackHost, fallbackPort, fallback2Host, fallback2Port)
        disconnectCluster(); val generation = ++clusterGeneration
        val endpoints = listOf(clusterHost to clusterPort, this.fallbackHost to this.fallbackPort,
            this.fallback2Host to this.fallback2Port).filter { it.first.isNotBlank() && it.second in 1..65535 }.distinct()
        scope.launch {
            var endpointIndex = 0
            var reconnectAttempt = 0
            while (generation == clusterGeneration && endpoints.isNotEmpty()) {
                val (endpointHost, endpointPort) = endpoints[endpointIndex]
                publishCluster("Connecting to $endpointHost:$endpointPort…")
                try {
                    val socket = Socket(); socket.connect(InetSocketAddress(endpointHost, endpointPort), 12_000); clusterSocket = socket
                    val output = socket.getOutputStream()
                    if (clusterCallsign.isNotBlank()) {
                        output.write((clusterCallsign + "\r\n").toByteArray()); output.flush()
                        delay(1_500)
                        if (generation != clusterGeneration) return@launch
                        output.write("sh/dx 50\r\n".toByteArray()); output.flush()
                    }
                    reconnectAttempt = 0
                    publishCluster("Connected to $endpointHost:$endpointPort")
                    BufferedReader(InputStreamReader(socket.getInputStream())).useLines { lines ->
                        lines.forEach { line ->
                            if (generation != clusterGeneration) return@useLines
                            if (NativeCore.featureClusterLine(handle, line, Instant.now().epochSecond)) refreshDX()
                        }
                    }
                } catch (error: Exception) {
                    if (generation == clusterGeneration) publishCluster("$endpointHost failed · trying next cluster")
                } finally { clusterSocket?.close(); clusterSocket = null }
                if (generation != clusterGeneration) return@launch
                endpointIndex = (endpointIndex + 1) % endpoints.size
                val next = endpoints[endpointIndex]
                val waitSeconds = minOf(30, 1 shl minOf(reconnectAttempt, 4))
                reconnectAttempt++
                publishCluster("Trying ${next.first}:${next.second} in ${waitSeconds}s…")
                delay(waitSeconds * 1_000L)
            }
        }
    }

    fun disconnectCluster() { clusterGeneration++; clusterSocket?.close(); clusterSocket = null; clusterStatus = "DX cluster disconnected" }

    fun postSpot(callsign: String, frequencyKHz: Double, comment: String) {
        val call = callsign.trim().uppercase()
        if (call.isBlank() || frequencyKHz !in 100.0..1_300_000.0) return
        val safeComment = comment.replace(Regex("[\\r\\n;]"), " ").trim().take(80)
        scope.launch {
            val socket = clusterSocket
            if (socket == null || socket.isClosed) {
                publishCluster("Cannot send spot · cluster is not connected")
            } else runCatching {
                val line = "DX %.1f %s %s\r\n".format(java.util.Locale.US, frequencyKHz, call, safeComment)
                socket.getOutputStream().apply { write(line.toByteArray()); flush() }
                publishCluster("Spot sent · $call")
            }.onFailure { publishCluster("Spot send failed · connection retained for retry") }
        }
    }

    fun refreshSolar() {
        scope.launch {
            try {
                val flux = summaryValue("https://services.swpc.noaa.gov/products/summary/10cm-flux.json", listOf("Flux", "flux"))
                val geomagnetic = summaryText("https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json")
                val kp = parseNoaaSummaryValue(geomagnetic, listOf("Kp", "kp_index", "KpIndex")) ?: error("Unexpected NOAA Kp response")
                val a = parseNoaaSummaryValue(geomagnetic, listOf("a_running", "A", "a_index")) ?: error("Unexpected NOAA A response")
                refreshSunspotNumber()
                NativeCore.featureSolar(handle, flux, a, kp, Instant.now().epochSecond); refreshDX()
            } catch (error: Exception) {
                sunspotError = error.message.orEmpty().take(120)
                publishCluster("NOAA solar data unavailable · retained last values")
            }
        }
    }

    private fun refreshSunspotNumber() {
        val body = summaryText("https://services.swpc.noaa.gov/json/solar-cycle/observed-solar-cycle-indices.json")
        val latest = parseLatestNoaaSunspot(body)
        sunspotNumber = latest.first
        sunspotObservedMonth = latest.second
        sunspotError = ""
        prefs.edit().putFloat("noaa_sunspot_number", sunspotNumber!!)
            .putString("noaa_sunspot_month", sunspotObservedMonth).apply()
    }

    fun close() {
        disconnectCluster(); scope.cancel(); NativeCore.featureDestroy(handle)
    }

    private suspend fun refreshDX() {
        val json = NativeCore.featureDxSnapshot(handle, Instant.now().epochSecond)
        val root = JSONObject(json)
        fun loadSpots(name: String) = buildList {
            val rows = root.optJSONArray(name)
            if (rows != null) for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index); val frequency = row.optLong("frequencyHz")
                add(AndroidDXSpot("${row.optString("callsign")}-$frequency-${row.optLong("receivedEpoch")}",
                    row.optString("callsign"), row.optString("spotter"), frequency, row.optLong("receivedEpoch"),
                    row.optString("band"), row.optString("mode"), row.optString("country"), row.optString("continent"),
                    row.optInt("cqZone"), row.optInt("ituZone"), row.optDouble("latitude"), row.optDouble("longitude"),
                    row.optString("comment"),
                    row.optInt("score"), row.optInt("confidence"), row.optInt("samples"), row.optBoolean("watchlisted"),
                    row.optBoolean("workedCountry"), row.optBoolean("workedCall"), row.optBoolean("workedBand"),
                    row.optBoolean("workedMode"), row.optBoolean("workedBandMode"), row.optBoolean("recentDupe"),
                    row.optInt("distanceKm"), row.optInt("bearingDegrees"), row.optString("pathState"), row.optString("reason")))
            }
        }
        val loaded = loadSpots("opportunities")
        val live = loadSpots("liveSpots")
        val watched = loadSpots("watchActivity")
        val bands = buildList {
            root.optJSONArray("bands")?.let { rows -> for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                add(AndroidDXBand(row.optString("band"), row.optInt("spots5m"), row.optInt("spots60m"),
                    row.optInt("uniqueCalls"), row.optInt("surgePercent"), row.optBoolean("surge")))
            } }
        }
        val regions = buildList {
            root.optJSONArray("regions")?.let { rows -> for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                add(AndroidDXRegion(row.optString("region"), row.optInt("spots15m"), row.optInt("spots60m"),
                    row.optInt("uniqueCalls"), row.optInt("activityPercent"), row.optBoolean("anomaly")))
            } }
        }
        fun matrix(name: String) = buildList {
            root.optJSONArray(name)?.let { rows -> for (rowIndex in 0 until rows.length()) {
                val row = rows.getJSONArray(rowIndex)
                add(List(row.length()) { column -> row.optInt(column) })
            } }
        }
        val summary = "${root.optInt("spots5m")} / 5m · ${root.optInt("spots60m")} / 60m · ${root.optInt("watchlistHits")} watch"
        val solarRow = root.optJSONObject("solar")
        val parsedSolar = AndroidSolar(solarRow?.optBoolean("valid") == true, solarRow?.optDouble("flux")?.toFloat() ?: 0f,
            solarRow?.optDouble("aIndex")?.toFloat() ?: 0f, solarRow?.optDouble("kpIndex")?.toFloat() ?: 0f,
            Instant.now().epochSecond)
        withContext(Dispatchers.Main) {
            spots = loaded; liveSpots = live; watchSpots = watched; dxBands = bands; dxRegions = regions
            dxTimeline = matrix("bandTimeline"); dxWorld = matrix("worldGrid"); dxSummary = summary; solar = parsedSolar
            learnedSpots = root.optInt("learnedSpots"); duplicateSpots = root.optInt("duplicateSpots")
            newestSpotEpoch = root.optLong("newestSpotEpoch")
        }
    }

    private fun summaryValue(url: String, keys: List<String>): Float {
        return parseNoaaSummaryValue(summaryText(url), keys)
            ?: error("Unexpected NOAA response")
    }

    private fun summaryText(url: String): String {
        val connection = URL(url).openConnection(); connection.connectTimeout = 10_000; connection.readTimeout = 10_000
        return connection.getInputStream().bufferedReader().use { it.readText() }
    }

    private suspend fun publishCluster(value: String) = withContext(Dispatchers.Main) { clusterStatus = value }
}
