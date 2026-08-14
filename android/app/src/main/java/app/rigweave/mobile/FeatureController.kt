package app.rigweave.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.time.Instant

data class AndroidDXSpot(
    val id: String, val callsign: String, val spotter: String, val frequencyHz: Long,
    val band: String, val mode: String, val country: String, val comment: String, val watchlisted: Boolean,
)

data class AndroidDXBand(val band: String, val spots5m: Int, val spots60m: Int, val uniqueCalls: Int,
    val surgePercent: Int, val surge: Boolean)
data class AndroidDXRegion(val region: String, val spots15m: Int, val spots60m: Int,
    val uniqueCalls: Int, val activityPercent: Int, val anomaly: Boolean)

class FeatureController(private val context: Context) {
    private val handle = NativeCore.featureCreate()
    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private var clusterSocket: Socket? = null
    private var udpSocket: DatagramSocket? = null
    private var audioRecord: AudioRecord? = null

    var clusterStatus by mutableStateOf("DX cluster disconnected"); private set
    var spots by mutableStateOf(emptyList<AndroidDXSpot>()); private set
    var liveSpots by mutableStateOf(emptyList<AndroidDXSpot>()); private set
    var watchSpots by mutableStateOf(emptyList<AndroidDXSpot>()); private set
    var dxBands by mutableStateOf(emptyList<AndroidDXBand>()); private set
    var dxRegions by mutableStateOf(emptyList<AndroidDXRegion>()); private set
    var dxTimeline by mutableStateOf(emptyList<List<Int>>()); private set
    var dxWorld by mutableStateOf(emptyList<List<Int>>()); private set
    var dxSummary by mutableStateOf("No live DX data"); private set
    var wsjtxStatus by mutableStateOf("WSJT-X listener stopped"); private set
    var wsjtxMessage by mutableStateOf("No WSJT-X datagram received"); private set
    var audioStatus by mutableStateOf("No physical audio capture"); private set
    var spectrum by mutableStateOf(FloatArray(0)); private set
    var waterfall by mutableStateOf(emptyList<FloatArray>()); private set
    var noiseFloor by mutableStateOf(-120f); private set
    var panRangeDb by mutableStateOf(72f)
    var panFloorOffsetDb by mutableStateOf(-6f)
    var panPalette by mutableStateOf(0)
    private var noiseFloorSeeded = false

    fun setWatchlist(value: String) { NativeCore.featureWatchlist(handle, value) }

    fun connectCluster(host: String, port: Int, callsign: String) {
        disconnectCluster(); clusterStatus = "Connecting to $host:$port…"
        scope.launch {
            try {
                val socket = Socket(); socket.connect(InetSocketAddress(host, port), 12_000); clusterSocket = socket
                if (callsign.isNotBlank()) { socket.getOutputStream().write((callsign.uppercase() + "\r\n").toByteArray()); socket.getOutputStream().flush() }
                publishCluster("Connected to $host:$port")
                BufferedReader(InputStreamReader(socket.getInputStream())).useLines { lines ->
                    lines.forEach { line ->
                        if (NativeCore.featureClusterLine(handle, line, Instant.now().epochSecond)) refreshDX()
                    }
                }
                publishCluster("Cluster closed connection")
            } catch (error: Exception) { publishCluster("Cluster failed: ${error.message ?: error.javaClass.simpleName}") }
        }
    }

    fun disconnectCluster() { clusterSocket?.close(); clusterSocket = null; clusterStatus = "DX cluster disconnected" }

    fun refreshSolar() {
        scope.launch {
            try {
                val flux = summaryValue("https://services.swpc.noaa.gov/products/summary/10cm-flux.json", listOf("Flux", "flux"))
                val kp = summaryValue("https://services.swpc.noaa.gov/products/summary/planetary-k-index.json", listOf("Kp", "kp_index", "KpIndex"))
                NativeCore.featureSolar(handle, flux, 0f, kp, Instant.now().epochSecond); refreshDX()
            } catch (error: Exception) { publishCluster("NOAA solar update failed: ${error.message}") }
        }
    }

