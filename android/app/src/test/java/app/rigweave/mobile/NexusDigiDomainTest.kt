package app.rigweave.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusDigiDomainTest {
    @Test fun everyModeHasOneVisibleTruthfulCapability() {
        assertEquals(DigiMode.entries.size, DigiCapabilities.all.map { it.mode }.distinct().size)
        assertTrue(DigiCapabilities.all.all { it.visible && it.rxEngine })
    }

    @Test fun onlyFt8AndFt4ClaimAutomaticSequencing() {
        val automatic = DigiCapabilities.all.filter { it.sequencer == DigiSequencerSupport.FT8_FT4 }.map { it.mode }.toSet()
        assertEquals(setOf(DigiMode.FT8, DigiMode.FT4), automatic)
    }

    @Test fun clickToNetBehaviorMatchesModeFamily() {
        assertEquals(DigiWaterfallBehavior.MARK_SPACE_CENTER, DigiCapabilities.forMode(DigiMode.RTTY).waterfall)
        assertEquals(DigiWaterfallBehavior.CARRIER, DigiCapabilities.forMode(DigiMode.PSK31).waterfall)
        assertEquals(DigiWaterfallBehavior.CW_PITCH, DigiCapabilities.forMode(DigiMode.CW).waterfall)
    }

    @Test fun parserRetainsPortableDisplayAndLocksBaseCall() {
        val parsed = DigiFtParser.parse("OM0RX EA8/K1ABC -12")
        assertEquals("EA8/K1ABC", parsed.from)
        assertEquals("K1ABC", DigiFtParser.baseCall(parsed.from))
        assertEquals(FtMessageKind.REPORT, parsed.kind)
    }

    @Test fun cqParserFindsCallAndGrid() {
        val parsed = DigiFtParser.parse("CQ DX K1ABC FN31")
        assertEquals("K1ABC", parsed.from)
        assertEquals("FN31", parsed.grid)
        assertEquals(FtMessageKind.CQ, parsed.kind)
    }

    @Test fun sequencerIgnoresBystandersAfterStationLock() {
        val sequencer = DigiFtSequencer { "OM0RX" }
        sequencer.answer("EA8/K1ABC", 100)
        assertFalse(sequencer.decode(DigiFtParser.parse("OM0RX W9XYZ -05")))
        assertEquals("K1ABC", sequencer.snapshot.lockedCall)
    }

    @Test fun sequencerAcceptsOnlyMessagesAddressedToOperator() {
        val sequencer = DigiFtSequencer { "OM0RX" }
        sequencer.startCq(100)
        assertFalse(sequencer.decode(DigiFtParser.parse("OM1ABC K1ABC -10")))
        assertTrue(sequencer.decode(DigiFtParser.parse("OM0RX K1ABC -10")))
        assertEquals("K1ABC", sequencer.snapshot.lockedCall)
    }

    @Test fun wsjtPacketsUseCanonicalMagicSchemaAndType() {
        assertEquals(0, WsjtDatagram.headerType(WsjtDatagram.heartbeat("RigWeave", "test")))
        assertEquals(1, WsjtDatagram.headerType(WsjtDatagram.status("RigWeave", 14_074_000, "FT8", "", "", "FT8", false, false, true, 1_000, 1_000, "OM0RX", "JN88TQ")))
        assertEquals(2, WsjtDatagram.headerType(WsjtDatagram.decode("RigWeave", true, 1_000, -10, .1, 250, "FT8", "CQ K1ABC FN31")))
    }

    @Test fun wsjtHeaderRejectsTruncationAndUnknownMagic() {
        assertEquals(null, WsjtDatagram.headerType(ByteArray(11)))
        val packet = WsjtDatagram.heartbeat("RigWeave", "test")
        packet[0] = 0
        assertEquals(null, WsjtDatagram.headerType(packet))
    }

    @Test fun allTransmitPathsHaveFiniteHardCaps() {
        DigiCapabilities.all.filter { it.txEngine }.forEach {
            assertTrue(it.mode.name, it.maximumTxMillis in 1..600_000)
            assertNotNull(it.adifMode)
        }
    }
}
