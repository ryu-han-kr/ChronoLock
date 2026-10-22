package com.chronolock.domain

/** Keeps partially entered time fields intact until the user saves. */
data class DailyTimeInput(val hours: String = "", val minutes: String = "", val seconds: String = "") {
    /** [title] names the limit in validation messages; every current title takes the particle 은. */
    fun toSecondsText(title: String = "하루 허용 시간"): String {
        if (listOf(hours, minutes, seconds).all { it.isBlank() }) return ""
        fun part(text: String, maximum: Long, label: String): Long {
            if (text.isBlank()) return 0
            val value = text.toLongOrNull()
            require(text.all { it in '0'..'9' } && value != null && value in 0..maximum) {
                "$title: $label 항목에 0~${maximum}의 정수를 입력해 주세요."
            }
            return value
        }
        val total = part(hours, 24, "시") * 3600 + part(minutes, 59, "분") * 60 + part(seconds, 59, "초")
        require(total in 1..86400) { "${title}은 1초~24시간으로 설정해 주세요. 제한하지 않으려면 세 칸을 모두 비워 주세요." }
        return total.toString()
    }

    companion object {
        fun fromSeconds(value: String): DailyTimeInput {
            if (value.isBlank()) return DailyTimeInput()
            val total = requireNotNull(value.toLongOrNull())
            require(total in 1..86400)
            return DailyTimeInput((total / 3600).toString(), (total % 3600 / 60).toString(), (total % 60).toString())
        }
    }
}
