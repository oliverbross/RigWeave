package app.rigweave.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class WavelogV2Request(
    val method: String,
    val url: String,
    val bearerToken: String,
    val body: String? = null,
    val idempotencyKey: String? = null,
)

data class WavelogV2Response(val status: Int, val body: String, val retryAfter: String? = null)

fun interface WavelogV2Transport { fun execute(request: WavelogV2Request): WavelogV2Response }

class DefaultWavelogV2Transport : WavelogV2Transport {
    override fun execute(request: WavelogV2Request): WavelogV2Response {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        connection.requestMethod = request.method
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", "Bearer ${request.bearerToken}")
        request.idempotencyKey?.let { connection.setRequestProperty("Idempotency-Key", it) }
        request.body?.let { body ->
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val status = connection.responseCode
        val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        return WavelogV2Response(status, text, connection.getHeaderField("Retry-After"))
    }
}

data class WavelogTokenMetadata(
    val id: Long,
    val name: String,
    val owner: String,
    val userId: Long,
    val scopes: Set<String>,
    val expiresAt: String,
) {
    val capabilities = WavelogCapabilities(
        scopes = scopes,
        canReadQsos = "qso:read" in scopes,
        canWriteQsos = "qso:write" in scopes,
        canDeleteQsos = "qso:delete" in scopes,
        canReadStations = "station:read" in scopes,
        canReadStatistics = "statistic:read" in scopes,
    )
}

data class WavelogV2Station(
    val id: String,
    val uuid: String,
    val name: String,
    val callsign: String,
    val grid: String,
    val active: Boolean,
)

data class WavelogV2Page(
    val rows: List<JSONObject>,
    val page: Int,
    val totalPages: Int,
    val total: Int,
    val hasMore: Boolean,
)

class WavelogApiException(
    val errorClass: WavelogErrorClass,
    val status: Int,
    val code: String,
    val retryAfterSeconds: Long?,
    message: String,
) : Exception(message)

class WavelogApiV2Client(
    baseUrl: String,
    private val token: String,
    private val transport: WavelogV2Transport = DefaultWavelogV2Transport(),
) {
    val apiRoot = normalizeWavelogV2Root(baseUrl)

    init { require(token.startsWith("wl2_")) { "API v2 requires a wl2_ token" } }

    fun tokenMetadata(): WavelogTokenMetadata {
        val data = request("GET", "token").getJSONObject("data")
        val scopes = data.optJSONArray("scopes").jsonStringList().toSet()
        return WavelogTokenMetadata(
            id = data.optLong("id"), name = data.optString("name"), owner = data.optString("owner"),
            userId = data.optLong("user_id"), scopes = scopes, expiresAt = data.optString("expires_at"),
        )
    }

    fun stations(): List<WavelogV2Station> {
        val rows = request("GET", "station").optJSONArray("data") ?: JSONArray()
        return buildList {
            for (index in 0 until rows.length()) rows.optJSONObject(index)?.let { row ->
                add(WavelogV2Station(
                    id = first(row, "id", "station_id"), uuid = first(row, "uuid", "station_uuid"),
                    name = first(row, "name", "station_profile_name"), callsign = first(row, "callsign", "station_callsign"),
                    grid = first(row, "grid", "gridsquare", "station_gridsquare"),
                    active = truth(row.opt("active")) || truth(row.opt("station_active")),
                ))
            }
        }
    }

    fun qsoPage(stationId: String, page: Int, perPage: Int = 250, sinceId: String = "0"): WavelogV2Page {
        require(page >= 1)
        val query = "qso?station_id=${encode(stationId)}&page=$page&per_page=${perPage.coerceIn(1, 5000)}&since_id=${encode(sinceId)}"
        val root = request("GET", query)
        val data = root.optJSONArray("data") ?: JSONArray()
        val meta = root.optJSONObject("meta") ?: JSONObject()
        return WavelogV2Page(
            rows = buildList { for (index in 0 until data.length()) data.optJSONObject(index)?.let(::add) },
            page = meta.optInt("page", page), totalPages = meta.optInt("total_pages", page),
            total = meta.optInt("total"), hasMore = meta.optBoolean("has_more"),
        )
    }

    fun createAdif(stationId: String, adif: String, idempotencyKey: String): JSONObject = request(
        "POST", "qso", JSONObject().put("station_profile_id", stationId.toLong())
            .put("import_type", "adif").put("adif", adif).toString(), idempotencyKey,
    )

    fun patchQso(remoteId: String, fields: Map<String, String>, idempotencyKey: String): JSONObject =
        request("PATCH", "qso/${encode(remoteId)}", JSONObject(fields).toString(), idempotencyKey)

    fun deleteQso(remoteId: String) {
        request("DELETE", "qso/${encode(remoteId)}", idempotencyKey = null)
    }

    private fun request(method: String, resource: String, body: String? = null, idempotencyKey: String? = null): JSONObject {
        val response = try {
            transport.execute(WavelogV2Request(method, "$apiRoot/$resource", token, body, idempotencyKey))
        } catch (error: Exception) {
            throw WavelogApiException(WavelogErrorClass.TEMPORARY, 0, "network_error", null,
                error.message?.take(300) ?: "Network request failed")
        }
        if (response.status == 204) return JSONObject()
        val root = runCatching { JSONObject(response.body) }.getOrElse {
            throw WavelogApiException(WavelogErrorClass.MALFORMED_RESPONSE, response.status, "malformed_response", null,
                "Wavelog returned an unexpected response")
        }
        if (response.status !in 200..299) {
            val error = root.optJSONObject("error") ?: JSONObject()
            val code = error.optString("code", "http_${response.status}")
            val safeMessage = error.optString("message", "Wavelog request failed").take(300)
            throw WavelogApiException(classify(response.status, code), response.status, code,
                parseRetryAfter(response.retryAfter), safeMessage)
        }
        return root
    }

    private fun classify(status: Int, code: String): WavelogErrorClass = when {
        code == "token_expired" -> WavelogErrorClass.EXPIRED_TOKEN
        status == 401 -> WavelogErrorClass.AUTHENTICATION
        status == 403 -> WavelogErrorClass.MISSING_SCOPE
        status == 404 -> WavelogErrorClass.NOT_FOUND
        status == 409 -> WavelogErrorClass.CONFLICT
        status == 429 -> WavelogErrorClass.RATE_LIMIT
        status in 400..499 -> WavelogErrorClass.VALIDATION
        else -> WavelogErrorClass.TEMPORARY
    }

    private fun first(row: JSONObject, vararg keys: String) = keys.firstNotNullOfOrNull { key ->
        row.optString(key).takeIf { it.isNotBlank() && !it.equals("null", true) }
    }.orEmpty()
    private fun truth(value: Any?) = when (value) { is Boolean -> value; is Number -> value.toInt() != 0; else -> value.toString() in setOf("1", "true") }
    private fun encode(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    private fun parseRetryAfter(value: String?) = value?.trim()?.toLongOrNull()?.coerceAtLeast(0)
}

fun normalizeWavelogV2Root(raw: String): String {
    var value = raw.trim().trimEnd('/')
    if (!value.contains("://")) value = "https://$value"
    val uri = runCatching { URI(value) }.getOrElse { throw IllegalArgumentException("Invalid Wavelog URL") }
    require(uri.scheme.equals("https", true)) { "Wavelog API v2 requires HTTPS" }
    require(!uri.host.isNullOrBlank()) { "Invalid Wavelog host" }
    val clean = URI("https", uri.userInfo, uri.host, uri.port, uri.path, null, null).toString().trimEnd('/')
    return when {
        clean.endsWith("/index.php/api/v2") || clean.endsWith("/api/v2") -> clean
        clean.endsWith("/index.php") -> "$clean/api/v2"
        else -> "$clean/index.php/api/v2"
    }
}
