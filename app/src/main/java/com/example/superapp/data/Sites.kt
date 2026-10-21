package com.example.superapp.data

import com.example.superapp.R

/**
 * A site on the home grid. One color in [colors] gives a solid tile, several give a gradient
 * (bottom-left to top-right). Sites without an [icon] show [initial] instead.
 *
 * Links to [linkHosts] tapped in other apps (WhatsApp, Gmail...) open on this site; `*.` stands
 * for any subdomain, as in intent filters. Each host needs the same entry in the link intent
 * filters in AndroidManifest.xml (a test checks they match).
 */
class Site(
    val id: String,
    val name: String,
    val url: String,
    val colors: IntArray,
    val icon: Int = 0,
    val initial: String = name.take(1),
    val linkHosts: List<String> = emptyList(),
)

/** The sites on the home grid, in display order. */
val SITES = listOf(
    Site(
        id = "facebook",
        name = "Facebook",
        url = "https://m.facebook.com/",
        colors = intArrayOf(0xFF1877F2.toInt()),
        icon = R.drawable.ic_site_facebook,
        // fb.watch: video links, fb.me: event and short links.
        linkHosts = listOf("facebook.com", "*.facebook.com", "fb.watch", "fb.me"),
    ),
    Site(
        id = "instagram",
        name = "Instagram",
        url = "https://www.instagram.com/",
        colors = intArrayOf(0xFFFEDA75.toInt(), 0xFFFA7E1E.toInt(), 0xFFD62976.toInt(), 0xFF962FBF.toInt(), 0xFF4F5BD5.toInt()),
        icon = R.drawable.ic_site_instagram,
        linkHosts = listOf("instagram.com", "*.instagram.com"),
    ),
    Site(
        id = "x",
        name = "X / Twitter",
        url = "https://x.com/",
        colors = intArrayOf(0xFF000000.toInt()),
        icon = R.drawable.ic_site_x,
        linkHosts = listOf("x.com", "*.x.com", "twitter.com", "*.twitter.com"),
    ),
    Site(
        id = "linkedin",
        name = "LinkedIn",
        url = "https://www.linkedin.com/",
        colors = intArrayOf(0xFF0A66C2.toInt()),
        icon = R.drawable.ic_site_linkedin,
        linkHosts = listOf("linkedin.com", "*.linkedin.com"),
    ),
)
