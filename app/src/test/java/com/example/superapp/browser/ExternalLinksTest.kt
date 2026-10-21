package com.example.superapp.browser

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.webkit.WebView
import com.example.superapp.data.SITES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ExternalLinksTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val webView = WebView(activity)

    @Test
    fun `web schemes load in the WebView`() {
        for (scheme in listOf("http", "https", "HTTPS", "about", "data", "blob", "javascript", null)) {
            assertEquals(scheme.toString(), Route.WEB_VIEW, routeFor(scheme, userGesture = false))
        }
    }

    @Test
    fun `app links open other apps only when the user tapped`() {
        for (scheme in listOf("intent", "market", "mailto", "tel", "fb", "whatsapp")) {
            assertEquals(scheme, Route.EXTERNAL_APP, routeFor(scheme, userGesture = true))
            assertEquals(scheme, Route.BLOCK, routeFor(scheme, userGesture = false))
        }
    }

    @Test
    fun `local file schemes are never followed`() {
        assertEquals(Route.BLOCK, routeFor("file", userGesture = true))
        assertEquals(Route.BLOCK, routeFor("content", userGesture = true))
    }

    @Test
    fun `app links open with browser hardening`() {
        activity.openExternally(webView, Uri.parse("mailto:someone@example.com"))

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("mailto:someone@example.com", started.dataString)
        assertTrue(started.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertNull(started.component)
    }

    @Test
    fun `intent links cannot hand out local files or grant URI access`() {
        activity.openExternally(webView, Uri.parse("intent://host/secret.txt#Intent;scheme=file;end"))
        activity.openExternally(webView, Uri.parse("intent://${activity.packageName}.captures/capture-1.jpg#Intent;scheme=content;end"))
        assertNull(shadowOf(activity).nextStartedActivity)

        val grants = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        activity.openExternally(webView, Uri.parse("intent://example.com/#Intent;scheme=geo;launchFlags=0x3;end"))

        assertEquals(0, shadowOf(activity).nextStartedActivity.flags and grants)
    }

    @Test
    fun `a missing app falls back to the link's web page`() {
        shadowOf(activity.application).checkActivities(true)

        activity.openExternally(
            webView,
            Uri.parse("intent://open#Intent;scheme=superapp-test;package=com.example.missing;S.browser_fallback_url=https%3A%2F%2Fexample.com%2Fhelp;end"),
        )

        assertEquals("https://example.com/help", shadowOf(webView).lastLoadedUrl)
    }

    private fun linkFilter(host: String?) = IntentFilter(Intent.ACTION_VIEW).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        addCategory(Intent.CATEGORY_BROWSABLE)
        addDataScheme("https")
        if (host != null) addDataAuthority(host, null)
    }

    private fun install(pkg: String, filter: IntentFilter) {
        val component = ComponentName(pkg, "$pkg.Main")
        shadowOf(activity.packageManager).addActivityIfNotPresent(component)
        shadowOf(activity.packageManager).addIntentFilterForActivity(component, filter)
    }

    private fun link(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)

    @Test
    fun `tapped links to other sites may open their app, SuperApp's own sites never`() {
        fun may(url: String, current: String? = "https://m.facebook.com/", mainFrame: Boolean = true, gesture: Boolean = true) =
            mayOpenInApp(Uri.parse(url), current, mainFrame, gesture, SITES)

        assertTrue(may("https://api.whatsapp.com/send?text=hi"))
        assertTrue(may("https://wa.me/?text=hi"))
        assertTrue(may("https://www.youtube.com/watch?v=1", current = null)) // a blank pop-up's first page
        assertFalse(may("https://api.whatsapp.com/send?text=hi", gesture = false))
        assertFalse(may("https://api.whatsapp.com/send?text=hi", mainFrame = false))
        assertFalse(may("https://www.instagram.com/p/abc/"))
        assertFalse(may("https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com"))
        assertFalse(may("https://www.youtube.com/watch?v=2", current = "https://m.youtube.com/watch?v=1"))
        assertFalse(may("whatsapp://send?text=hi"))
    }

    @Test
    fun `web links open the app installed for them, otherwise they stay`() {
        shadowOf(activity.application).checkActivities(true)
        install("com.whatsapp", linkFilter("api.whatsapp.com"))

        assertTrue(activity.openInApp(Uri.parse("https://api.whatsapp.com/send?text=hi")))
        val started = shadowOf(activity).nextStartedActivity
        assertEquals("https://api.whatsapp.com/send?text=hi", started.dataString)
        assertTrue(started.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertTrue((started.flags and Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER) != 0)

        assertFalse(activity.openInApp(Uri.parse("https://example.com/article")))
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `before Android 11, apps whose link filter names the host count, browsers don't`() {
        install("com.android.chrome", linkFilter(null))
        install("com.whatsapp", linkFilter("api.whatsapp.com"))

        assertEquals(listOf("com.whatsapp"), activity.appsForLink(link("https://api.whatsapp.com/send?text=hi")))
        assertEquals(emptyList<String>(), activity.appsForLink(link("https://example.com/article")))
    }
}
