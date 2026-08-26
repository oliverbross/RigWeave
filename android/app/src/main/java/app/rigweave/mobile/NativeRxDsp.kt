// SPDX-License-Identifier: GPL-3.0-only
package app.rigweave.mobile

object NativeRxDsp {
    init { System.loadLibrary("rigweave") }

    external fun create(): Long
    external fun destroy(handle: Long)
    external fun process(
        handle: Long,
        samples: FloatArray,
        sampleRate: Int,
        noiseBlanker: Boolean,
        automaticNotch: Boolean,
        noiseReduction: Float,
        agc: Boolean,
        agcHangMillis: Int,
        squelchDb: Float,
        outputGain: Float,
    ): FloatArray
}
