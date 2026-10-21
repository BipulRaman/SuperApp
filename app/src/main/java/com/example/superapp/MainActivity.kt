package com.example.superapp

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.Message
import android.os.PowerManager
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.example.superapp.browser.BrowserScreen
import com.example.superapp.browser.Downloads
import com.example.superapp.browser.FileChooser
import com.example.superapp.browser.FullscreenVideo
import com.example.superapp.browser.LinkApproval
import com.example.superapp.browser.Route
import com.example.superapp.browser.SiteDrawer
import com.example.superapp.browser.Tab
import com.example.superapp.browser.Tabs
import com.example.superapp.browser.documentStartScripts
import com.example.superapp.browser.incomingLink
import com.example.superapp.browser.linkApproval
import com.example.superapp.browser.openExternally
import com.example.superapp.browser.openLinkSettings
import com.example.superapp.browser.routeFor
import com.example.superapp.browser.runPageTweaks
import com.example.superapp.browser.withoutTracking
import com.example.superapp.data.SITES
import com.example.superapp.data.Site
import com.example.superapp.home.HomeScreen
import com.example.superapp.ui.setUpEdgeToEdge

/**
 * The only activity. It switches between the home grid and the site screen, owns the lifecycle
 * and back navigation, connects the WebViews' callbacks to the browser features, and opens site
 * links tapped in other apps (singleTask, so they reach the running app via onNewIntent).
 *
 * Speed comes from never throwing work away (see [Tabs]) and from warming up the WebView engine
 * right after the first frame, so even the first tap doesn't pay for engine start-up.
 */
class MainActivity : Activity() {

    private lateinit var home: HomeScreen
    private lateinit var browser: BrowserScreen
    private lateinit var drawer: SiteDrawer
    private lateinit var tabs: Tabs
    private lateinit var fullscreen: FullscreenVideo
    private val fileChooser = FileChooser(this, REQUEST_FILE)
    private val downloads = Downloads(this)

    private var backCallback: Any? = null // OnBackInvokedCallback, API 33+
    private var backCallbackRegistered = false

    /**
     * The page showing a link another app sent: back from it returns to that app, like a browser
     * tab opened from a chat. Cleared once you move on to another screen in SuperApp.
     */
    private var linkFromApp: WebView? = null

    private val blankPoster by lazy(LazyThreadSafetyMode.NONE) { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.setUpEdgeToEdge(findViewById(R.id.root))
        home = HomeScreen(findViewById(R.id.home), SITES, ::open)
        browser = BrowserScreen(
            findViewById(R.id.browser),
            onMenu = ::openDrawer,
            onClose = ::closeSite,
            onReload = { tabs.active?.top?.reload() },
            onShare = ::shareCurrentPage,
        )
        drawer = SiteDrawer(
            findViewById(R.id.drawer_layer),
            SITES,
            version = BuildConfig.VERSION_NAME,
            covered = findViewById(R.id.browser),
            onHome = ::showHome,
            onSite = ::open,
            // Android 12+ only sends the sites' links to SuperApp once the user approves them.
            linkApproval = if (Build.VERSION.SDK_INT >= 31) this::linkApproval else null,
            onLinkSettings = ::openLinkSettingsWithHint,
        )
        tabs = Tabs(this, browser.webContainer, liveSiteLimit(), ::attachCallbacks)
        fullscreen = FullscreenVideo(this, findViewById(R.id.fullscreen), ::updateBackCallback)
        // Started by a link tapped in another app. Not when restored or reopened from Recents,
        // which hand back the same link that was already shown.
        if (savedInstanceState == null && (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
            openLink(intent)
        }
        afterFirstFrame {
            Looper.myQueue().addIdleHandler {
                if (!isDestroyed) tabs.prewarm()
                false
            }
        }
    }

    /** A link tapped in another app while SuperApp was already running (singleTask). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openLink(intent) // Now, while referrer still names the app that sent it.
    }

    override fun onStart() {
        super.onStart()
        // Listened to only while in front (nothing runs in the background); re-checked on every start.
        registerReceiver(batterySaverChanged, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        applyBatterySaver()
        tabs.onStart()
    }

    override fun onStop() {
        unregisterReceiver(batterySaverChanged)
        tabs.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        tabs.destroyAll()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Only delivered up to Android 13; newer versions rely on the live-site limit in Tabs.
        @Suppress("DEPRECATION")
        if (level == TRIM_MEMORY_RUNNING_CRITICAL) {
            tabs.closeHidden()
            onTabsChanged()
        }
    }

    @SuppressLint("GestureBackNavigation") // API 33+ registers an OnBackInvokedCallback instead.
    @Deprecated("Only called below API 33; newer versions use OnBackInvokedCallback.")
    override fun onBackPressed() {
        // Never finish: moving to the background keeps the open sites alive.
        if (!handleBack()) moveTaskToBack(true)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!fileChooser.onActivityResult(requestCode, resultCode, data)) super.onActivityResult(requestCode, resultCode, data)
    }

    // ---- Screens -----------------------------------------------------------------------------

    private fun open(site: Site) {
        linkFromApp = null
        showTab(tabs.show(site))
    }

    private fun showTab(tab: Tab) {
        browser.showSite(tab.site, tab.progress)
        home.isVisible = false
        browser.isVisible = true
        onTabsChanged()
    }

    /** Shows a site link another app sent (e.g. tapped in WhatsApp) on that site's tab. */
    private fun openLink(intent: Intent) {
        val link = incomingLink(intent, SITES) ?: return
        val fromOtherApp = referrer?.host != packageName
        drawer.close(animate = false)
        fullscreen.exit()
        val tab = tabs.showLink(link.site, link.url)
        showTab(tab)
        linkFromApp = if (fromOtherApp) tab.top else null
    }

    private fun showHome() {
        linkFromApp = null
        drawer.close(animate = false)
        tabs.hideActive()
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        browser.isVisible = false
        home.isVisible = true
        onTabsChanged()
    }

    /** Unloads the site on screen to free its memory and returns to the grid; logins stay. */
    private fun closeSite() {
        tabs.closeActive() ?: return
        showHome()
    }

    private fun onTabsChanged() {
        home.showOpenSites(tabs::isOpen)
        updateBackCallback()
    }

    private val batterySaverChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = applyBatterySaver()
    }

