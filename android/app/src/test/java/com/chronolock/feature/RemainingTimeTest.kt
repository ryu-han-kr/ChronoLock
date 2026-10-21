package com.chronolock.feature

import org.junit.Assert.assertEquals
import org.junit.Test

class RemainingTimeTest {
    @Test fun unusedLimit_isFullyAvailable() = assertEquals(3600L, remainingDailySeconds(3600, 0))
    @Test fun partialSecond_roundsUpUntilFullyUsed() = assertEquals(1L, remainingDailySeconds(60, 59_001))
    @Test fun exactLimit_leavesNothing() = assertEquals(0L, remainingDailySeconds(60, 60_000))
    @Test fun overuse_neverGoesNegative() = assertEquals(0L, remainingDailySeconds(60, 90_000))
}
