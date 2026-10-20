package com.example.superapp.browser

import java.net.URLDecoder

/*
 * Links on social sites carry parameters that only record who shared what with whom (utm_*,
 * fbclid, igsh, X's s/t, LinkedIn's trk/rcm...). Sharing strips them; parameters a page needs in
 * order to open (Facebook's story_fbid/id, YouTube's v...) are kept.
 */

/** Tracking parameters removed whatever the site. */
private val TRACKING = setOf(
    "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "twclid", "ttclid",
    "igshid", "igsh", "mibextid", "li_fat_id", "mc_cid", "mc_eid", "_ga", "_gl", "_hsenc", "_hsmi",
    "mkt_tok", "ref_src", "ref_url",
)
private val TRACKING_PREFIXES = listOf("utm_")

/** Parameter names that only track on these sites; elsewhere the same names can matter (?s=, ?ref=). */
private class SiteRule(val domains: List<String>, val names: Set<String>, val prefixes: List<String> = emptyList())

private val SITE_RULES = listOf(
    SiteRule(listOf("x.com", "twitter.com"), setOf("s", "t")),
    SiteRule(
        listOf("facebook.com", "fb.com", "fb.watch"),
        setOf(
            "rdid", "ref", "fref", "refid", "refsrc", "hc_ref", "hc_location", "__tn__", "eav", "paipv",
            "acontext", "notif_id", "notif_t", "sfnsn", "extid", "_rdc", "_rdr",
        ),
        prefixes = listOf("__cft__", "__xts__"),
    ),
    SiteRule(
        listOf("linkedin.com"),
        setOf(
            "trk", "trkinfo", "trackingid", "lipi", "midtoken", "midsig", "trkemail", "eid", "otptoken",
            "refid", "rcm", "originalsubdomain",
        ),
    ),
    SiteRule(listOf("youtube.com", "youtu.be"), setOf("si", "feature", "pp")),
)

/** [url] without tracking parameters; the rest (path, other parameters, #fragment) is untouched. */
internal fun withoutTracking(url: String): String {
    val fragmentAt = url.indexOf('#').let { if (it < 0) url.length else it }
    val queryAt = url.indexOf('?').takeIf { it in 0 until fragmentAt } ?: return url
    val base = url.substring(0, queryAt)
    val host = hostOf(base)
    val rules = SITE_RULES.filter { rule -> rule.domains.any { host == it || host.endsWith(".$it") } }
    val kept = url.substring(queryAt + 1, fragmentAt).split('&').filter { param ->
        param.isNotEmpty() && !isTracking(paramName(param), rules)
    }
    val query = if (kept.isEmpty()) "" else kept.joinToString("&", prefix = "?")
    return base + query + url.substring(fragmentAt)
}

private fun isTracking(name: String, rules: List<SiteRule>): Boolean =
    name in TRACKING ||
        TRACKING_PREFIXES.any { name.startsWith(it) } ||
        rules.any { rule -> name in rule.names || rule.prefixes.any { name.startsWith(it) } }

/** The parameter's name, decoded (Facebook sends `__cft__%5B0%5D`) and lower-cased. */
private fun paramName(param: String): String {
    val raw = param.substringBefore('=')
    val decoded = try {
        URLDecoder.decode(raw, "UTF-8")
    } catch (e: IllegalArgumentException) {
        raw
    }
    return decoded.lowercase()
}

private fun hostOf(base: String): String =
    base.substringAfter("://", "").substringBefore('/').substringAfterLast('@').substringBefore(':').lowercase()
