package com.chronolock.data

import com.chronolock.domain.TimeBank

/**
 * Counts a focus that ran out into the time bank. Called before a new focus replaces the old end, when the app opens
 * and from the periodic job, so a finished focus is counted even if nothing else happens.
 */
internal object TimeBankStore {
    suspend fun settle(repository: LocalRepository, preferences: Preferences, nowEpochMillis: Long) {
        val (until, startedAt) = repository.finishedFocus(nowEpochMillis) ?: return
        preferences.creditFocus(until, startedAt) { credited -> TimeBank.earned(until, startedAt, credited, nowEpochMillis) }
    }
}
