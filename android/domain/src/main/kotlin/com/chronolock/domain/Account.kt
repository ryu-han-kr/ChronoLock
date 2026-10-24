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
    /** The only place with an ad: the end of Settings, inside the list, so it never covers or crowds a screen. */
    const val SETTINGS_TAB = 3

    fun showBanner(tab: Int, configured: Boolean, adFree: Boolean, canRequestAds: Boolean, focusActive: Boolean): Boolean =
        configured && !adFree && canRequestAds && !focusActive && tab == SETTINGS_TAB
}
