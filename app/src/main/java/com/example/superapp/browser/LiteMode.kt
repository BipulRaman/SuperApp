package com.example.superapp.browser

import android.content.Context
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.WeakHashMap

/*
 * Lite mode saves data and battery: videos load and play only when you tap them. The sites'
 * feeds autoplay videos and, autoplay or not, download them ahead as they scroll by (Facebook's
 * NASA page: 20 MB in 30 s): that's where most of their data and battery goes. It applies to
 * every page and frame, the sites' and any other. A player the user taps (or one already playing
 * when lite mode comes on) is left alone; the others:
 *  - can't start: play() fails as if autoplay were blocked, and autoplay attributes are dropped;
 *  - don't preload: preload stays "none" (also for players in the page's own HTML);
 *  - don't stream: the video files they request (fetch or XHR) wait until a tapped video plays,
 *    without failing, so players don't give up. Playlists (.m3u8, .mpd) still load;
 *  - on most sites aren't even connected to their stream (MediaSource) until tapped, so players
 *    that wait for it (X's, hls.js, dash.js) fetch nothing and, once one video is tapped, the
 *    others still don't. Facebook's and Instagram's players fetch whether connected or not, so
 *    there the waiting downloads alone hold them back.
 * Pages are also told to save data (navigator.connection.saveData), which some use to load less.
 *
 * Battery: the script does nothing on its own (no timers, no polling; its watch on the page's HTML
 * stops once the page has loaded). It only runs when the page acts: a string check per request
 * (a full URL check only for possible video), a few microseconds per tap or media event. Measured
 * on X's video feed: 0 MB of video instead of 79.5 MB and a third less CPU; on a page without
 * videos, no measurable difference.
 */

/**
 * The page side of lite mode (see above). A tap counts for a player when it lands on it, or for
 * a keyboard or accessibility click, on the element clicked; activation alone (e.g. right after
 * a page load) doesn't. Re-running it turns lite mode back on.
 */
