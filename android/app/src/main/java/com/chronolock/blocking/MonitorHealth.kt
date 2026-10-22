package com.chronolock.blocking

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Health(val connected: Boolean = false, val error: String? = null, val resumedAt: Long? = null)

@Singleton
class MonitorHealth @Inject constructor() {
    private val mutable = MutableStateFlow(Health())
    val state = mutable.asStateFlow()
    fun connected() { mutable.value = Health(connected = true, resumedAt = System.currentTimeMillis()) }
    fun disconnected() { mutable.value = mutable.value.copy(connected = false) }
    fun error() { mutable.value = mutable.value.copy(connected = false, error = "앱 감지가 중단되었습니다. 접근성 서비스를 껐다 켜 주세요.") }
}
