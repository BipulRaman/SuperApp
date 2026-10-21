package com.example.superapp

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.widget.TextView
import com.example.superapp.browser.PAUSE_MEDIA_JS
import com.example.superapp.data.SITES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowToast
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    private var controller: ActivityController<MainActivity> = Robolectric.buildActivity(MainActivity::class.java).setup()
    private val activity: MainActivity get() = controller.get()

    /** Starts SuperApp over with [intent], e.g. a link tapped in another app, instead of from the launcher. */
    private fun launch(intent: Intent) {
        controller.pause().stop().destroy()
        controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
    }

    /** A link tapped in another app ([from] is that app's package). */
    private fun link(url: String, from: String = "com.whatsapp"): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://$from"))

    private val tiles: List<View>
        get() {
            val grid = activity.findViewById<ViewGroup>(R.id.grid)
            return (0 until grid.childCount)
                .map { grid.getChildAt(it) as ViewGroup }
                .flatMap { row -> (0 until row.childCount).map(row::getChildAt) }
                .filter { it.isClickable }
        }

    private val webViews: List<WebView>
        get() {
            val container = activity.findViewById<ViewGroup>(R.id.web_container)
            return (0 until container.childCount).map(container::getChildAt).filterIsInstance<WebView>()
        }

    private fun isShown(id: Int) = activity.findViewById<View>(id).visibility == View.VISIBLE

    private fun isLive(tile: View) = tile.findViewById<View>(R.id.live).visibility == View.VISIBLE

    @Suppress("DEPRECATION")
    private fun pressBack() = activity.onBackPressed()

    @Test
    fun `home grid shows every site`() {
        assertEquals(SITES.size, tiles.size)
        assertTrue(isShown(R.id.home))
        assertTrue(tiles.none(::isLive))
    }

    @Test
    fun `opening a site loads it in its own WebView`() {
        tiles[0].performClick()

        assertTrue(isShown(R.id.browser))
        assertEquals(View.GONE, activity.findViewById<View>(R.id.home).visibility)
        val webView = webViews.single()
        assertEquals(SITES[0].url, shadowOf(webView).lastLoadedUrl)
        val title = activity.findViewById<TextView>(R.id.title)
        assertEquals(SITES[0].name, title.text)
        assertTrue(title.compoundDrawablesRelative[0] != null)
    }

    @Test
    fun `leaving a site keeps it alive and reopening resumes exactly where it was`() {
        tiles[0].performClick()
        val webView = webViews.single()
        val somewhereInside = "https://m.facebook.com/some/post"
        webView.loadUrl(somewhereInside)

        pressBack()

        assertTrue(isShown(R.id.home))
        assertSame(webView, webViews.single())
        assertEquals(View.GONE, webView.visibility)
        assertTrue(shadowOf(webView).wasOnPauseCalled())
        assertEquals(PAUSE_MEDIA_JS, shadowOf(webView).lastEvaluatedJavascript)
        assertTrue(isLive(tiles[0]))

        tiles[0].performClick()

        assertSame(webView, webViews.single())
        assertEquals(View.VISIBLE, webView.visibility)
        assertEquals(somewhereInside, shadowOf(webView).lastLoadedUrl)
        assertEquals(0, shadowOf(webView).reloadInvocations)
    }

    @Test
    fun `switching sites keeps every opened site loaded`() {
        tiles[0].performClick()
        pressBack()
        tiles[1].performClick()

        assertEquals(2, webViews.size)
        val (first, second) = webViews.sortedBy { shadowOf(it).lastLoadedUrl != SITES[0].url }
        assertEquals(View.GONE, first.visibility)
        assertEquals(View.VISIBLE, second.visibility)
        assertEquals(SITES[1].url, shadowOf(second).lastLoadedUrl)
        assertTrue(isLive(tiles[0]) && isLive(tiles[1]))
    }

    @Test
    fun `back walks the page history before returning to the grid`() {
        tiles[0].performClick()
        val webView = webViews.single()
        shadowOf(webView).pushEntryToHistory(SITES[0].url)
        shadowOf(webView).pushEntryToHistory("https://m.facebook.com/some/post")

        pressBack()

        assertEquals(1, shadowOf(webView).goBackInvocations)
        assertTrue(isShown(R.id.browser))

        pressBack()

        assertTrue(isShown(R.id.home))
        assertSame(webView, webViews.single())
    }

    @Test
    fun `back on the grid keeps the app alive instead of finishing`() {
        tiles[0].performClick()
        pressBack()
        pressBack()

        assertTrue(!activity.isFinishing)
        assertEquals(1, webViews.size)
    }

    @Test
    @Suppress("DEPRECATION") // Apps never construct this; the test has to fake the renderer's report.
    fun `a crashed renderer drops the site and returns to the grid instead of killing the app`() {
        tiles[0].performClick()
        val webView = webViews.single()
        val detail = object : RenderProcessGoneDetail() {
            override fun didCrash() = true
            override fun rendererPriorityAtExit() = 0
        }

        val handled = shadowOf(webView).webViewClient.onRenderProcessGone(webView, detail)

        assertTrue(handled)
        assertTrue(isShown(R.id.home))
        assertTrue(webViews.isEmpty())
        assertTrue(!isLive(tiles[0]))

        tiles[0].performClick()
        assertEquals(SITES[0].url, shadowOf(webViews.single()).lastLoadedUrl)
    }

    @Test
    fun `destroying the activity destroys its WebViews`() {
        tiles[0].performClick()
        val webView = webViews.single()

        controller.pause().stop().destroy()

        assertTrue(shadowOf(webView).wasDestroyCalled())
    }

    @Test
    fun `battery saver keeps only the site on screen loaded`() {
        tiles[0].performClick()
        pressBack()
        tiles[1].performClick()
        assertEquals(2, webViews.size)

        shadowOf(activity.getSystemService(PowerManager::class.java)).setIsPowerSaveMode(true)
        activity.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, webViews.size)
        assertEquals(SITES[1].url, shadowOf(webViews.single()).lastLoadedUrl)
        assertEquals(listOf(false, true), tiles.take(2).map(::isLive))

        // Switching sites closes the one shown before.
        pressBack()
        tiles[0].performClick()
        assertEquals(1, webViews.size)
    }

    @Test
    fun `close sits left of reload and unloads the site, keeping the other open sites`() {
        tiles[1].performClick()
        pressBack()
        tiles[0].performClick()
        val (other, site) = webViews.sortedBy { shadowOf(it).lastLoadedUrl == SITES[0].url }
        controller.newIntent(link("https://www.facebook.com/share/p/abc/"))
        val linkWindow = webViews.single { it !== other && it !== site }
        val toolbar = activity.findViewById<View>(R.id.close).parent as ViewGroup
        val close = activity.findViewById<View>(R.id.close)
        assertEquals(toolbar.indexOfChild(activity.findViewById(R.id.reload)) - 1, toolbar.indexOfChild(close))
        assertEquals("Close Facebook", close.contentDescription)

        close.performClick()

        assertTrue(isShown(R.id.home))
        assertTrue(shadowOf(site).wasDestroyCalled())
        assertTrue(shadowOf(linkWindow).wasDestroyCalled())
        assertEquals(listOf(other), webViews)
        assertEquals(listOf(false, true), tiles.take(2).map(::isLive))

        pressBack()
        assertTrue(shadowOf(activity).isTaskMovedToBack) // Back on the grid leaves the app, as usual.

        tiles[0].performClick()
        assertEquals(SITES[0].url, shadowOf(webViews.single { it !== other }).lastLoadedUrl)
    }

    /** Simulates a page calling window.open(); returns the new window, or null if it was refused. */
    private fun requestWindow(opener: WebView, userGesture: Boolean = true): WebView? {
        val transport = opener.WebViewTransport()
        val message = Message.obtain(Handler(Looper.getMainLooper()), 0, transport)
        val created = shadowOf(opener).webChromeClient.onCreateWindow(opener, false, userGesture, message)
        shadowOf(Looper.getMainLooper()).idle()
        return if (created) transport.webView else null
    }

    @Test
    fun `sign-in pop-ups open over the site and back closes them`() {
        tiles[0].performClick()
        val site = webViews.single()

        val popup = requestWindow(site)!!

        assertEquals(listOf(site, popup), webViews)
        assertEquals(View.VISIBLE, popup.visibility)

        pressBack()

        assertTrue(shadowOf(popup).wasDestroyCalled())
        assertEquals(listOf(site), webViews)
        assertTrue(isShown(R.id.browser))
    }

    @Test
    fun `pages can't open windows on their own`() {
        tiles[0].performClick()

        assertNull(requestWindow(webViews.single(), userGesture = false))
        assertEquals(1, webViews.size)
    }

    @Test
    fun `a pop-up closed by its page returns to the site`() {
        tiles[0].performClick()
        val site = webViews.single()
        val popup = requestWindow(site)!!

        shadowOf(site).webChromeClient.onCloseWindow(popup)

        assertEquals(listOf(site), webViews)
        assertTrue(shadowOf(popup).wasDestroyCalled())
    }

    private fun drawerRows(): List<View> {
        val items = activity.findViewById<ViewGroup>(R.id.drawer_items)
        return (0 until items.childCount).map(items::getChildAt)
    }

    private fun openDrawer() = activity.findViewById<View>(R.id.menu).performClick()

    /** Lets the drawer's slide animation finish. */
    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

    @Test
    fun `the menu opens a drawer with home and every site`() {
        tiles[0].performClick()

        openDrawer()

        assertTrue(isShown(R.id.drawer_layer))
        val rows = drawerRows()
        assertEquals(
            listOf(activity.getString(R.string.menu_home)) + SITES.map { it.name },
            rows.map { it.findViewById<TextView>(R.id.label).text.toString() },
        )
        assertEquals(listOf(false, true, false, false, false), rows.map { it.isSelected })
        assertEquals(View.VISIBLE, rows[1].findViewById<View>(R.id.live).visibility)
        assertEquals(View.GONE, rows[2].findViewById<View>(R.id.live).visibility)
        assertEquals(
            activity.getString(R.string.version, BuildConfig.VERSION_NAME),
            activity.findViewById<TextView>(R.id.drawer_version).text.toString(),
        )
    }

    @Test
    fun `picking a site in the drawer switches to it`() {
        tiles[0].performClick()
        openDrawer()

        drawerRows()[2].performClick()
        settle()

        assertEquals(View.GONE, activity.findViewById<View>(R.id.drawer_layer).visibility)
        assertEquals(SITES[1].name, activity.findViewById<TextView>(R.id.title).text)
        assertEquals(2, webViews.size)
    }

    @Test
    fun `back closes the drawer before leaving the site`() {
        tiles[0].performClick()
        openDrawer()

        pressBack()
        settle()

        assertEquals(View.GONE, activity.findViewById<View>(R.id.drawer_layer).visibility)
        assertTrue(isShown(R.id.browser))
    }

    @Test
    fun `home in the drawer returns to the grid`() {
        tiles[0].performClick()
        openDrawer()

        drawerRows()[0].performClick()
        settle()

        assertTrue(isShown(R.id.home))
        assertEquals(View.GONE, activity.findViewById<View>(R.id.drawer_layer).visibility)
        assertTrue(isLive(tiles[0]))
    }

    @Test
    fun `share sends the current page without tracking parameters`() {
        tiles[0].performClick()
        webViews.single().loadUrl("https://m.facebook.com/story.php?story_fbid=1&id=2&mibextid=abc&utm_source=share")

        activity.findViewById<View>(R.id.share).performClick()

        val chooser = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("https://m.facebook.com/story.php?story_fbid=1&id=2", send.getStringExtra(Intent.EXTRA_TEXT))
    }

    private fun title() = activity.findViewById<TextView>(R.id.title).text.toString()

    @Test
    fun `a link tapped in another app opens on its site`() {
        launch(link("https://www.instagram.com/p/abc/?igsh=xyz"))

        assertTrue(isShown(R.id.browser))
        assertEquals("Instagram", title())
        assertEquals("https://www.instagram.com/p/abc/?igsh=xyz", shadowOf(webViews.single()).lastLoadedUrl)
    }

    @Test
    fun `back from a link returns to the app it came from, then works as usual`() {
        launch(link("https://x.com/someone/status/1"))
        val webView = webViews.single()

        pressBack()

        assertTrue(shadowOf(activity).isTaskMovedToBack)
        assertFalse(shadowOf(webView).wasDestroyCalled())
        assertTrue(isShown(R.id.browser))

        pressBack()

        assertTrue(isShown(R.id.home))
    }

    @Test
    fun `a link for an open site shows over it and back keeps the site as it was`() {
        tiles[0].performClick()
        val site = webViews.single()

        controller.newIntent(link("https://www.facebook.com/share/p/abc/"))

        val window = webViews.last()
        assertEquals(listOf(site, window), webViews)
        assertEquals("https://www.facebook.com/share/p/abc/", shadowOf(window).lastLoadedUrl)
        assertEquals(SITES[0].url, shadowOf(site).lastLoadedUrl)

        pressBack()

        assertTrue(shadowOf(window).wasDestroyCalled())
        assertEquals(listOf(site), webViews)
        assertTrue(shadowOf(activity).isTaskMovedToBack)
        assertTrue(isShown(R.id.browser))
    }

    @Test
    fun `a link arriving while another site is open switches to the link's site`() {
        tiles[1].performClick()

        controller.newIntent(link("https://m.facebook.com/groups/1"))

        assertEquals("Facebook", title())
        assertEquals(2, webViews.size)
        assertEquals(View.GONE, webViews.single { shadowOf(it).lastLoadedUrl == SITES[1].url }.visibility)
    }

    @Test
    fun `links SuperApp opened itself, or that you moved on from, keep back inside the app`() {
        controller.newIntent(link("https://www.linkedin.com/feed/", from = activity.packageName))
        pressBack()
        assertFalse(shadowOf(activity).isTaskMovedToBack)
        assertTrue(isShown(R.id.home))

        controller.newIntent(link("https://x.com/someone"))
        openDrawer()
        drawerRows()[0].performClick()
        settle()
        tiles[2].performClick()
        pressBack()
        assertFalse(shadowOf(activity).isTaskMovedToBack)
        assertTrue(isShown(R.id.home))
    }

    @Test
    fun `other links, and links reopened from Recents, are not opened`() {
        launch(link("https://example.com/"))
        assertTrue(isShown(R.id.home))
        assertTrue(webViews.isEmpty())

        launch(link("https://x.com/someone").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
        assertTrue(isShown(R.id.home))
        assertTrue(webViews.isEmpty())

        controller.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        assertTrue(isShown(R.id.home))
    }

    @Test
    fun `the drawer shows whether links open here and leads to Android's link settings`() {
        tiles[0].performClick()
        openDrawer()

        val row = activity.findViewById<ViewGroup>(R.id.drawer_links).getChildAt(0)
        assertEquals(activity.getString(R.string.links_title), row.findViewById<TextView>(R.id.label).text.toString())
        assertEquals(activity.getString(R.string.links_off), row.findViewById<TextView>(R.id.detail).text.toString())

        row.performClick()
        settle()

        val settings = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, settings.action)
        assertEquals("package:${activity.packageName}", settings.dataString)
        assertEquals(activity.getString(R.string.links_hint), ShadowToast.getTextOfLatestToast())
        assertEquals(View.GONE, activity.findViewById<View>(R.id.drawer_layer).visibility)
    }
}