internal const val LITE_MODE_SCRIPT = """(function () {
  var KEY = Symbol.for('superapp.lite');
  if (window[KEY]) { window[KEY].turnOn(); return; }
  var state = window[KEY] = { on: true };
  var tapped = new WeakSet();
  var watching = new Set();
  var pausedAt = new WeakMap();
  var held = [];
  var lastTap = null;
  var streams = new WeakMap();
  var streamUrls = new Set();
  var keptUrls = new Set();
  var attachLater = !/(^|\.)(facebook|instagram|threads)\.(com|net)/.test(location.hostname);
  var media = HTMLMediaElement.prototype;
  var nativePlay = media.play;
  var nativeLoad = media.load;
  var nativeSetAttribute = Element.prototype.setAttribute;
  var nativeRevoke = URL.revokeObjectURL;
  var VIDEO_FILES = ['mp4', 'm4s', 'm4v', 'm4a', 'webm', 'mov', 'ts'];
  var MAYBE_VIDEO = /video|playlist\/vid|\.(mp4|m4s|m4v|m4a|webm|mov|ts)/i;

  function remember(x, y) { lastTap = { x: x, y: y, time: Date.now() }; }
  window.addEventListener('pointerup', function (event) { remember(event.clientX, event.clientY); }, { capture: true, passive: true });
  window.addEventListener('click', function (event) {
    if (!event.isTrusted || event.detail || !event.target.getBoundingClientRect) return;
    var box = event.target.getBoundingClientRect();
    remember(box.left + box.width / 2, box.top + box.height / 2);
  }, true);

  function startedByUser(player) {
    var activation = navigator.userActivation;
    if (activation && !activation.isActive) return false;
    if (!lastTap || Date.now() - lastTap.time > (activation ? 5000 : 1000)) return false;
    var box = player.getBoundingClientRect();
    if (!box.width || !box.height) return true;
    return lastTap.x >= box.left && lastTap.x <= box.right && lastTap.y >= box.top && lastTap.y <= box.bottom;
  }

  function free(player) { return !state.on || tapped.has(player) || !!player.srcObject; }

  function release() {
    var waiting = held;
    held = [];
    waiting.forEach(function (download) { download(); });
  }

  function hold(download) {
    held.push(download);
    if (held.length > 300) held.shift();
  }

  function isWatching() {
    var now = Date.now();
    var any = false;
    watching.forEach(function (player) {
      if (!player.isConnected || player.ended || (player.paused && now - (pausedAt.get(player) || 0) > 3000)) watching.delete(player);
      else any = true;
    });
    return any;
  }

  function isVideo(url) {
    if (!MAYBE_VIDEO.test(url)) return false;
    var link;
    try { link = new URL(url, location.href); } catch (e) { return false; }
    if (link.protocol !== 'https:' && link.protocol !== 'http:') return false;
    var path = link.pathname;
    var name = path.slice(path.lastIndexOf('/') + 1);
    var dot = name.lastIndexOf('.');
    var type = dot < 0 ? '' : name.slice(dot + 1).toLowerCase();
    if (type === 'm3u8' || type === 'mpd') return false;
    return VIDEO_FILES.indexOf(type) >= 0 || /^video[.-]/.test(link.hostname) ||
      path.indexOf('/playlist/vid/') >= 0 || path.indexOf('/videoplayback') >= 0;
  }

  function holds(url) { return state.on && isVideo(url) && !isWatching(); }

  var nativeFetch = window.fetch;
  if (nativeFetch) {
    window.fetch = function fetch(input, init) {
      if (!holds(input instanceof Request ? input.url : String(input))) return nativeFetch.apply(this, arguments);
      var self = this;
      var args = arguments;
      var signal = (init && init.signal) || (input instanceof Request ? input.signal : null);
      return new Promise(function (resolve, reject) {
        var settled = false;
        function abort() {
          if (settled) return;
          settled = true;
          reject(signal.reason !== undefined ? signal.reason : new DOMException('The user aborted a request.', 'AbortError'));
        }
        if (signal) {
          if (signal.aborted) return abort();
          signal.addEventListener('abort', abort);
        }
        hold(function () {
          if (settled) return;
          settled = true;
          if (signal) signal.removeEventListener('abort', abort);
          nativeFetch.apply(self, args).then(resolve, reject);
        });
      });
    };
  }

  var xhr = XMLHttpRequest.prototype;
  var nativeOpen = xhr.open;
  var nativeSend = xhr.send;
  var nativeAbort = xhr.abort;
  var xhrUrls = new WeakMap();
  var heldXhrs = new WeakMap();
  xhr.open = function open(method, url) {
    xhrUrls.set(this, String(url));
    heldXhrs.delete(this);
    return nativeOpen.apply(this, arguments);
  };
  xhr.send = function send() {
    if (!holds(xhrUrls.get(this) || '')) return nativeSend.apply(this, arguments);
    var request = this;
    var args = arguments;
    function download() {
      if (heldXhrs.get(request) !== download) return;
      heldXhrs.delete(request);
      try { nativeSend.apply(request, args); } catch (e) {}
    }
    heldXhrs.set(request, download);
    hold(download);
  };
  xhr.abort = function abort() {
    heldXhrs.delete(this);
    return nativeAbort.apply(this, arguments);
  };

  URL.revokeObjectURL = function revokeObjectURL(url) {
    if (streamUrls.has(String(url))) { keptUrls.add(String(url)); return; }
    return nativeRevoke.apply(URL, arguments);
  };

  function guard(player) { if (!free(player)) nativeSetAttribute.call(player, 'preload', 'none'); }

  function forget(player) {
    var url = streams.get(player);
    if (url === undefined) return undefined;
    streams.delete(player);
    streamUrls.delete(url);
    return url;
  }

  function setSource(player, value, write) {
    var old = forget(player);
    if (old !== undefined && keptUrls.delete(old)) nativeRevoke.call(URL, old);
    var url = String(value);
    if (attachLater && !free(player) && url.lastIndexOf('blob:', 0) === 0) {
      streams.set(player, url);
      streamUrls.add(url);
      return;
    }
    guard(player);
    write();
  }

  function attach(player) {
    var url = forget(player);
    if (url === undefined) return;
    srcProperty.set.call(player, url);
    if (keptUrls.delete(url)) setTimeout(function () { nativeRevoke.call(URL, url); }, 10000);
  }

  var srcProperty = Object.getOwnPropertyDescriptor(media, 'src');
  Object.defineProperty(media, 'src', {
    configurable: true,
    enumerable: srcProperty.enumerable,
    get: function () { var url = streams.get(this); return url !== undefined ? url : srcProperty.get.call(this); },
    set: function (value) { var player = this; setSource(player, value, function () { srcProperty.set.call(player, value); }); }
  });
  function intercept(name, write) {
    var property = Object.getOwnPropertyDescriptor(media, name);
    if (!property || !property.set) return;
    Object.defineProperty(media, name, {
      configurable: true,
      enumerable: property.enumerable,
      get: property.get,
      set: function (value) { write(this, value, property.set); }
    });
  }
  intercept('preload', function (player, value, set) { if (free(player)) set.call(player, value); });
  intercept('autoplay', function (player, value, set) { if (free(player) || !value) set.call(player, value); });
  media.setAttribute = function setAttribute(name, value) {
    var player = this;
    var args = arguments;
    var key = String(name).toLowerCase();
    if (key === 'src') return setSource(player, value, function () { nativeSetAttribute.apply(player, args); });
    if (!free(player) && (key === 'preload' || key === 'autoplay')) return;
    return nativeSetAttribute.apply(player, args);
  };
  media.load = function load() {
    guard(this);
    return nativeLoad.apply(this, arguments);
  };
  ['appendChild', 'insertBefore', 'append', 'prepend'].forEach(function (name) {
    var native = Element.prototype[name] || Node.prototype[name];
    if (native) media[name] = function () { guard(this); return native.apply(this, arguments); };
  });
  media.play = function play() {
    if (!free(this)) {
      if (!startedByUser(this)) {
        return Promise.reject(new DOMException("play() failed because the user didn't interact with the document first.", 'NotAllowedError'));
      }
      tapped.add(this);
    }
    attach(this);
    if (state.on) {
      watching.add(this);
      release();
    }
    return nativePlay.apply(this, arguments);
  };

  document.addEventListener('play', function (event) {
    var player = event.target;
    if (player instanceof HTMLMediaElement && !free(player)) player.pause();
  }, true);
  document.addEventListener('pause', function (event) {
    if (watching.has(event.target)) pausedAt.set(event.target, Date.now());
  }, true);
  document.addEventListener('emptied', function (event) {
    var player = event.target;
    if (state.on && tapped.has(player) && !startedByUser(player)) {
      tapped.delete(player);
      watching.delete(player);
    }
  }, true);

  if (document.readyState === 'loading') {
    var parsing = new MutationObserver(function (records) {
      for (var i = 0; i < records.length; i++) {
        var nodes = records[i].addedNodes;
        for (var j = 0; j < nodes.length; j++) {
          if (nodes[j] instanceof HTMLMediaElement && !free(nodes[j])) {
            nativeSetAttribute.call(nodes[j], 'preload', 'none');
            nodes[j].removeAttribute('autoplay');
          }
        }
      }
    });
    parsing.observe(document, { childList: true, subtree: true });
    document.addEventListener('DOMContentLoaded', function () { parsing.disconnect(); });
  }

  try {
    if (navigator.connection) {
      Object.defineProperty(navigator.connection, 'saveData', { configurable: true, get: function () { return state.on; } });
    }
  } catch (e) {}

  state.turnOn = function () {
    state.on = true;
    var players = document.querySelectorAll('video, audio');
    for (var i = 0; i < players.length; i++) {
      if (!players[i].paused && !players[i].ended) { tapped.add(players[i]); watching.add(players[i]); }
    }
  };
  state.turnOff = function () {
    state.on = false;
    var players = document.querySelectorAll('video, audio');
    for (var i = 0; i < players.length; i++) attach(players[i]);
    release();
  };
  state.turnOn();
})();"""

/** Turns lite mode off in a page that has it: waiting players connect and downloads go ahead. */
internal const val LITE_MODE_OFF_SCRIPT =
    "(function () { var state = window[Symbol.for('superapp.lite')]; if (state) state.turnOff(); })();"

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
