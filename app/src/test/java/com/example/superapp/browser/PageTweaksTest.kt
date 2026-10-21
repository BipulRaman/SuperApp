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
    fun `facebook tweak is a self-contained script that runs once per page`() {
        val script = facebook.script
        assertTrue(script.startsWith("(function () {"))
        assertTrue(script.endsWith("})();"))
        assertTrue(script.contains("Symbol.for('superapp.open-app-hider')"))
        assertTrue(script.contains("window.top !== window"))
    }

    @Test
    fun `facebook tweak finds open app controls by tag, label or text`() {
        val script = facebook.script
        assertTrue(script.contains("""[data-log-comp-type*="open_app" i]"""))
        assertTrue(script.contains("""[role="button"][aria-label="Open app" i]"""))
        assertTrue(script.contains("""/^(open|use|get)( in| the)? app$/i"""))
    }

    @Test
    fun `facebook tweak hides only small bottom bars that hold nothing but open app`() {
        val script = facebook.script
        assertTrue(script.contains("getElementsByClassName('fixed-container')"))
        assertTrue(script.contains("MAX_BAR_HEIGHT = 150"))
        assertTrue(script.contains("classList.contains('bottom') || bar.classList.contains('above-bottom')"))
        assertTrue(script.contains("bottom && found && !others"))
    }

    @Test
    fun `facebook tweak avoids newer selectors and forced layout`() {
        val script = facebook.script
        for (avoided in listOf(":has(", ":is(", "getComputedStyle", "getBoundingClientRect", "offsetHeight")) {
            assertFalse(avoided, script.contains(avoided))
        }
    }

    @Test
    fun `facebook tweak only rescans when a change touches a pinned bar`() {
        val script = facebook.script
        assertTrue(script.contains("new MutationObserver(onChange)"))
        assertTrue(script.contains("if (touchesBars(records)) sweep();"))
        assertTrue(script.contains("el.closest(BAR)"))
        assertFalse(script.contains("new MutationObserver(sweep)"))
    }

    @Test
    fun `hidden pages stop their media, in every frame`() {
        assertTrue(PAUSE_MEDIA_WHEN_HIDDEN.contains("document.addEventListener('visibilitychange'"))
        assertTrue(PAUSE_MEDIA_WHEN_HIDDEN.contains("if (document.hidden) document.querySelectorAll('video,audio')"))
    }
}
