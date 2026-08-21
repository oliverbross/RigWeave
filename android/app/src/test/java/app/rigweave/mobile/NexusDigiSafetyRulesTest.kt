package app.rigweave.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NexusDigiSafetyRulesTest {
    private fun source(name: String) = File("src/main/java/app/rigweave/mobile/$name").readText()

    @Test fun settingsSerializerCannotRestoreTransmitState() {
        val domain = source("DigiDomain.kt")
        val serializer = domain.substring(domain.indexOf("fun toJson()"), domain.indexOf("companion object"))
        listOf("txArmed", "txActive", "PTT", "sequencer transmitting").forEach { assertFalse(serializer.contains(it)) }
    }

    @Test fun exactRouteLossStopsReceiveAndClearsTransmit() {
        val controller = source("DigiController.kt")
        assertTrue(controller.contains("stopRx(\"Selected USB route was lost\"); haltTx()"))
    }

    @Test fun transmitRechecksModeFrequencyAndSessionEnableBeforePtt() {
        val controller = source("DigiController.kt")
        assertTrue(controller.contains("!txEnabled || mode != selectedMode || currentRadio.frequencyHz != selectedFrequency"))
        assertTrue(controller.contains("currentRadio.identity != selectedIdentity || radioFamily() != selectedFamily"))
        assertTrue(controller.contains("withTimeoutOrNull(DigiCapabilities.forMode(selectedMode).maximumTxMillis + 5_000L)"))
        assertTrue(controller.contains("plan.remainsValid(System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime())"))
    }

    @Test fun qsoSaveUsesCanonicalMutationCoordinator() {
        val controller = source("DigiController.kt")
        assertTrue(controller.contains("dependencies.mutations.save"))
        assertFalse(controller.contains("WavelogApiV2("))
    }

    @Test fun companionModeDisablesLocalModemAuthority() {
        val interop = source("DigiWsjtInterop.kt")
        val controller = source("DigiController.kt")
        assertTrue(interop.contains("val localModemAllowed get() = !state.companionMode"))
        assertTrue(controller.contains("settings.companionMode || !interop.localModemAllowed"))
    }

    @Test fun inboundUdpCannotEnableTransmit() {
        val interop = source("DigiWsjtInterop.kt")
        assertTrue(interop.contains("8 -> { onHaltTx(); true }"))
        assertTrue(interop.contains("3 -> { onClear(); true }"))
        assertTrue(interop.contains("7 -> { onReplay(); true }"))
        assertFalse(interop.contains("onEnableTx"))
    }

    @Test fun rawRecordingHasTimeAgeAndSizeCaps() {
        val recorder = source("DigiRawRecorder.kt")
        assertTrue(recorder.contains("sampleRate * 60L * 10L"))
        assertTrue(recorder.contains("7L * 86_400_000L"))
        assertTrue(recorder.contains("100L * 1024L * 1024L"))
    }

    @Test fun waterfallMemoryAndPublicationAreBounded() {
        val controller = source("DigiController.kt")
        assertTrue(controller.contains("now - lastSpectrumPublishedMs < 100L"))
        assertTrue(controller.contains("digiSpectrum(samples, 12_000, display.lowHz, display.highHz, 384"))
        assertTrue(controller.contains("takeLast(900)"))
    }

    @Test fun issSessionHasPermanentNoTransmitBoundary() {
        val controller = source("DigiController.kt")
        val screen = source("DigiScreen.kt")
        assertTrue(controller.contains("issSessionEnabled"))
        assertTrue(screen.contains("145.800 MHz is an ISS downlink"))
        assertTrue(screen.contains("enabled = !controller.issSessionEnabled"))
    }

    @Test fun closeIsIdempotentAndRequestsReceive() {
        val controller = source("DigiController.kt")
        val close = controller.substring(controller.indexOf("override fun close()"))
        assertTrue(close.contains("if (closed) return"))
        assertTrue(close.contains("transport.send(\"RX;\")"))
        assertTrue(close.contains("flex.stopTransmit"))
    }
}
