package com.chronolock.domain

import org.junit.Assert.*
import org.junit.Test

class DailyTimeInputTest {
    @Test fun combinedFields_saveAsSeconds() {
        assertEquals("5415", DailyTimeInput("1", "30", "15").toSecondsText())
    }
    @Test fun allBlank_removesDailyLimit() {
        assertEquals("", DailyTimeInput().toSecondsText())
        assertNull(RuleDraft(daily = DailyTimeInput().toSecondsText(), opens = "5").build("test", "Test").dailyLimitSeconds)
    }
    @Test fun blankParts_areZero() {
        assertEquals("1800", DailyTimeInput(minutes = "30").toSecondsText())
        assertEquals("1", DailyTimeInput(seconds = "1").toSecondsText())
    }
    @Test fun dayLimit_accepts24HoursButNotMore() {
        assertEquals("86400", DailyTimeInput("24", "0", "0").toSecondsText())
        assertThrows(IllegalArgumentException::class.java) { DailyTimeInput("24", "0", "1").toSecondsText() }
    }
    @Test fun invalidParts_areNotSilentlyNormalized() {
        listOf(DailyTimeInput("0", "0", "0"), DailyTimeInput(hours = "25"), DailyTimeInput(minutes = "60"),
            DailyTimeInput(seconds = "60"), DailyTimeInput(hours = "-1"), DailyTimeInput(minutes = "x"),
            DailyTimeInput(hours = "9999999999999999999999")).forEach { input ->
            assertThrows(IllegalArgumentException::class.java) { input.toSecondsText() }
        }
    }
    @Test fun sessionAndCooldownFields_nameTheirOwnLimitInErrors() {
        assertEquals("300", DailyTimeInput(minutes = "5").toSecondsText("1회 허용 시간"))
        val range = assertThrows(IllegalArgumentException::class.java) { DailyTimeInput("0", "0", "0").toSecondsText("재실행 대기 시간") }
        assertEquals("재실행 대기 시간은 1초~24시간으로 설정해 주세요. 제한하지 않으려면 세 칸을 모두 비워 주세요.", range.message)
        val part = assertThrows(IllegalArgumentException::class.java) { DailyTimeInput(minutes = "60").toSecondsText("1회 허용 시간") }
        assertEquals("1회 허용 시간: 분 항목에 0~59의 정수를 입력해 주세요.", part.message)
    }
    @Test fun storedLimits_roundTripWithoutLosingSeconds() {
        listOf("", "1", "59", "60", "3599", "3600", "5415", "86399", "86400").forEach {
            assertEquals(it, DailyTimeInput.fromSeconds(it).toSecondsText())
        }
    }
}
