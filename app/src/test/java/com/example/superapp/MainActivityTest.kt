package com.example.superapp

import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.PowerManager
import android.provider.Settings
import android.view.ContextMenu
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebView.HitTestResult
import android.widget.Switch
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
import org.robolectric.shadows.ShadowWebView
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

    /** A navigation the page asks for, e.g. after a tap on a link. */
    private fun navigation(url: String, gesture: Boolean = true) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = true
        override fun isRedirect() = false
        override fun hasGesture() = gesture
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private fun installWhatsApp() {
        val component = ComponentName("com.whatsapp", "com.whatsapp.Main")
        val filter = IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
            addDataScheme("https")
            addDataAuthority("api.whatsapp.com", null)
        }
        shadowOf(activity.packageManager).addActivityIfNotPresent(component)
        shadowOf(activity.packageManager).addIntentFilterForActivity(component, filter)
    }

    @Test
    fun `share to WhatsApp opens the WhatsApp app, other links stay in the page`() {
        shadowOf(activity.application).checkActivities(true)
        installWhatsApp()
        tiles[0].performClick()
        val site = webViews.single()
        val client = shadowOf(site).webViewClient
        val share = "https://api.whatsapp.com/send?text=https%3A%2F%2Fwww.facebook.com%2Fshare%2Fp%2Fabc%2F"

        assertTrue(client.shouldOverrideUrlLoading(site, navigation(share)))
        assertEquals(share, shadowOf(activity).nextStartedActivity.dataString)

        assertFalse(client.shouldOverrideUrlLoading(site, navigation(share, gesture = false)))
        assertFalse(client.shouldOverrideUrlLoading(site, navigation("https://example.com/article")))
        assertFalse(client.shouldOverrideUrlLoading(site, navigation("https://m.facebook.com/groups/1")))
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `a pop-up opened only to reach an app closes once the app opens`() {
        shadowOf(activity.application).checkActivities(true)
        installWhatsApp()
        tiles[0].performClick()
        val site = webViews.single()
        val popup = requestWindow(site)!!

        assertTrue(shadowOf(popup).webViewClient.shouldOverrideUrlLoading(popup, navigation("https://api.whatsapp.com/send?text=hi")))
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(shadowOf(popup).wasDestroyCalled())
        assertEquals(listOf(site), webViews)
        assertTrue(isShown(R.id.browser))
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
        assertNull(send.getStringExtra(Intent.EXTRA_TITLE)) // Just the link.
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

    private fun liteSwitch(id: Int) = activity.findViewById<Switch>(id)

    @Test
    fun `lite mode switches sit at the top of home and drawer and stay in sync`() {
        val homeSwitch = liteSwitch(R.id.home_lite)
        val homeContent = homeSwitch.parent as ViewGroup
        assertEquals(1, homeContent.indexOfChild(homeSwitch)) // Right under the header, above the tiles.
        assertEquals(2, homeContent.indexOfChild(activity.findViewById(R.id.grid)))
        val drawerSwitch = liteSwitch(R.id.drawer_lite)
        val panel = drawerSwitch.parent as ViewGroup
        assertTrue(panel.indexOfChild(drawerSwitch) < panel.indexOfChild(activity.findViewById<View>(R.id.drawer_items).parent as View))
        assertFalse(homeSwitch.isChecked || drawerSwitch.isChecked)

        tiles[0].performClick()
        val webView = webViews.single()
        openDrawer()
        drawerSwitch.performClick()

        assertTrue(drawerSwitch.isChecked && homeSwitch.isChecked)
        assertTrue(webView.settings.mediaPlaybackRequiresUserGesture)
        assertTrue(isShown(R.id.drawer_layer)) // Switching doesn't close the drawer.

        homeSwitch.performClick()

        assertFalse(drawerSwitch.isChecked || homeSwitch.isChecked)
        assertFalse(webView.settings.mediaPlaybackRequiresUserGesture)
    }

    @Test
    fun `lite mode is remembered`() {
        liteSwitch(R.id.home_lite).performClick()

        launch(Intent(activity, MainActivity::class.java))

        assertTrue(liteSwitch(R.id.home_lite).isChecked && liteSwitch(R.id.drawer_lite).isChecked)
        tiles[0].performClick()
        assertTrue(webViews.single().settings.mediaPlaybackRequiresUserGesture)
    }

    /** The long-press menu for a hit test of [type] on [webView], built into Android's real context menu. */
    private fun longPress(webView: WebView, type: Int, extra: String?): ContextMenu {
        shadowOf(webView).setHitTestResult(ShadowWebView.createHitTestResult(type, extra))
        val menu = Class.forName("com.android.internal.view.menu.ContextMenuBuilder")
            .getConstructor(Context::class.java).newInstance(activity) as ContextMenu
        activity.onCreateContextMenu(menu, webView, null)
        return menu
    }

    private fun ContextMenu.titles() = (0 until size()).map { getItem(it).title.toString() }

    private fun ContextMenu.tap(title: Int) {
        val item = (0 until size()).map(::getItem).single { it.title == activity.getString(title) }
        item.javaClass.getMethod("invoke").invoke(item)
    }

    @Test
    fun `long-pressing a link offers to open, copy or share it, without tracking`() {
        tiles[0].performClick()
        val link = "https://example.com/article?utm_source=facebook&id=7&fbclid=abc"

        val menu = longPress(webViews.single(), HitTestResult.SRC_ANCHOR_TYPE, link)

        assertEquals(
            listOf(R.string.menu_open_in_browser, R.string.menu_copy_link, R.string.menu_share_link).map(activity::getString),
            menu.titles(),
        )
        menu.tap(R.string.menu_copy_link)
        val clip = activity.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals("https://example.com/article?id=7", clip.getItemAt(0).text.toString())

        menu.tap(R.string.menu_share_link)
        val chooser = shadowOf(activity).nextStartedActivity
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals("https://example.com/article?id=7", send.getStringExtra(Intent.EXTRA_TEXT))

        menu.tap(R.string.menu_open_in_browser)
        val browser = shadowOf(activity).nextStartedActivity
        assertEquals(link, browser.dataString)
        assertTrue(browser.selector!!.hasCategory(Intent.CATEGORY_APP_BROWSER))
    }

    @Test
    fun `a link through facebook's click-tracking redirect is shared as where it leads`() {
        tiles[0].performClick()
        val shim = "https://lm.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Fnews%3Fid%3D7%26fbclid%3DIwAR0&h=AT0abc&__tn__=-UK-R"

        val menu = longPress(webViews.single(), HitTestResult.SRC_ANCHOR_TYPE, shim)

        assertEquals("example.com/news", menu.javaClass.getMethod("getHeaderTitle").invoke(menu).toString())
        menu.tap(R.string.menu_share_link)
        val send = shadowOf(activity).nextStartedActivity.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals("https://example.com/news?id=7", send.getStringExtra(Intent.EXTRA_TEXT))
        menu.tap(R.string.menu_copy_link)
        val clip = activity.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertEquals("https://example.com/news?id=7", clip.getItemAt(0).text.toString())
    }

    @Test
    fun `long-pressing an image offers to save it or copy its address`() {
        tiles[0].performClick()
        val image = "https://pbs.twimg.com/media/ABC?format=png&name=small"

        val menu = longPress(webViews.single(), HitTestResult.IMAGE_TYPE, image)

        assertEquals(listOf(R.string.menu_save_image, R.string.menu_copy_image_link).map(activity::getString), menu.titles())
        menu.tap(R.string.menu_save_image)
        val manager = activity.getSystemService(DownloadManager::class.java)
        val request = (0L..3L).firstNotNullOf { shadowOf(manager).getRequest(it) }
        assertEquals(image, shadowOf(request).uri.toString())
        assertTrue(shadowOf(request).destination.toString().endsWith(".png"))
    }

    @Test
    fun `an image inside a link offers both`() {
        tiles[0].performClick()

        val menu = longPress(webViews.single(), HitTestResult.SRC_IMAGE_ANCHOR_TYPE, "https://cdn.example.com/p.jpg")

        assertEquals(5, menu.size())
    }

    @Test
    fun `long-pressing anything else shows no menu`() {
        tiles[0].performClick()
        val webView = webViews.single()

        assertEquals(0, longPress(webView, HitTestResult.UNKNOWN_TYPE, null).size())
        assertEquals(0, longPress(webView, HitTestResult.EDIT_TEXT_TYPE, null).size())
        assertEquals(0, longPress(webView, HitTestResult.SRC_ANCHOR_TYPE, "javascript:alert(1)").size())
    }
}