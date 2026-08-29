package app.rigweave.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WorkspaceScreenVisibilityTest {
    @Test
    fun homeAndSettingsCannotBePersistedAsHidden() {
        assertEquals(setOf("RADIO", "PANADAPTER"), normalizeHiddenWorkspaceScreens(
            setOf("HOME", "RADIO", "PANADAPTER", "SETTINGS")
        ))
    }

    @Test
    fun visibilityPreferenceUsesVersionedPrivateKey() {
        assertFalse(WORKSPACE_SCREEN_VISIBILITY_PREF.isBlank())
        assertEquals("hidden_workspace_screens_v1", WORKSPACE_SCREEN_VISIBILITY_PREF)
    }
}
