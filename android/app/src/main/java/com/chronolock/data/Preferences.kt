package com.chronolock.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore("preferences")

@Singleton
class Preferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val disclosure = booleanPreferencesKey("permission_disclosure_acknowledged")
    private val themeKey = stringPreferencesKey("app_theme")
    private val languageKey = stringPreferencesKey("app_language")
    private val onboardedKey = booleanPreferencesKey("onboarding_done")
    val language = context.preferences.data.map { AppLanguage.fromStored(it[languageKey]) }
    val theme = context.preferences.data.map { AppTheme.fromStored(it[themeKey]) }
    val acknowledged = context.preferences.data.map { it[disclosure] ?: false }
    // People who already accepted the permission disclosure predate the welcome screen and skip it.
    val onboarded = context.preferences.data.map { it[onboardedKey] ?: it[disclosure] ?: false }
    suspend fun finishOnboarding() { context.preferences.edit { it[onboardedKey] = true } }
    suspend fun acknowledge() { context.preferences.edit { it[disclosure] = true } }
    suspend fun setTheme(theme: AppTheme) { context.preferences.edit { it[themeKey] = theme.name } }
    suspend fun setLanguage(language: AppLanguage) { context.preferences.edit { it[languageKey] = language.name } }
}
