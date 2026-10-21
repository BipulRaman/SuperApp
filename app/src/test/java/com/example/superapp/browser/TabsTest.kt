package com.example.superapp.browser

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import com.example.superapp.data.Site
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class TabsTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val container = FrameLayout(activity)
    private val sites = (1..4).map { Site("s$it", "Site $it", "https://s$it.example/", intArrayOf(0)) }

    private fun tabs(maxLive: Int) = Tabs(activity, container, maxLive) {}

    @Test
    fun `opening past the limit closes the least recently used hidden site`() {
        val tabs = tabs(maxLive = 2)
        val first = tabs.show(sites[0]).webView
        val second = tabs.show(sites[1]).webView
        tabs.show(sites[0]) // site 0 is now the most recently used

        tabs.show(sites[2])

        assertEquals(listOf(true, false, true), sites.take(3).map(tabs::isOpen))
        assertTrue(shadowOf(second).wasDestroyCalled())
        assertFalse(shadowOf(first).wasDestroyCalled())
        assertEquals(2, container.childCount)
    }

    @Test
    fun `without a limit every site stays loaded`() {
        val tabs = tabs(maxLive = Int.MAX_VALUE)

        sites.forEach { tabs.show(it) }

        assertTrue(sites.all(tabs::isOpen))
        assertEquals(sites.size, container.childCount)
    }

    @Test
    fun `closing hidden tabs keeps only the one on screen`() {
        val tabs = tabs(maxLive = Int.MAX_VALUE)
        sites.forEach { tabs.show(it) }

        tabs.closeHidden()

        assertEquals(listOf(sites.last()), sites.filter(tabs::isOpen))
        assertEquals(1, container.childCount)
    }

    @Test
    fun `a pop-up opens over its tab and closes back to it`() {
        val tabs = tabs(maxLive = Int.MAX_VALUE)
        val tab = tabs.show(sites[0])

        val popup = tabs.openPopup(tab.webView)!!

        assertSame(popup, tab.top)
        assertSame(tab, tabs.find(popup))
        assertEquals(container.indexOfChild(tab.webView) + 1, container.indexOfChild(popup))
        assertEquals(View.VISIBLE, popup.visibility)

        assertSame(tab, tabs.closePopup(popup))
        assertSame(tab.webView, tab.top)
        assertTrue(shadowOf(popup).wasDestroyCalled())
        assertNull(tabs.find(popup))
    }

    @Test
    fun `pop-ups are hidden, shown and closed with their tab`() {
        val tabs = tabs(maxLive = Int.MAX_VALUE)
        val tab = tabs.show(sites[0])
        val popup = tabs.openPopup(tab.webView)!!

        tabs.show(sites[1])
        assertEquals(View.GONE, popup.visibility)
        assertTrue(shadowOf(popup).wasOnPauseCalled())

        tabs.show(sites[0])
        assertEquals(View.VISIBLE, popup.visibility)

        tabs.show(sites[1])
        tabs.closeHidden()
        assertTrue(shadowOf(popup).wasDestroyCalled())
        assertTrue(shadowOf(tab.webView).wasDestroyCalled())
    }

    @Test
    fun `discarding a pop-up keeps its site open`() {
        val tabs = tabs(maxLive = Int.MAX_VALUE)
        val tab = tabs.show(sites[0])
        val popup = tabs.openPopup(tab.webView)!!

        assertNull(tabs.discard(popup))

        assertTrue(tabs.isOpen(sites[0]))
        assertSame(tab.webView, tab.top)
    }

    @Test
    fun `closing the tab on screen destroys it and its windows, and keeps the other sites`() {
        val tabs = tabs(maxLive = Int.MAX_VALUE)
        val other = tabs.show(sites[1])
        val tab = tabs.show(sites[0])
        val popup = tabs.openPopup(tab.webView)!!

        assertSame(tab, tabs.closeActive())

        assertNull(tabs.active)
        assertFalse(tabs.isOpen(sites[0]))
        assertTrue(tabs.isOpen(sites[1]))
        assertTrue(shadowOf(tab.webView).wasDestroyCalled())
        assertTrue(shadowOf(popup).wasDestroyCalled())
        assertFalse(shadowOf(other.webView).wasDestroyCalled())
        assertEquals(listOf(other.webView), (0 until container.childCount).map(container::getChildAt))
        assertNull(tabs.closeActive())
    }

    @Test
    fun `pages run only while a site is on screen and the app is in front`() {
        val tabs = tabs(maxLive = Int.MAX_VALUE)
        tabs.onStart()

        tabs.show(sites[0])
        assertFalse(tabs.timersPaused)

        tabs.hideActive() // home grid
        assertTrue(tabs.timersPaused)

        tabs.show(sites[1])
        assertFalse(tabs.timersPaused)

        tabs.closeActive()
        assertTrue(tabs.timersPaused)

        tabs.destroyAll()
        assertFalse(tabs.timersPaused) // Process-wide, so a new activity mustn't start frozen.
    }

    @Test
    fun `leaving the app stops media first, then pauses every page`() {
        val tabs = tabs(maxLive = Int.MAX_VALUE)
        tabs.onStart()
        val tab = tabs.show(sites[0])
        val popup = tabs.openPopup(tab.webView)!!

        tabs.onStop()

        for (view in listOf(tab.webView, popup)) {
            assertTrue(shadowOf(view).wasOnPauseCalled())
            assertEquals(PAUSE_MEDIA_JS, shadowOf(view).lastEvaluatedJavascript)
        }
        assertFalse(tabs.timersPaused) // Not until every page has stopped its media...
        shadowOf(tab.webView).lastEvaluatedJavascriptCallback.onReceiveValue(null)
        assertFalse(tabs.timersPaused)
        shadowOf(popup).lastEvaluatedJavascriptCallback.onReceiveValue(null)
        assertTrue(tabs.timersPaused)

        tabs.onStart()
        assertFalse(tabs.timersPaused)
    }

    @Test
    fun `pages pause even if one never answers, but not after coming back`() {
        val tabs = tabs(maxLive = Int.MAX_VALUE)
        tabs.onStart()
        val webView = tabs.show(sites[0]).webView

        tabs.onStop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertTrue(tabs.timersPaused)

        tabs.onStart()
        tabs.onStop()
        tabs.onStart()
        shadowOf(webView).lastEvaluatedJavascriptCallback.onReceiveValue(null)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertFalse(tabs.timersPaused)
    }

    @Test
    fun `in battery saver only one site stays loaded`() {
        val tabs = tabs(maxLive = Int.MAX_VALUE)
        tabs.prewarm()
        val first = tabs.show(sites[0]).webView // Takes the pre-warmed WebView.
        val second = tabs.show(sites[1]).webView
        tabs.show(sites[2])

        tabs.saveBattery = true

        assertEquals(listOf(false, false, true), sites.take(3).map(tabs::isOpen))
        assertTrue(shadowOf(first).wasDestroyCalled() && shadowOf(second).wasDestroyCalled())

        tabs.show(sites[3])
        assertEquals(listOf(false, false, false, true), sites.map(tabs::isOpen))

        tabs.hideActive() // The site last shown stays loaded on the grid, paused.
        assertTrue(tabs.isOpen(sites[3]))
        tabs.prewarm()
        assertEquals(1, container.childCount)

        tabs.saveBattery = false
        tabs.show(sites[0])
        assertEquals(listOf(true, false, false, true), sites.map(tabs::isOpen))
    }

    @Test
    fun `a link for a site that isn't open opens the site straight at the link`() {
        val tabs = tabs(maxLive = Int.MAX_VALUE)

        val tab = tabs.showLink(sites[0], "https://s1.example/post")

        assertSame(tab, tabs.active)
        assertSame(tab.webView, tab.top)
        assertNull(tab.linkWindow)
        assertEquals("https://s1.example/post", shadowOf(tab.webView).lastLoadedUrl)
        assertEquals(1, container.childCount)
    }

    @Test
    fun `a link for an open site shows over it, and a newer link replaces it`() {
        val tabs = tabs(maxLive = Int.MAX_VALUE)
        val tab = tabs.show(sites[0])
        tabs.show(sites[1])

        val first = tabs.showLink(sites[0], "https://s1.example/a").top

        assertSame(tab, tabs.active)
        assertSame(first, tab.linkWindow)
        assertEquals("https://s1.example/a", shadowOf(first).lastLoadedUrl)
        assertEquals(sites[0].url, shadowOf(tab.webView).lastLoadedUrl)
        assertEquals(PAUSE_MEDIA_JS, shadowOf(tab.webView).lastEvaluatedJavascript)
        assertEquals(View.VISIBLE, first.visibility)

        val second = tabs.showLink(sites[0], "https://s1.example/b").top

        assertTrue(shadowOf(first).wasDestroyCalled())
        assertEquals(listOf(tab.webView, second), tab.views)
        assertSame(second, tab.linkWindow)

        tabs.closePopup(second)

        assertNull(tab.linkWindow)
        assertSame(tab.webView, tab.top)
    }
}
