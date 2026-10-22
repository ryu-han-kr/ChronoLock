package com.chronolock.domain

/** Supabase session kept on this device only. Rules and usage history are never attached to it yet. */
data class AuthSession(
    val userId: String,
    val email: String?,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
) {
    /** Refresh a minute early so a request does not start with a token that expires in flight. */
    fun needsRefresh(nowEpochSeconds: Long): Boolean = nowEpochSeconds >= expiresAtEpochSeconds - 60
}

/** Banner ads stay out of blocking, focus and editing moments; only Today and History carry one. */
object AdPolicy {
    const val TODAY_TAB = 0
    const val HISTORY_TAB = 2

    fun showBanner(tab: Int, configured: Boolean, adFree: Boolean, canRequestAds: Boolean, focusActive: Boolean): Boolean =
        configured && !adFree && canRequestAds && !focusActive && (tab == TODAY_TAB || tab == HISTORY_TAB)
}
