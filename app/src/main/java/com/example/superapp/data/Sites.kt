package com.example.superapp.data

import com.example.superapp.R

/**
 * A site on the home grid. One color in [colors] gives a solid tile, several give a gradient
 * (bottom-left to top-right). Sites without an [icon] show [initial] instead.
 */
class Site(
    val id: String,
    val name: String,
    val url: String,
    val colors: IntArray,
    val icon: Int = 0,
    val initial: String = name.take(1),
)

/** The sites on the home grid, in display order. */
val SITES = listOf(
    Site(
        id = "facebook",
        name = "Facebook",
        url = "https://m.facebook.com/",
        colors = intArrayOf(0xFF1877F2.toInt()),
        icon = R.drawable.ic_site_facebook,
    ),
    Site(
        id = "instagram",
        name = "Instagram",
        url = "https://www.instagram.com/",
        colors = intArrayOf(0xFFFEDA75.toInt(), 0xFFFA7E1E.toInt(), 0xFFD62976.toInt(), 0xFF962FBF.toInt(), 0xFF4F5BD5.toInt()),
        icon = R.drawable.ic_site_instagram,
    ),
    Site(
        id = "x",
        name = "X / Twitter",
        url = "https://x.com/",
        colors = intArrayOf(0xFF000000.toInt()),
        icon = R.drawable.ic_site_x,
    ),
    Site(
        id = "linkedin",
        name = "LinkedIn",
        url = "https://www.linkedin.com/",
        colors = intArrayOf(0xFF0A66C2.toInt()),
        icon = R.drawable.ic_site_linkedin,
    ),
)
