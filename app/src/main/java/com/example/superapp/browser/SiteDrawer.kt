package com.example.superapp.browser

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.superapp.R
import com.example.superapp.data.Site
import com.example.superapp.ui.dp
import com.example.superapp.ui.homeChip
import com.example.superapp.ui.siteChip

/**
 * The site screen's menu: Home plus every site, sliding in over the page from the start edge,
 * with the app [version] at the bottom. The current site is highlighted and loaded sites carry a
 * dot, so switching is one tap. It closes on a pick, a tap outside it, or back.
 */
class SiteDrawer(
    private val layer: View,
    sites: List<Site>,
    version: String,
    private val covered: View,
    private val onHome: () -> Unit,
    private val onSite: (Site) -> Unit,
) {
    private val panel: View = layer.findViewById(R.id.drawer)
    private val scrim: View = layer.findViewById(R.id.drawer_scrim)
    private val rows = LinkedHashMap<Site, View>()

    var isOpen = false
        private set

    init {
        val context = layer.context
        val items = layer.findViewById<LinearLayout>(R.id.drawer_items)
        val inflater = LayoutInflater.from(context)
        items.addView(row(inflater, items, context.getString(R.string.menu_home), context.homeChip()) { pick(onHome) })
        for (site in sites) {
            val row = row(inflater, items, site.name, context.siteChip(site)) { pick { onSite(site) } }
            rows[site] = row
            items.addView(row)
        }
        layer.findViewById<TextView>(R.id.drawer_version).text = context.getString(R.string.version, version)
        context.getDrawable(R.mipmap.ic_launcher)?.let { icon ->
            icon.setBounds(0, 0, context.dp(40), context.dp(40))
            layer.findViewById<TextView>(R.id.drawer_title).setCompoundDrawablesRelative(icon, null, null, null)
        }
        scrim.setOnClickListener { close() }
    }

    /** Opens the drawer, highlighting [current] and marking the sites [isLoaded] reports. */
    fun open(current: Site?, isLoaded: (Site) -> Boolean) {
        if (isOpen) return
        isOpen = true
        for ((site, row) in rows) {
            val loaded = isLoaded(site)
            row.isSelected = site === current
            row.findViewById<View>(R.id.live).visibility = if (loaded) View.VISIBLE else View.GONE
            row.contentDescription = if (loaded) layer.context.getString(R.string.cd_site_open, site.name) else site.name
        }
        val width = panelWidth()
        panel.layoutParams = panel.layoutParams.apply { this.width = width }
        layer.visibility = View.VISIBLE
        covered.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        panel.translationX = hiddenOffset(width)
        scrim.alpha = 0f
        panel.animate().translationX(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        scrim.animate().alpha(1f).setDuration(220).start()
    }

    fun close(animate: Boolean = true) {
        if (!isOpen) return
        isOpen = false
        covered.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        if (!animate) {
            panel.animate().cancel()
            scrim.animate().cancel()
            layer.visibility = View.GONE
            return
        }
        panel.animate().translationX(hiddenOffset(panel.layoutParams.width)).setDuration(180)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { if (!isOpen) layer.visibility = View.GONE }
            .start()
        scrim.animate().alpha(0f).setDuration(180).start()
    }

    /** Closes first, so the drawer slides away over the screen the pick leads to. */
    private fun pick(action: () -> Unit) {
        close()
        action()
    }

    /** Material's drawer width: the screen minus a 56dp margin to tap outside it, at most 304dp. */
    private fun panelWidth(): Int {
        val context = layer.context
        val parent = layer.parent as View
        val available = (parent.width - parent.paddingLeft - parent.paddingRight)
            .takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        return minOf(context.dp(304), available - context.dp(56))
    }

    private fun hiddenOffset(width: Int): Float =
        if (layer.layoutDirection == View.LAYOUT_DIRECTION_RTL) width.toFloat() else -width.toFloat()

    private fun row(inflater: LayoutInflater, parent: ViewGroup, label: String, icon: Drawable, onClick: () -> Unit): View =
        inflater.inflate(R.layout.drawer_item, parent, false).apply {
            findViewById<ImageView>(R.id.icon).setImageDrawable(icon)
            findViewById<TextView>(R.id.label).text = label
            contentDescription = label
            setOnClickListener { onClick() }
        }
}
