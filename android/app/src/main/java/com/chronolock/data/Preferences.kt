package com.chronolock.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chronolock.domain.LockSetting
import com.chronolock.domain.StrictSetting
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore("preferences")

/** Outcome of entering a password: [blockedUntil] is set after too many wrong tries. */
data class PinResult(val ok: Boolean, val triesLeft: Int = 0, val blockedUntil: Long? = null)

@Singleton
class Preferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val disclosure = booleanPreferencesKey("permission_disclosure_acknowledged")
    private val themeKey = stringPreferencesKey("app_theme")
    private val languageKey = stringPreferencesKey("app_language")
    private val onboardedKey = booleanPreferencesKey("onboarding_done")
    private val strictKey = intPreferencesKey("strict_delay_minutes")
    private val strictPendingKey = intPreferencesKey("strict_pending_minutes")
    private val strictPendingAtKey = longPreferencesKey("strict_pending_at")
    private val focusMinutesKey = intPreferencesKey("last_focus_minutes")
    private val favoriteThemesKey = stringSetPreferencesKey("favorite_themes")
    private val historyPinKey = stringPreferencesKey("history_pin")
    private val lockUntilKey = longPreferencesKey("lock_until")
    private val goalDaysKey = intPreferencesKey("lock_goal_days")
    private val goalStartKey = stringPreferencesKey("lock_goal_start")
    private val goalPinKey = stringPreferencesKey("lock_goal_pin")
    private val goalCodeKey = stringPreferencesKey("lock_goal_code")
    private val goalRevealedKey = intPreferencesKey("lock_goal_revealed")
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
    /** The last focus length started from the app, reused by the quick settings tile. */
    val focusMinutes = context.preferences.data.map { it[focusMinutesKey] ?: 25 }
    suspend fun setFocusMinutes(minutes: Int) { context.preferences.edit { it[focusMinutesKey] = minutes } }
    suspend fun finishOnboarding() { context.preferences.edit { it[onboardedKey] = true } }
    suspend fun acknowledge() { context.preferences.edit { it[disclosure] = true } }
    val lock = context.preferences.data.map { prefs ->
        val code = prefs[goalCodeKey]
        LockSetting(prefs[lockUntilKey], prefs[goalDaysKey], prefs[goalStartKey]?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
            pieceCode = code != null, revealed = code?.take(prefs[goalRevealedKey] ?: 0).orEmpty())
    }
    /** Locks until [until]; a running time lock can only be extended, never shortened. */
    suspend fun extendLock(until: Long) { context.preferences.edit { if ((it[lockUntilKey] ?: 0) < until) it[lockUntilKey] = until } }
    /**
     * Starts a goal lock unless one is running. With [pin] the person's password is stored as a salted hash; without one
     * the app makes up a [days]-digit code and hands it out a digit per kept day ([revealNextPiece]).
     */
    suspend fun startGoalLock(days: Int, today: java.time.LocalDate, pin: String?) {
        context.preferences.edit {
            if (it[goalDaysKey] != null) return@edit
            it[goalDaysKey] = days; it[goalStartKey] = today.toString()
            if (pin != null) it[goalPinKey] = saltedHash(pin)
            else { it[goalCodeKey] = (1..days).map { java.security.SecureRandom().nextInt(10) }.joinToString(""); it[goalRevealedKey] = 0 }
        }
    }
    suspend fun revealNextPiece() { context.preferences.edit { it[goalRevealedKey] = (it[goalRevealedKey] ?: 0) + 1 } }
    /** Ends the goal lock when [code] matches its password or piece code; the caller checks that the goal was reached. */
    suspend fun endGoalLock(code: String, now: Long): PinResult = checkPin(GOAL, code, now) { prefs ->
        prefs[goalCodeKey]?.let { java.security.MessageDigest.isEqual(it.toByteArray(), code.toByteArray()) }
            ?: prefs[goalPinKey]?.let { matches(it, code) } ?: false
    }.also { result ->
        if (result.ok) context.preferences.edit {
            it.remove(goalDaysKey); it.remove(goalStartKey); it.remove(goalPinKey); it.remove(goalCodeKey); it.remove(goalRevealedKey)
        }
    }

    /** Whether a password for deleting history has been set. Only a salted hash is stored. */
    val historyPinSet = context.preferences.data.map { it[historyPinKey] != null }
    suspend fun setHistoryPin(pin: String) { context.preferences.edit { it[historyPinKey] = saltedHash(pin) } }
    suspend fun checkHistoryPin(pin: String, now: Long): PinResult = checkPin(HISTORY, pin, now) { prefs -> prefs[historyPinKey]?.let { matches(it, pin) } == true }
    /** Replaces the history password after the current one is confirmed (counted like any other attempt). */
    suspend fun changeHistoryPin(current: String, next: String, now: Long): PinResult =
        checkHistoryPin(current, now).also { if (it.ok) setHistoryPin(next) }
    /** Replaces a goal lock's own password after the current one is confirmed; piece codes cannot be changed. */
    suspend fun changeGoalPin(current: String, next: String, now: Long): PinResult =
        checkPin(GOAL, current, now) { prefs -> prefs[goalPinKey]?.let { matches(it, current) } == true }
            .also { result -> if (result.ok) context.preferences.edit { if (it[goalPinKey] != null) it[goalPinKey] = saltedHash(next) } }

    /** Five wrong tries in a row pause that password for ten minutes; a right one resets the count. */
    private suspend fun checkPin(kind: String, pin: String, now: Long, check: (androidx.datastore.preferences.core.Preferences) -> Boolean): PinResult {
        val fails = intPreferencesKey("${kind}_pin_fails")
        val blockedUntil = longPreferencesKey("${kind}_pin_blocked_until")
        val prefs = context.preferences.data.first()
        prefs[blockedUntil]?.takeIf { now < it }?.let { return PinResult(false, blockedUntil = it) }
        if (check(prefs)) { context.preferences.edit { it.remove(fails); it.remove(blockedUntil) }; return PinResult(true) }
        var result = PinResult(false)
        context.preferences.edit {
            val count = (it[fails] ?: 0) + 1
            if (count >= MAX_PIN_TRIES) { it.remove(fails); it[blockedUntil] = now + PIN_BLOCK_MILLIS; result = PinResult(false, blockedUntil = now + PIN_BLOCK_MILLIS) }
            else { it[fails] = count; result = PinResult(false, triesLeft = MAX_PIN_TRIES - count) }
        }
        return result
    }
    private fun saltedHash(pin: String): String {
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        return salt + ":" + pinHash(salt, pin)
    }
    private fun matches(stored: String, pin: String): Boolean {
        val (salt, hash) = stored.split(':', limit = 2).takeIf { it.size == 2 } ?: return false
        return java.security.MessageDigest.isEqual(hash.toByteArray(), pinHash(salt, pin).toByteArray())
    }
    private fun pinHash(salt: String, pin: String) =
        java.security.MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    /** Theme names the person pinned; names of removed palettes are simply never matched. */
    val favoriteThemes = context.preferences.data.map { it[favoriteThemesKey] ?: emptySet() }
    suspend fun toggleFavoriteTheme(theme: AppTheme) {
        context.preferences.edit { val current = it[favoriteThemesKey] ?: emptySet(); it[favoriteThemesKey] = if (theme.name in current) current - theme.name else current + theme.name }
    }
    suspend fun setTheme(theme: AppTheme) { context.preferences.edit { it[themeKey] = theme.name } }
    suspend fun setLanguage(language: AppLanguage) { context.preferences.edit { it[languageKey] = language.name } }

    private companion object {
        const val HISTORY = "history"
        const val GOAL = "goal"
        const val MAX_PIN_TRIES = 5
        const val PIN_BLOCK_MILLIS = 10 * 60_000L
    }
}
