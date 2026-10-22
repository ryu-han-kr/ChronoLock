package com.chronolock.feature

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chronolock.data.AppLanguage
import org.junit.Assert.*
import org.junit.Test

class LocalizationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Test fun allMessages_areTranslated_andFormatArgumentsMatchInEveryLanguage() {
        val placeholder = Regex("%([0-9]+)\\$([sd])")
        AppLanguage.values().filter { it != AppLanguage.SYSTEM }.forEach { language ->
            val localized = context.forLanguage(language)
            translationKeys.forEach { (source, id) ->
                val translated = localized.getText(id).toString()
                val expected = placeholder.findAll(source).map { it.groupValues[1] to it.groupValues[2] }.toSet()
                val actual = placeholder.findAll(translated).map { it.groupValues[1] to it.groupValues[2] }.toSet()
                assertEquals("${language.name}: $source", expected, actual)
                if (language != AppLanguage.KOREAN) assertFalse("${language.name}: $translated", Regex("[가-힣]").containsMatchIn(translated))
                val args = Array<Any>(expected.maxOfOrNull { it.first.toInt() } ?: 0) { index ->
                    if (expected.firstOrNull { it.first.toInt() == index + 1 }?.second == "d") 12L else "Example"
                }
                assertTrue(localized.getString(id, *args).isNotBlank())
            }
        }
    }
    @Test fun everyMascotLine_hasATranslation() {
        // Lines rotate by date, so a missing key shows Korean to other languages only on some days.
        MascotLines.lines.values.flatten().forEach { assertTrue("Missing translation key: $it", it in translationKeys) }
    }
    @Test fun domainValidation_andDuration_areLocalized() {
        val text = UiText(context.forLanguage(AppLanguage.ENGLISH))
        assertEquals("Opens: enter a whole number from 1 to 10000.", text("실행 횟수: 1~10000 범위의 정수를 입력해 주세요."))
        assertEquals("Daily time limit: enter a whole number from 0 to 59 for Min.", text("하루 허용 시간: 분 항목에 0~59의 정수를 입력해 주세요."))
        assertEquals("Wait after leaving must be 1 second to 24 hours. Clear all three fields for no limit.",
            text("재실행 대기 시간은 1초~24시간으로 설정해 주세요. 제한하지 않으려면 세 칸을 모두 비워 주세요."))
        assertEquals("1 h 1 min 1 s", formatDuration(3661, text))
        assertEquals("Choose at least one day.", text("예약할 요일을 하나 이상 선택해 주세요."))
    }
}
