package com.chronolock.blocking

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Health(val connected: Boolean = false, val error: String? = null, val resumedAt: Long? = null)

/**
 * Whether a monitoring service instance is running. Each call names its [owner] (the service instance): when the system
 * restarts the service, the old instance's onDestroy can arrive after the new one connected, and must not mark it down.
 */
@Singleton
class MonitorHealth @Inject constructor() {
    private val mutable = MutableStateFlow(Health())
    val state = mutable.asStateFlow()
    private var owner: Any? = null

    @Synchronized fun connected(owner: Any) {
        this.owner = owner
        mutable.value = Health(connected = true, resumedAt = System.currentTimeMillis())
    }

    /** Ignored for an instance that is no longer the current one. */
    @Synchronized fun disconnected(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        mutable.value = mutable.value.copy(connected = false)
    }

    /** A stale instance's failure does not override a newer running one. */
    @Synchronized fun error(owner: Any) {
        if (this.owner != null && this.owner !== owner) return
        this.owner = null
        mutable.value = mutable.value.copy(connected = false, error = "앱 감지가 중단되었습니다. 접근성 서비스를 껐다 켜 주세요.")
    }
}
