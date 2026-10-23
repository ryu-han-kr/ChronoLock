package com.chronolock.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chronolock.domain.StrictSetting
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
    private val strictKey = intPreferencesKey("strict_delay_minutes")
    private val strictPendingKey = intPreferencesKey("strict_pending_minutes")
    private val strictPendingAtKey = longPreferencesKey("strict_pending_at")
    val language = context.preferences.data.map { AppLanguage.fromStored(it[languageKey]) }
    val theme = context.preferences.data.map { AppTheme.fromStored(it[themeKey]) }
    val acknowledged = context.preferences.data.map { it[disclosure] ?: false }
    // People who already accepted the permission disclosure predate the welcome screen and skip it.
    val onboarded = context.preferences.data.map { it[onboardedKey] ?: it[disclosure] ?: false }
    val strict = context.preferences.data.map { StrictSetting(it[strictKey] ?: 0, it[strictPendingKey], it[strictPendingAtKey]) }
    /** Applies [change] to the stored strict setting atomically. */
    suspend fun updateStrict(change: (StrictSetting) -> StrictSetting) {
        context.preferences.edit {
            val next = change(StrictSetting(it[strictKey] ?: 0, it[strictPendingKey], it[strictPendingAtKey]))
            it[strictKey] = next.delayMinutes
            val minutes = next.pendingMinutes
            val at = next.pendingAtEpochMillis
            if (minutes != null && at != null) { it[strictPendingKey] = minutes; it[strictPendingAtKey] = at }
            else { it.remove(strictPendingKey); it.remove(strictPendingAtKey) }
        }
    }
    suspend fun finishOnboarding() { context.preferences.edit { it[onboardedKey] = true } }
    suspend fun acknowledge() { context.preferences.edit { it[disclosure] = true } }
    suspend fun setTheme(theme: AppTheme) { context.preferences.edit { it[themeKey] = theme.name } }
    suspend fun setLanguage(language: AppLanguage) { context.preferences.edit { it[languageKey] = language.name } }
}
