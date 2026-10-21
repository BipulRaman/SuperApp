package com.example.superapp.browser

import android.content.Intent
import android.webkit.URLUtil
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.superapp.data.SITES
import org.json.JSONException
import org.json.JSONObject

/*
 * The sites' own Share buttons use the Web Share API (navigator.share), which WebView lacks, so
 * they do nothing or offer less. On the sites' pages a small script provides navigator.share and
 * passes the request to SuperApp, which opens Android's share sheet (WhatsApp, Messages...) with
 * just the link, minus tracking parameters. Only the sites' own pages (not other sites opened in
 * their tabs, not frames) get it; nothing goes back to the page but whether the sheet opened.
 */

/** Name of the object WebView injects into the sites' pages to reach SuperApp. */
private const val BRIDGE = "superAppShare"

/** navigator.share and navigator.canShare, on top of the injected [BRIDGE] object. */
internal const val WEB_SHARE_SCRIPT = """(function () {
  var bridge = window.superAppShare;
  if (!bridge || navigator.share) return;
  var waiting = null;
  bridge.addEventListener('message', function (event) {
    var settle = waiting;
    waiting = null;
    if (!settle) return;
    if (event.data === 'ok') settle.resolve();
    else settle.reject(new DOMException('Share canceled', 'AbortError'));
  });
  function text(value) { return value == null ? '' : String(value); }
  function link(value) { return value == null ? '' : new URL(text(value), location.href).href; }
  navigator.share = function (data) {
    return new Promise(function (resolve, reject) {
      data = data || {};
      var activation = navigator.userActivation;
      if (activation && !activation.isActive) {
        return reject(new DOMException('Must be handling a user gesture to share.', 'NotAllowedError'));
      }
      if (data.files && data.files.length) return reject(new DOMException('Files can\'t be shared.', 'NotAllowedError'));
      var share;
      try {
        share = { title: text(data.title), text: text(data.text), url: link(data.url) };
      } catch (e) {
        return reject(new TypeError('Invalid URL'));
      }
      if (!share.title && !share.text && !share.url) return reject(new TypeError('Nothing to share'));
      if (waiting) return reject(new DOMException('An earlier share has not yet completed.', 'InvalidStateError'));
      waiting = { resolve: resolve, reject: reject };
      bridge.postMessage(JSON.stringify(share));
    });
  };
  navigator.canShare = function (data) {
    if (!data || (data.files && data.files.length)) return false;
    try { link(data.url); } catch (e) { return false; }
    return data.title != null || data.text != null || data.url != null;
  };
})();"""

/** The sites' own pages: https on each of their link hosts (subdomain patterns included). */
internal val WEB_SHARE_ORIGINS: Set<String> = SITES.flatMap { it.linkHosts }.map { "https://$it" }.toSet()

/** What a page asked to share. */
internal class WebShare(val title: String, val text: String, val url: String)

/** The page's share request; null unless it's a well-formed one with something to share. */
internal fun parseWebShare(json: String?): WebShare? {
    val data = try {
        JSONObject(json ?: return null)
    } catch (e: JSONException) {
        return null
    }
    val url = data.optString("url").take(4096)
    if (url.isNotEmpty() && !URLUtil.isNetworkUrl(url)) return null
    val share = WebShare(data.optString("title").take(500), data.optString("text").take(10_000), url)
    return share.takeIf { it.title.isNotBlank() || it.text.isNotBlank() || it.url.isNotEmpty() }
}

/** A link inside a page's share text, for pages that put it there instead of in url. */
private val LINK_IN_TEXT = Regex("""https?://\S+""")

/**
 * Android's share intent: just the link, without tracking parameters; the page's title and text
 * are left out. Only a share with no link at all sends its text.
 */
internal fun WebShare.toSendIntent(): Intent {
    val link = url.ifEmpty { LINK_IN_TEXT.find(text)?.value?.trimEnd('.', ',', ';', ':', '!', '?', '"', '\'').orEmpty() }
    val body = if (link.isNotEmpty()) withoutTracking(link) else text.trim().ifEmpty { title.trim() }
    return Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body)
}

/**
 * Gives the sites' pages navigator.share; [onShare] shows the share sheet and returns whether it
 * did. Needs WebView's document-start scripts and web message listeners.
 */
internal fun WebView.addWebShare(onShare: (WebShare) -> Boolean) {
    if (!documentStartScripts || !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
    WebViewCompat.addWebMessageListener(this, BRIDGE, WEB_SHARE_ORIGINS) { _, message, _, isMainFrame, reply ->
        val share = if (isMainFrame) parseWebShare(message.data) else null
        reply.postMessage(if (share != null && onShare(share)) "ok" else "error")
    }
    WebViewCompat.addDocumentStartJavaScript(this, WEB_SHARE_SCRIPT, WEB_SHARE_ORIGINS)
}
