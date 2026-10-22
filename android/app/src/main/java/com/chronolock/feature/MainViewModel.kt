package com.chronolock.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronolock.account.AccountRepository
import com.chronolock.account.AccountState
import com.chronolock.account.GoogleToken
import com.chronolock.ads.AdState
import com.chronolock.ads.AdsController
import com.chronolock.billing.BillingController
import com.chronolock.billing.BillingState
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
    val account: AccountState = AccountState(),
    val accountBusy: Boolean = false,
    val ads: AdState = AdState(),
    val billing: BillingState = BillingState(),
    /** Null until the stored flag is read, so the welcome screen never flashes for returning users. */
    val onboarded: Boolean? = null,
) {
    val adFree get() = billing.adFree
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: LocalRepository,
    private val ruleRepository: RuleRepository,
    private val environment: AndroidEnvironment,
    private val preferences: Preferences,
    private val historyRepository: HistoryRepository,
    private val accounts: AccountRepository,
    private val ads: AdsController,
    billing: BillingController,
    health: MonitorHealth,
) : ViewModel() {
    private val local = MutableStateFlow(MainState())
    val bannerId get() = ads.bannerId
    // Stored data is merged separately from [local] so a failed read reports an error without freezing the UI state
    // (a catch inside one combined flow would end it, and no later error, busy flag or dismissal would reach the screen).
    private var lastLoaded = MainState()
    private val loaded = combine(repository.rules, repository.daily, health.state, preferences.theme) { rules, daily, monitor, theme ->
        MainState(rules = rules, daily = daily, health = monitor, theme = theme)
    }.combine(preferences.language) { data, language -> data.copy(language = language) }
        .combine(historyRepository.daily) { data, history -> data.copy(history = history) }
        .combine(historyRepository.meta) { data, meta -> data.copy(historyUpdatedAt = meta?.collectedAt ?: 0) }
        .combine(accounts.state) { data, account -> data.copy(account = account) }
        .combine(ads.state) { data, ad -> data.copy(ads = ad) }
        .combine(billing.state) { data, store -> data.copy(billing = store) }
        .combine(preferences.onboarded) { data, done -> data.copy(onboarded = done) }
        .onEach { lastLoaded = it }
        .catch { failure ->
            android.util.Log.w("ChronoLock", "Stored data could not be read", failure)
            local.update { it.copy(error = "저장된 데이터를 읽지 못했습니다. 앱을 다시 열어 주세요.", loading = false) }
            emit(lastLoaded)
        }
    val state = combine(local, loaded) { ui, data ->
        ui.copy(rules = data.rules, daily = data.daily, health = data.health, theme = data.theme, language = data.language,
            history = data.history, historyUpdatedAt = data.historyUpdatedAt, account = data.account, ads = data.ads,
            billing = data.billing, onboarded = data.onboarded)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainState())

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
        try { ruleRepository.deleteRule(packageName); onSaved() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("제한을 삭제하지 못했습니다.") }
    }
    fun clear() = viewModelScope.launch {
        try { repository.clearAll() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("로컬 데이터를 삭제하지 못했습니다.") }
    }
    fun finishOnboarding() = viewModelScope.launch {
        try { preferences.finishOnboarding() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("설정을 저장하지 못했습니다. 다시 시도해 주세요.") }
    }

    fun acknowledge(onComplete: () -> Unit) = viewModelScope.launch {
        try { preferences.acknowledge(); onComplete() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("설명 확인 상태를 저장하지 못했습니다.") }
    }
    fun signIn(token: GoogleToken) = account("로그인하지 못했습니다. 네트워크를 확인하고 다시 시도해 주세요.") {
        accounts.signInWithGoogle(token.idToken, token.rawNonce)
    }
    fun signOut() = account("로그아웃 중 서버에 연결하지 못했습니다. 이 기기에서는 로그아웃되었습니다.") { accounts.signOut() }
    fun deleteAccount() = account("계정을 삭제하지 못했습니다. 네트워크를 확인하고 다시 시도해 주세요.") { accounts.deleteAccount() }

    private fun account(failure: String, action: suspend () -> Unit) {
        if (local.value.accountBusy) return
        local.update { it.copy(accountBusy = true) }
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("ChronoLock", "Account request failed", error); showError(failure) }
            finally { local.update { it.copy(accountBusy = false) } }
        }
    }
    fun showError(message: String) { local.update { it.copy(error = message) } }
    fun dismissError() { local.update { it.copy(error = null) } }
}
