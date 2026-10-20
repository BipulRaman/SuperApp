package com.example.superapp.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebChromeClient.CustomViewCallback
import android.widget.FrameLayout
import com.example.superapp.ui.setSystemBarsVisible

/** A page's fullscreen video, shown over everything with the system bars hidden and rotation unlocked. */
internal class FullscreenVideo(
    private val activity: Activity,
    private val container: FrameLayout,
    private val onChanged: () -> Unit,
) {
    private var view: View? = null
    private var callback: CustomViewCallback? = null

    val isShowing: Boolean get() = view != null

    fun show(view: View, callback: CustomViewCallback) {
        if (this.view != null) {
            callback.onCustomViewHidden()
            return
        }
        this.view = view
        this.callback = callback
        container.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        container.visibility = View.VISIBLE
        activity.window.setSystemBarsVisible(false)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        onChanged()
    }

    /** Leaves fullscreen and tells the page. */
    fun exit() {
        val callback = this.callback
        if (dismiss()) callback?.onCustomViewHidden()
    }

    /** Removes the fullscreen UI without telling the page (e.g. because its WebView is gone). */
    @SuppressLint("SourceLockedOrientationActivity")
    fun dismiss(): Boolean {
        val view = this.view ?: return false
        this.view = null
        callback = null
        container.removeView(view)
        container.visibility = View.GONE
        activity.window.setSystemBarsVisible(true)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onChanged()
        return true
    }
}