    fun startWSJTX(port: Int) {
        stopWSJTX(); scope.launch {
            try {
                val socket = DatagramSocket(port); socket.broadcast = true; udpSocket = socket
                publishWSJTX("Listening for WSJT-X UDP on $port")
                val bytes = ByteArray(65_507)
                while (!socket.isClosed) {
                    val packet = DatagramPacket(bytes, bytes.size); socket.receive(packet)
                    val payload = bytes.copyOf(packet.length); val json = NativeCore.featureWsjtx(payload)
                    withContext(Dispatchers.Main) { wsjtxMessage = json }
                }
            } catch (error: Exception) { if (udpSocket != null) publishWSJTX("WSJT-X failed: ${error.message}") }
        }
    }

    fun stopWSJTX() { udpSocket?.close(); udpSocket = null; wsjtxStatus = "WSJT-X listener stopped" }

    fun startAudio() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioStatus = "Microphone / USB audio permission required"; return
        }
        stopAudio()
        val rate = 48_000; val channelMask = AudioFormat.CHANNEL_IN_STEREO; val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minimum = AudioRecord.getMinBufferSize(rate, channelMask, encoding)
        if (minimum <= 0) { audioStatus = "No supported physical stereo input"; return }
        val record = AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, rate, channelMask, encoding, minimum * 2)
        val usb = record.routedDevice ?: context.getSystemService(android.media.AudioManager::class.java)
            .getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
        if (usb != null) record.preferredDevice = usb
        if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); audioStatus = "Physical audio input could not initialize"; return }
        audioRecord = record; record.startRecording(); audioStatus = "Capturing ${usb?.productName ?: "physical input"} · $rate Hz"
        scope.launch {
            val buffer = ByteArray(minimum)
            while (audioRecord === record && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val count = record.read(buffer, 0, buffer.size)
                if (count > 0) {
                    val bins = NativeCore.featurePanadapter(handle, buffer.copyOf(count), 2, 2, 16)
                    if (bins.isNotEmpty()) {
                        val centre = bins.size / 2
                        val usable = bins.filterIndexed { index, value -> kotlin.math.abs(index - centre) > 4 && value.isFinite() }
                        val mean = usable.average().toFloat()
                        val quiet = usable.filter { it <= mean }
                        val measured = (if (quiet.isEmpty()) mean.toDouble() else quiet.average()).toFloat()
                        val row = FloatArray(256) { column ->
                            val start = column * bins.size / 256
                            val end = maxOf(start + 1, (column + 1) * bins.size / 256)
                            var peak = measured
                            for (index in start until minOf(end, bins.size)) peak = maxOf(peak, bins[index])
                            peak
                        }
                        withContext(Dispatchers.Main) {
                            noiseFloor = if (noiseFloorSeeded) noiseFloor + 0.1f * (measured - noiseFloor) else measured
                            noiseFloorSeeded = true; spectrum = bins
                            waterfall = (listOf(row) + waterfall).take(140)
                        }
                    }
                }
            }
        }
    }

    fun stopAudio() {
        audioRecord?.let { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop(); it.release() }
        audioRecord = null; spectrum = FloatArray(0); waterfall = emptyList(); noiseFloorSeeded = false
        audioStatus = "Audio capture stopped"
    }

    fun close() {
        disconnectCluster(); stopWSJTX(); stopAudio(); scope.cancel(); NativeCore.featureDestroy(handle)
    }

    private suspend fun refreshDX() {
        val json = NativeCore.featureDxSnapshot(handle, Instant.now().epochSecond)
        val root = JSONObject(json)
        fun loadSpots(name: String) = buildList {
            val rows = root.optJSONArray(name)
            if (rows != null) for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index); val frequency = row.optLong("frequencyHz")
                add(AndroidDXSpot("${row.optString("callsign")}-$frequency-${row.optLong("receivedEpoch")}",
                    row.optString("callsign"), row.optString("spotter"), frequency, row.optString("band"),
                    row.optString("mode"), row.optString("country"), row.optString("comment"), row.optBoolean("watchlisted")))
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
    private suspend fun publishWSJTX(value: String) = withContext(Dispatchers.Main) { wsjtxStatus = value }
}
