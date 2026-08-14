package app.rigweave.mobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AndroidWavelogStation(val id: String, val name: String, val callsign: String, val grid: String, val active: Boolean) {
    val label get() = listOf(name, callsign, grid).filter { it.isNotBlank() }.joinToString(" · ")
}
data class AndroidWavelogContact(val id: String, val callsign: String, val band: String, val mode: String,
    val submode: String, val country: String, val date: String, val time: String, val frequency: String)

class WavelogController(private val context: Context) {
    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("wavelog", Context.MODE_PRIVATE)
    private val queueFile = File(context.filesDir, "wavelog-queue.json")
    private val contactsFile = File(context.filesDir, "wavelog-contacts.json")
    var baseURL by mutableStateOf(prefs.getString("base_url", "") ?: ""); private set
    var stationId by mutableStateOf(prefs.getString("station_id", "") ?: ""); private set
    var apiKey by mutableStateOf(decrypt(prefs.getString("api_key", "") ?: "")); private set
    var status by mutableStateOf("Wavelog not configured"); private set
    var stations by mutableStateOf(emptyList<AndroidWavelogStation>()); private set
    var contacts by mutableStateOf(loadContacts()); private set
    var pendingCount by mutableStateOf(loadQueue().length()); private set
    var syncPages by mutableStateOf(0); private set

    fun updateBaseURL(value: String) { baseURL = value; prefs.edit().putString("base_url", value).apply() }
    fun setStation(value: String) { stationId = value; prefs.edit().putString("station_id", value).apply() }
    fun updateApiKey(value: String) { apiKey = value; prefs.edit().putString("api_key", encrypt(value)).apply() }

    fun enqueue(id: String, adif: String) {
        val queue = loadQueue()
        if ((0 until queue.length()).any { queue.getJSONObject(it).optString("id") == id }) return
        queue.put(JSONObject().put("id", id).put("adif", adif)); queueFile.writeText(queue.toString())
        pendingCount = queue.length(); syncQueue()
    }

