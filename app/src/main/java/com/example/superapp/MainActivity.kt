package com.example.superapp

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.webkit.RenderProcessGoneDetail
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
import com.example.superapp.browser.Route
import com.example.superapp.browser.Tabs
import com.example.superapp.browser.openExternally
import com.example.superapp.browser.routeFor
import com.example.superapp.data.SITES
import com.example.superapp.data.Site
import com.example.superapp.home.HomeScreen
import com.example.superapp.ui.setUpEdgeToEdge

/**
 * The only activity. It switches between the home grid and the site screen, owns the lifecycle
 * and back navigation, and connects the WebViews' callbacks to the browser features.
 *
 * Speed comes from never throwing work away (see [Tabs]) and from warming up the WebView engine
 * right after the first frame, so even the first tap doesn't pay for engine start-up.
 */
class MainActivity : Activity() {

    private lateinit var home: HomeScreen
    private lateinit var browser: BrowserScreen
    private lateinit var tabs: Tabs
    private lateinit var fullscreen: FullscreenVideo
    private val fileChooser = FileChooser(this, REQUEST_FILE)
    private val downloads = Downloads(this, REQUEST_STORAGE)

    private var backCallback: Any? = null // OnBackInvokedCallback, API 33+
    private var backCallbackRegistered = false

    private val blankPoster by lazy(LazyThreadSafetyMode.NONE) { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.setUpEdgeToEdge(findViewById(R.id.root))
        home = HomeScreen(findViewById(R.id.home), SITES, ::open)
        browser = BrowserScreen(findViewById(R.id.browser), onBack = ::showHome, onReload = { tabs.active?.webView?.reload() })
        tabs = Tabs(this, browser.webContainer, liveSiteLimit(), ::attachCallbacks)
        fullscreen = FullscreenVideo(this, findViewById(R.id.fullscreen), ::updateBackCallback)
        afterFirstFrame {
            Looper.myQueue().addIdleHandler {
                if (!isDestroyed) tabs.prewarm()
                false
            }
        }
    }

    override fun onStart() {
        super.onStart()
        tabs.onStart()
    }

    override fun onStop() {
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (!downloads.onRequestPermissionsResult(requestCode, grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    // ---- Screens -----------------------------------------------------------------------------

    private fun open(site: Site) {
        val tab = tabs.show(site)
        browser.showSite(site, tab.progress)
        home.isVisible = false
        browser.isVisible = true
        onTabsChanged()
    }

    private fun showHome() {
        tabs.hideActive()
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        browser.isVisible = false
        home.isVisible = true
        onTabsChanged()
    }

    private fun onTabsChanged() {
        home.showOpenSites(tabs::isOpen)
        updateBackCallback()
    }

    // ---- WebView callbacks -------------------------------------------------------------------

    private fun attachCallbacks(webView: WebView) {
        webView.webViewClient = pageClient
        webView.webChromeClient = chromeClient
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            downloads.start(url, userAgent, contentDisposition, mimeType)
        }
    }

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
    }

    private val chromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            val tab = tabs.find(view) ?: return
            tab.progress = newProgress
            if (tab === tabs.active) browser.setProgress(newProgress)
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

    /** A page renderer crashed or was killed for memory: drop that site, never the whole app. */
    private fun onRendererGone(view: WebView) {
        val wasActive = tabs.active?.webView === view
        if (wasActive) fullscreen.dismiss()
        val tab = tabs.discard(view)
        if (wasActive && tab != null) {
            showHome()
            Toast.makeText(this, getString(R.string.site_closed, tab.site.name), Toast.LENGTH_LONG).show()
        } else {
            onTabsChanged()
        }
    }

    // ---- Back navigation ---------------------------------------------------------------------

    /** Leaves fullscreen, goes back in the page, or returns to the grid. False when on the grid. */
    private fun handleBack(): Boolean {
        if (fullscreen.isShowing) {
            fullscreen.exit()
            return true
        }
        val tab = tabs.active ?: return false
        if (tab.webView.canGoBack()) tab.webView.goBack() else showHome()
        return true
    }

    /** API 33+: only intercept back while there is something to go back from, so the system's
     *  predictive back-to-home animation still works on the grid. */
    private fun updateBackCallback() {
        if (Build.VERSION.SDK_INT < 33) return
        val wanted = fullscreen.isShowing || tabs.active != null
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
        const val REQUEST_STORAGE = 2
    }
}
