package app.rigweave.mobile

import app.rigweave.mobile.rotator.RotatorBackend
import app.rigweave.mobile.rotator.RotatorProtocolKind
import app.rigweave.mobile.rotator.RotatorTransportKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RotatorProfileDialogTest {
    @Test fun loopbackRotctldProfileIsValidAndRemainsDisconnectedConfiguration() {
        val profile = buildRotatorProfile(null, "Shack rotator", RotatorBackend.REMOTE_ROTCTLD,
            RotatorProtocolKind.GS232, "TCP", "", null, "127.0.0.1", 4_533, false, null, 1_000)

        assertEquals(RotatorProtocolKind.ROTCTLD, profile.protocol)
        assertEquals(RotatorTransportKind.ROTCTLD, profile.transport)
        assertEquals("127.0.0.1", profile.tcp?.host)
    }

    @Test fun nonLoopbackEndpointRequiresExplicitTrustedLanOptIn() {
        assertThrows(IllegalArgumentException::class.java) {
            buildRotatorProfile(null, "LAN rotator", RotatorBackend.NATIVE, RotatorProtocolKind.GS232,
                "TCP", "", null, "192.0.2.10", 23, false, null, 1_000)
        }
    }

    @Test fun embeddedHamlibRequiresModelAndOneExplicitTransport() {
        val profile = buildRotatorProfile(null, "Hamlib rotator", RotatorBackend.EMBEDDED_HAMLIB,
            RotatorProtocolKind.HAMLIB, "USB", "0123456789abcdef", 9_600,
            "127.0.0.1", 4_533, false, 601, 1_000)

        assertEquals(RotatorTransportKind.EMBEDDED_HAMLIB, profile.transport)
        assertEquals(601, profile.hamlibModelId)
        assertNotNull(profile.hamlibSerial)
    }
}
