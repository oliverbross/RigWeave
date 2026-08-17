package app.rigweave.mobile

object AudioOwners {
    const val NONE = "NONE"
    const val MONITOR = "MONITOR"
    const val PANADAPTER = "PANADAPTER"
    const val EQ = "EQ"
    const val VOICE = "VOICE"
    const val VOICE_TX = "VOICE_TX"
}

data class AudioLeaseDecision(
    val accepted: Boolean,
    val pauseMonitor: Boolean = false,
)

fun decideAudioLease(
    currentOwner: String,
    requestedOwner: String,
    monitorRunning: Boolean,
    pauseMonitor: Boolean,
): AudioLeaseDecision {
    if (requestedOwner in setOf(AudioOwners.NONE, AudioOwners.MONITOR)) return AudioLeaseDecision(false)
    if (currentOwner == AudioOwners.NONE) return AudioLeaseDecision(true)
    if (currentOwner == AudioOwners.MONITOR && monitorRunning && pauseMonitor) {
        return AudioLeaseDecision(accepted = true, pauseMonitor = true)
    }
    return AudioLeaseDecision(false)
}

fun canStartAudioMonitor(currentOwner: String): Boolean = currentOwner == AudioOwners.NONE