    /** With Android's Battery Saver on, only one site stays loaded and nothing is pre-warmed. */
    private fun applyBatterySaver() {
        val on = getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        if (on == tabs.saveBattery) return
        tabs.saveBattery = on
        onTabsChanged()
    }

    private fun openDrawer() {
        drawer.open(tabs.active?.site, tabs::isOpen)
        updateBackCallback()
    }

    /** Opens Android's link settings for SuperApp, with a reminder of what to tap there. */
    private fun openLinkSettingsWithHint() {
        if (Build.VERSION.SDK_INT < 31) return
        if (linkApproval() != LinkApproval.ALL) Toast.makeText(this, R.string.links_hint, Toast.LENGTH_LONG).show()
        openLinkSettings()
    }

    /** Shares the page on screen through the system share sheet, minus tracking parameters. */
    private fun shareCurrentPage() {
        val tab = tabs.active ?: return
        val page = tab.top
        val url = withoutTracking(page.url?.takeIf(URLUtil::isNetworkUrl) ?: tab.site.url)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        page.title?.takeIf { it.isNotBlank() }?.let { send.putExtra(Intent.EXTRA_TITLE, it) }
        try {
            startActivity(Intent.createChooser(send, null))
        } catch (e: ActivityNotFoundException) {
            // Nothing on the device can share text.
        }
    }

    // ---- WebView callbacks -------------------------------------------------------------------

