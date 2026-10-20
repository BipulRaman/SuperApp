package com.example.superapp.browser

import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/*
 * Sites refuse logins (or push "open the app") when they detect an embedded WebView, so every
 * WebView identifies itself exactly like Chrome for Android on the same engine version:
 *  - the User-Agent string, in Chrome's reduced format;
 *  - User-Agent Client Hints: the Sec-CH-UA* request headers and navigator.userAgentData, which
 *    otherwise name "Android WebView" and contradict the User-Agent string.
 * (WebView no longer sends the old X-Requested-With header, so there is nothing to hide there.)
 */

private val CHROME_VERSION = Regex("""Chrome/(\d+)""")
private val FULL_VERSION = Regex("""^\d+\.\d+\.\d+\.\d+""")

/** Applies Chrome's identity to these settings; call before the first page loads. */
internal fun WebSettings.useChromeIdentity() {
    val webViewUserAgent = userAgentString
    userAgentString = chromeUserAgent(webViewUserAgent)
    val major = chromeMajorVersion(webViewUserAgent) ?: return
    try {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            WebSettingsCompat.setUserAgentMetadata(this, chromeMetadata(major, isMobile(webViewUserAgent)))
        }
    } catch (e: RuntimeException) {
        // Older or unusual WebView builds: the Chrome User-Agent string alone still applies.
    }
}

private fun chromeMetadata(major: Int, mobile: Boolean): UserAgentMetadata {
    val fullVersion = WebView.getCurrentWebViewPackage()?.versionName
        ?.let { FULL_VERSION.find(it)?.value }
        ?.takeIf { it.substringBefore('.') == major.toString() }
        ?: "$major.0.0.0"
    val brands = chromeBrands(major, fullVersion).map {
        UserAgentMetadata.BrandVersion.Builder()
            .setBrand(it.name)
            .setMajorVersion(it.majorVersion)
            .setFullVersion(it.fullVersion)
            .build()
    }
    // Same values Chrome reports: real OS version and model (high-entropy hints), empty architecture.
    val metadata = UserAgentMetadata.Builder()
        .setBrandVersionList(brands)
        .setFullVersion(fullVersion)
        .setPlatform("Android")
        .setPlatformVersion(platformVersion(Build.VERSION.RELEASE))
        .setModel(Build.MODEL)
        .setMobile(mobile)
        .setArchitecture("")
        .setBitness(UserAgentMetadata.BITNESS_DEFAULT)
        .setWow64(false)
    if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA_FORM_FACTORS)) {
        metadata.setFormFactors(listOf(if (mobile) UserAgentMetadata.FORM_FACTOR_MOBILE else UserAgentMetadata.FORM_FACTOR_TABLET))
    }
    return metadata.build()
}

/** One entry of the Sec-CH-UA brand list. */
internal data class Brand(val name: String, val majorVersion: String, val fullVersion: String)

private val GREASE_CHARS = listOf(" ", "(", ":", "-", ".", "/", ")", ";", "=", "?", "_")
private val GREASE_VERSIONS = listOf("8", "99", "24")
private val BRAND_ORDERS = listOf(
    intArrayOf(0, 1, 2), intArrayOf(0, 2, 1), intArrayOf(1, 0, 2),
    intArrayOf(1, 2, 0), intArrayOf(2, 0, 1), intArrayOf(2, 1, 0),
)

/**
 * The brand list Chrome itself sends for a major version: Chromium's "GREASE" algorithm, seeded by
 * the major version, picks the decoy brand, its version and the order. For 141 that is
 * `"Google Chrome";v="141", "Not?A_Brand";v="8", "Chromium";v="141"`.
 */
internal fun chromeBrands(major: Int, fullVersion: String): List<Brand> {
    val greaseName = "Not" + GREASE_CHARS[major % GREASE_CHARS.size] + "A" + GREASE_CHARS[(major + 1) % GREASE_CHARS.size] + "Brand"
    val greaseVersion = GREASE_VERSIONS[major % GREASE_VERSIONS.size]
    val order = BRAND_ORDERS[major % BRAND_ORDERS.size]
    val brands = arrayOfNulls<Brand>(3)
    brands[order[0]] = Brand(greaseName, greaseVersion, "$greaseVersion.0.0.0")
    brands[order[1]] = Brand("Chromium", major.toString(), fullVersion)
    brands[order[2]] = Brand("Google Chrome", major.toString(), fullVersion)
    return brands.map { checkNotNull(it) }
}

/**
 * Rewrites WebView's default user agent into the reduced one Chrome for Android sends, keeping the
 * real engine's major version.
 */
internal fun chromeUserAgent(webViewUserAgent: String): String {
    val major = chromeMajorVersion(webViewUserAgent) ?: return webViewUserAgent.replace("; wv)", ")")
    val mobile = if (isMobile(webViewUserAgent)) " Mobile" else ""
    return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0$mobile Safari/537.36"
}

internal fun chromeMajorVersion(userAgent: String): Int? = CHROME_VERSION.find(userAgent)?.groupValues?.get(1)?.toIntOrNull()

private fun isMobile(userAgent: String) = " Mobile " in userAgent

/** Android's release string as Chrome reports it: "14" → "14.0.0". Non-numeric releases → "". */
internal fun platformVersion(release: String): String {
    val parts = release.split('.').takeWhile { it.isNotEmpty() && it.all(Char::isDigit) }.take(3)
    return if (parts.isEmpty()) "" else (parts + List(3 - parts.size) { "0" }).joinToString(".")
}
