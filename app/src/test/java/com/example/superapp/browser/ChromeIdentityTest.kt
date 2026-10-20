package com.example.superapp.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChromeIdentityTest {

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
        assertNull(chromeMajorVersion("Custom (Linux; Android 14; wv)"))
    }

    @Test
    fun `client hint brands match what Chrome itself sends`() {
        // Sec-CH-UA values observed from real Chrome releases.
        val expected = mapOf(
            120 to """"Not_A Brand";v="8", "Chromium";v="120", "Google Chrome";v="120"""",
            124 to """"Chromium";v="124", "Google Chrome";v="124", "Not-A.Brand";v="99"""",
            131 to """"Google Chrome";v="131", "Chromium";v="131", "Not_A Brand";v="24"""",
            138 to """"Not)A;Brand";v="8", "Chromium";v="138", "Google Chrome";v="138"""",
            140 to """"Chromium";v="140", "Not=A?Brand";v="24", "Google Chrome";v="140"""",
            141 to """"Google Chrome";v="141", "Not?A_Brand";v="8", "Chromium";v="141"""",
        )
        for ((major, header) in expected) {
            val brands = chromeBrands(major, "$major.0.7390.122")
            assertEquals(header, brands.joinToString(", ") { "\"${it.name}\";v=\"${it.majorVersion}\"" })
        }
    }

    @Test
    fun `full version list uses the engine version for Chrome brands`() {
        val brands = chromeBrands(141, "141.0.7390.122").associateBy { it.name }

        assertEquals("141.0.7390.122", brands.getValue("Google Chrome").fullVersion)
        assertEquals("141.0.7390.122", brands.getValue("Chromium").fullVersion)
        assertEquals("8.0.0.0", brands.getValue("Not?A_Brand").fullVersion)
    }

    @Test
    fun `platform version is reported with three parts`() {
        assertEquals("14.0.0", platformVersion("14"))
        assertEquals("8.1.0", platformVersion("8.1.0"))
        assertEquals("16.1.0", platformVersion("16.1"))
        assertEquals("", platformVersion("Baklava"))
    }
}