    private fun attachCallbacks(webView: WebView) {
        webView.webViewClient = pageClient
        webView.webChromeClient = chromeClient
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            downloads.start(url, userAgent, contentDisposition, mimeType)
        }
    }

    // Lint misses the onRenderProcessGone override below in a Kotlin anonymous object.
    @SuppressLint("MissingOnRenderProcessGone")
    private val pageClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            when (routeFor(request.url.scheme, request.hasGesture())) {
                Route.WEB_VIEW -> false
                Route.EXTERNAL_APP -> {
                    openExternally(view, request.url)
                    true
                }
                Route.BLOCK -> true
            }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            onRendererGone(view)
            return true // Handled: without this, a renderer crash or OOM kill takes the whole app down.
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            if (!documentStartScripts) view.runPageTweaks(url)
        }
    }

    private val chromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            val tab = tabs.find(view) ?: return
            if (view !== tab.top) return
            tab.progress = newProgress
            if (tab === tabs.active) browser.setProgress(newProgress)
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            // Like a browser's pop-up blocker: only windows the user asked for, e.g. by tapping
            // "Sign in with Google". The page keeps a live window.opener link to the pop-up.
            if (!isUserGesture) return false
            val popup = tabs.openPopup(view) ?: return false
            (resultMsg.obj as WebView.WebViewTransport).webView = popup
            resultMsg.sendToTarget()
            if (tabs.find(popup) === tabs.active) browser.setProgress(0, animate = false)
            return true
        }

        override fun onCloseWindow(window: WebView) {
            val tab = tabs.closePopup(window) ?: return
            if (tab === tabs.active) browser.setProgress(tab.progress, animate = false)
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) = fullscreen.show(view, callback)

        override fun onHideCustomView() = fullscreen.exit()

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean = fileChooser.show(filePathCallback, fileChooserParams)

        // Otherwise videos without a poster flash WebView's grey placeholder while loading.
        override fun getDefaultVideoPoster(): Bitmap = blankPoster
    }

    /** A page renderer crashed or was killed for memory: drop what it showed, never the whole app. */
    private fun onRendererGone(view: WebView) {
        if (view === linkFromApp) linkFromApp = null
        val activeOwner = tabs.find(view)?.takeIf { it === tabs.active }
        if (activeOwner != null) fullscreen.dismiss()
        val closed = tabs.discard(view)
        if (activeOwner != null && closed != null) {
            showHome()
            Toast.makeText(this, getString(R.string.site_closed, closed.site.name), Toast.LENGTH_LONG).show()
        } else {
            if (activeOwner != null) browser.setProgress(activeOwner.progress, animate = false)
            onTabsChanged()
        }
    }

    // ---- Back navigation ---------------------------------------------------------------------

    /**
     * Leaves fullscreen, goes back in the page (or out of a pop-up window), or returns to the grid.
     * Back from a link another app sent closes it and returns to that app. False when already on
     * the grid.
     */
    private fun handleBack(): Boolean {
        if (drawer.isOpen) {
            drawer.close()
            return true
        }
        if (fullscreen.isShowing) {
            fullscreen.exit()
            return true
        }
        val tab = tabs.active ?: return false
        val top = tab.top
        when {
            top.canGoBack() -> top.goBack()
            top === linkFromApp -> {
                linkFromApp = null
                if (top !== tab.webView) closePopup(tab, top)
                moveTaskToBack(true)
            }
            top !== tab.webView -> closePopup(tab, top)
            else -> showHome()
        }
        return true
    }

    private fun closePopup(tab: Tab, popup: WebView) {
        tabs.closePopup(popup)
        browser.setProgress(tab.progress, animate = false)
    }

    /** API 33+: only intercept back while there is something to go back from, so the system's
     *  predictive back-to-home animation still works on the grid. */
    private fun updateBackCallback() {
        if (Build.VERSION.SDK_INT < 33) return
        val wanted = drawer.isOpen || fullscreen.isShowing || tabs.active != null
        if (wanted == backCallbackRegistered) return
        val callback = (backCallback ?: OnBackInvokedCallback { handleBack() }.also { backCallback = it }) as OnBackInvokedCallback
        if (wanted) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        } else {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
        }
        backCallbackRegistered = wanted
    }

    /** How many sites may stay loaded at once; phones with less memory keep fewer (see [Tabs]). */
    private fun liveSiteLimit(): Int {
        val manager = getSystemService(ActivityManager::class.java) ?: return Int.MAX_VALUE
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        val gigabytes = memory.totalMem / (1024.0 * 1024 * 1024)
        return when {
            manager.isLowRamDevice || gigabytes < 3 -> 2
            gigabytes < 6 -> 3
            else -> Int.MAX_VALUE
        }
    }

    /** Runs [action] once the first frame has been drawn, so start-up work never delays it. */
    private fun afterFirstFrame(action: () -> Unit) {
        val decor = window.decorView
        decor.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            private var fired = false

            override fun onDraw() {
                if (fired) return
                fired = true
                decor.post {
                    decor.viewTreeObserver.removeOnDrawListener(this)
                    action()
                }
            }
        })
    }

    private companion object {
        const val REQUEST_FILE = 1
    }
}
