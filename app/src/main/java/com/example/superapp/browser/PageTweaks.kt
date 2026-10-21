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
 * Facebook's mobile site pins bars to the top and bottom of the screen: .fixed-container.top and
 * .bottom (or .above-bottom), each with its height in data-actual-height. When you're logged in,
 * the bottom one is a white bar holding just a big "Open app" button that has no label or logging
 * tag, only its text; the logged-out top bar has a tagged "Open app" button next to Log in.
 * The script hides every "Open app" control in a small pinned bar (found by Facebook's
 * language-independent tag or label, or by its text), and the whole bottom bar when that is the
 * only control in it. Big pinned layers (dialogs, sheets, viewers), bars with anything else to
 * use (e.g. the comment box) and the page itself are never hidden. Facebook reuses these nodes,
 * so the marks are re-checked, before the change is drawn, whenever a page change touches a
 * pinned bar or a marked element. Telling costs about as much as the change itself, so the rest
 * of the page's changes (feed, timestamps...) never trigger a scan of the whole page (battery).
 */
private const val FACEBOOK_OPEN_APP_HIDER = """(function () {
  var KEY = Symbol.for('superapp.open-app-hider');
  if (window[KEY] || window.top !== window) return;
  window[KEY] = true;
  var BAR = '.fixed-container';
  var TAGGED = '[data-log-comp-type*="open_app" i],[role="button"][aria-label="Open app" i]';
  var CONTROLS = '[role="button"],[role="link"],a[href],button,input,textarea,select,[contenteditable]';
  var TEXT = /^(open|use|get)( in| the)? app$/i;
  var ICONS = /[\uE000-\uF8FF]|[\uDB80-\uDBFF][\uDC00-\uDFFF]/g;
  var BUTTON = 'data-superapp-open-app';
  var BANNER = 'data-superapp-banner';
  var MAX_BAR_HEIGHT = 150;
  var style = document.createElement('style');
  style.textContent = TAGGED + ',[' + BUTTON + '],[' + BANNER + ']{display:none!important}';
  var bars = document.getElementsByClassName('fixed-container');
  var marked = [];
  var observer = new MutationObserver(onChange);

  function isOpenApp(control) {
    return control.matches(TAGGED) ||
      TEXT.test(control.textContent.replace(ICONS, '').replace(/\s+/g, ' ').trim());
  }
  function barHeight(bar) {
    var height = parseFloat(bar.getAttribute('data-actual-height'));
    return isFinite(height) ? height : parseFloat(bar.style.height);
  }
  function listed(list, el, attr) {
    for (var i = 0; i < list.length; i++) if (list[i][0] === el && list[i][1] === attr) return true;
    return false;
  }
  function addStyle() {
    var root = document.head || document.documentElement;
    if (!style.isConnected && root) root.appendChild(style);
  }
  function holdsMark(node) {
    for (var i = 0; i < marked.length; i++) if (node.contains(marked[i][0])) return true;
    return false;
  }
  function touchesBars(records) {
    var last = null;
    for (var i = 0; i < records.length; i++) {
      var r = records[i], el = r.target.nodeType === 1 ? r.target : r.target.parentElement;
      if (el && el !== last) {
        last = el;
        if (el.closest(BAR)) return true;
      }
      if (r.type === 'attributes' && el && holdsMark(el)) return true;
      for (var j = 0; j < r.addedNodes.length; j++) {
        var n = r.addedNodes[j];
        if (n.nodeType === 1 && (n.matches(BAR) || n.querySelector(BAR) || holdsMark(n))) return true;
      }
    }
    return false;
  }
  function onChange(records) {
    addStyle();
    if (touchesBars(records)) sweep();
  }
  function sweep() {
    addStyle();
    var next = [];
    for (var i = 0; i < bars.length; i++) {
      var bar = bars[i];
      if (!(barHeight(bar) <= MAX_BAR_HEIGHT)) continue;
      var controls = bar.querySelectorAll(CONTROLS), found = null, others = 0;
      for (var j = 0; j < controls.length; j++) {
        if (found && found.contains(controls[j])) continue;
        if (isOpenApp(controls[j])) { found = controls[j]; next.push([found, BUTTON]); } else others++;
      }
      var bottom = bar.classList.contains('bottom') || bar.classList.contains('above-bottom');
      if (bottom && found && !others) next.push([bar, BANNER]);
    }
    for (i = 0; i < marked.length; i++) {
      if (!listed(next, marked[i][0], marked[i][1])) marked[i][0].removeAttribute(marked[i][1]);
    }
    for (i = 0; i < next.length; i++) {
      if (!next[i][0].hasAttribute(next[i][1])) next[i][0].setAttribute(next[i][1], '');
    }
    marked = next;
    observer.takeRecords();
  }
  observer.observe(document, {
    childList: true, subtree: true, characterData: true,
    attributes: true, attributeFilter: ['class', 'data-actual-height', BUTTON, BANNER]
  });
  sweep();
})();"""

internal val PAGE_TWEAKS = listOf(PageTweak("facebook.com", FACEBOOK_OPEN_APP_HIDER))

/**
 * Runs in every frame of every site: a page that gets hidden (another site or the home grid
 * shows, or the app is left) stops its audio and video, including embedded players (YouTube...)
 * that the activity can't reach.
 */
internal const val PAUSE_MEDIA_WHEN_HIDDEN = """document.addEventListener('visibilitychange', function () {
  if (document.hidden) document.querySelectorAll('video,audio').forEach(function (m) { m.pause(); });
});"""

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
        WebViewCompat.addDocumentStartJavaScript(this, PAUSE_MEDIA_WHEN_HIDDEN, setOf("*"))
        for (tweak in PAGE_TWEAKS) WebViewCompat.addDocumentStartJavaScript(this, tweak.script, tweak.originRules)
    }
}

/** Fallback for WebViews without document-start scripts: runs the matching tweaks on a shown page. */
internal fun WebView.runPageTweaks(url: String) {
    val host = url.substringAfter("://", "").substringBefore('/').substringBefore(':').lowercase()
    for (tweak in PAGE_TWEAKS) if (tweak.appliesTo(host)) evaluateJavascript(tweak.script, null)
}
