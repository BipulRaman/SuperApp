package com.example.superapp.browser

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.FrameLayout
import com.example.superapp.data.Site

/** An opened site, its WebView, and any windows its pages opened. */
class Tab internal constructor(val site: Site, val webView: WebView) {

    /** Windows the site opened (sign-in pop-ups, links meant for a new tab), newest last. */
    internal val popups = ArrayList<WebView>()

    /** What's on screen for this tab: its newest pop-up, or the site itself. */
    val top: WebView get() = popups.lastOrNull() ?: webView

    /** Last reported load progress (0-100) of [top], so switching back shows the right progress bar. */
    var progress = 0

    internal val views: List<WebView> get() = listOf(webView) + popups
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

    /** The tab [webView] belongs to, as its site or as one of its pop-ups. */
    fun find(webView: WebView): Tab? = tabs.values.firstOrNull { it.webView === webView || it.popups.contains(webView) }

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
        for (view in tab.views) {
            view.visibility = View.VISIBLE
            view.onResume()
        }
        return tab
    }

    /** Hides the active tab (it stays loaded), e.g. when going back to the home grid. */
    fun hideActive() {
        active?.let(::hide)
        active = null
    }

    /**
     * Creates a window that [opener]'s page asked for and shows it over that tab, so the page can
     * keep talking to it (window.opener), as sign-in pop-ups need. Null if [opener] isn't a tab.
     */
    fun openPopup(opener: WebView): WebView? {
        val tab = find(opener) ?: return null
        val popup = create()
        container.addView(popup, container.indexOfChild(tab.top) + 1, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        if (tab !== active) popup.visibility = View.GONE
        tab.popups += popup
        tab.progress = 0
        return popup
    }

    /** Closes a pop-up window (its page closed it, or the user went back out of it). */
    fun closePopup(popup: WebView): Tab? {
        val tab = tabs.values.firstOrNull { it.popups.contains(popup) } ?: return null
        tab.popups.remove(popup)
        destroy(popup)
        tab.progress = 100
        return tab
    }

    /**
     * Destroys [webView]: a pop-up just closes, a site's WebView takes its whole tab with it.
     * Returns the tab that was closed, if any.
     */
    fun discard(webView: WebView): Tab? {
        if (closePopup(webView) != null) return null
        if (webView === spare) {
            spare = null
            destroy(webView)
            return null
        }
        val tab = find(webView)
        if (tab == null) {
            destroy(webView)
            return null
        }
        close(tab)
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
        hidden.take(maxOf(excess, 0)).forEach(::close)
    }

    /** Creates a WebView ahead of time, so the first site opened doesn't pay for engine start-up. */
    fun prewarm() {
        if (spare == null) spare = create()
    }

    fun onStart() {
        anyWebView()?.resumeTimers()
        active?.views?.forEach(WebView::onResume)
    }

    fun onStop() {
        active?.views?.forEach(WebView::onPause)
        anyWebView()?.let {
            // Timers are global to all WebViews: nothing runs while the app is in the background.
            it.pauseTimers()
            CookieManager.getInstance().flush()
        }
    }

    fun destroyAll() {
        // pauseTimers() is process-wide; undo it so a recreated activity doesn't start frozen.
        anyWebView()?.resumeTimers()
        tabs.values.toList().forEach(::close)
        spare?.let(::destroy)
        spare = null
    }

    private fun create(): WebView = newWebView(context).also(setUp)

    /** A hidden tab stays loaded but stops drawing, playing media and (mostly) running. */
    private fun hide(tab: Tab) {
        for (view in tab.views) {
            view.evaluateJavascript(PAUSE_MEDIA_JS, null)
            view.onPause()
            view.visibility = View.GONE
        }
    }

    private fun close(tab: Tab) {
        tabs.remove(tab.site.id)
        if (tab === active) active = null
        tab.views.forEach(::destroy)
        tab.popups.clear()
    }

    private fun anyWebView(): WebView? = active?.webView ?: tabs.values.firstOrNull()?.webView ?: spare

    private fun destroy(webView: WebView) {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
}
