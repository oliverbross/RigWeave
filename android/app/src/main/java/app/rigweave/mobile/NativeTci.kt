// SPDX-License-Identifier: GPL-3.0-only
package app.rigweave.mobile

/** Minimal JNI surface over the existing shared C++ TCI contract. */
object NativeTci {
    init { System.loadLibrary("rigweave") }

    external fun parseStatus(text: String): Array<String>
    external fun decodeBinary(message: ByteArray, metadata: IntArray): FloatArray
    external fun buildCommand(kind: Int, receiver: Int, channel: Int, number: Long, text: String): String

    const val VFO = 0
    const val MODE = 1
    const val IQ_RATE = 2
    const val IQ_START = 3
    const val IQ_STOP = 4
    const val AUDIO_START = 5
    const val AUDIO_STOP = 6
    const val RX_ENABLE = 7
    const val MUTE = 8
    const val SAFE_STOP = 9
    const val IF_OFFSET = 10
    const val VOLUME = 11
    const val SPLIT = 12

    const val DATA_IQ = 0
    const val DATA_RX_AUDIO = 1
    const val DATA_TX_AUDIO = 2
    const val DATA_TX_CHRONO = 3
}
