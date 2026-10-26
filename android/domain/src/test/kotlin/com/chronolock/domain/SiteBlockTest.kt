package com.chronolock.domain

import org.junit.Assert.*
import org.junit.Test

class SiteBlockTest {
    @Test fun typedAddresses_normalizeToTheirDomain() {
        assertEquals("youtube.com", SiteDomains.normalize("https://www.YouTube.com/watch?v=1"))
        assertEquals("m.youtube.com", SiteDomains.normalize(" m.youtube.com/shorts "))
        assertEquals("naver.com", SiteDomains.normalize("naver.com:443"))
    }

    @Test fun nonAddresses_areRejected() {
        listOf("", "youtube", "you tube.com", "http://", "-a.com", "1.2.3.4", "a..com").forEach { value ->
            assertThrows(value, IllegalArgumentException::class.java) { SiteDomains.normalize(value) }
        }
    }

    @Test fun addressBarText_givesTheHostOrNothing() {
        assertEquals("m.youtube.com", SiteDomains.hostOf("m.youtube.com/watch?v=abc"))
        assertEquals("www.instagram.com", SiteDomains.hostOf("https://user@www.instagram.com:8443/reels#x"))
        assertEquals("m.youtube.com", SiteDomains.hostOf("‎m.youtube.com"))
        assertNull(SiteDomains.hostOf("how to focus"))
        assertNull(SiteDomains.hostOf("Search or type URL"))
        assertNull(SiteDomains.hostOf("   "))
    }

    @Test fun domain_coversItsSubdomainsOnly() {
        val sites = listOf(BlockedSite("youtube.com"), BlockedSite("x.com", enabled = false))
        assertEquals("youtube.com", SiteDomains.match("youtube.com", sites)?.domain)
        assertEquals("youtube.com", SiteDomains.match("m.youtube.com", sites)?.domain)
        assertNull(SiteDomains.match("notyoutube.com", sites))
        assertNull(SiteDomains.match("youtube.com.evil.net", sites))
        assertNull(SiteDomains.match("x.com", sites))
    }

    @Test fun strictMode_holdsChangesThatUnblockASite() {
        val on = BlockedSite("youtube.com")
        assertTrue(Strictness.loosens(on, null))
        assertTrue(Strictness.loosens(on, on.copy(enabled = false)))
        assertFalse(Strictness.loosens(on, on))
        assertFalse(Strictness.loosens(null, on))
        assertFalse(Strictness.loosens(on.copy(enabled = false), null))
    }

    @Test fun everyBrowserId_belongsToItsPackage() {
        Browsers.urlBarIds.forEach { (app, id) -> assertTrue(id, id.startsWith("$app:id/")) }
    }

    @Test fun inAppPage_isTheHostMostLinksPointTo() {
        val youtube = List(5) { "https://m.youtube.com/watch?v=$it" } + "https://accounts.google.com/login" + "javascript:void(0)"
        assertEquals("m.youtube.com", Browsers.pageHost(youtube))
        assertEquals("youtube.com", Browsers.pageHost(List(3) { "https://www.youtube.com/@a$it" }))
        // A blog with one embedded video is not the video site.
        assertEquals("m.blog.naver.com", Browsers.pageHost(List(6) { "https://m.blog.naver.com/p/$it" } + "https://youtu.be/x"))
        assertNull(Browsers.pageHost(listOf("https://a.com/1", "https://a.com/2")))
        assertNull(Browsers.pageHost(List(3) { "https://a.com/$it" } + List(3) { "https://b.com/$it" }))
        assertNull(Browsers.pageHost(emptyList()))
        assertTrue(Browsers.watches("com.nhn.android.search") && Browsers.watches("com.kakao.talk") && !Browsers.watches("com.example"))
    }

    @Test fun inAppPage_isTheBlockedSite_byLinksOrOwnTitle() {
        val sites = listOf(BlockedSite("youtube.com"), BlockedSite("11st.co.kr"))
        // YouTube's empty home has no link targets, only its title.
        assertEquals("youtube.com", Browsers.pageSite(emptyList(), "YouTube", sites))
        assertEquals("youtube.com", Browsers.pageSite(emptyList(), "고양이 영상 - YouTube", sites))
        assertEquals("11st.co.kr", Browsers.pageSite(emptyList(), "11st", sites))
        assertEquals("youtube.com", Browsers.pageSite(List(4) { "https://m.youtube.com/watch?v=$it" }, "", sites))
        assertEquals("", Browsers.pageSite(emptyList(), "YouTube 수익 창출 방법 : 네이버 블로그", sites))
        assertEquals("", Browsers.pageSite(List(4) { "https://news.naver.com/$it" }, null, sites))
        assertNull(Browsers.pageSite(emptyList(), null, sites))
        assertNull(Browsers.pageSite(emptyList(), " ", sites))
    }

    @Test fun siteApps_coverSubdomains_andUnknownSitesHaveNone() {
        assertEquals(listOf("com.google.android.youtube"), SiteApps.of("youtube.com"))
        assertEquals(listOf("com.google.android.youtube"), SiteApps.of("m.youtube.com"))
        assertEquals(emptyList<String>(), SiteApps.of("example.com"))
    }
}
