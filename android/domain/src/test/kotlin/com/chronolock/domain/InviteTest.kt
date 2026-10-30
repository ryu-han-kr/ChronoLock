package com.chronolock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteTest {
    @Test fun sharedLink_bringsTheFriendsGoals() {
        val link = Invite.link(setOf(OnboardingGoal.VIDEO, OnboardingGoal.SOCIAL))
        // Play hands the app the decoded referrer value.
        val referrer = java.net.URLDecoder.decode(link.substringAfter("&referrer="), "UTF-8")
        assertEquals("utm_source=invite&utm_medium=app&utm_content=SOCIAL.VIDEO", referrer)
        assertEquals(Invite(setOf(OnboardingGoal.SOCIAL, OnboardingGoal.VIDEO), null, fromFriend = true), Invite.parse(referrer))
    }

    @Test fun storeListingCampaign_picksTheSituationsGoals() {
        val invite = Invite.parse("utm_source=google-play&utm_campaign=study")
        assertEquals(Situation.STUDY, invite?.situation)
        assertEquals(Situation.STUDY.goals, invite?.goals)
        assertEquals(false, invite?.fromFriend)
    }

    @Test fun organicOrUnknownReferrers_bringNothing() {
        assertNull(Invite.parse(null))
        assertNull(Invite.parse("utm_source=google-play&utm_medium=organic"))
        assertNull(Invite.parse("utm_source=invite&utm_content=NOPE"))
        assertNull(Invite.parse("garbage"))
        assertNull(Invite.parse("utm_content=%ZZ"))
        // The older share link had no goals.
        assertNull(Invite.parse("utm_source=share&utm_medium=app"))
    }
}
