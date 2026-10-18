package com.chronolock.data

import java.util.Locale

enum class AppLanguage(val tag: String, val nativeName: String) {
    SYSTEM("", "System"), KOREAN("ko", "한국어"), ENGLISH("en", "English"),
    JAPANESE("ja", "日本語"), CHINESE("zh-CN", "简体中文"), SPANISH("es", "Español");

    fun locale(): Locale = if (this != SYSTEM) Locale.forLanguageTag(tag) else {
        val system = android.content.res.Resources.getSystem().configuration.locales[0]
        if (system.language in setOf("ko", "en", "ja", "zh", "es")) {
            if (system.language == "zh") Locale.SIMPLIFIED_CHINESE else system
        } else Locale.ENGLISH
    }
    companion object {
        fun fromStored(value: String?) = values().firstOrNull { it.name == value } ?: KOREAN
    }
}
