package com.example.superapp.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTweaksTest {

    private val facebook = PAGE_TWEAKS.single { it.domain == "facebook.com" }

    @Test
    fun `facebook tweak runs on facebook and its subdomains only`() {
        assertEquals(setOf("https://facebook.com", "https://*.facebook.com"), facebook.originRules)
        for (host in listOf("facebook.com", "m.facebook.com", "www.facebook.com", "web.facebook.com")) {
            assertTrue(host, facebook.appliesTo(host))
        }
        for (host in listOf("notfacebook.com", "facebook.com.evil.example", "instagram.com", "x.com")) {
            assertFalse(host, facebook.appliesTo(host))
        }
    }

    @Test
    fun `facebook tweak targets the open app buttons but never the top bar`() {
        val script = facebook.script
        assertTrue(script.contains("""[data-log-comp-type*="open_app" i]"""))
        assertTrue(script.contains("""[role="button"][aria-label="Open app" i]"""))
        assertTrue(script.contains(".fixed-container:is(.bottom,.above-bottom)"))
        assertFalse(script.contains(".fixed-container.top"))
    }
}
