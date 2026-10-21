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
        assertCleaned("https://m.facebook.com/", "https://m.facebook.com/?wtsid=rdr_0UrzUJ29Ux0m2pJVr")
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
    fun `youtube keeps the video and its start time and loses si`() {
        assertCleaned("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=XYZ&pp=ygU")
        assertCleaned("https://youtu.be/dQw4w9WgXcQ", "https://youtu.be/dQw4w9WgXcQ?si=XYZ")
        assertCleaned(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s&list=PL1",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s&ab_channel=Rick&list=PL1&embeds_referring_euri=https%3A%2F%2Fexample.com%2F",
        )
    }

    @Test
    fun `referrer tags go whatever the site`() {
        assertCleaned(
            "https://example.com/post?id=7",
            "https://example.com/post?id=7&ref=producthunt&referrer=newsletter&ref_src=twsrc%5Etfw&referral=abc&ref_=nav",
        )
        assertCleaned("https://blog.example/launch", "https://blog.example/launch?_branch_referrer=H4sI&_branch_match_id=1")
    }

    @Test
    fun `analytics and ad-click tags go whatever the site`() {
        assertCleaned(
            "https://shop.example/item?id=1&color=red",
            "https://shop.example/item?id=1&gclid=x&gad_source=1&gad_campaignid=2&srsltid=AfmB&mtm_campaign=a&color=red" +
                "&pk_campaign=b&_hsenc=c&__hstc=d&hsa_acc=1&mc_eid=e&mkt_tok=f&s_cid=g&ef_id=h&_kx=i&_bhlid=j&ttclid=k" +
                "&msclkid=l&yclid=m&ysclid=n&WT.mc_id=o&elqTrackId=p&epik=q&ScCid=r&irclickid=s&spm=t&cmpid=u",
        )
    }

    @Test
    fun `other sites' share trackers go too`() {
        assertCleaned(
            "https://www.tiktok.com/@someone/video/7300000000000000000?lang=en",
            "https://www.tiktok.com/@someone/video/7300000000000000000?is_from_webapp=1&sender_device=pc&web_id=7&_r=1&_t=ZS-8&lang=en",
        )
        assertCleaned(
            "https://www.reddit.com/r/android/comments/1abc/title/",
            "https://www.reddit.com/r/android/comments/1abc/title/?share_id=Xy&utm_medium=android_app&utm_source=share&rdt=12345",
        )
        assertCleaned(
            "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?context=spotify%3Aalbum%3A1",
            "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=abc123&context=spotify%3Aalbum%3A1",
        )
        assertCleaned("https://www.threads.net/@someone/post/C8abc", "https://www.threads.net/@someone/post/C8abc?xmt=AQGz&igshid=MzRl")
        assertCleaned("https://medium.com/@someone/title-1a2b?sk=friend", "https://medium.com/@someone/title-1a2b?source=rss----1&sk=friend")
    }

    @Test
    fun `site-specific names are kept on other sites`() {
        assertCleaned(
            "https://example.com/search?s=kotlin&t=10&si=1&source=app&trk=7",
            "https://example.com/search?s=kotlin&t=10&ref=home&si=1&source=app&trk=7&utm_source=x&fbclid=abc",
        )
    }

    @Test
    fun `click-tracking redirects give way to the link they lead to, cleaned`() {
        assertCleaned(
            "https://example.com/news?id=7",
            "https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Fnews%3Fid%3D7%26fbclid%3DIwAR0&h=AT0abc&__tn__=-UK-R&c[0]=AT1",
        )
        assertCleaned(
            "https://www.youtube.com/watch?v=abc",
            "https://lm.facebook.com/l.php?u=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3Dabc%26si%3Dx&h=AT0",
        )
        assertCleaned("https://example.com/a#part", "https://www.facebook.com/flx/warn/?u=https%3A%2F%2Fexample.com%2Fa%23part&h=AT0")
        assertCleaned("https://example.com/", "https://l.messenger.com/l.php?u=https%3A%2F%2Fexample.com%2F&h=AT0")
        assertCleaned("https://example.com/", "https://l.instagram.com/?u=https%3A%2F%2Fexample.com%2F%3Futm_source%3Dig&e=AT0")
        assertCleaned("https://example.com/", "https://l.threads.net/?u=https%3A%2F%2Fexample.com%2F&e=AT0")
        assertCleaned(
            "https://example.com/job",
            "https://www.linkedin.com/redir/redirect?url=https%3A%2F%2Fexample.com%2Fjob&urlhash=Ab&trk=public_post-text",
        )
        assertCleaned("https://example.com", "https://www.linkedin.com/safety/go?url=https%3A%2F%2Fexample.com&trk=flagship-messaging-web")
        assertCleaned(
            "https://example.com/",
            "https://www.youtube.com/redirect?event=video_description&redir_token=QUFF&q=https%3A%2F%2Fexample.com%2F",
        )
        assertCleaned("https://example.com/a", "https://www.google.com/url?sa=t&url=https%3A%2F%2Fexample.com%2Fa&ved=2ah")
        assertCleaned("https://example.com/b", "https://www.google.co.in/url?q=https://example.com/b&sa=U")
        assertCleaned(
            "https://example.com/",
            "https://www.google.com/url?q=https%3A%2F%2Fl.facebook.com%2Fl.php%3Fu%3Dhttps%253A%252F%252Fexample.com%252F%26h%3DAT&sa=D",
        )
    }

    @Test
    fun `redirects that lead nowhere valid, and look-alikes, are only cleaned`() {
        assertCleaned("https://l.facebook.com/l.php?u=javascript%3Aalert(1)&h=AT0", "https://l.facebook.com/l.php?u=javascript%3Aalert(1)&h=AT0")
        assertCleaned("https://l.facebook.com/l.php?h=AT0", "https://l.facebook.com/l.php?h=AT0&utm_source=x")
        assertCleaned("https://www.google.com/url?q=%2Fsearch", "https://www.google.com/url?q=%2Fsearch")
        assertCleaned(
            "https://notgoogle.com/url?q=https%3A%2F%2Fexample.com%2F",
            "https://notgoogle.com/url?q=https%3A%2F%2Fexample.com%2F",
        )
        assertCleaned(
            "https://www.facebook.com/groups/1/l.php?u=https%3A%2F%2Fexample.com%2F",
            "https://www.facebook.com/groups/1/l.php?u=https%3A%2F%2Fexample.com%2F",
        )
        assertCleaned("https://example.com/l.php?u=https%3A%2F%2Fevil.example%2F", "https://example.com/l.php?u=https%3A%2F%2Fevil.example%2F")
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
