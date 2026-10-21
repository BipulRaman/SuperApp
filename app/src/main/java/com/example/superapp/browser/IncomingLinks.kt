package com.example.superapp.browser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.example.superapp.data.Site

/*
 * Links to the sites tapped in other apps (WhatsApp, Gmail, Messages...) open in SuperApp: the
 * manifest declares every site's link hosts for http/https VIEW intents. Android 11 and older
 * offer SuperApp next to the browser; Android 12+ only hands a link to an app the user approved
 * for its host, on the app's "Open by default" settings page.
 */

/** A link another app asked SuperApp to show, and the site whose tab shows it. */
internal class IncomingLink(val site: Site, val url: String)

/** Plain host names only: anything else (\ @ % spaces...) could make WebView load another host. */
private val HOST_NAME = Regex("[a-z0-9-]+(\\.[a-z0-9-]+)+")

/** The site link [intent] asks to view, or null if it isn't one (see the Uri overload). */
internal fun incomingLink(intent: Intent?, sites: List<Site>): IncomingLink? =
    intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data?.let { incomingLink(it, sites) }

/**
 * [uri] as a link to one of [sites], or null for other hosts and anything but a plain http(s)
 * link. The URL is rebuilt over https around the checked host, so WebView can't read a different
 * host out of it than the one checked here (user info, backslashes, escapes...).
 */
internal fun incomingLink(uri: Uri, sites: List<Site>): IncomingLink? {
    val scheme = uri.scheme?.lowercase()
    if ((scheme != "http" && scheme != "https") || uri.isOpaque || uri.encodedUserInfo != null) return null
    val host = uri.host?.lowercase()?.removeSuffix(".")?.takeIf(HOST_NAME::matches) ?: return null
    val site = sites.firstOrNull { it.hasLinkHost(host) } ?: return null
    val url = buildString {
        append("https://").append(host)
        append(uri.encodedPath?.takeIf { it.startsWith('/') } ?: "/")
        uri.encodedQuery?.let { append('?').append(it) }
        uri.encodedFragment?.let { append('#').append(it) }
    }
    return IncomingLink(site, url)
}

/** Whether links to [host] belong to this site: one of its hosts, or a subdomain of a `*.` one. */
internal fun Site.hasLinkHost(host: String): Boolean =
    linkHosts.any { if (it.startsWith("*.")) host.endsWith(it.substring(1)) else host == it }

/** How many of the sites' links Android 12+ sends to SuperApp, as approved by the user. */
enum class LinkApproval { OFF, SOME, ALL }

/**
 * [hosts] maps each link host SuperApp declares to its DomainVerificationUserState state, and
 * [allowed] is the app's "Open supported links" switch. An approved `*.example.com` also covers
 * example.com itself, as Android treats it.
 */
@RequiresApi(31)
internal fun linkApproval(hosts: Map<String, Int>, allowed: Boolean): LinkApproval {
    val approved = hosts.filterValues { it != DomainVerificationUserState.DOMAIN_STATE_NONE }.keys
    val covered = hosts.keys.count { host ->
        host in approved || approved.any { it.startsWith("*.") && host.endsWith(it.substring(2)) }
    }
    return when {
        !allowed || covered == 0 -> LinkApproval.OFF
        covered == hosts.size -> LinkApproval.ALL
        else -> LinkApproval.SOME
    }
}

/** The user's current approval of SuperApp's links; OFF if Android doesn't say. */
@RequiresApi(31)
internal fun Context.linkApproval(): LinkApproval {
    val state = try {
        getSystemService(DomainVerificationManager::class.java)?.getDomainVerificationUserState(packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: RuntimeException) {
        null
    }
    return if (state == null) LinkApproval.OFF else linkApproval(state.hostToStateMap, state.isLinkHandlingAllowed)
}

/**
 * Opens Android's "Open by default" page for SuperApp, where the user approves the sites' links
 * ("Add link"), or the app's info page on devices without it.
 */
@RequiresApi(31)
internal fun Activity.openLinkSettings() {
    val app = Uri.fromParts("package", packageName, null)
    for (action in listOf(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Settings.ACTION_APPLICATION_DETAILS_SETTINGS)) {
        try {
            startActivity(Intent(action, app))
            return
        } catch (e: ActivityNotFoundException) {
            // Try the next page.
        }
    }
}
