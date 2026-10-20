package com.example.superapp.browser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.URLUtil
import android.webkit.WebView
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

/** Opens a non-web link in the app that handles it, falling back to the link's web fallback. */
internal fun Activity.openExternally(webView: WebView, uri: Uri) {
    val intent = try {
        if (uri.scheme.equals("intent", ignoreCase = true)) {
            Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }
    } catch (e: URISyntaxException) {
        return
    }
    // An intent: link can carry local data (file:, content:) that must never be handed out.
    val data = intent.data
    if (data != null && routeFor(data.scheme, userGesture = true) == Route.BLOCK) return
    // Same hardening browsers apply: only browsable activities, no URI permission grants.
    intent.addCategory(Intent.CATEGORY_BROWSABLE)
    intent.component = null
    intent.selector = null
    intent.clipData = null
    intent.flags = intent.flags and URI_GRANT_FLAGS.inv()
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        intent.getStringExtra("browser_fallback_url")?.takeIf(URLUtil::isNetworkUrl)?.let(webView::loadUrl)
    } catch (e: RuntimeException) {
        // The target refused the launch (SecurityException) or the intent was malformed.
    }
}