    fun loadStations() = scope.launch {
        if (baseURL.isBlank() || apiKey.isBlank()) { publish("Wavelog URL and API key are required"); return@launch }
        try {
            val rows = JSONArray(request("station_info/" + encodePath(apiKey), "GET", null))
            val loaded = buildList {
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index); val id = row.optString("station_id")
                    if (id.toLongOrNull() != null) add(AndroidWavelogStation(id, row.optString("station_profile_name"),
                        row.optString("station_callsign"), row.optString("station_gridsquare"), truth(row.opt("station_active"))))
                }
            }
            withContext(Dispatchers.Main) {
                stations = loaded
                if (stationId.isBlank()) loaded.firstOrNull { it.active }?.let { setStation(it.id) }
                status = if (loaded.isEmpty()) "No Wavelog stations available" else loaded.size.toString() + " Wavelog stations loaded"
            }
        } catch (error: Exception) { publish("Load stations failed: " + error.message) }
    }

    fun syncQueue() = scope.launch {
        if (baseURL.isBlank() || apiKey.isBlank() || stationId.toLongOrNull() == null) {
            publish("Wavelog credentials required; local QSOs remain queued"); return@launch
        }
        val queue = loadQueue(); val remaining = JSONArray()
        for (index in 0 until queue.length()) {
            val item = queue.getJSONObject(index)
            try {
                val payload = JSONObject().put("key", apiKey).put("station_profile_id", stationId.toLong())
                    .put("type", "adif").put("string", item.getString("adif"))
                request("qso", "POST", payload)
            } catch (_: Exception) { remaining.put(item) }
        }
        queueFile.writeText(remaining.toString())
        withContext(Dispatchers.Main) {
            pendingCount = remaining.length()
            status = if (pendingCount == 0) "Wavelog queue synchronized" else pendingCount.toString() + " QSOs remain safely queued"
        }
    }

    fun fullSync() = scope.launch {
        val station = stationId.toLongOrNull()
        if (baseURL.isBlank() || apiKey.isBlank() || station == null) {
            publish("Wavelog URL, API key and station are required"); return@launch
        }
        var cursor = 0L; var pages = 0; val loaded = mutableListOf<AndroidWavelogContact>()
        try {
            for (page in 0 until 256) {
                val payload = JSONObject().put("key", apiKey).put("station_id", station)
                    .put("fetchfromid", cursor).put("output_format", "json").put("fields", JSONArray(contactFields))
                val root = JSONObject(request("get_contacts_adif", "POST", payload))
                val exported = integer(root, setOf("exported_qsos", "exported_records", "exportedRecords"))
                    ?: error("Missing exported QSO count")
                val next = integer(root, setOf("lastfetchedid", "lastFetchedId", "last_fetched_id"))
                    ?: error("Missing pagination cursor")
                if (exported == 0L) break
                if (next <= cursor) error("Invalid Wavelog cursor")
                loaded += contactRows(root); cursor = next; pages = page + 1
                withContext(Dispatchers.Main) {
                    syncPages = pages; status = "Full sync page " + pages + " · " + loaded.size + " QSOs"
                }
            }
            val unique = loaded.distinctBy { it.id }
            contactsFile.writeText(JSONArray(unique.map(::contactJson)).toString())
            withContext(Dispatchers.Main) {
                contacts = unique; status = "Full Wavelog sync complete · " + unique.size + " QSOs"
            }
        } catch (error: Exception) { publish("Full Wavelog sync failed: " + error.message) }
    }

    fun close() = scope.cancel()

    private fun request(resource: String, method: String, payload: JSONObject?): String {
        val connection = URL(apiRoot() + "/" + resource).openConnection() as HttpURLConnection
        connection.requestMethod = method; connection.connectTimeout = 15_000; connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/json")
        if (payload != null) {
            connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(payload.toString().toByteArray()) }
        }
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("HTTP " + code)
        return text
    }

    private fun apiRoot(): String {
        var value = baseURL.trim().replace(Regex("/+$"), "")
        if (!value.contains("://")) value = "https://" + value
        if (value.startsWith("http://")) value = "https://" + value.removePrefix("http://")
        if (!value.endsWith("/index.php/api")) value += "/index.php/api"
        return value
    }

    private suspend fun publish(value: String) = withContext(Dispatchers.Main) { status = value }
    private fun loadQueue() = runCatching { JSONArray(queueFile.readText()) }.getOrElse { JSONArray() }
    private fun loadContacts() = runCatching {
        val rows = JSONArray(contactsFile.readText())
        List(rows.length()) { contact(rows.getJSONObject(it)) }
    }.getOrElse { emptyList() }

    private fun contactRows(node: Any): List<AndroidWavelogContact> {
        val output = mutableListOf<AndroidWavelogContact>()
        fun visit(value: Any?) {
            when (value) {
                is JSONArray -> {
                    val objects = (0 until value.length()).mapNotNull { value.opt(it) as? JSONObject }
                    if (objects.any { row -> row.keys().asSequence().any { it.equals("CALL", true) } })
                        output += objects.mapNotNull { runCatching { contact(it) }.getOrNull() }
                    else (0 until value.length()).forEach { visit(value.opt(it)) }
                }
                is JSONObject -> value.keys().forEach { visit(value.opt(it)) }
            }
        }
        visit(node); return output
    }

    private fun contact(row: JSONObject): AndroidWavelogContact {
        fun field(name: String): String {
            val key = row.keys().asSequence().firstOrNull { it.equals(name, true) } ?: return ""
            return row.optString(key)
        }
        val call = field("CALL").uppercase(); require(call.isNotBlank())
        val id = field("COL_PRIMARY_KEY").ifBlank {
            listOf(call, field("QSO_DATE"), field("TIME_ON"), field("BAND"), field("MODE")).joinToString("-")
        }
        return AndroidWavelogContact(id, call, field("BAND"), field("MODE"), field("SUBMODE"),
            field("COUNTRY"), field("QSO_DATE"), field("TIME_ON"), field("FREQ"))
    }

    private fun contactJson(row: AndroidWavelogContact) = JSONObject().put("id", row.id).put("callsign", row.callsign)
        .put("band", row.band).put("mode", row.mode).put("submode", row.submode).put("country", row.country)
        .put("date", row.date).put("time", row.time).put("frequency", row.frequency)

    private fun integer(node: Any?, keys: Set<String>): Long? {
        when (node) {
            is JSONObject -> {
                node.keys().forEach { key ->
                    if (key in keys) return node.optString(key).toLongOrNull()
                    integer(node.opt(key), keys)?.let { return it }
                }
            }
            is JSONArray -> for (index in 0 until node.length()) integer(node.opt(index), keys)?.let { return it }
        }
        return null
    }

    private fun truth(value: Any?) = when (value) {
        is Boolean -> value; is Number -> value.toInt() != 0; else -> value.toString().lowercase() in listOf("1", "true")
    }
    private fun encodePath(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, secret())
            Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
        }.getOrDefault("")
    }

    private fun decrypt(value: String): String {
        if (value.isEmpty()) return ""
        return runCatching {
            val bytes = Base64.decode(value, Base64.NO_WRAP); val iv = bytes.copyOfRange(0, 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
        }.getOrDefault("")
    }

    companion object {
        private const val keyAlias = "app.rigweave.mobile.wavelog"
        private val contactFields = listOf("CALL", "NAME", "BAND", "MODE", "SUBMODE", "DXCC", "COUNTRY",
            "QSO_DATE", "TIME_ON", "FREQ", "RST_SENT", "RST_RCVD", "GRIDSQUARE", "STATION_CALLSIGN",
            "MY_GRIDSQUARE", "COMMENT")
    }
}
