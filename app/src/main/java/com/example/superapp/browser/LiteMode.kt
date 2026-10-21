package com.example.superapp.browser

import android.content.Context
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.WeakHashMap

/*
 * Lite mode saves data and battery: audio and video only play when you tap them. The sites'
 * feeds autoplay (muted) videos, which is where most of their data and battery goes. Pages are
 * also told to save data (navigator.connection.saveData), which some use to load less. It
 * applies to every page and frame, the sites' and any other.
 */

/**
 * Pauses audio and video that start without the user's tap (autoplay, a feed playing videos as
 * they scroll by); once tapped, a player plays freely. Re-running it turns lite mode back on.
 */
internal const val LITE_MODE_SCRIPT = """(function () {
  var KEY = Symbol.for('superapp.lite');
  if (window[KEY]) { window[KEY].on = true; return; }
  var state = window[KEY] = { on: true };
  var tapped = new WeakSet();
  document.addEventListener('play', function (event) {
    var media = event.target;
    if (!state.on || !(media instanceof HTMLMediaElement) || tapped.has(media)) return;
    var activation = navigator.userActivation;
    if (!activation || activation.isActive) tapped.add(media);
    else media.pause();
  }, true);
  try {
    if (navigator.connection) {
      Object.defineProperty(navigator.connection, 'saveData', { configurable: true, get: function () { return state.on; } });
    }
  } catch (e) {}
})();"""

/** Turns lite mode off in a page that has it. */
internal const val LITE_MODE_OFF_SCRIPT =
    "(function () { var state = window[Symbol.for('superapp.lite')]; if (state) state.on = false; })();"

/** Lite mode's setting (kept across restarts) and its effect on the WebViews; see above. */
internal class LiteMode(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val scripts = WeakHashMap<WebView, ScriptHandler>()

    var isOn: Boolean = prefs.getBoolean(KEY, false)
        private set

    /** Sets up a new WebView, before it loads anything. */
    fun setUp(webView: WebView) {
        webView.settings.mediaPlaybackRequiresUserGesture = isOn
        if (isOn) addScript(webView)
    }

    /** Turns lite mode on or off for the pages in [webViews] now, and for every page from now on. */
    fun set(on: Boolean, webViews: List<WebView>) {
        isOn = on
        prefs.edit().putBoolean(KEY, on).apply()
        for (webView in webViews) {
            webView.settings.mediaPlaybackRequiresUserGesture = on
            if (on) {
                addScript(webView)
                webView.evaluateJavascript(LITE_MODE_SCRIPT, null)
            } else {
                scripts.remove(webView)?.remove()
                webView.evaluateJavascript(LITE_MODE_OFF_SCRIPT, null)
            }
        }
    }

    private fun addScript(webView: WebView) {
        if (!documentStartScripts || webView in scripts) return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            scripts[webView] = WebViewCompat.addDocumentStartJavaScript(webView, LITE_MODE_SCRIPT, setOf("*"))
        }
    }

    private companion object {
        const val KEY = "lite_mode"
    }
}
