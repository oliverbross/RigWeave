package app.rigweave.mobile.hamclock

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool

internal data class HamClockHttpResponse(
    val body: String,
    val etag: String = "",
    val lastModified: String = "",
)

internal fun interface HamClockHttpClient {
    fun get(request: HamClockHttpRequest): HamClockHttpResponse
}

internal data class HamClockHttpRequest(
    val url: String,
    val accept: String,
    val maxBytes: Int,
    val etag: String = "",
    val lastModified: String = "",
)

internal class HamClockUrlConnectionClient : HamClockHttpClient {
    override fun get(request: HamClockHttpRequest): HamClockHttpResponse {
        require(request.url.startsWith("https://")) { "Only HTTPS public providers are allowed" }
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "RigWeave-Android/0.1 OpenHamClock-integration")
            connection.setRequestProperty("Accept", request.accept)
            if (request.etag.isNotBlank()) connection.setRequestProperty("If-None-Match", request.etag)
            if (request.lastModified.isNotBlank()) connection.setRequestProperty("If-Modified-Since", request.lastModified)
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) throw HamClockNotModified()
            if (code !in 200..299) throw IllegalStateException("Provider returned HTTP $code")
            if (connection.contentLengthLong > request.maxBytes) throw IllegalStateException("Provider response is too large")
            val bytes = connection.inputStream.use { it.readNBytes(request.maxBytes + 1) }
            if (bytes.size > request.maxBytes) throw IllegalStateException("Provider response is too large")
            return HamClockHttpResponse(
                body = bytes.toString(Charsets.UTF_8),
                etag = connection.getHeaderField("ETag").orEmpty(),
                lastModified = connection.getHeaderField("Last-Modified").orEmpty(),
            )
        } finally {
            connection.disconnect()
        }
    }
}

internal class HamClockNotModified : Exception()

internal data class HamClockCacheEntry(
    val body: String,
    val fetchedAtEpoch: Long,
    val etag: String,
    val lastModified: String,
)

/** Small atomic disk cache. Invalid downloads never replace the last good payload. */
internal class HamClockLastGoodCache(directory: File, name: String) {
    private val data = File(directory.apply(File::mkdirs), "$name.json")
    private val metadata = File(directory, "$name.meta")

    @Synchronized fun read(): HamClockCacheEntry? = runCatching {
        if (!data.isFile) return null
        val lines = metadata.takeIf(File::isFile)?.readLines().orEmpty()
        HamClockCacheEntry(
            body = data.readText(Charsets.UTF_8),
            fetchedAtEpoch = lines.getOrNull(0)?.toLongOrNull() ?: data.lastModified() / 1000,
            etag = lines.getOrNull(1).orEmpty(),
            lastModified = lines.getOrNull(2).orEmpty(),
        )
    }.getOrNull()

    @Synchronized fun write(body: String, fetchedAtEpoch: Long, etag: String, lastModified: String) {
        writeAtomic(data, body)
        writeAtomic(metadata, listOf(fetchedAtEpoch, etag.replace('\n', ' '), lastModified.replace('\n', ' ')).joinToString("\n"))
    }

    private fun writeAtomic(destination: File, body: String) {
        val part = File(destination.parentFile, destination.name + ".part")
        part.writeText(body, Charsets.UTF_8)
        if (!part.renameTo(destination)) {
            destination.writeText(body, Charsets.UTF_8)
            part.delete()
        }
    }
}

internal fun providerError(error: Throwable): String = when (error) {
    is java.net.SocketTimeoutException -> "Timed out"
    is UnknownHostException -> "Offline"
    else -> error.message?.take(120)?.ifBlank { "Unavailable" } ?: "Unavailable"
}

/** Shares one active provider request without retaining completed results. */
internal class HamClockInFlightCoalescer(
    private val executor: Executor = ForkJoinPool.commonPool(),
) {
    private val active = ConcurrentHashMap<String, CompletableFuture<Any?>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> run(key: String, block: () -> T): T {
        val candidate = CompletableFuture<Any?>()
        val shared = active.putIfAbsent(key, candidate) ?: candidate.also { future ->
            executor.execute {
                try {
                    val result = block()
                    active.remove(key, future)
                    future.complete(result)
                } catch (error: Throwable) {
                    active.remove(key, future)
                    future.completeExceptionally(error)
                }
            }
        }
        return try {
            shared.get() as T
        } catch (error: ExecutionException) {
            throw (error.cause ?: error)
        }
    }

    internal fun activeRequestCount(): Int = active.size
}
