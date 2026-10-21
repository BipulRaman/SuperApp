package com.example.superapp.browser

import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView.HitTestResult

/*
 * Long-pressing a link or an image in a page opens a menu like Chrome's: open in browser, copy
 * or share the link (minus tracking parameters), save or copy the image. Pages that handle long
 * presses themselves (e.g. reactions) keep doing so: WebView only offers a menu when they don't.
 */

/**
 * What was long-pressed. [image] is an image address; [link] is a link address, or for an image
 * inside a link ([linkInImage]) not known yet: WebView reports the image and the link's address
 * has to be asked for separately.
 */
internal class LongPressTarget(val link: String?, val image: String?, val linkInImage: Boolean) {
    val hasLink: Boolean get() = link != null || linkInImage
}

/** The long-pressed link or image in WebView's hit test; web (http/https) addresses only. */
internal fun longPressTarget(type: Int, extra: String?): LongPressTarget? {
    val web = extra?.takeIf(URLUtil::isNetworkUrl)
    return when (type) {
        HitTestResult.SRC_ANCHOR_TYPE -> web?.let { LongPressTarget(link = it, image = null, linkInImage = false) }
        HitTestResult.IMAGE_TYPE -> web?.let { LongPressTarget(link = null, image = it, linkInImage = false) }
        HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> LongPressTarget(link = null, image = web, linkInImage = true)
        else -> null
    }
}

/** A short form of [url] for the menu's title: host and path, without the scheme or query. */
internal fun displayUrl(url: String): String {
    val uri = Uri.parse(url)
    val shown = (uri.host.orEmpty() + uri.path.orEmpty()).trimEnd('/').ifEmpty { url }
    return if (shown.length > 60) shown.take(59) + "…" else shown
}

/**
 * The image's type, from its file name or X's ?format=, so a saved image gets the right
 * extension; JPEG when nothing says.
 */
internal fun imageMimeType(url: String): String {
    val format = try {
        Uri.parse(url).getQueryParameter("format")
    } catch (e: UnsupportedOperationException) {
        null
    }
    return when ((format ?: MimeTypeMap.getFileExtensionFromUrl(url)).lowercase()) {
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "avif" -> "image/avif"
        "heic" -> "image/heic"
        else -> "image/jpeg"
    }
}
