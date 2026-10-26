package com.chronolock.review

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Google Play's in-app review card, asked only after the person has had a good stretch (see the caller) and at most
 * once every [GAP_MS]. Play also applies its own quota, so the card may not appear; there is no "did they review" signal.
 */
class ReviewPrompt(private val activity: Activity) {
    private val prefs = activity.getSharedPreferences("review", Context.MODE_PRIVATE)

    fun maybeAsk(now: Long = System.currentTimeMillis()) {
        val firstSeen = prefs.getLong(KEY_FIRST, 0L).takeIf { it > 0 } ?: now.also { prefs.edit { putLong(KEY_FIRST, it) } }
        if (now - firstSeen < MIN_AGE_MS || now - prefs.getLong(KEY_LAST, 0L) < GAP_MS) return
        prefs.edit { putLong(KEY_LAST, now) }
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful && !activity.isFinishing) manager.launchReviewFlow(activity, request.result)
        }
    }

    private companion object {
        const val KEY_FIRST = "first_seen"
        const val KEY_LAST = "last_asked"
        const val MIN_AGE_MS = 3L * 24 * 60 * 60 * 1000
        const val GAP_MS = 60L * 24 * 60 * 60 * 1000
    }
}
