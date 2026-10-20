package com.example.superapp.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class WebViewsTest {

    @Test
    fun `phone WebView user agent becomes Chrome's reduced user agent`() {
        val webView = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP2A.240805.005; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/127.0.6533.103 Mobile Safari/537.36"

        assertEquals(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Mobile Safari/537.36",
            chromeUserAgent(webView),
        )
    }

    @Test
    fun `tablet user agent stays non-mobile`() {
        val webView = "Mozilla/5.0 (Linux; Android 14; Pixel Tablet Build/AP2A.240805.005; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.39 Safari/537.36"

        assertEquals(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            chromeUserAgent(webView),
        )
    }

    @Test
    fun `user agent without a Chrome version only loses the WebView marker`() {
        assertEquals("Custom (Linux; Android 14)", chromeUserAgent("Custom (Linux; Android 14; wv)"))
    }
}
