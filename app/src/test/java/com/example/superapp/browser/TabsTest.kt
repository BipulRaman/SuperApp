package com.example.superapp.browser

import android.app.Activity
import android.widget.FrameLayout
import com.example.superapp.data.Site
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

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
}
