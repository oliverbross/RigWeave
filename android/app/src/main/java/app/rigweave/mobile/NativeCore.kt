package app.rigweave.mobile

data class RadioState(
    val identity: String = "UNAVAILABLE",
    val model: String = "UNIDENTIFIED",
    val mode: String = "--",
    val frequencyHz: Long = 0,
    val frequencyBHz: Long = 0,
    val connected: Boolean = false,
    val transmitting: Boolean = false,
    val meter: Int = 0,
    val swrTenths: Int = -1,
    val rfOutputTenths: Int = -1,
    val afGain: Int = 0,
    val rfGain: Int = 0,
    val bandwidthHz: Int = 0,
    val powerW: Int = 0,
    val preamp: Boolean = false,
    val attenuator: Boolean = false,
    val rit: Boolean = false,
    val xit: Boolean = false,
    val rxVfo: Int = 0,
    val txVfo: Int = 0,
    val split: Boolean = false,
    val agcMode: Int = -1,
    val cwt: Boolean = false,
    val monitorLevel: Int = -1,
    val micGain: Int = -1,
    val keyerSpeed: Int = -1,
    val ifShiftHz: Int = -1,
    val revision: Long = 0,
) {
    val status get() = if (connected) "LIVE" else "OFFLINE"
    val frequencyText get() = if (connected) formatRadioFrequency(frequencyHz) + " MHz" else "—.——— MHz"
}

fun formatRadioFrequency(frequencyHz: Long): String {
    if (frequencyHz <= 0) return "—.———"
    val megahertz = frequencyHz / 1_000_000
    val kilohertz = (frequencyHz / 1_000) % 1_000
    val subKilohertz = frequencyHz % 1_000
    val fine = when {
        subKilohertz % 100 == 0L -> "%01d".format(subKilohertz / 100)
        subKilohertz % 10 == 0L -> "%02d".format(subKilohertz / 10)
        else -> "%03d".format(subKilohertz)
    }
    return "%d.%03d.%s".format(megahertz, kilohertz, fine)
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

    fun parseState(value: String): RadioState {
        val fields = value.split('|')
        if (fields.size != 28) return RadioState()
        return RadioState(fields[0], fields[1], fields[2], fields[3].toLongOrNull() ?: 0,
            fields[4].toLongOrNull() ?: 0, fields[5] == "1", fields[6] == "1",
            fields[7].toIntOrNull() ?: 0, fields[8].toIntOrNull() ?: -1, fields[9].toIntOrNull() ?: -1,
            fields[10].toIntOrNull() ?: 0, fields[11].toIntOrNull() ?: 0, fields[12].toIntOrNull() ?: 0,
            fields[13].toIntOrNull() ?: 0, fields[14] == "1", fields[15] == "1", fields[16] == "1",
            fields[17] == "1", fields[18].toIntOrNull() ?: 0, fields[19].toIntOrNull() ?: 0,
            fields[20] == "1", fields[21].toIntOrNull() ?: -1, fields[22] == "1",
            fields[23].toIntOrNull() ?: -1, fields[24].toIntOrNull() ?: -1,
            fields[25].toIntOrNull() ?: -1, fields[26].toIntOrNull() ?: -1,
            fields[27].toLongOrNull() ?: 0)
    }
}
