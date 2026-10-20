package com.example.superapp

import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.widget.TextView
import com.example.superapp.browser.PAUSE_MEDIA_JS
import com.example.superapp.data.SITES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    private val controller: ActivityController<MainActivity> = Robolectric.buildActivity(MainActivity::class.java).setup()
    private val activity: MainActivity = controller.get()

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

        activity.findViewById<View>(R.id.back).performClick()

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
}
