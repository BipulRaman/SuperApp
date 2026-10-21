package com.example.superapp.browser

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebShareTest {

    @Test
    fun `a page's share request is read and checked`() {
        val share = parseWebShare("""{"title":"A post","text":"Look at this","url":"https://www.facebook.com/share/p/abc/"}""")!!
        assertEquals("A post", share.title)
        assertEquals("Look at this", share.text)
        assertEquals("https://www.facebook.com/share/p/abc/", share.url)

        assertEquals("x".repeat(500), parseWebShare("""{"title":"${"x".repeat(600)}"}""")!!.title)
        for (bad in listOf(null, "", "not json", "{}", """{"title":"  "}""", """{"url":"javascript:alert(1)"}""", """{"url":"file:///sdcard/x"}""")) {
            assertNull(bad, parseWebShare(bad))
        }
    }

    @Test
    fun `the share sheet gets just the link, without tracking, title or text`() {
        fun shared(title: String, text: String, url: String): Intent = WebShare(title, text, url).toSendIntent()

        val tracked = "https://x.com/someone/status/1?s=20&t=abc"
        val send = shared("A post", "Look at this", tracked)
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("https://x.com/someone/status/1", send.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(send.getStringExtra(Intent.EXTRA_TITLE))
        assertNull(send.getStringExtra(Intent.EXTRA_SUBJECT))

        // Pages that put the link in the text: just that link.
        assertEquals("https://x.com/someone/status/1", shared("", "See $tracked.", "").getStringExtra(Intent.EXTRA_TEXT))
        // Nothing to link to: the text, else the title.
        assertEquals("Just text", shared("A title", " Just text ", "").getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Only a title", shared("Only a title", "", "").getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `only the sites' own pages get navigator share`() {
        assertTrue(WEB_SHARE_ORIGINS.containsAll(listOf("https://facebook.com", "https://*.facebook.com", "https://*.instagram.com", "https://x.com", "https://*.linkedin.com")))
        assertTrue(WEB_SHARE_ORIGINS.all { it.startsWith("https://") })
        assertFalse("*" in WEB_SHARE_ORIGINS)
    }

    @Test
    fun `the page script asks for a tap and never overrides a real navigator share`() {
        assertTrue(WEB_SHARE_SCRIPT.contains("if (!bridge || navigator.share) return;"))
        assertTrue(WEB_SHARE_SCRIPT.contains("activation && !activation.isActive"))
        assertTrue(WEB_SHARE_SCRIPT.contains("navigator.canShare = function"))
        assertFalse(WEB_SHARE_SCRIPT.contains("$"))
    }
}
