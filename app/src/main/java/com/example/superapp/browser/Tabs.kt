package com.example.superapp.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.FrameLayout
import com.example.superapp.data.Site

/** An opened site, its WebView, and the windows shown over it. */
class Tab internal constructor(val site: Site, val webView: WebView) {

    /**
     * Windows over the site, newest last: pop-ups its pages opened (sign-in, links meant for a new
     * tab) and links from other apps ([linkWindow]).
     */
    internal val popups = ArrayList<WebView>()

    /** The window showing a link another app sent, while it's open (it's one of [popups]). */
    internal var linkWindow: WebView? = null

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
 *
 * Battery: pages only run while a site is on screen. Hidden sites stop their media and are
 * throttled by WebView; on the home grid and in the background, every page's scripts, timers and
 * layout are paused (see [updateTimers]); in Battery Saver ([saveBattery]) only one site stays
 * loaded.
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
    private var started = false
    private val handler = Handler(Looper.getMainLooper())
    private val pauseTimersIfStopped = Runnable { updateTimers() }

    /** The tab on screen, or null while the home grid is showing. */
    var active: Tab? = null
        private set

    /** Whether WebView's timers, layout and parsing are paused (process-wide, for every page). */
    internal var timersPaused = false
        private set

    /**
     * Android's Battery Saver is on: only one site stays loaded (the one on screen, or last shown)
     * and no WebView is pre-warmed. Turning it on closes the rest right away.
     */
    var saveBattery = false
        set(value) {
            field = value
            if (!value) return
            spare?.let(::destroy)
            spare = null
            closeLeastRecentlyUsed(keep = 1)
        }

    fun isOpen(site: Site) = site.id in tabs

    /** The tab [webView] belongs to, as its site or as one of its pop-ups. */
    fun find(webView: WebView): Tab? = tabs.values.firstOrNull { it.webView === webView || it.popups.contains(webView) }

    /** Shows [site]'s tab, creating and loading it the first time. */
    fun show(site: Site): Tab = show(site, site.url)

    /**
     * Shows [url], a link to [site] that another app sent. A site that isn't open yet opens
     * straight at the link. An open one keeps its page and shows the link in a window over it, so
     * closing the link lands back where you were; a link window already on screen is replaced
     * rather than stacked.
     */
    fun showLink(site: Site, url: String): Tab {
        if (site.id !in tabs) return show(site, url)
        val tab = show(site)
        tab.linkWindow?.takeIf { it === tab.top }?.let(::closePopup)
        for (view in tab.views) view.evaluateJavascript(PAUSE_MEDIA_JS, null)
        val window = takeSpare()
        container.addView(window, container.indexOfChild(tab.top) + 1, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        tab.popups += window
        tab.linkWindow = window
        tab.progress = 0
        window.loadUrl(url)
        return tab
    }

    private fun show(site: Site, url: String): Tab {
        val tab = tabs[site.id] ?: run {
            closeLeastRecentlyUsed(keep = maxLive - 1)
            val webView = takeSpare()
            // Index 0 keeps the WebViews below the progress bar.
            container.addView(webView, 0, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            webView.loadUrl(url)
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
        if (saveBattery) closeLeastRecentlyUsed(keep = 1)
        updateTimers()
        return tab
    }

    /** Hides the active tab (it stays loaded), e.g. when going back to the home grid. */
    fun hideActive() {
        active?.let(::hide)
        active = null
        updateTimers()
    }

    /** Closes the tab on screen with its windows; opening the site again loads it afresh. */
    fun closeActive(): Tab? = active?.also {
        close(it)
        updateTimers()
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
        if (tab.linkWindow === popup) tab.linkWindow = null
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
        if (spare == null && !saveBattery) spare = create()
    }

    fun onStart() {
        started = true
        handler.removeCallbacks(pauseTimersIfStopped)
        updateTimers()
        active?.views?.forEach(WebView::onResume)
    }

    fun onStop() {
        started = false
        val views = active?.views.orEmpty()
        views.forEach(WebView::onPause)
        if (anyWebView() == null) return
        CookieManager.getInstance().flush()
        if (views.isEmpty()) {
            updateTimers()
            return
        }
        // WebView keeps playing audio and video in the background: stop them, then pause everything
        // once that has run (paused timers could hold the script back), or after a second at most.
        var pending = views.size
        for (view in views) view.evaluateJavascript(PAUSE_MEDIA_JS) { if (--pending == 0) updateTimers() }
        handler.postDelayed(pauseTimersIfStopped, 1000)
    }

    fun destroyAll() {
        handler.removeCallbacks(pauseTimersIfStopped)
        // pauseTimers() is process-wide; undo it so a recreated activity doesn't start frozen.
        anyWebView()?.resumeTimers()
        timersPaused = false
        tabs.values.toList().forEach(::close)
        spare?.let(::destroy)
        spare = null
    }

    /**
     * Pages run only while a site is on screen and the app is in front. WebView can only pause
     * timers, layout and parsing for all pages at once, so they pause on the home grid and in the
     * background, and resume when a site shows.
     */
    private fun updateTimers() {
        val pause = !started || active == null
        if (pause == timersPaused) return
        val webView = anyWebView() ?: return
        if (pause) webView.pauseTimers() else webView.resumeTimers()
        timersPaused = pause
    }

    private fun create(): WebView = newWebView(context).also(setUp)

    /** Every WebView: the sites', their windows, and the pre-warmed one. */
    fun allWebViews(): List<WebView> = tabs.values.flatMap { it.views } + listOfNotNull(spare)

    /** The pre-warmed WebView if there is one, else a new one. */
    private fun takeSpare(): WebView = (spare ?: create()).also { spare = null }

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
        tab.linkWindow = null
    }

    private fun anyWebView(): WebView? = active?.webView ?: tabs.values.firstOrNull()?.webView ?: spare

    private fun destroy(webView: WebView) {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
}
