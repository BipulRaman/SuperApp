package com.example.superapp.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import com.example.superapp.data.Site

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

/** The site's brand colors as a rounded rectangle; [sheen] adds a soft top highlight (home tiles). */
internal fun Context.siteBackground(site: Site, radiusDp: Int, sheen: Boolean): Drawable {
    val radius = dp(radiusDp).toFloat()
    val base = GradientDrawable().apply {
        cornerRadius = radius
        if (site.colors.size > 1) {
            orientation = GradientDrawable.Orientation.BL_TR
            colors = site.colors
        } else {
            setColor(site.colors[0])
        }
        setStroke(dp(1), 0x1FFFFFFF)
    }
    if (!sheen) return base
    val gloss = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x26FFFFFF, 0x00FFFFFF))
    gloss.cornerRadius = radius
    return LayerDrawable(arrayOf(base, gloss))
}

/** 24dp brand-colored square with the site's glyph, shown next to the toolbar title. */
internal fun Context.siteChip(site: Site): Drawable {
    val background = siteBackground(site, radiusDp = 7, sheen = false)
    val glyph = if (site.icon != 0) getDrawable(site.icon) else null
    val chip = if (glyph != null) {
        LayerDrawable(arrayOf(background, glyph)).apply {
            val inset = dp(4)
            setLayerInset(1, inset, inset, inset, inset)
        }
    } else {
        background
    }
    return chip.apply { setBounds(0, 0, dp(24), dp(24)) }
}
