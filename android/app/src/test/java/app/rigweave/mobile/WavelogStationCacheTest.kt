package app.rigweave.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class WavelogStationCacheTest {
    @Test fun publicOperatorDefaultsMatchTheConfiguredRigWeaveProfile() {
        assertEquals("OM0RX", RigWeaveDefaults.OPERATOR_CALLSIGN)
        assertEquals("Oliver Bross", RigWeaveDefaults.OPERATOR_NAME)
        assertEquals("JN88TQ", RigWeaveDefaults.OPERATOR_GRID)
        assertEquals("cluster.om0rx.com", RigWeaveDefaults.CLUSTER_HOST)
        assertEquals(7300, RigWeaveDefaults.CLUSTER_PORT)
        assertEquals("OM0JRX", RigWeaveDefaults.CLUSTER_LOGIN)
        assertEquals("https://om0rx.wavelog.online/index.php", RigWeaveDefaults.WAVELOG_BASE_URL)
        assertEquals("OM0RX", RigWeaveDefaults.QRZ_USERNAME)
    }
}
