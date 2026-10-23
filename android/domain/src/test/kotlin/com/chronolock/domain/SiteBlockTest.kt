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
}
