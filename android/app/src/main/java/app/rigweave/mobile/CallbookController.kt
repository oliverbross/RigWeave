package app.rigweave.mobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.io.ByteArrayInputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Node

data class AndroidCallbookRecord(
    val callsign: String, val name: String, val qth: String, val country: String, val grid: String,
    val dxcc: String, val continent: String, val region: String, val cqZone: String, val ituZone: String,
    val state: String, val email: String, val latitude: String, val longitude: String,
)

class CallbookController(context: Context) {
    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("callbook", Context.MODE_PRIVATE)
    var provider by mutableStateOf(prefs.getString("provider", "QRZ") ?: "QRZ"); private set
    var username by mutableStateOf(prefs.getString("username", "") ?: ""); private set
    var password by mutableStateOf(decrypt(prefs.getString("password", "") ?: "")); private set
    var status by mutableStateOf("Callbook not tested"); private set
    private var session = ""

    fun configure(provider: String, username: String, password: String) {
        this.provider = if (provider == "HamQTH") "HamQTH" else "QRZ"
        this.username = username.trim(); this.password = password; session = ""
        prefs.edit().putString("provider", this.provider).putString("username", this.username)
            .putString("password", encrypt(password)).apply()
    }

    fun test() = scope.launch {
        try { session = login(); publish("$provider connection passed") }
        catch (error: Exception) { publish("$provider test failed: ${error.message}") }
    }

    fun lookup(callsign: String, completion: (AndroidCallbookRecord?) -> Unit) = scope.launch {
        try {
            if (session.isBlank()) session = login()
            val call = callsign.trim().uppercase(); val fields = request(call)
            val continent = (fields["continent"] ?: fields["cont"] ?: "").uppercase()
            val record = AndroidCallbookRecord(fields["call"] ?: fields["callsign"] ?: call,
                listOfNotNull(fields["fname"], fields["name"], fields["nick"]).joinToString(" ").trim(),
                fields["addr2"] ?: fields["qth"] ?: "", fields["country"] ?: fields["land"] ?: "",
                fields["grid"] ?: "", fields["dxcc"] ?: fields["adif"] ?: "", continent,
                continentName(continent), fields["cqzone"] ?: fields["cq"] ?: "",
                fields["ituzone"] ?: fields["itu"] ?: "", fields["state"] ?: "", fields["email"] ?: "",
                fields["lat"] ?: "", fields["lon"] ?: "")
            withContext(Dispatchers.Main) { status = "Live $provider result"; completion(record) }
        } catch (error: Exception) { withContext(Dispatchers.Main) { status = error.message ?: "Callbook lookup failed"; completion(null) } }
    }

    fun close() = scope.cancel()

    private fun login(): String {
        require(username.isNotBlank() && password.isNotBlank()) { "$provider username and password required" }
        val fields = if (provider == "HamQTH") xml("https://www.hamqth.com/xml.php?u=${encode(username)}&p=${encode(password)}")
            else xml("https://xmldata.qrz.com/xml/current/?username=${encode(username)}&password=${encode(password)}&agent=RigWeave-0.1")
        fields["error"]?.let { error(it) }
        return fields[if (provider == "HamQTH") "session_id" else "key"]?.takeIf(String::isNotBlank)
            ?: error("Callbook login returned no session")
    }

    private fun request(call: String) = if (provider == "HamQTH")
        xml("https://www.hamqth.com/xml.php?id=${encode(session)}&callsign=${encode(call)}&prg=RigWeave")
    else xml("https://xmldata.qrz.com/xml/current/?s=${encode(session)}&callsign=${encode(call)}")

    private fun xml(value: String): Map<String, String> {
        val connection = URL(value).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000; connection.readTimeout = 20_000
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(text.toByteArray()))
        val fields = linkedMapOf<String, String>()
        fun collect(node: Node) {
            val children = (0 until node.childNodes.length).map(node.childNodes::item)
            val elements = children.filter { it.nodeType == Node.ELEMENT_NODE }
            if (elements.isEmpty() && node.nodeType == Node.ELEMENT_NODE) {
                fields[(node.localName ?: node.nodeName).lowercase()] = node.textContent.trim()
            } else {
                elements.forEach(::collect)
            }
        }
        collect(document.documentElement)
        return fields
    }

    private suspend fun publish(value: String) = withContext(Dispatchers.Main) { status = value }
    private fun continentName(code: String) = mapOf("AF" to "Africa", "AN" to "Antarctica", "AS" to "Asia",
        "EU" to "Europe", "NA" to "North America", "OC" to "Oceania", "SA" to "South America")[code].orEmpty()
    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(value: String): String = if (value.isEmpty()) "" else runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, secret())
        Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }.getOrDefault("")
    private fun decrypt(value: String): String = if (value.isEmpty()) "" else runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP); val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
    }.getOrDefault("")
    companion object { private const val keyAlias = "app.rigweave.mobile.callbook" }
}
