package app.rigweave.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalConvergenceContractsTest {
    @Test fun staleOperatingContextGenerationCannotPublish() {
        val authority = OperatingContextAuthority()
        val first = authority.beginUpdate()
        val second = authority.beginUpdate()
        assertFalse(authority.publish(first, OperatingContextSnapshot(stationCallsign = ContextValue("OLD", "test"))))
        assertTrue(authority.publish(second, OperatingContextSnapshot(stationCallsign = ContextValue("OM0RX", "test"))))
        assertEquals("OM0RX", authority.snapshot.stationCallsign.value)
        assertFalse(authority.publish(second, OperatingContextSnapshot(stationCallsign = ContextValue("REPLAY", "test"))))
    }

    @Test fun actionRouterPreservesExactIdentityWithoutAutomaticRadioOrMutationAuthority() {
        val route = WorkspaceActionRouter.resolve(WorkspaceAction(
            destination = WorkspaceDestination.SYNC,
            qsoId = "qso-42",
            wavelogConflictId = "conflict-7",
            source = "Wavelog conflict",
            reason = "Open exact reconciliation item",
        ))
        assertTrue(route.requiresExactSelection)
        assertEquals("qso-42", route.action.qsoId)
        assertEquals("conflict-7", route.action.wavelogConflictId)
        assertNull(route.receiveReview)
        assertFalse(route.mayKeyPtt)
        assertFalse(route.mayStartTune)
        assertFalse(route.mayChangeTransmitFrequency)
        assertFalse(route.mayArmTransmit)
        assertFalse(route.mayPostOrLog)
    }

    @Test fun frequencyActionCreatesReceiveReviewOnly() {
        val route = WorkspaceActionRouter.resolve(WorkspaceAction(
            destination = WorkspaceDestination.DIGI,
            callsign = "VK8ABC",
            frequencyHz = 14_074_000,
            mode = "USB",
            source = "Neural opportunity",
            reason = "Prepare Digi receive review",
        ))
        assertEquals(14_074_000L, route.receiveReview?.frequencyHz)
        assertEquals("USB", route.receiveReview?.mode)
        assertFalse(route.mayChangeTransmitFrequency)
        assertFalse(route.mayArmTransmit)
    }
}
