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
        // "Sign in with Google/Apple" and similar buttons open a pop-up window and report back to
        // the page through window.opener; without real windows those logins silently break.
        setSupportMultipleWindows(true)
        javaScriptCanOpenWindowsAutomatically = true
        useChromeIdentity()
    }
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    // Visible only until the page paints; match the theme the page will most likely use.
    val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    setBackgroundColor(if (night) context.getColor(R.color.bg) else Color.WHITE)
}
