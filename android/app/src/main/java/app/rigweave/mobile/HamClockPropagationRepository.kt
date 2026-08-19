package app.rigweave.mobile

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.Instant

internal data class HamClockPropagationBand(
    val band: String,
    val frequencyMHz: Double,
    val reliability: Int,
    val snr: String,
    val status: String,
)

internal data class HamClockPropagationSnapshot(
    val model: String = "",
    val engine: String = "",
    val source: String = "",
    val mufMHz: Double? = null,
    val lufMHz: Double? = null,
    val distanceKm: Int? = null,
    val bands: List<HamClockPropagationBand> = emptyList(),
    val fetchedAt: Long = 0,
    val cached: Boolean = false,
    val error: String = "",
) {
    val available get() = bands.isNotEmpty()
    val authoritative get() = model.contains("P.533", true) || engine.contains("p533", true) || engine == "rest"
}

internal class HamClockPropagationRepository(context: Context) {
    private val cache = File(context.applicationContext.filesDir, "hamclock-propagation.json")

    suspend fun prediction(de: GeoPoint, dx: GeoPoint, mode: String, powerW: Int,
        antenna: String = "isotropic", force: Boolean = false): HamClockPropagationSnapshot = withContext(Dispatchers.IO) {
        val now = Instant.now().epochSecond
        val key = listOf("%.2f".format(de.latitude), "%.2f".format(de.longitude), "%.2f".format(dx.latitude),
            "%.2f".format(dx.longitude), mode.uppercase(), powerW.coerceIn(1, 1_500), antenna).joinToString("|")
        val previous = readCache()?.takeIf { it.first == key }?.second
        if (!force && previous != null && now - previous.fetchedAt < 10 * 60) return@withContext previous.copy(cached = true)
        runCatching {
            val query = linkedMapOf(
                "deLat" to de.latitude.toString(), "deLon" to de.longitude.toString(),
                "dxLat" to dx.latitude.toString(), "dxLon" to dx.longitude.toString(),
                "mode" to mode.uppercase(), "power" to powerW.coerceIn(1, 1_500).toString(), "antenna" to antenna,
            ).entries.joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, "UTF-8")}" }
            val connection = URL("https://openhamclock.com/api/propagation?$query").openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000; connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "RigWeave/0.1 Android OpenHamClock integration")
            connection.setRequestProperty("Accept", "application/json")
            try {
                require(connection.responseCode in 200..299) { "Propagation HTTP ${connection.responseCode}" }
                val raw = connection.inputStream.bufferedReader().use { it.readText() }
                parseHamClockPropagation(raw, now).also { parsed ->
                    require(parsed.available) { "Propagation response contained no bands" }
                    writeCache(key, raw, now)
                }
            } finally { connection.disconnect() }
        }.getOrElse { failure ->
            previous?.copy(cached = true, error = "Refresh failed · ${failure.message ?: "unavailable"}")
                ?: HamClockPropagationSnapshot(error = failure.message ?: "Propagation unavailable")
        }
    }

    private fun writeCache(key: String, raw: String, fetchedAt: Long) {
        val temp = File(cache.parentFile, "${cache.name}.tmp")
        temp.writeText(JSONObject().put("key", key).put("fetched", fetchedAt).put("payload", JSONObject(raw)).toString())
        if (!temp.renameTo(cache)) { cache.writeText(temp.readText()); temp.delete() }
    }

    private fun readCache(): Pair<String, HamClockPropagationSnapshot>? = runCatching {
        val root = JSONObject(cache.readText())
        root.getString("key") to parseHamClockPropagation(root.getJSONObject("payload").toString(), root.getLong("fetched"))
    }.getOrNull()
}

internal fun parseHamClockPropagation(raw: String, fetchedAt: Long): HamClockPropagationSnapshot {
    val root = JSONObject(raw)
    val rows = root.optJSONArray("currentBands")
    val bands = buildList {
        if (rows != null) for (index in 0 until rows.length()) rows.optJSONObject(index)?.let { row -> add(
            HamClockPropagationBand(row.optString("band"), row.optDouble("freq"), row.optInt("reliability"),
                row.optString("snr"), row.optString("status"))
        ) }
    }
    val itu = root.optJSONObject("iturhfprop")
    return HamClockPropagationSnapshot(
        model = root.optString("model"),
        engine = root.optString("engine").ifBlank { if (itu?.optBoolean("available") == true) "rest" else "heuristic" },
        source = root.optString("dataSource"),
        mufMHz = root.optionalDouble("muf"), lufMHz = root.optionalDouble("luf"),
        distanceKm = root.optInt("distance").takeIf { it > 0 }, bands = bands, fetchedAt = fetchedAt,
    )
}

private fun JSONObject.optionalDouble(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key).takeIf(Double::isFinite) else null
