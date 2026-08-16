package app.rigweave.mobile

data class RadioFeedback(val title: String, val value: String)

/** Describes operator-facing KX3 changes while intentionally ignoring VFO tuning and meter traffic. */
fun detectRadioFeedback(previous: RadioState, current: RadioState): RadioFeedback? {
    if (!previous.connected || !current.connected) return null
    return when {
        previous.keyerSpeed != current.keyerSpeed && current.keyerSpeed >= 8 ->
            RadioFeedback("KEYER SPEED", "${current.keyerSpeed} WPM")
        previous.afGain != current.afGain -> RadioFeedback("AF GAIN", "${current.afGain} / 255")
        previous.rfGain != current.rfGain -> RadioFeedback("RF GAIN", "${current.rfGain} / 250")
        previous.monitorLevel != current.monitorLevel && current.monitorLevel >= 0 ->
            RadioFeedback("MONITOR", "${current.monitorLevel} / 60")
        previous.micGain != current.micGain && current.micGain >= 0 ->
            RadioFeedback("MIC GAIN", "${current.micGain} / 60")
        previous.bandwidthHz != current.bandwidthHz && current.bandwidthHz > 0 ->
            RadioFeedback("FILTER WIDTH", "${current.bandwidthHz} Hz")
        previous.ifShiftHz != current.ifShiftHz && current.ifShiftHz >= 0 ->
            RadioFeedback("IF SHIFT", "${current.ifShiftHz} Hz")
        previous.powerW != current.powerW -> RadioFeedback("TX POWER", "${current.powerW} W")
        previous.agcMode != current.agcMode && current.agcMode >= 0 ->
            RadioFeedback("AGC", when (current.agcMode) { 0 -> "OFF"; 2 -> "FAST"; 4 -> "SLOW"; else -> current.agcMode.toString() })
        previous.preamp != current.preamp -> RadioFeedback("PREAMP", if (current.preamp) "ON" else "OFF")
        previous.attenuator != current.attenuator -> RadioFeedback("ATTENUATOR", if (current.attenuator) "ON" else "OFF")
        previous.rit != current.rit -> RadioFeedback("RIT", if (current.rit) "ON" else "OFF")
        previous.xit != current.xit -> RadioFeedback("XIT", if (current.xit) "ON" else "OFF")
        previous.split != current.split -> RadioFeedback("SPLIT", if (current.split) "ON" else "OFF")
        previous.cwt != current.cwt -> RadioFeedback("CW TUNING", if (current.cwt) "ON" else "OFF")
        previous.mode != current.mode -> RadioFeedback("MODE", current.mode)
        previous.rxVfo != current.rxVfo -> RadioFeedback("RX VFO", if (current.rxVfo == 0) "A" else "B")
        previous.txVfo != current.txVfo -> RadioFeedback("TX VFO", if (current.txVfo == 0) "A" else "B")
        else -> null
    }
}
