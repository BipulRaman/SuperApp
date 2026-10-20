package com.example.superapp.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.webkit.CookieManager
import android.webkit.WebView
import com.example.superapp.R

/** Pauses audio/video in a page that is being hidden, so it doesn't keep playing in the background. */
internal const val PAUSE_MEDIA_JS =
    "document.querySelectorAll('video,audio').forEach(function(m){m.pause()})"

private val CHROME_MAJOR = Regex("""Chrome/(\d+)""")

/** A WebView set up like a mobile browser tab. */
@SuppressLint("SetJavaScriptEnabled")
internal fun newWebView(context: Context): WebView = WebView(context).apply {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        useWideViewPort = true
        loadWithOverviewMode = true
        builtInZoomControls = true
        displayZoomControls = false
        allowFileAccess = false
        allowContentAccess = false
        userAgentString = chromeUserAgent(userAgentString)
    }
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    // Visible only until the page paints; match the theme the page will most likely use.
    val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    setBackgroundColor(if (night) context.getColor(R.color.bg) else Color.WHITE)
}

/**
 * Rewrites WebView's default user agent into the reduced one Chrome for Android sends, keeping the
 * real engine's major version. Sites then serve their normal mobile web app instead of WebView
 * fallbacks or "open in the app" walls.
 */
internal fun chromeUserAgent(webViewUserAgent: String): String {
    val major = CHROME_MAJOR.find(webViewUserAgent)?.groupValues?.get(1)
        ?: return webViewUserAgent.replace("; wv)", ")")
    val mobile = if (" Mobile " in webViewUserAgent) " Mobile" else ""
    return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0$mobile Safari/537.36"
}
