package com.example.superapp.browser

import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_NONE
import android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_SELECTED
import android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_VERIFIED
import android.net.Uri
import com.example.superapp.MainActivity
import com.example.superapp.data.SITES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class IncomingLinksTest {

    private fun link(url: String) = incomingLink(Uri.parse(url), SITES)

    @Test
    fun `site links open on their site`() {
        val links = mapOf(
            "https://www.facebook.com/share/p/1AbCdEf/?mibextid=wwXIfr" to "facebook",
            "https://facebook.com/groups/123" to "facebook",
            "https://m.facebook.com/story.php?story_fbid=1&id=2" to "facebook",
            "https://web.facebook.com/events/1" to "facebook",
            "https://fb.watch/abcDEF/" to "facebook",
            "https://fb.me/e/abc" to "facebook",
            "https://www.instagram.com/reel/C1x/?igsh=abc" to "instagram",
            "https://instagram.com/someone" to "instagram",
            "https://x.com/someone/status/1?s=20" to "x",
            "https://twitter.com/someone" to "x",
            "https://mobile.twitter.com/someone" to "x",
            "https://www.linkedin.com/posts/someone_activity-1" to "linkedin",
            "https://in.linkedin.com/in/someone" to "linkedin",
        )
        for ((url, site) in links) {
            assertEquals(url, site, link(url)?.site?.id)
            assertEquals(url, url, link(url)?.url)
        }
    }

    @Test
    fun `links load over https, exactly as checked`() {
        assertEquals("https://m.facebook.com/a?b=c#d", link("http://m.facebook.com/a?b=c#d")?.url)
        assertEquals("https://www.instagram.com/p/x/", link("HTTPS://WWW.Instagram.COM./p/x/")?.url)
        assertEquals("https://x.com/", link("https://x.com")?.url)
        assertEquals("https://x.com/?lang=en", link("https://x.com?lang=en")?.url)
        assertEquals("https://www.linkedin.com/feed/", link("https://www.linkedin.com:443/feed/")?.url)
    }

    @Test
    fun `other sites and look-alike hosts are not taken`() {
        for (url in listOf(
            "https://example.com/", "https://notfacebook.com/", "https://facebook.com.evil.example/",
            "https://xx.com/", "https://fb.watch.evil.example/", "https://157.240.1.35/", "https:///path",
            "https://www.f\u0430cebook.com/", // Cyrillic "а"
        )) {
            assertNull(url, link(url))
        }
    }

    @Test
    fun `only plain web links are taken`() {
        for (url in listOf(
            "javascript:alert(1)", "file:///sdcard/page.html", "content://media/external/1",
            "intent://www.facebook.com/#Intent;scheme=https;end", "fb://profile/1", "ftp://facebook.com/",
            "https:www.facebook.com",
        )) {
            assertNull(url, link(url))
        }
    }

    @Test
    fun `links WebView could read as another host are refused`() {
        for (url in listOf(
            "https://evil.example\\@www.facebook.com/", // Chromium reads the host as evil.example
            "https://evil.example\\.facebook.com/", // same
            "https://evil.example%5C.facebook.com/",
            "https://user:pass@www.facebook.com/",
            "https://www.facebook.com%2F.evil.example/",
        )) {
            assertNull(url, link(url))
        }
    }

    @Test
    fun `only view intents carry links`() {
        val url = Uri.parse("https://www.facebook.com/x")
        assertEquals("facebook", incomingLink(Intent(Intent.ACTION_VIEW, url), SITES)?.site?.id)
        assertNull(incomingLink(Intent(Intent.ACTION_SEND).setData(url), SITES))
        assertNull(incomingLink(Intent(Intent.ACTION_MAIN), SITES))
        assertNull(incomingLink(null, SITES))
    }

    @Test
    fun `the manifest declares exactly the sites' link hosts`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val declared = Regex("""android:host="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toList()
        val hosts = SITES.flatMap { it.linkHosts }
        assertEquals(hosts.sorted(), declared.sorted())
        assertEquals(hosts.size, hosts.toSet().size)
    }

    @Test
    fun `Android offers SuperApp for the sites' links only`() {
        val packageManager = RuntimeEnvironment.getApplication().packageManager
        fun handlers(url: String) = packageManager
            .queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE), 0)
            .map { it.activityInfo.name }
        for (host in SITES.flatMap { it.linkHosts }) {
            for (scheme in listOf("https", "http")) {
                val url = "$scheme://${host.replace("*", "www")}/some/page"
                assertEquals(url, listOf(MainActivity::class.java.name), handlers(url))
            }
        }
        for (url in listOf("https://example.com/", "https://xx.com/", "https://notfacebook.com/", "fb://profile/1")) {
            assertEquals(url, emptyList<String>(), handlers(url))
        }
    }

    @Test
    fun `link approval reflects the hosts the user approved`() {
        val hosts = SITES.flatMap { it.linkHosts }
        fun approval(approved: Collection<String>, allowed: Boolean = true) =
            linkApproval(hosts.associateWith { if (it in approved) DOMAIN_STATE_SELECTED else DOMAIN_STATE_NONE }, allowed)

        assertEquals(LinkApproval.OFF, approval(emptyList()))
        assertEquals(LinkApproval.SOME, approval(listOf("*.instagram.com", "instagram.com")))
        assertEquals(LinkApproval.ALL, approval(hosts))
        assertEquals(LinkApproval.OFF, approval(hosts, allowed = false))
        // Android treats an approved *.example.com as covering example.com too.
        assertEquals(LinkApproval.ALL, approval(hosts.filter { "*.$it" !in hosts }))
        assertEquals(LinkApproval.SOME, linkApproval(mapOf("a.example" to DOMAIN_STATE_VERIFIED, "b.example" to DOMAIN_STATE_NONE), true))
    }
}
