package com.example.superapp.browser

import android.app.Activity
import android.webkit.WebView
import android.webkit.WebView.HitTestResult
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
class LongPressAndLiteModeTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test
    fun `long-pressed web links and images are recognized, anything else is not`() {
        val link = longPressTarget(HitTestResult.SRC_ANCHOR_TYPE, "https://example.com/a")!!
        assertEquals("https://example.com/a", link.link)
        assertNull(link.image)

        val image = longPressTarget(HitTestResult.IMAGE_TYPE, "https://cdn.example.com/p.jpg")!!
        assertEquals("https://cdn.example.com/p.jpg", image.image)
        assertFalse(image.hasLink)

        val imageLink = longPressTarget(HitTestResult.SRC_IMAGE_ANCHOR_TYPE, "https://cdn.example.com/p.jpg")!!
        assertEquals("https://cdn.example.com/p.jpg", imageLink.image)
        assertTrue(imageLink.hasLink && imageLink.linkInImage)

        assertNull(longPressTarget(HitTestResult.SRC_ANCHOR_TYPE, "javascript:alert(1)"))
        assertNull(longPressTarget(HitTestResult.IMAGE_TYPE, "data:image/png;base64,AAAA"))
        assertNull(longPressTarget(HitTestResult.EDIT_TEXT_TYPE, null))
        assertNull(longPressTarget(HitTestResult.UNKNOWN_TYPE, null))
    }

    @Test
    fun `the menu title shows a short address`() {
        assertEquals("www.facebook.com/groups/1", displayUrl("https://www.facebook.com/groups/1/?ref=share"))
        assertEquals(60, displayUrl("https://example.com/" + "x".repeat(100)).length)
    }

    @Test
    fun `saved images get their real type`() {
        assertEquals("image/png", imageMimeType("https://cdn.example.com/a/photo.png?x=1"))
        assertEquals("image/webp", imageMimeType("https://pbs.twimg.com/media/ABC?format=webp&name=small"))
        assertEquals("image/jpeg", imageMimeType("https://scontent.xx.fbcdn.net/v/t39/123_n.jpg?stp=dst"))
        assertEquals("image/jpeg", imageMimeType("https://example.com/image"))
    }

    @Test
    fun `lite mode applies to every page now and later, and is remembered`() {
        val lite = LiteMode(activity)
        val open = WebView(activity)
        lite.setUp(open)
        assertFalse(lite.isOn)
        assertFalse(open.settings.mediaPlaybackRequiresUserGesture)

        lite.set(true, listOf(open))

        assertTrue(open.settings.mediaPlaybackRequiresUserGesture)
        assertEquals(LITE_MODE_SCRIPT, shadowOf(open).lastEvaluatedJavascript)
        val restarted = LiteMode(activity)
        assertTrue(restarted.isOn)
        val later = WebView(activity)
        restarted.setUp(later)
        assertTrue(later.settings.mediaPlaybackRequiresUserGesture)

        lite.set(false, listOf(open, later))

        assertFalse(open.settings.mediaPlaybackRequiresUserGesture || later.settings.mediaPlaybackRequiresUserGesture)
        assertEquals(LITE_MODE_OFF_SCRIPT, shadowOf(later).lastEvaluatedJavascript)
        assertFalse(LiteMode(activity).isOn)
    }

    @Test
    fun `the lite mode script keeps players the user didn't tap from playing or downloading`() {
        // Stops autoplay, preloading and the players' own downloads, and starts a player on a tap.
        assertTrue(LITE_MODE_SCRIPT.contains("media.play = function play() {"))
        assertTrue(LITE_MODE_SCRIPT.contains("nativeSetAttribute.call(player, 'preload', 'none')"))
        assertTrue(LITE_MODE_SCRIPT.contains("window.fetch = function fetch(input, init) {"))
        assertTrue(LITE_MODE_SCRIPT.contains("xhr.send = function send() {"))
        assertTrue(LITE_MODE_SCRIPT.contains("window.addEventListener('pointerup'"))
        assertTrue(LITE_MODE_SCRIPT.contains("'saveData'"))
        assertTrue(LITE_MODE_OFF_SCRIPT.contains("state.turnOff()"))
        // Kotlin would read a dollar sign in these raw strings as a template.
        assertFalse(LITE_MODE_SCRIPT.contains("$") || LITE_MODE_OFF_SCRIPT.contains("$"))
    }
}
