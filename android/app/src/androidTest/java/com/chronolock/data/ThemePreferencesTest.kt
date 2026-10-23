package com.chronolock.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferencesTest {
    @Test fun selectedLanguage_isReadByNewPreferencesInstance() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Preferences(context)
        val original = preferences.language.first()
        try {
            AppLanguage.values().forEach { language ->
                preferences.setLanguage(language)
                assertEquals(language, Preferences(context).language.first())
            }
        } finally { preferences.setLanguage(original) }
    }
    @Test fun selectedTheme_isReadByNewPreferencesInstance() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Preferences(context)
        val original = preferences.theme.first()
        try {
            AppTheme.values().forEach { theme ->
                preferences.setTheme(theme)
                assertEquals(theme, Preferences(context).theme.first())
            }
        } finally { preferences.setTheme(original) }
    }
    @Test fun favoriteThemes_toggleAndPersist() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = Preferences(context)
        val theme = ThemeCatalog.pastel.first()
        val had = theme.name in preferences.favoriteThemes.first()
        try {
            preferences.toggleFavoriteTheme(theme)
            assertEquals(!had, theme.name in Preferences(context).favoriteThemes.first())
        } finally { if (theme.name in preferences.favoriteThemes.first() != had) preferences.toggleFavoriteTheme(theme) }
    }
}
