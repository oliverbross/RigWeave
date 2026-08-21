package app.rigweave.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InAppBrowserTest {
    @Test
    fun acceptsOnlyHttpsUrlsWithHosts() {
        assertEquals("https://amsat-dl.org/en/", validatedInAppBrowserUrl(" https://amsat-dl.org/en/ "))
        assertNull(validatedInAppBrowserUrl("http://amsat-dl.org"))
        assertNull(validatedInAppBrowserUrl("file:///data/local/tmp/page.html"))
        assertNull(validatedInAppBrowserUrl("javascript:alert(1)"))
        assertNull(validatedInAppBrowserUrl("https://"))
    }
}
