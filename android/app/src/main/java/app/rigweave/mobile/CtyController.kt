package app.rigweave.mobile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AndroidCtyRecord(
    val country: String = "", val dxcc: String = "", val continent: String = "",
    val region: String = "", val cqZone: String = "", val ituZone: String = "",
)

private data class CtyPrefix(val value: String, val exact: Boolean, val entity: AndroidCtyRecord)

class CtyController(context: Context) {
    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private val datFile = File(context.filesDir, "cty.dat")
    private val csvFile = File(context.filesDir, "cty.csv")
    private var prefixes = emptyList<CtyPrefix>()
    var status by mutableStateOf("CTY.DAT not installed"); private set

    init { load() }

    fun update() = scope.launch {
        publish("Downloading CTY.DAT entity data…")
        try {
            val dat = download("https://www.country-files.com/cty/cty.dat")
            val csv = download("https://www.country-files.com/cty/cty.csv")
            require(dat.size > 50_000 && csv.size > 70_000)
            require(dat.toString(Charsets.UTF_8).contains(';') && csv.toString(Charsets.UTF_8).contains(','))
            commit(datFile, dat); commit(csvFile, csv)
            load(); publish("CTY.DAT ready · ${prefixes.size} prefixes with DXCC/zones")
        } catch (error: Exception) { publish("CTY.DAT update failed: ${error.message}") }
    }

    fun lookup(callsign: String): AndroidCtyRecord? {
        val call = callsign.trim().uppercase()
        if (call.isBlank()) return null
        return prefixes.firstOrNull { if (it.exact) call == it.value else call.startsWith(it.value) }?.entity
    }

    fun country(callsign: String): String = lookup(callsign)?.country.orEmpty()
    fun close() = scope.cancel()

    private fun load() {
        prefixes = when {
            csvFile.exists() -> parseCsv(csvFile.readText())
            datFile.exists() -> parseDat(datFile.readText())
            else -> emptyList()
        }.sortedWith(compareByDescending<CtyPrefix> { it.exact }.thenByDescending { it.value.length })
        if (prefixes.isNotEmpty()) status = "CTY.DAT ready · ${prefixes.size} prefixes"
    }

    private fun parseCsv(text: String): List<CtyPrefix> = text.lineSequence().flatMap { line ->
        val fields = line.split(',', limit = 10)
        if (fields.size != 10) return@flatMap emptySequence()
        val continent = fields[3].trim().uppercase()
        val entity = AndroidCtyRecord(fields[1].trim(), fields[2].trim(), continent,
            continentName(continent), fields[4].trim(), fields[5].trim())
        val primary = fields[0].trim()
        sequenceOf(primary).plus(fields[9].removeSuffix(";").trim().split(Regex("\\s+")).asSequence())
            .mapNotNull { prefix(it, entity) }
    }.toList()

    private fun parseDat(text: String): List<CtyPrefix> = text.split(';').flatMap { record ->
        val fields = record.split(':', limit = 9)
        if (fields.size != 9) emptyList() else {
            val continent = fields[3].trim().uppercase()
            val entity = AndroidCtyRecord(fields[0].trim(), "", continent, continentName(continent),
                fields[1].trim(), fields[2].trim())
            (listOf(fields[7]) + fields[8].split(',')).mapNotNull { prefix(it, entity) }
        }
    }

    private fun prefix(raw: String, entity: AndroidCtyRecord): CtyPrefix? {
        val trimmed = raw.trim().uppercase(); val exact = trimmed.startsWith('=')
        val value = trimmed.removePrefix("=").substringBeforeAny("(", "[", "<", "{", "~")
            .removePrefix("*").trim()
        return value.takeIf(String::isNotBlank)?.let { CtyPrefix(it, exact, entity) }
    }

    private fun download(value: String): ByteArray {
        val connection = URL(value).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000; connection.readTimeout = 30_000
        val data = connection.inputStream.use { it.readBytes() }
        require(connection.responseCode in 200..299)
        return data
    }

    private fun commit(destination: File, data: ByteArray) {
        val temporary = File(destination.parentFile, destination.name + ".tmp")
        val previous = File(destination.parentFile, destination.name + ".previous")
        temporary.writeBytes(data); previous.delete()
        if (destination.exists()) require(destination.renameTo(previous))
        if (!temporary.renameTo(destination)) { previous.renameTo(destination); error("${destination.name} commit failed") }
        previous.delete()
    }

    private suspend fun publish(value: String) = withContext(Dispatchers.Main) { status = value }
    private fun String.substringBeforeAny(vararg markers: String): String = markers.fold(this) { value, marker -> value.substringBefore(marker) }
    private fun continentName(code: String) = mapOf("AF" to "Africa", "AN" to "Antarctica", "AS" to "Asia",
        "EU" to "Europe", "NA" to "North America", "OC" to "Oceania", "SA" to "South America")[code].orEmpty()
}
