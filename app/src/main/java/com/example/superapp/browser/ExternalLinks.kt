package com.example.superapp.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.URLUtil
import android.webkit.WebView
import com.example.superapp.data.Site
import java.net.URISyntaxException

/** How a navigation should be handled; see [routeFor]. */
internal enum class Route { WEB_VIEW, EXTERNAL_APP, BLOCK }

/**
 * Web schemes load in the WebView. Local-file schemes are never followed. Anything else (intent:,
 * market:, mailto:, tel:, fb:...) belongs to another app, and only a user tap may open it so pages
 * can't bounce people out of SuperApp on their own.
 */
internal fun routeFor(scheme: String?, userGesture: Boolean): Route =
    when (scheme?.lowercase()) {
        null, "http", "https", "about", "data", "blob", "javascript" -> Route.WEB_VIEW
        "file", "content" -> Route.BLOCK
        else -> if (userGesture) Route.EXTERNAL_APP else Route.BLOCK
    }

private const val URI_GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

/**
 * Opens a non-web link in the app that handles it, falling back to the link's web fallback.
 * True if another app was opened.
 */
internal fun Activity.openExternally(webView: WebView, uri: Uri): Boolean {
    val intent = try {
        if (uri.scheme.equals("intent", ignoreCase = true)) {
            Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }
    } catch (e: URISyntaxException) {
        return false
    }
    // An intent: link can carry local data (file:, content:) that must never be handed out.
    val data = intent.data
    if (data != null && routeFor(data.scheme, userGesture = true) == Route.BLOCK) return false
    // Same hardening browsers apply: only browsable activities, no URI permission grants.
    intent.addCategory(Intent.CATEGORY_BROWSABLE)
    intent.component = null
    intent.selector = null
    intent.clipData = null
    intent.flags = intent.flags and URI_GRANT_FLAGS.inv()
    return try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        intent.getStringExtra("browser_fallback_url")?.takeIf(URLUtil::isNetworkUrl)?.let(webView::loadUrl)
        false
    } catch (e: RuntimeException) {
        // The target refused the launch (SecurityException) or the intent was malformed.
        false
    }
}

/*
 * Like Chrome, a tapped web link that an installed app owns (an app link: WhatsApp for wa.me and
 * api.whatsapp.com, YouTube, Maps...) opens that app; that's how a site's "Share to WhatsApp"
 * works. Only when the user tapped it in the page itself (not a frame), and only when it leaves
 * the site the page is on: SuperApp's own sites always stay here.
 */

/** Whether a page's navigation to [target] may open the app installed for it; see above. */
internal fun mayOpenInApp(target: Uri, currentUrl: String?, mainFrame: Boolean, userGesture: Boolean, sites: List<Site>): Boolean {
    if (!mainFrame || !userGesture) return false
    val scheme = target.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return false
    val host = target.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
    if (sites.any { it.hasLinkHost(host) }) return false
    val current = currentUrl?.let { Uri.parse(it).host?.lowercase() }
    return current == null || siteOf(host) != siteOf(current)
}

/** Roughly the registrable domain (m.youtube.com and www.youtube.com are both youtube.com). */
private fun siteOf(host: String): String = host.split('.').takeLast(2).joinToString(".")

/**
 * Opens a web link in the app installed for it, if any app but a browser handles it. False when
 * only browsers do, so it loads in the WebView.
 */
internal fun Activity.openInApp(uri: Uri): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
    if (Build.VERSION.SDK_INT >= 30) {
        intent.addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
    } else {
        val apps = appsForLink(intent)
        if (apps.isEmpty()) return false
        apps.singleOrNull()?.let(intent::setPackage)
    }
    return try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: RuntimeException) {
        false
    }
}

/**
 * Android 10 and older: the apps (other than SuperApp) with a link filter that names the link's
 * host. Browsers' filters name no host.
 */
// Only called before Android 11, which has no package visibility rules; newer versions let
// FLAG_ACTIVITY_REQUIRE_NON_BROWSER decide, so no <queries> declaration is needed.
@SuppressLint("QueryPermissionsNeeded")
internal fun Context.appsForLink(intent: Intent): List<String> =
    packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_RESOLVED_FILTER)
        .filter { (it.filter?.countDataAuthorities() ?: 0) > 0 }
        .map { it.activityInfo.packageName }
        .filter { it != packageName }
        .distinct()
