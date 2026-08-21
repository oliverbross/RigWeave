package app.rigweave.mobile

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import kotlin.math.roundToInt

internal class DigiRawRecorder(context: Context) : AutoCloseable {
    private val directory = File(context.filesDir, "digi/recordings")
    private var output: RandomAccessFile? = null
    private var temporary: File? = null
    private var finalFile: File? = null
    private var samplesWritten = 0L
    private val sampleRate = 12_000
    private val maximumSamples = sampleRate * 60L * 10L

    val active get() = output != null

    @Synchronized fun start(): Boolean {
        if (active) return true
        prune()
        directory.mkdirs()
        val stem = "digi-${Instant.now().epochSecond}"
        temporary = File(directory, "$stem.part")
        finalFile = File(directory, "$stem.wav")
        samplesWritten = 0
        output = runCatching { RandomAccessFile(temporary, "rw").also { file -> repeat(44) { file.writeByte(0) } } }.getOrNull()
        return active
    }

    @Synchronized fun append(samples: FloatArray): Boolean {
        val file = output ?: return false
        val remaining = (maximumSamples - samplesWritten).coerceAtLeast(0).toInt()
        val count = samples.size.coerceAtMost(remaining)
        repeat(count) { index ->
            val pcm = (samples[index].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt()
            file.writeByte(pcm and 0xff)
            file.writeByte((pcm ushr 8) and 0xff)
        }
        samplesWritten += count
        if (count < samples.size || samplesWritten >= maximumSamples) {
            stop()
            return false
        }
        return true
    }

    @Synchronized fun stop(): File? {
        val file = output ?: return null
        val dataBytes = samplesWritten * 2
        file.seek(0)
        fun ascii(value: String) = file.write(value.toByteArray(Charsets.US_ASCII))
        fun le16(value: Int) { file.writeByte(value and 0xff); file.writeByte((value ushr 8) and 0xff) }
        fun le32(value: Long) { repeat(4) { shift -> file.writeByte(((value ushr (shift * 8)) and 0xff).toInt()) } }
        ascii("RIFF"); le32(36 + dataBytes); ascii("WAVEfmt "); le32(16); le16(1); le16(1)
        le32(sampleRate.toLong()); le32(sampleRate * 2L); le16(2); le16(16); ascii("data"); le32(dataBytes)
        file.fd.sync()
        file.close()
        output = null
        val source = temporary
        val target = finalFile
        val completed = if (source != null && target != null && source.renameTo(target)) target else null
        if (completed == null) source?.delete()
        temporary = null
        finalFile = null
        prune()
        return completed
    }

    private fun prune() {
        if (!directory.isDirectory) return
        val cutoff = System.currentTimeMillis() - 7L * 86_400_000L
        directory.listFiles().orEmpty().filter { it.lastModified() < cutoff || it.extension == "part" }.forEach(File::delete)
        val files = directory.listFiles { file -> file.extension == "wav" }.orEmpty().sortedByDescending(File::lastModified)
        var total = 0L
        files.forEach { file ->
            total += file.length()
            if (total > 100L * 1024L * 1024L) file.delete()
        }
    }

    override fun close() { stop() }
}
