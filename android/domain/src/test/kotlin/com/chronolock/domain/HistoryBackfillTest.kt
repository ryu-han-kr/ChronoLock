package com.chronolock.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryBackfillTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private fun at(day: LocalDate, hour: Int = 12) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val d1 = LocalDate.of(2026, 9, 23)
    private val hour = 3_600_000L

    @Test fun dailyBucket_fillsItsGapDayExactly() {
        val daily = listOf(UsageBucket("a", at(d1, 9), at(d1, 20), 2 * hour))
        val result = estimateGapUsage(listOf(daily), emptyMap(), d1, d1.plusDays(1), zone)
        assertEquals(mapOf(DailyKey("a", d1) to 2 * hour), result)
    }

    @Test fun weeklyBucket_subtractsRecordedDays_andSharesRestOverUnknownDays() {
        // Bucket spans 7 days; the last 3 are recorded (1 h each), the first 4 are the gap.
        val weekly = listOf(UsageBucket("a", at(d1, 0), at(d1.plusDays(6), 23), 7 * hour))
        val recorded = mapOf("a" to (4..6L).associate { d1.plusDays(it) to hour })
        val result = estimateGapUsage(listOf(emptyList(), weekly), recorded, d1, d1.plusDays(4), zone)
        assertEquals(4, result.size)
        assertTrue(result.values.all { it == hour }) // (7 h - 3 h) / 4 unknown days
    }

    @Test fun bucketReachingBeforeGap_onlyReturnsGapShare() {
        // 4-day bucket, 2 days before the gap start and 2 inside it, nothing recorded.
        val weekly = listOf(UsageBucket("a", at(d1.minusDays(2), 0), at(d1.plusDays(1), 23), 8 * hour))
        val result = estimateGapUsage(listOf(emptyList(), weekly), emptyMap(), d1, d1.plusDays(2), zone)
        assertEquals(2 * hour, result[DailyKey("a", d1)])
        assertEquals(2 * hour, result[DailyKey("a", d1.plusDays(1))])
    }

    @Test fun finerInterval_wins_coarserDoesNotDoubleCount() {
        val daily = listOf(UsageBucket("a", at(d1, 9), at(d1, 18), hour))
        val weekly = listOf(UsageBucket("a", at(d1, 0), at(d1.plusDays(1), 23), 10 * hour))
        val result = estimateGapUsage(listOf(daily, weekly), emptyMap(), d1, d1.plusDays(2), zone)
        assertEquals(hour, result[DailyKey("a", d1)])
        assertEquals(5 * hour, result[DailyKey("a", d1.plusDays(1))]) // weekly only fills the day daily did not cover
    }

    @Test fun recordedMoreThanBucket_givesNoEstimate() {
        val weekly = listOf(UsageBucket("a", at(d1, 0), at(d1.plusDays(1), 23), hour))
        val recorded = mapOf("a" to mapOf(d1.plusDays(1) to 2 * hour))
        assertTrue(estimateGapUsage(listOf(weekly), recorded, d1, d1.plusDays(1), zone).isEmpty())
    }

    @Test fun emptyGap_returnsNothing() =
        assertTrue(estimateGapUsage(listOf(listOf(UsageBucket("a", at(d1), at(d1), hour))), emptyMap(), d1, d1, zone).isEmpty())
}
