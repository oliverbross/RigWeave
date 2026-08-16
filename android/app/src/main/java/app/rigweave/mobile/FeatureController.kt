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
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.time.Instant

data class AndroidDXSpot(
    val id: String, val callsign: String, val spotter: String, val frequencyHz: Long,
    val receivedEpoch: Long, val band: String, val mode: String, val country: String, val continent: String,
    val cqZone: Int, val comment: String,
    val score: Int, val confidence: Int, val samples: Int, val watchlisted: Boolean,
    val workedCountry: Boolean, val workedCall: Boolean, val workedBand: Boolean,
    val workedMode: Boolean, val workedBandMode: Boolean, val recentDupe: Boolean,
    val distanceKm: Int, val bearingDegrees: Int, val pathState: String, val reason: String,
)

data class AndroidDXBand(val band: String, val spots5m: Int, val spots60m: Int, val uniqueCalls: Int,
    val surgePercent: Int, val surge: Boolean)
data class AndroidDXRegion(val region: String, val spots15m: Int, val spots60m: Int,
    val uniqueCalls: Int, val activityPercent: Int, val anomaly: Boolean)

class FeatureController(private val context: Context) {
    private val handle = NativeCore.featureCreate()
    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private var clusterSocket: Socket? = null
    private var clusterGeneration = 0
    private val prefs = context.getSharedPreferences("dx_cluster", Context.MODE_PRIVATE)

    var clusterHost by mutableStateOf(prefs.getString("host", "cluster.om0rx.com") ?: "cluster.om0rx.com")
    var clusterPort by mutableStateOf(prefs.getInt("port", 7300))
    var fallbackHost by mutableStateOf(prefs.getString("fallback_host", "") ?: "")
    var fallbackPort by mutableStateOf(prefs.getInt("fallback_port", 7300))
    var fallback2Host by mutableStateOf(prefs.getString("fallback2_host", "") ?: "")
    var fallback2Port by mutableStateOf(prefs.getInt("fallback2_port", 7300))
    var clusterCallsign by mutableStateOf(prefs.getString("callsign", "") ?: "")

    var clusterStatus by mutableStateOf("DX cluster disconnected"); private set
    var spots by mutableStateOf(emptyList<AndroidDXSpot>()); private set
    var liveSpots by mutableStateOf(emptyList<AndroidDXSpot>()); private set
    var watchSpots by mutableStateOf(emptyList<AndroidDXSpot>()); private set
    var dxBands by mutableStateOf(emptyList<AndroidDXBand>()); private set
    var dxRegions by mutableStateOf(emptyList<AndroidDXRegion>()); private set
    var dxTimeline by mutableStateOf(emptyList<List<Int>>()); private set
    var dxWorld by mutableStateOf(emptyList<List<Int>>()); private set
    var dxSummary by mutableStateOf("No live DX data"); private set

    fun setWatchlist(value: String) { NativeCore.featureWatchlist(handle, value) }

    fun connectConfiguredCluster() {
        if (clusterHost.isNotBlank() && clusterPort in 1..65535 && clusterCallsign.isNotBlank()) {
            connectCluster(clusterHost, clusterPort, clusterCallsign,
                fallbackHost, fallbackPort, fallback2Host, fallback2Port)
        }
    }

    fun connectCluster(host: String, port: Int, callsign: String,
        fallbackHost: String = "", fallbackPort: Int = 7300,
        fallback2Host: String = "", fallback2Port: Int = 7300) {
        clusterHost = host.trim().lowercase(); clusterPort = port
        clusterCallsign = callsign.trim().uppercase()
        this.fallbackHost = fallbackHost.trim().lowercase(); this.fallbackPort = fallbackPort
        this.fallback2Host = fallback2Host.trim().lowercase(); this.fallback2Port = fallback2Port
        prefs.edit().putString("host", clusterHost).putInt("port", clusterPort).putString("callsign", clusterCallsign)
            .putString("fallback_host", this.fallbackHost).putInt("fallback_port", this.fallbackPort)
            .putString("fallback2_host", this.fallback2Host).putInt("fallback2_port", this.fallback2Port).apply()
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

    fun refreshSolar() {
        scope.launch {
            try {
                val flux = summaryValue("https://services.swpc.noaa.gov/products/summary/10cm-flux.json", listOf("Flux", "flux"))
                val kp = summaryValue("https://services.swpc.noaa.gov/products/summary/planetary-k-index.json", listOf("Kp", "kp_index", "KpIndex"))
                NativeCore.featureSolar(handle, flux, 0f, kp, Instant.now().epochSecond); refreshDX()
            } catch (error: Exception) { publishCluster("NOAA solar update failed: ${error.message}") }
        }
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
                    row.optInt("cqZone"), row.optString("comment"),
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
        withContext(Dispatchers.Main) {
            spots = loaded; liveSpots = live; watchSpots = watched; dxBands = bands; dxRegions = regions
            dxTimeline = matrix("bandTimeline"); dxWorld = matrix("worldGrid"); dxSummary = summary
        }
    }

    private fun summaryValue(url: String, keys: List<String>): Float {
        val connection = URL(url).openConnection(); connection.connectTimeout = 10_000; connection.readTimeout = 10_000
        val root = JSONObject(connection.getInputStream().bufferedReader().use { it.readText() })
        for (key in keys) if (root.has(key)) return root.getString(key).toFloat()
        error("Unexpected NOAA response")
    }

    private suspend fun publishCluster(value: String) = withContext(Dispatchers.Main) { clusterStatus = value }
}
