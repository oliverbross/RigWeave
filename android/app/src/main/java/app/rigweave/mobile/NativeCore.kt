package app.rigweave.mobile

data class RadioState(
    val identity: String = "UNAVAILABLE",
    val model: String = "UNIDENTIFIED",
    val mode: String = "--",
    val frequencyHz: Long = 0,
    val connected: Boolean = false,
    val transmitting: Boolean = false,
    val meter: Int = 0,
    val revision: Long = 0,
) {
    val status get() = if (connected) "LIVE" else "OFFLINE"
    val frequencyText get() = if (connected) "%.3f MHz".format(frequencyHz / 1_000_000.0) else "—.——— MHz"
}

object NativeCore {
    init { System.loadLibrary("rigweave") }
    external fun create(): Long
    external fun destroy(handle: Long)
    external fun feed(handle: Long, data: ByteArray): Int
    external fun state(handle: Long): String
    external fun classify(command: String): Int
    external fun qsoIdentity(callsign: String, timestamp: String, frequency: Long, mode: String): String
    external fun adif(identity: String, callsign: String, date: String, time: String, frequency: Long, mode: String, sent: String, received: String): String
    external fun version(): String
    external fun featureCreate(): Long
    external fun featureDestroy(handle: Long)
    external fun featureWatchlist(handle: Long, value: String)
    external fun featureClusterLine(handle: Long, value: String, epoch: Long): Boolean
    external fun featureDxSnapshot(handle: Long, epoch: Long): String
    external fun featureSolar(handle: Long, flux: Float, aIndex: Float, kpIndex: Float, epoch: Long)
    external fun featurePanadapter(handle: Long, pcm: ByteArray, channels: Int, subframeBytes: Int, bits: Int): ByteArray
    external fun featureWsjtx(datagram: ByteArray): String

    fun parseState(value: String): RadioState {
        val fields = value.split('|')
        if (fields.size != 8) return RadioState()
        return RadioState(fields[0], fields[1], fields[2], fields[3].toLongOrNull() ?: 0,
            fields[4] == "1", fields[5] == "1", fields[6].toIntOrNull() ?: 0, fields[7].toLongOrNull() ?: 0)
    }
}
