package com.chronolock.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronolock.blocking.*
import com.chronolock.data.*
import com.chronolock.domain.AppRule
import com.chronolock.domain.RuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PermissionState(val usage: Boolean = false, val accessibility: Boolean = false)
data class MainState(
    val apps: List<InstalledApp> = emptyList(),
    val rules: List<RuleEntity> = emptyList(),
    val daily: List<DailyEntity> = emptyList(),
    val permissions: PermissionState = PermissionState(),
    val health: Health = Health(),
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: LocalRepository,
    private val ruleRepository: RuleRepository,
    private val environment: AndroidEnvironment,
    private val preferences: Preferences,
    health: MonitorHealth,
) : ViewModel() {
    private val local = MutableStateFlow(MainState())
    val state = combine(local, repository.rules, repository.daily, health.state) { ui, rules, daily, monitor ->
        ui.copy(rules = rules, daily = daily, health = monitor)
    }.catch { local.update { it.copy(error = "저장된 데이터를 읽지 못했습니다. 앱을 다시 열어 주세요.", loading = false) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainState())

    fun refresh() {
        local.update { it.copy(permissions = PermissionState(environment.hasUsageAccess(), environment.accessibilityEnabled())) }
        viewModelScope.launch {
            runCatching { environment.installedApps() }
                .onSuccess { apps -> local.update { it.copy(apps = apps, loading = false) } }
                .onFailure { local.update { it.copy(error = "앱 목록을 읽지 못했습니다.", loading = false) } }
        }
    }

    fun save(rule: AppRule, onSaved: () -> Unit) = viewModelScope.launch {
        runCatching {
            require(rule.packageName !in environment.safetyPackages())
            ruleRepository.saveRule(rule)
        }.onSuccess { onSaved() }.onFailure { showError("제한을 저장하지 못했습니다. 다시 시도해 주세요.") }
    }
    fun remove(packageName: String, onSaved: () -> Unit) = viewModelScope.launch {
        runCatching { ruleRepository.deleteRule(packageName) }.onSuccess { onSaved() }
            .onFailure { showError("제한을 삭제하지 못했습니다.") }
    }
    fun clear() = viewModelScope.launch {
        runCatching { repository.clearAll() }.onFailure { showError("로컬 데이터를 삭제하지 못했습니다.") }
    }
    fun acknowledge(onComplete: () -> Unit) = viewModelScope.launch {
        runCatching { preferences.acknowledge() }.onSuccess { onComplete() }
            .onFailure { showError("설명 확인 상태를 저장하지 못했습니다.") }
    }
    fun showError(message: String) { local.update { it.copy(error = message) } }
    fun dismissError() { local.update { it.copy(error = null) } }
}
