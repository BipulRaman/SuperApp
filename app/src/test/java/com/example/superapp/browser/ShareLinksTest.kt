package com.example.superapp.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareLinksTest {

    private fun assertCleaned(expected: String, url: String) = assertEquals(expected, withoutTracking(url))

    @Test
    fun `instagram share links lose utm and igsh`() {
        assertCleaned(
            "https://www.instagram.com/p/C1x2y3z/",
            "https://www.instagram.com/p/C1x2y3z/?utm_source=ig_web_copy_link&igsh=MWZ4b2t5bHQ1",
        )
    }

    @Test
    fun `x links lose s and t`() {
        assertCleaned(
            "https://x.com/someone/status/1790000000000000000",
            "https://x.com/someone/status/1790000000000000000?s=46&t=AbCdEfGh",
        )
    }

    @Test
    fun `facebook links keep the post ids and lose the trackers`() {
        assertCleaned(
            "https://m.facebook.com/story.php?story_fbid=123&id=456",
            "https://m.facebook.com/story.php?story_fbid=123&id=456&mibextid=Nif5oz&rdid=Ab12&ref=sharing",
        )
        assertCleaned(
            "https://www.facebook.com/photo/?fbid=1&set=a.2",
            "https://www.facebook.com/photo/?fbid=1&set=a.2&__cft__%5B0%5D=AZXq&__tn__=%2CO%2CP-R",
        )
    }

    @Test
    fun `linkedin links lose utm, rcm and trk`() {
        assertCleaned(
            "https://www.linkedin.com/posts/jane_activity-7123-AbCd",
            "https://www.linkedin.com/posts/jane_activity-7123-AbCd?utm_source=share&utm_medium=member_android&rcm=ACoAAB",
        )
        assertCleaned(
            "https://www.linkedin.com/jobs/view/42/?currentJobId=42",
            "https://www.linkedin.com/jobs/view/42/?trk=public_jobs&currentJobId=42&trackingId=x%3D%3D",
        )
    }

    @Test
    fun `youtube keeps the video and loses si`() {
        assertCleaned("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=XYZ&pp=ygU")
        assertCleaned("https://youtu.be/dQw4w9WgXcQ", "https://youtu.be/dQw4w9WgXcQ?si=XYZ")
    }

    @Test
    fun `site-specific names are kept on other sites`() {
        assertCleaned(
            "https://example.com/search?s=kotlin&t=10&ref=home&si=1",
            "https://example.com/search?s=kotlin&t=10&ref=home&si=1&utm_source=x&fbclid=abc",
        )
    }

    @Test
    fun `fragments survive and query-like fragments are left alone`() {
        assertCleaned("https://example.com/a#section", "https://example.com/a?fbclid=1#section")
        assertCleaned("https://example.com/a#x?utm_source=y", "https://example.com/a#x?utm_source=y")
    }

    @Test
    fun `names are matched case-insensitively and empty queries disappear`() {
        assertCleaned("https://example.com/a?keep=1", "https://example.com/a?UTM_Source=x&keep=1&FBCLID=2")
        assertCleaned("https://example.com/a", "https://example.com/a?")
        assertCleaned("https://example.com/a", "https://example.com/a")
    }
}
