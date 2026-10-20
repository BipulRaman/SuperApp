package com.example.superapp.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.example.superapp.R
import com.example.superapp.data.Site
import com.example.superapp.ui.dp
import com.example.superapp.ui.siteBackground

/** The home grid: a square tile per site, two per row, with a dot on sites that are already loaded. */
class HomeScreen(private val view: View, sites: List<Site>, onOpen: (Site) -> Unit) {

    private val tiles = LinkedHashMap<Site, View>()

    init {
        val context = view.context
        val grid = view.findViewById<LinearLayout>(R.id.grid)
        val inflater = LayoutInflater.from(context)
        val gap = context.dp(14)
        sites.chunked(2).forEachIndexed { rowIndex, rowSites ->
            val row = LinearLayout(context)
            rowSites.forEachIndexed { column, site ->
                val tile = inflater.inflate(R.layout.tile, row, false)
                bind(tile, site, onOpen)
                row.addView(tile, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (column == 0) marginEnd = gap })
            }
            if (rowSites.size == 1) row.addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
            grid.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { if (rowIndex > 0) topMargin = gap })
        }
    }

    var isVisible: Boolean
        get() = view.visibility == View.VISIBLE
        set(value) {
            view.visibility = if (value) View.VISIBLE else View.GONE
        }

    /** Marks the tiles of sites that are loaded in the background (reopening those is instant). */
    fun showOpenSites(isOpen: (Site) -> Boolean) {
        for ((site, tile) in tiles) {
            val open = isOpen(site)
            tile.findViewById<View>(R.id.live).visibility = if (open) View.VISIBLE else View.GONE
            tile.contentDescription = if (open) view.context.getString(R.string.cd_site_open, site.name) else site.name
        }
    }

    private fun bind(tile: View, site: Site, onOpen: (Site) -> Unit) {
        tile.background = tile.context.siteBackground(site, radiusDp = 22, sheen = true)
        tile.contentDescription = site.name
        if (site.icon != 0) {
            tile.findViewById<ImageView>(R.id.glyph).setImageResource(site.icon)
        } else {
            tile.findViewById<View>(R.id.glyph).visibility = View.GONE
            tile.findViewById<TextView>(R.id.initial).apply {
                visibility = View.VISIBLE
                text = site.initial
            }
        }
        tile.findViewById<TextView>(R.id.name).text = site.name
        tile.setOnClickListener { onOpen(site) }
        tiles[site] = tile
    }
}
