package com.example.superapp.browser

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.example.superapp.R
import com.example.superapp.data.Site
import com.example.superapp.ui.ACCENT_COLORS
import com.example.superapp.ui.siteChip

/** The site screen around the WebViews: toolbar (menu, site title, reload, share) and progress bar. */
class BrowserScreen(
    private val view: View,
    onMenu: () -> Unit,
    onReload: () -> Unit,
    onShare: () -> Unit,
) {

    /** Holds the sites' WebViews, underneath the progress bar. */
    val webContainer: FrameLayout = view.findViewById(R.id.web_container)
    private val title: TextView = view.findViewById(R.id.title)
    private val progressBar: View = view.findViewById(R.id.progress)

    init {
        view.findViewById<View>(R.id.menu).setOnClickListener { onMenu() }
        view.findViewById<View>(R.id.reload).setOnClickListener { onReload() }
        view.findViewById<View>(R.id.share).setOnClickListener { onShare() }
        progressBar.pivotX = 0f
        progressBar.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, ACCENT_COLORS)
    }

    var isVisible: Boolean
        get() = view.visibility == View.VISIBLE
        set(value) {
            view.visibility = if (value) View.VISIBLE else View.GONE
        }

    fun showSite(site: Site, progress: Int) {
        title.text = site.name
        title.setCompoundDrawablesRelative(title.context.siteChip(site), null, null, null)
        setProgress(progress, animate = false)
    }

    /** A thin bar over the top of the page: content stays visible while it loads. */
    fun setProgress(progress: Int, animate: Boolean = true) {
        val bar = progressBar
        bar.animate().cancel()
        if (progress >= 100) {
            if (animate && bar.alpha > 0f) {
                bar.animate().scaleX(1f).alpha(0f).setDuration(250).start()
            } else {
                bar.alpha = 0f
            }
            return
        }
        val target = maxOf(progress, 8) / 100f
        if (animate && bar.alpha == 1f && target > bar.scaleX) {
            bar.animate().scaleX(target).setDuration(200).start()
        } else {
            bar.scaleX = target
            bar.alpha = 1f
        }
    }
}
