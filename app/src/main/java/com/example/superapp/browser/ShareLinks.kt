package com.example.superapp.browser

import java.net.URLDecoder

/*
 * Shared links keep only what the page needs. Removed from the query string: who referred you
 * and where the link was shared or clicked from (ref, referrer, Instagram's igsh, X's s/t,
 * YouTube's si...), ad-click and analytics tags (utm_*, fbclid, gclid, Matomo's, HubSpot's,
 * Mailchimp's...) and the sites' own trackers (Facebook's __cft__, LinkedIn's trk...).
 * Click-tracking redirects (Facebook's l.php?u=..., LinkedIn's, YouTube's, Google's /url?q=...)
 * give way to the link they lead to. Parameters a page needs in order to open (Facebook's
 * story_fbid/id, YouTube's v and t...) are kept.
 */

/** Referrer, ad-click and analytics parameters, removed whatever the site. */
private val TRACKING = setOf(
    // Who referred you: where the link was shared or clicked from.
    "ref", "ref_", "ref_src", "ref_url", "ref_source", "ref_medium", "ref_campaign", "referrer", "referer", "referral",
    "_branch_referrer", "_branch_match_id", "igshid", "igsh", "mibextid",
    // Ad clicks.
    "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "srsltid", "msclkid", "yclid", "ysclid", "twclid",
    "ttclid", "epik", "sccid", "irclickid", "irgwc", "rb_clickid", "li_fat_id", "s_cid", "s_kwcid", "ef_id",
    // Analytics and mailing tools: Google, Matomo/Piwik, HubSpot, Mailchimp, Marketo, Eloqua, Facebook...
    "_ga", "_gl", "_openstat", "cmpid", "spm", "pk_campaign", "pk_kwd", "pk_keyword", "pk_source", "pk_medium",
    "pk_content", "pk_cid", "pk_vid", "_hsenc", "_hsmi", "__hssc", "__hstc", "__hsfp", "hsctatracking", "mc_cid",
    "mc_eid", "mkt_tok", "elqtrackid", "elqtrack", "elqaid", "elqat", "elqcampaignid", "oly_anon_id", "oly_enc_id",
    "vero_id", "vero_conv", "wickedid", "ml_subscriber", "ml_subscriber_hash", "_kx", "_bhlid", "__s", "trk_contact",
    "trk_msg", "trk_module", "trk_sid", "itm_source", "itm_medium", "itm_campaign", "itm_content", "itm_term",
    "fb_action_ids", "fb_action_types", "fb_ref", "fb_source", "action_object_map", "action_type_map",
    "action_ref_map", "__twitter_impression",
)

/** Families of analytics parameters: utm_source, mtm_campaign, hsa_acc, gad_source, WT.mc_id... */
private val TRACKING_PREFIXES = listOf("utm_", "mtm_", "matomo_", "piwik_", "hsa_", "gad_", "wt.")

/** Parameter names that only track on these sites; elsewhere the same names can matter (?s=, ?si=, ?source=). */
private class SiteRule(val domains: List<String>, val names: Set<String>, val prefixes: List<String> = emptyList())

private val SITE_RULES = listOf(
    SiteRule(listOf("x.com", "twitter.com"), setOf("s", "t")),
    SiteRule(
        listOf("facebook.com", "fb.com", "fb.watch"),
        setOf(
            "rdid", "fref", "refid", "refsrc", "hc_ref", "hc_location", "__tn__", "eav", "paipv", "acontext",
            "notif_id", "notif_t", "sfnsn", "extid", "_rdc", "_rdr", "wtsid", "comment_tracking",
        ),
        prefixes = listOf("__cft__", "__xts__"),
    ),
    SiteRule(listOf("threads.net", "threads.com"), setOf("xmt")),
    SiteRule(
        listOf("linkedin.com"),
        setOf(
            "trk", "trkinfo", "trackingid", "lipi", "midtoken", "midsig", "trkemail", "eid", "otptoken",
            "refid", "rcm", "originalsubdomain",
        ),
    ),
    SiteRule(
        listOf("youtube.com", "youtu.be"),
        setOf("si", "feature", "pp", "ab_channel", "source_ve_path"),
        prefixes = listOf("embeds_"),
    ),
    SiteRule(listOf("spotify.com"), setOf("si")),
    SiteRule(listOf("reddit.com"), setOf("share_id", "rdt")),
    SiteRule(listOf("medium.com"), setOf("source")),
    SiteRule(
        listOf("tiktok.com"),
        setOf(
            "_r", "_t", "is_from_webapp", "sender_device", "sender_web_id", "web_id", "share_app_id", "share_link_id",
            "share_item_id", "social_sharing", "tt_from", "u_code", "user_id", "sec_uid", "is_copy_url", "timestamp",
            "checksum", "source",
        ),
    ),
)

