package com.example.superapp.browser

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.FrameLayout
import com.example.superapp.data.Site

/** An opened site and its WebView. */
class Tab internal constructor(val site: Site, val webView: WebView) {
    /** Last reported load progress (0-100), so switching back shows the right progress bar. */
    var progress = 0
}

/**
 * One persistent WebView per site, which is what makes SuperApp fast: a site's WebView is created
 * the first time it is opened and is then only hidden and paused when you leave, never destroyed,
 * so going back is instant and lands exactly where you were. [setUp] attaches the activity's
 * callbacks to every new WebView.
 *
 * All WebViews share one renderer process, so on phones with less memory at most [maxLive] sites
 * stay loaded: opening another one closes the least recently used hidden site first, rather than
 * risking the system killing the renderer (which would close every site at once).
 */
class Tabs(
    private val context: Context,
    private val container: ViewGroup,
    private val maxLive: Int,
    private val setUp: (WebView) -> Unit,
) {
    // Access order: iteration starts at the least recently shown tab.
    private val tabs = LinkedHashMap<String, Tab>(8, 0.75f, true)
    private var spare: WebView? = null

    /** The tab on screen, or null while the home grid is showing. */
    var active: Tab? = null
        private set

    fun isOpen(site: Site) = site.id in tabs

    fun find(webView: WebView): Tab? = tabs.values.firstOrNull { it.webView === webView }

    /** Shows [site]'s tab, creating and loading it the first time. */
    fun show(site: Site): Tab {
        val tab = tabs[site.id] ?: run {
            closeLeastRecentlyUsed(keep = maxLive - 1)
            val webView = spare ?: create()
            spare = null
            // Index 0 keeps the WebViews below the progress bar.
            container.addView(webView, 0, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            webView.loadUrl(site.url)
            Tab(site, webView).also { tabs[site.id] = it }
        }
        if (active !== tab) {
            active?.let(::hide)
            active = tab
        }
        tab.webView.visibility = View.VISIBLE
        tab.webView.onResume()
        return tab
    }

    /** Hides the active tab (it stays loaded), e.g. when going back to the home grid. */
    fun hideActive() {
        active?.let(::hide)
        active = null
    }

    /** Destroys [webView] and forgets its tab. Returns that tab, if it belonged to one. */
    fun discard(webView: WebView): Tab? {
        val tab = find(webView)
        if (tab != null) {
            tabs.remove(tab.site.id)
            if (tab === active) active = null
        }
        if (webView === spare) spare = null
        destroy(webView)
        return tab
    }

    /** Frees memory by destroying every tab that isn't on screen. */
    fun closeHidden() {
        spare?.let(::destroy)
        spare = null
        closeLeastRecentlyUsed(keep = 0)
    }

    /** Destroys least recently shown hidden tabs until at most [keep] tabs (plus the active one) remain. */
    private fun closeLeastRecentlyUsed(keep: Int) {
        val hidden = tabs.values.filter { it !== active }
        val excess = tabs.size - maxOf(keep, if (active != null) 1 else 0)
        hidden.take(maxOf(excess, 0)).forEach {
            tabs.remove(it.site.id)
            destroy(it.webView)
        }
    }

    /** Creates a WebView ahead of time, so the first site opened doesn't pay for engine start-up. */
    fun prewarm() {
        if (spare == null) spare = create()
    }

    fun onStart() {
        anyWebView()?.resumeTimers()
        active?.webView?.onResume()
    }

    fun onStop() {
        active?.webView?.onPause()
        anyWebView()?.let {
            // Timers are global to all WebViews: nothing runs while the app is in the background.
            it.pauseTimers()
            CookieManager.getInstance().flush()
        }
    }

    fun destroyAll() {
        // pauseTimers() is process-wide; undo it so a recreated activity doesn't start frozen.
        anyWebView()?.resumeTimers()
        tabs.values.forEach { destroy(it.webView) }
        tabs.clear()
        spare?.let(::destroy)
        spare = null
        active = null
    }

    private fun create(): WebView = newWebView(context).also(setUp)

    /** A hidden tab stays loaded but stops drawing, playing media and (mostly) running. */
    private fun hide(tab: Tab) {
        tab.webView.evaluateJavascript(PAUSE_MEDIA_JS, null)
        tab.webView.onPause()
        tab.webView.visibility = View.GONE
    }

    private fun anyWebView(): WebView? = active?.webView ?: tabs.values.firstOrNull()?.webView ?: spare

    private fun destroy(webView: WebView) {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
}
