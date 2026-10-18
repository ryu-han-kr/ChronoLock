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
import kotlinx.coroutines.CancellationException

data class PermissionState(val usage: Boolean = false, val accessibility: Boolean = false)
data class MainState(
    val apps: List<InstalledApp> = emptyList(),
    val rules: List<RuleEntity> = emptyList(),
    val daily: List<DailyEntity> = emptyList(),
    val permissions: PermissionState = PermissionState(),
    val health: Health = Health(),
    val loading: Boolean = true,
    val error: String? = null,
    val focusBusy: Boolean = false,
    val theme: AppTheme = AppTheme.DARK,
    val language: AppLanguage = AppLanguage.KOREAN,
    val history: List<HistoryDailyEntity> = emptyList(),
    val historyUpdatedAt: Long = 0,
    val historyBusy: Boolean = false,
    val historyError: Boolean = false,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: LocalRepository,
    private val ruleRepository: RuleRepository,
    private val environment: AndroidEnvironment,
    private val preferences: Preferences,
    private val historyRepository: HistoryRepository,
    health: MonitorHealth,
) : ViewModel() {
    private val local = MutableStateFlow(MainState())
    val state = combine(local, repository.rules, repository.daily, health.state, preferences.theme) { ui, rules, daily, monitor, theme ->
        ui.copy(rules = rules, daily = daily, health = monitor, theme = theme)
    }.combine(preferences.language) { ui, language -> ui.copy(language = language) }
        .combine(historyRepository.daily) { ui, history -> ui.copy(history = history) }
        .combine(historyRepository.meta) { ui, meta -> ui.copy(historyUpdatedAt = meta?.collectedAt ?: 0) }
        .catch { local.update { it.copy(error = "저장된 데이터를 읽지 못했습니다. 앱을 다시 열어 주세요.", loading = false) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainState())

    fun refresh() {
        local.update { it.copy(permissions = PermissionState(environment.hasUsageAccess(), environment.accessibilityEnabled())) }
        refreshHistory()
        viewModelScope.launch {
            runCatching { environment.installedApps() }
                .onSuccess { apps -> local.update { it.copy(apps = apps, loading = false) } }
                .onFailure { local.update { it.copy(error = "앱 목록을 읽지 못했습니다.", loading = false) } }
        }
    }

    fun refreshHistory() {
        if (local.value.historyBusy || !environment.hasUsageAccess()) return
        local.update { it.copy(historyBusy = true, historyError = false) }
        viewModelScope.launch {
            try { historyRepository.collect() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { local.update { it.copy(historyError = true) } }
            finally { local.update { it.copy(historyBusy = false) } }
        }
    }

    fun selectLanguage(language: AppLanguage) = viewModelScope.launch {
        try { preferences.setLanguage(language) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { showError("언어를 저장하지 못했습니다. 다시 선택해 주세요.") }
    }

    fun selectTheme(theme: AppTheme) = viewModelScope.launch {
        try { preferences.setTheme(theme) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("테마를 저장하지 못했습니다. 다시 선택해 주세요.") }
    }

    fun save(rule: AppRule, onSaved: () -> Unit, onFinished: () -> Unit = {}) = viewModelScope.launch {
        try {
            require(rule.packageName !in environment.safetyPackages())
            ruleRepository.saveRule(rule)
            onSaved()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("제한을 저장하지 못했습니다. 다시 시도해 주세요.") }
        finally { onFinished() }
    }

    fun startFocus(minutes: Int) {
        if (local.value.focusBusy) return
        local.update { it.copy(focusBusy = true) }
        viewModelScope.launch {
            try {
                require(environment.hasUsageAccess() && environment.accessibilityEnabled()) { "사용 정보와 접근성 권한을 먼저 켜 주세요." }
                repository.startFocus(minutes, environment.clock().epochMillis)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showError(if (error is IllegalArgumentException) error.message.orEmpty() else "집중 모드를 시작하지 못했습니다.") }
            finally { local.update { it.copy(focusBusy = false) } }
        }
    }

    fun stopFocus() {
        if (local.value.focusBusy) return
        local.update { it.copy(focusBusy = true) }
        viewModelScope.launch {
            try { repository.stopFocus(environment.clock().epochMillis) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showError("집중 모드를 종료하지 못했습니다.") }
            finally { local.update { it.copy(focusBusy = false) } }
        }
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
