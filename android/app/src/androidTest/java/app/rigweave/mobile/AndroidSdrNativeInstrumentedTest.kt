package app.rigweave.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class AndroidSdrNativeInstrumentedTest {
    @Test fun tciStatusAndSafeCommandsUseSharedNativeContract() {
        val rows = NativeTci.parseStatus("protocol:TCI,1.9;device:ExpertSDR;ready;")
        assertEquals(listOf("protocol|TCI,1.9", "device|ExpertSDR", "ready|"), rows.toList())
        assertEquals("vfo:1,0,14074000;", NativeTci.buildCommand(NativeTci.VFO, 1, 0, 14_074_000, ""))
        assertEquals("", NativeTci.buildCommand(99, 0, 0, 0, ""))
    }

    @Test fun nativeRxDspProcessesFiniteAudioAndReportsMetrics() {
        val handle = NativeRxDsp.create()
        try {
            val samples = FloatArray(4_800) { index ->
                (sin(2.0 * PI * 1_000.0 * index / 48_000.0) * .4).toFloat()
            }
            samples[1_000] = 1.2f
            val metrics = NativeRxDsp.process(handle, samples, 48_000, true, true, .35f, true, 250, -110f, .8f)
            assertEquals(8, metrics.size)
            assertTrue(samples.all(Float::isFinite))
            assertTrue(metrics.all(Float::isFinite))
            assertTrue(metrics[3] in 0f..1f)
        } finally {
            NativeRxDsp.destroy(handle)
        }
    }
}