/** A click-tracking redirect: on [hosts], at one of [paths] (any if null), leading to the link in one of [params]. */
private class Redirect(val hosts: (String) -> Boolean, val paths: Set<String>?, val params: List<String>)

/** The hosts [names] and their subdomains. */
private fun domains(vararg names: String): (String) -> Boolean = { host -> names.any { host == it || host.endsWith(".$it") } }

/** google.com, google.co.in, google.com.au... */
private val GOOGLE = Regex("""(?:www\.)?google\.(?:com|[a-z]{2})(?:\.[a-z]{2})?""")

private val REDIRECTS = listOf(
    // Facebook's and Messenger's link shim (l.facebook.com/l.php?u=...) and "Leaving Facebook" page.
    Redirect(domains("facebook.com", "messenger.com"), setOf("/l.php", "/flx/warn"), listOf("u")),
    Redirect(domains("l.instagram.com", "l.threads.net", "l.threads.com"), null, listOf("u")),
    Redirect(domains("linkedin.com"), setOf("/redir/redirect", "/safety/go"), listOf("url")),
    Redirect(domains("youtube.com"), setOf("/redirect"), listOf("q")),
    Redirect({ GOOGLE.matches(it) }, setOf("/url"), listOf("q", "url")),
)

/** Redirects in a row followed at most (Google's leading to Facebook's...). */
private const val MAX_REDIRECTS = 4

/**
 * [url] as it should be shared: a click-tracking redirect replaced by the link it leads to, and
 * without tracking parameters; the rest (path, other parameters, #fragment) is untouched.
 */
internal fun withoutTracking(url: String): String {
    var link = url
    for (hop in 1..MAX_REDIRECTS) link = redirectTarget(link) ?: break
    return withoutTrackingParameters(link)
}

private fun withoutTrackingParameters(url: String): String {
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

/** The web link a click-tracking redirect [url] leads to; null if it isn't one, or leads nowhere valid. */
private fun redirectTarget(url: String): String? {
    val fragmentAt = url.indexOf('#').let { if (it < 0) url.length else it }
    val queryAt = url.indexOf('?').takeIf { it in 0 until fragmentAt } ?: return null
    val base = url.substring(0, queryAt)
    val host = hostOf(base)
    val path = "/" + base.substringAfter("://", "").substringAfter('/', "").trimEnd('/').lowercase()
    val redirect = REDIRECTS.firstOrNull { it.hosts(host) && (it.paths == null || path in it.paths) } ?: return null
    val params = url.substring(queryAt + 1, fragmentAt).split('&')
    for (name in redirect.params) {
        val value = params.firstOrNull { it.isNotEmpty() && paramName(it) == name }?.substringAfter('=', "") ?: continue
        val target = decoded(value) ?: continue
        if (isWebLink(target)) return target
    }
    return null
}

private fun isWebLink(url: String): Boolean =
    (url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)) &&
        hostOf(url.substringBefore('#').substringBefore('?')).isNotEmpty() &&
        url.none { it.isWhitespace() || it.isISOControl() }

private fun isTracking(name: String, rules: List<SiteRule>): Boolean =
    name in TRACKING ||
        TRACKING_PREFIXES.any { name.startsWith(it) } ||
        rules.any { rule -> name in rule.names || rule.prefixes.any { name.startsWith(it) } }

/** The parameter's name, decoded (Facebook sends `__cft__%5B0%5D`) and lower-cased. */
private fun paramName(param: String): String {
    val raw = param.substringBefore('=')
    return (decoded(raw) ?: raw).lowercase()
}

private fun decoded(text: String): String? =
    try {
        URLDecoder.decode(text, "UTF-8")
    } catch (e: IllegalArgumentException) {
        null
    }

private fun hostOf(base: String): String =
    base.substringAfter("://", "").substringBefore('/').substringAfterLast('@').substringBefore(':').lowercase()