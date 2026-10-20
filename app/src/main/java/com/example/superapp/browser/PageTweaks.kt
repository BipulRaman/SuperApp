package com.example.superapp.browser

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * A script injected into one site's pages (including its subdomains) to fix things up for
 * SuperApp, e.g. hiding "Open app" nags. Scripts never run on any other site.
 */
internal class PageTweak(val domain: String, val script: String) {
    val originRules: Set<String> = setOf("https://$domain", "https://*.$domain")

    fun appliesTo(host: String): Boolean = host == domain || host.endsWith(".$domain")
}

/*
 * Facebook's mobile site pins an "Open app" button to its top bar, and a bar with a big "Open app"
 * button to the bottom of the feed. The buttons carry data-log-comp-type="...open_app" (language
 * independent) and aria-label="Open app"; pinned bars are .fixed-container.top/.bottom/.above-bottom.
 * CSS hides the buttons and any bottom bar holding one, which keeps working as Facebook re-renders;
 * a light sweep also catches a bottom bar wrapped differently. Bars in the top half (with Log in)
 * are never hidden.
 */
private const val FACEBOOK_OPEN_APP_HIDER = """(function () {
  var ID = 'superapp-open-app-hider';
  if (document.getElementById(ID)) return;
  var OPEN_APP = '[data-log-comp-type*="open_app" i],[role="button"][aria-label="Open app" i]';
  var CSS = OPEN_APP + '{display:none!important}' +
    '.fixed-container:is(.bottom,.above-bottom):has(' + OPEN_APP + '){display:none!important}';
  function addStyle() {
    if (document.getElementById(ID)) return true;
    var root = document.head || document.documentElement;
    if (!root) return false;
    var style = document.createElement('style');
    style.id = ID;
    style.textContent = CSS;
    root.appendChild(style);
    return true;
  }
  var pending = false;
  function sweep() {
    pending = false;
    var buttons = document.querySelectorAll(OPEN_APP);
    for (var i = 0; i < buttons.length; i++) {
      for (var e = buttons[i].parentElement; e && e !== document.body; e = e.parentElement) {
        if (getComputedStyle(e).position !== 'fixed') continue;
        var r = e.getBoundingClientRect();
        if (r.height > 0 && r.top > innerHeight / 2) e.style.setProperty('display', 'none', 'important');
        break;
      }
    }
  }
  if (!addStyle()) document.addEventListener('DOMContentLoaded', addStyle, { once: true });
  new MutationObserver(function () {
    if (!pending) { pending = true; setTimeout(sweep, 500); }
  }).observe(document, { childList: true, subtree: true });
})();"""

internal val PAGE_TWEAKS = listOf(PageTweak("facebook.com", FACEBOOK_OPEN_APP_HIDER))

/** Whether this WebView can run scripts as a page starts loading, before the page's own scripts. */
internal val documentStartScripts: Boolean by lazy {
    try {
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    } catch (e: RuntimeException) {
        false
    }
}

/** Installs the page tweaks so they run at the start of every matching page and frame. */
internal fun WebView.addPageTweaks() {
    if (!documentStartScripts) return
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        for (tweak in PAGE_TWEAKS) WebViewCompat.addDocumentStartJavaScript(this, tweak.script, tweak.originRules)
    }
}

/** Fallback for WebViews without document-start scripts: runs the matching tweaks on a shown page. */
internal fun WebView.runPageTweaks(url: String) {
    val host = url.substringAfter("://", "").substringBefore('/').substringBefore(':').lowercase()
    for (tweak in PAGE_TWEAKS) if (tweak.appliesTo(host)) evaluateJavascript(tweak.script, null)
}
