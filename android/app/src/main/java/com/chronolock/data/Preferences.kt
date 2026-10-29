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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore("preferences")

/** App detection was turned off at [offAt] and came back at [backAt] (null while still off); [locked] if a lock or strict mode held then. */
data class MonitorGap(val offAt: Long, val backAt: Long?, val locked: Boolean)

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
    private val followSystemKey = booleanPreferencesKey("theme_follow_system")
    private val goalsKey = stringSetPreferencesKey("onboarding_goals")
    private val nightThemeKey = stringPreferencesKey("night_theme")
    private val historyPinKey = stringPreferencesKey("history_pin")
    private val strictPendingElapsedKey = longPreferencesKey("strict_pending_elapsed")
    private val strictPendingBootKey = intPreferencesKey("strict_pending_boot")
    private val lockUntilKey = longPreferencesKey("lock_until")
    private val lockUntilElapsedKey = longPreferencesKey("lock_until_elapsed")
    private val lockBootKey = intPreferencesKey("lock_boot")
    private val pinMutex = kotlinx.coroutines.sync.Mutex()
    private val goalDaysKey = intPreferencesKey("lock_goal_days")
    private val goalStartKey = stringPreferencesKey("lock_goal_start")
    private val goalPinKey = stringPreferencesKey("lock_goal_pin")
    private val goalCodeKey = stringPreferencesKey("lock_goal_code")
    private val goalRevealedKey = intPreferencesKey("lock_goal_revealed")
    private val mascotKey = stringPreferencesKey("mascot")
    private val reduceMotionKey = booleanPreferencesKey("reduce_motion")
    private val monitorOffKey = longPreferencesKey("monitor_off_at")
    private val monitorOffLockedKey = booleanPreferencesKey("monitor_off_locked")
    private val monitorBackKey = longPreferencesKey("monitor_back_at")
    /** The last time the user turned app detection off, until they have seen it on Today. */
    val monitorGap = context.preferences.data.map { prefs ->
        prefs[monitorOffKey]?.let { MonitorGap(it, prefs[monitorBackKey], prefs[monitorOffLockedKey] ?: false) }
    }
    /** Keeps the earliest unseen turn-off; a later one before it was seen widens the same gap. */
    suspend fun recordMonitorOff(atEpochMillis: Long, locked: Boolean) { context.preferences.edit {
        if (it[monitorOffKey] == null) it[monitorOffKey] = atEpochMillis
        it[monitorOffLockedKey] = locked || (it[monitorOffLockedKey] ?: false)
        it.remove(monitorBackKey)
    } }
    suspend fun recordMonitorBack(atEpochMillis: Long) { context.preferences.edit {
        if (it[monitorOffKey] != null && it[monitorBackKey] == null) it[monitorBackKey] = atEpochMillis
    } }
    suspend fun dismissMonitorGap() { context.preferences.edit {
        it.remove(monitorOffKey); it.remove(monitorOffLockedKey); it.remove(monitorBackKey)
    } }
    val language = context.preferences.data.map { AppLanguage.fromStored(it[languageKey]) }
    val theme = context.preferences.data.map { AppTheme.fromStored(it[themeKey]) }
    val mascot = context.preferences.data.map { MascotKind.fromStored(it[mascotKey]) }
    suspend fun setMascot(kind: MascotKind) { context.preferences.edit { it[mascotKey] = kind.name } }
    /** Stops moving theme backgrounds and the breathing character, to save battery or for comfort. */
    val reduceMotion = context.preferences.data.map { it[reduceMotionKey] ?: false }
    suspend fun setReduceMotion(on: Boolean) { context.preferences.edit { it[reduceMotionKey] = on } }
    val acknowledged = context.preferences.data.map { it[disclosure] ?: false }
    // People who already accepted the permission disclosure predate the welcome screen and skip it.
    val onboarded = context.preferences.data.map { it[onboardedKey] ?: it[disclosure] ?: false }
    private fun strictOf(it: androidx.datastore.preferences.core.Preferences) =
        StrictSetting(it[strictKey] ?: 0, it[strictPendingKey], it[strictPendingAtKey], it[strictPendingElapsedKey], it[strictPendingBootKey])
    val strict = context.preferences.data.map { strictOf(it) }
    /** Applies [change] to the stored strict setting atomically. */
    suspend fun updateStrict(change: (StrictSetting) -> StrictSetting) {
        context.preferences.edit {
            val next = change(strictOf(it))
            it[strictKey] = next.delayMinutes
            val minutes = next.pendingMinutes
            val at = next.pendingAtEpochMillis
            if (minutes != null && at != null) { it[strictPendingKey] = minutes; it[strictPendingAtKey] = at }
            else { it.remove(strictPendingKey); it.remove(strictPendingAtKey) }
            val elapsed = next.pendingAtElapsedMillis
            val boot = next.bootId
            if (minutes != null && elapsed != null && boot != null) { it[strictPendingElapsedKey] = elapsed; it[strictPendingBootKey] = boot }
            else { it.remove(strictPendingElapsedKey); it.remove(strictPendingBootKey) }
        }
    }
    /** The last focus length started from the app, reused by the quick settings tile. */
    val focusMinutes = context.preferences.data.map { it[focusMinutesKey] ?: 25 }
    suspend fun setFocusMinutes(minutes: Int) { context.preferences.edit { it[focusMinutesKey] = minutes } }
    /** Goals picked at first run (OnboardingGoal names); they shape the first suggestions on the Today tab. */
    val goals = context.preferences.data.map { it[goalsKey] ?: emptySet() }
    suspend fun finishOnboarding(goals: Set<String> = emptySet()) { context.preferences.edit { it[onboardedKey] = true; it[goalsKey] = goals } }
    suspend fun acknowledge() { context.preferences.edit { it[disclosure] = true } }
    val lock = context.preferences.data.map { prefs ->
        val code = prefs[goalCodeKey]
        LockSetting(prefs[lockUntilKey], prefs[goalDaysKey], prefs[goalStartKey]?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
            pieceCode = code != null, revealed = code?.take(prefs[goalRevealedKey] ?: 0).orEmpty(),
            untilElapsedMillis = prefs[lockUntilElapsedKey], bootId = prefs[lockBootKey])
    }
    /** Locks for [millis] from [clock]; a running time lock can only be extended, never shortened. */
    suspend fun extendLock(clock: com.chronolock.domain.ClockReading, millis: Long) {
        context.preferences.edit {
            val until = clock.epochMillis + millis
            if ((it[lockUntilKey] ?: 0) < until) it[lockUntilKey] = until
            // The boot-clock deadline only grows within one boot; a new boot starts from this lock.
            val elapsedUntil = clock.elapsedMillis + millis
            if (it[lockBootKey] != clock.bootId || (it[lockUntilElapsedKey] ?: 0) < elapsedUntil) {
                it[lockUntilElapsedKey] = elapsedUntil; it[lockBootKey] = clock.bootId
            }
        }
    }
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
    suspend fun endGoalLock(code: String, now: com.chronolock.domain.ClockReading): PinResult = checkPin(GOAL, code, now) { prefs ->
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
    suspend fun checkHistoryPin(pin: String, now: com.chronolock.domain.ClockReading): PinResult = checkPin(HISTORY, pin, now) { prefs -> prefs[historyPinKey]?.let { matches(it, pin) } == true }
    /** Replaces the history password after the current one is confirmed (counted like any other attempt). */
    suspend fun changeHistoryPin(current: String, next: String, now: com.chronolock.domain.ClockReading): PinResult =
        checkHistoryPin(current, now).also { if (it.ok) setHistoryPin(next) }
    /** Replaces a goal lock's own password after the current one is confirmed; piece codes cannot be changed. */
    suspend fun changeGoalPin(current: String, next: String, now: com.chronolock.domain.ClockReading): PinResult =
        checkPin(GOAL, current, now) { prefs -> prefs[goalPinKey]?.let { matches(it, current) } == true }
            .also { result -> if (result.ok) context.preferences.edit { if (it[goalPinKey] != null) it[goalPinKey] = saltedHash(next) } }

    /**
     * Five wrong tries in a row pause that password for ten minutes, measured on both the wall clock and the boot clock so
     * moving the time forward does not lift it; a right one resets the count. Checks run one at a time.
     */
    private suspend fun checkPin(kind: String, pin: String, now: com.chronolock.domain.ClockReading,
        check: (androidx.datastore.preferences.core.Preferences) -> Boolean): PinResult = pinMutex.withLock {
        val fails = intPreferencesKey("${kind}_pin_fails")
        val blockedUntil = longPreferencesKey("${kind}_pin_blocked_until")
        val blockedElapsed = longPreferencesKey("${kind}_pin_blocked_elapsed")
        val blockedBoot = intPreferencesKey("${kind}_pin_blocked_boot")
        val prefs = context.preferences.data.first()
        val until = prefs[blockedUntil]
        val stillBlocked = until != null && (now.epochMillis < until ||
            (prefs[blockedBoot] == now.bootId && prefs[blockedElapsed]?.let { now.elapsedMillis < it } == true))
        if (stillBlocked) return@withLock PinResult(false, blockedUntil = maxOf(until!!, now.epochMillis + ((prefs[blockedElapsed] ?: 0) - now.elapsedMillis).coerceAtLeast(0)))
        if (check(prefs)) {
            context.preferences.edit { it.remove(fails); it.remove(blockedUntil); it.remove(blockedElapsed); it.remove(blockedBoot) }
            return@withLock PinResult(true)
        }
        var result = PinResult(false)
        context.preferences.edit {
            val count = (it[fails] ?: 0) + 1
            if (count >= MAX_PIN_TRIES) {
                it.remove(fails)
                it[blockedUntil] = now.epochMillis + PIN_BLOCK_MILLIS
                it[blockedElapsed] = now.elapsedMillis + PIN_BLOCK_MILLIS; it[blockedBoot] = now.bootId
                result = PinResult(false, blockedUntil = now.epochMillis + PIN_BLOCK_MILLIS)
            } else { it[fails] = count; result = PinResult(false, triesLeft = MAX_PIN_TRIES - count) }
        }
        result
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
    val favoriteThemes = context.preferences.data.map { prefs -> (prefs[favoriteThemesKey] ?: emptySet()).mapNotNullTo(mutableSetOf()) { ThemeCatalog.resolve(it) } }
    suspend fun setFavoriteThemes(names: Set<String>) { context.preferences.edit { it[favoriteThemesKey] = names } }
    suspend fun toggleFavoriteTheme(theme: AppTheme) {
        context.preferences.edit {
            // Ids of dropped duplicates are stored under the palette they open, so un-pinning it really removes the pin.
            val current = (it[favoriteThemesKey] ?: emptySet()).mapNotNullTo(mutableSetOf()) { name -> ThemeCatalog.resolve(name) }
            it[favoriteThemesKey] = if (theme.name in current) current - theme.name else current + theme.name
        }
    }
    /** With [followSystem] on, [nightTheme] is used while the device is in dark mode and [theme] otherwise. */
    val followSystem = context.preferences.data.map { it[followSystemKey] ?: false }
    val nightTheme = context.preferences.data.map { prefs -> prefs[nightThemeKey]?.let { AppTheme.fromStored(it) } ?: AppTheme.DARK }
    suspend fun setFollowSystem(on: Boolean) { context.preferences.edit { it[followSystemKey] = on } }
    suspend fun setNightTheme(theme: AppTheme) { context.preferences.edit { it[nightThemeKey] = theme.name } }
    suspend fun setTheme(theme: AppTheme) { context.preferences.edit { it[themeKey] = theme.name } }
    suspend fun setLanguage(language: AppLanguage) { context.preferences.edit { it[languageKey] = language.name } }

    private companion object {
        const val HISTORY = "history"
        const val GOAL = "goal"
        const val MAX_PIN_TRIES = 5
        const val PIN_BLOCK_MILLIS = 10 * 60_000L
    }
}
