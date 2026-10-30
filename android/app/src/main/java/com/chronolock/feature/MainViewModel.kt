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
import com.chronolock.domain.AppGroup
import com.chronolock.domain.BlockedSite
import com.chronolock.domain.SiteDomains
import com.chronolock.domain.AppRule
import com.chronolock.domain.StrictSetting
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.core.net.toUri
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

data class PermissionState(val usage: Boolean = false, val accessibility: Boolean = false)
data class MainState(
    val apps: List<InstalledApp> = emptyList(),
    val rules: List<RuleEntity> = emptyList(),
    /** Boot-clock deadlines for the focus and pauses; the screen shows [rules] through them. */
    val anchors: List<com.chronolock.data.ClockAnchorEntity> = emptyList(),
    val daily: List<DailyEntity> = emptyList(),
    val permissions: PermissionState = PermissionState(),
    val health: Health = Health(),
    /** Detection the user turned off and Today has not shown yet. */
    val monitorGap: com.chronolock.data.MonitorGap? = null,
    val autoBackup: com.chronolock.data.AutoBackup? = null,
    val loading: Boolean = true,
    val error: String? = null,
    /** Play finished downloading a newer build; a restart installs it. */
    val updateReady: Boolean = false,
    val focusBusy: Boolean = false,
    val theme: AppTheme = AppTheme.DARK,
    val favoriteThemes: Set<String> = emptySet(),
    /** The companion character drawn across the app. */
    val mascot: com.chronolock.data.MascotKind = com.chronolock.data.MascotKind.DEFAULT,
    val reduceMotion: Boolean = false,
    /** Plugging in at night asks whether to focus until morning. */
    val bedtime: Boolean = false,
    /** Minutes banked by finishing focuses, spent as a short pause of one app. */
    val timeBank: Int = 0,
    /** The printed QR's secret, when stopping a focus needs it scanned. */
    val deskQr: String? = null,
    /** The exam counted down on Today and the block screen. */
    val exam: com.chronolock.domain.Exam? = null,
    /** Day/night themes that follow the device's dark mode, and which of the two the picker is editing. */
    val followSystem: Boolean = false,
    /** First-run goals, as [com.chronolock.domain.OnboardingGoal] names. */
    val goals: Set<String> = emptySet(),
    /** Goals brought by the Play link this install came from, shown at first run. */
    val invite: com.chronolock.domain.Invite? = null,
    /** A tab a launcher shortcut asked for; the screen switches to it once and clears it. */
    val requestedTab: Int? = null,
    /** A partner goal lock's code, held only until the person sends it to their friend. */
    val partnerCode: String? = null,
    /** Lines to oneself per app, shown on its block screen. */
    val notes: Map<String, String> = emptyMap(),
    /** Reasons picked on the block screen, per day. */
    val openReasons: Map<java.time.LocalDate, Map<com.chronolock.domain.OpenReason, Int>> = emptyMap(),
    val nightTheme: AppTheme = AppTheme.DARK,
    val editingNight: Boolean = false,
    /** Day totals for the year calendar, and whether a password for deleting history exists. */
    val historyYear: List<com.chronolock.data.HistoryYearEntity> = emptyList(),
    val historyPinSet: Boolean = false,
    val lock: com.chronolock.domain.LockSetting = com.chronolock.domain.LockSetting(),
    /** After a wrong password: tries left, or the time it can be entered again. */
    val pinTriesLeft: Int? = null,
    val pinBlockedUntil: Long? = null,
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
    val strict: StrictSetting = StrictSetting(),
    /** Relaxing changes strict mode is holding back, one per app. */
    val pending: List<PendingRuleEntity> = emptyList(),
    /** Apps sharing one limit, and their relaxing changes strict mode is holding back. */
    val groups: List<AppGroup> = emptyList(),
    val pendingGroups: List<PendingGroupEntity> = emptyList(),
    /** Websites blocked in browsers, and the unblocking changes strict mode is holding back. */
    val sites: List<BlockedSite> = emptyList(),
    val pendingSites: List<com.chronolock.data.PendingSiteEntity> = emptyList(),
) {
    val adFree get() = billing.adFree
    /** Every rule with the groups attached, members without a rule of their own included; for day results and streaks. */
    val rulesWithGroups: List<com.chronolock.domain.AppRule> get() =
        com.chronolock.domain.withGroups(rules.associate { it.packageName to it.toDomain() }, groups).values.toList()
    /** Every feature is open while the Pro split is switched off ([com.chronolock.BuildConfig.PRO_GATING]). */
    val pro get() = !com.chronolock.BuildConfig.PRO_GATING || billing.proAccess
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: LocalRepository,
    private val environment: AndroidEnvironment,
    private val preferences: Preferences,
    private val historyRepository: HistoryRepository,
    private val backups: BackupRepository,
    private val accounts: AccountRepository,
    private val ads: AdsController,
    private val billing: BillingController,
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
        .combine(preferences.strict) { data, strict -> data.copy(strict = strict) }
        .combine(repository.pending) { data, pending -> data.copy(pending = pending) }
        .combine(repository.groups) { data, groups -> data.copy(groups = groups.map { it.toDomain() }) }
        .combine(repository.pendingGroups) { data, pending -> data.copy(pendingGroups = pending) }
        .combine(preferences.favoriteThemes) { data, favorites -> data.copy(favoriteThemes = favorites) }
        .combine(preferences.mascot) { data, kind -> data.copy(mascot = kind) }
        .combine(preferences.reduceMotion) { data, still -> data.copy(reduceMotion = still) }
        .combine(preferences.bedtime) { data, on -> data.copy(bedtime = on) }
        .combine(preferences.timeBank) { data, bank -> data.copy(timeBank = bank) }
        .combine(preferences.deskQr) { data, secret -> data.copy(deskQr = secret) }
        .combine(preferences.exam) { data, exam -> data.copy(exam = exam) }
        .combine(preferences.followSystem) { data, follow -> data.copy(followSystem = follow) }
        .combine(preferences.goals) { data, goals -> data.copy(goals = goals) }
        .combine(preferences.nightTheme) { data, night -> data.copy(nightTheme = night) }
        .combine(historyRepository.year) { data, year -> data.copy(historyYear = year) }
        .combine(preferences.historyPinSet) { data, set -> data.copy(historyPinSet = set) }
        .combine(preferences.lock) { data, lock -> data.copy(lock = lock) }
        .combine(repository.sites) { data, sites -> data.copy(sites = sites.map { it.toDomain() }) }
        .combine(repository.pendingSites) { data, pending -> data.copy(pendingSites = pending) }
        .combine(repository.anchors) { data, anchors -> data.copy(anchors = anchors) }
        .combine(preferences.monitorGap) { data, gap -> data.copy(monitorGap = gap) }
        .combine(preferences.autoBackup) { data, auto -> data.copy(autoBackup = auto) }
        .combine(preferences.openReasons) { data, reasons -> data.copy(openReasons = reasons) }
        .combine(preferences.notes) { data, notes -> data.copy(notes = notes) }
        .onEach { lastLoaded = it }
        .catch { failure ->
            android.util.Log.w("ChronoLock", "Stored data could not be read", failure)
            local.update { it.copy(error = "저장된 데이터를 읽지 못했습니다. 앱을 다시 열어 주세요.", loading = false) }
            emit(lastLoaded)
        }
    val state = combine(local, loaded) { ui, data ->
        ui.copy(rules = data.rules, anchors = data.anchors, daily = data.daily, health = data.health, theme = data.theme, language = data.language,
            history = data.history, historyUpdatedAt = data.historyUpdatedAt, account = data.account, ads = data.ads,
            billing = data.billing, onboarded = data.onboarded, strict = data.strict, pending = data.pending,
            groups = data.groups, pendingGroups = data.pendingGroups, sites = data.sites, pendingSites = data.pendingSites,
            favoriteThemes = data.favoriteThemes, historyYear = data.historyYear, historyPinSet = data.historyPinSet,
            lock = data.lock, monitorGap = data.monitorGap, autoBackup = data.autoBackup, openReasons = data.openReasons, notes = data.notes, followSystem = data.followSystem, nightTheme = data.nightTheme, goals = data.goals, mascot = data.mascot, reduceMotion = data.reduceMotion, bedtime = data.bedtime, timeBank = data.timeBank, deskQr = data.deskQr, exam = data.exam)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainState())

    fun refresh() {
        local.update { it.copy(permissions = PermissionState(environment.hasUsageAccess(), environment.accessibilityEnabled())) }
        refreshHistory()
        viewModelScope.launch {
            runCatching { environment.clock().let { repository.applyDuePending(it.epochMillis, it) } }
                .onFailure { android.util.Log.w("ChronoLock", "Held changes could not be applied", it) }
            // Opening the app catches up a weekly backup the background job has not written yet.
            runCatching { backups.autoBackup(environment.clock().epochMillis) }
                .onFailure { android.util.Log.w("ChronoLock", "Weekly backup failed", it) }
            runCatching { com.chronolock.data.TimeBankStore.settle(repository, preferences, environment.clock().epochMillis) }
                .onFailure { android.util.Log.w("ChronoLock", "Time bank not settled", it) }
            runCatching { environment.installedApps() }
                .onSuccess { apps -> local.update { it.copy(apps = apps, loading = false) } }
                .onFailure { local.update { it.copy(error = "앱 목록을 읽지 못했습니다.", loading = false) } }
        }
    }

    fun refreshHistory() {
        if (local.value.historyBusy || !environment.hasUsageAccess()) return
        local.update { it.copy(historyBusy = true, historyError = false) }
        viewModelScope.launch {
            try {
                historyRepository.collect()
                // The background job may be deferred for days on battery-saving devices; opening the app sends a due report.
                runCatching { com.chronolock.data.WeeklyReport.maybeNotify(environment.context, historyRepository, preferences) }
            }
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

    fun toggleFavoriteTheme(theme: AppTheme) = viewModelScope.launch {
        try { preferences.toggleFavoriteTheme(theme) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("테마를 저장하지 못했습니다. 다시 선택해 주세요.") }
    }

    fun setFollowSystem(on: Boolean) = viewModelScope.launch {
        try { preferences.setFollowSystem(on); if (!on) local.update { it.copy(editingNight = false) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("테마를 저장하지 못했습니다. 다시 선택해 주세요.") }
    }
    fun editNightTheme(night: Boolean) { local.update { it.copy(editingNight = night) } }

    fun selectTheme(theme: AppTheme) = viewModelScope.launch {
        if (!state.value.pro && theme !in AppTheme.builtIn) { showError(PRO_THEMES); return@launch }
        if (state.value.followSystem && state.value.editingNight) {
            try { preferences.setNightTheme(theme) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showError("테마를 저장하지 못했습니다. 다시 선택해 주세요.") }
            return@launch
        }
        try { preferences.setTheme(theme) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("테마를 저장하지 못했습니다. 다시 선택해 주세요.") }
    }

    fun save(rule: AppRule, onSaved: () -> Unit, onFinished: () -> Unit = {}) = viewModelScope.launch {
        try {
            require(rule.packageName !in environment.safetyPackages())
            val clock = environment.clock()
            if (repository.requestRule(rule, strictDelay(clock), clock.epochMillis, clock) != null) showError(HELD)
            onSaved()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(failure(error, "제한을 저장하지 못했습니다. 다시 시도해 주세요.")) }
        finally { onFinished() }
    }

    /** A launcher shortcut opened the app: start a focus of the last chosen length, or show History. */
    fun openShortcut(action: String?) {
        when (action) {
            AppShortcuts.ACTION_FOCUS -> viewModelScope.launch {
                if (repository.rules.first().none { it.enabled }) {
                    local.update { it.copy(requestedTab = 1) }
                    showError("집중할 앱을 먼저 추가해 주세요.")
                } else startFocus(preferences.focusMinutes.first())
            }
            AppShortcuts.ACTION_HISTORY -> local.update { it.copy(requestedTab = 2) }
        }
    }

    fun tabShown() = local.update { it.copy(requestedTab = null) }
    /** The friend's code has been offered for sending; it is not kept on screen after that. */
    fun partnerCodeShown() = local.update { it.copy(partnerCode = null) }

    fun saveNote(packageName: String, note: String) = viewModelScope.launch {
        try { preferences.setNote(packageName, note) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("설정을 저장하지 못했습니다. 다시 시도해 주세요.") }
    }

    fun startFocus(minutes: Int) {
        if (local.value.focusBusy) return
        local.update { it.copy(focusBusy = true) }
        viewModelScope.launch {
            try {
                require(environment.hasUsageAccess() && environment.accessibilityEnabled()) { "사용 정보와 접근성 권한을 먼저 켜 주세요." }
                // A finished focus is counted before the new one replaces its end.
                environment.clock().let { com.chronolock.data.TimeBankStore.settle(repository, preferences, it.epochMillis); repository.startFocus(minutes, it.epochMillis, it) }
                runCatching { preferences.setFocusMinutes(minutes) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showError(if (error is IllegalArgumentException) error.message.orEmpty() else "집중 모드를 시작하지 못했습니다.") }
            finally { local.update { it.copy(focusBusy = false) } }
        }
    }

    /** Stops the focus; with the desk QR on, only after [scanned] matches the printed code. */
    fun stopFocus(scanned: String? = null) {
        if (local.value.focusBusy) return
        local.update { it.copy(focusBusy = true) }
        viewModelScope.launch {
            try {
                val now = environment.clock().epochMillis
                // Read from storage: the screen state may not have loaded the lock or strict setting yet.
                if (locked(now)) { showError(LocalRepository.LOCKED); return@launch }
                if (preferences.strict.first().delayAt(environment.clock()) > 0) { showError("엄격 모드에서는 집중을 중간에 끝낼 수 없습니다."); return@launch }
                val desk = preferences.deskQr.first()
                if (desk != null && !com.chronolock.domain.DeskQr.matches(desk, scanned)) {
                    showError(if (scanned == null) "책상에 둔 QR을 찍어야 집중을 끝낼 수 있어요." else "이 기기의 집중 QR이 아니에요.")
                    return@launch
                }
                repository.stopFocus(now)
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showError("집중 모드를 종료하지 못했습니다.") }
            finally { local.update { it.copy(focusBusy = false) } }
        }
    }
    fun remove(packageName: String, onSaved: () -> Unit) = viewModelScope.launch {
        try {
            val clock = environment.clock()
            if (repository.requestDelete(packageName, strictDelay(clock), clock.epochMillis, clock) != null) showError(HELD)
            onSaved()
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(failure(error, "제한을 삭제하지 못했습니다.")) }
    }

    /** Pauses one rule for [minutes], or for the rest of today when null. */
    fun pause(packageName: String, minutes: Int?) = viewModelScope.launch {
        try {
            val clock = environment.clock()
            if (repository.requestPause(packageName, minutes, strictDelay(clock), clock.epochMillis, clock.zone, clock) != null) showError(HELD)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(failure(error, "제한을 쉬게 하지 못했습니다. 다시 시도해 주세요.")) }
    }

    fun resume(packageName: String) = viewModelScope.launch {
        try { repository.resume(packageName, environment.clock().epochMillis) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("제한을 다시 켜지 못했습니다. 다시 시도해 주세요.") }
    }

    fun cancelPending(packageName: String) = viewModelScope.launch {
        try { repository.cancelPending(packageName) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("설정을 저장하지 못했습니다. 다시 시도해 주세요.") }
    }

    fun selectMascot(kind: com.chronolock.data.MascotKind) = viewModelScope.launch {
        // A character already chosen stays; only switching to a locked one needs Pro.
        if (!state.value.pro && kind != com.chronolock.data.MascotKind.FREE && kind != state.value.mascot) { showError(PRO_MASCOT); return@launch }
        preferences.setMascot(kind)
    }
    fun setReduceMotion(on: Boolean) = viewModelScope.launch { preferences.setReduceMotion(on) }
    fun setBedtime(on: Boolean) = viewModelScope.launch { preferences.setBedtime(on) }
    fun setExam(exam: com.chronolock.domain.Exam?) = viewModelScope.launch {
        try { preferences.setExam(exam) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("설정을 저장하지 못했습니다. 다시 시도해 주세요.") }
    }

    /**
     * Turns the desk QR on with a new secret, or off. Neither while a focus runs: turning it off, or printing a new
     * code, would be a way around the code already out of reach.
     */
    fun setDeskQr(on: Boolean) = viewModelScope.launch {
        try {
            val now = environment.clock().epochMillis
            if (repository.rules.first().any { (it.focusUntilEpochMillis ?: 0) > now }) {
                showError("집중이 끝난 뒤에 바꿀 수 있어요."); return@launch
            }
            preferences.setDeskQr(if (on) com.chronolock.domain.DeskQr.newSecret(
                ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }) else null)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("설정을 저장하지 못했습니다. 다시 시도해 주세요.") }
    }

    /** Spends banked minutes as a pause of [packageName]; strict mode and locks hold it back like any pause. */
    fun spendTimeBank(packageName: String) = viewModelScope.launch {
        val minutes = com.chronolock.domain.TimeBank.REWARD_MINUTES
        if (!preferences.spendTimeBank(minutes)) return@launch
        try {
            val clock = environment.clock()
            val held = repository.requestPause(packageName, minutes, strictDelay(clock), clock.epochMillis, clock.zone, clock)
            showError(if (held != null) HELD else "모은 시간 10분을 썼어요.")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            runCatching { preferences.spendTimeBank(-minutes) }
            showError(failure(error, "제한을 쉬게 하지 못했습니다. 다시 시도해 주세요."))
        }
    }
    fun dismissMonitorGap() = viewModelScope.launch { preferences.dismissMonitorGap() }

    fun saveGroup(group: AppGroup, onSaved: () -> Unit, onFinished: () -> Unit = {}) = viewModelScope.launch {
        if (group.id == 0L && !state.value.pro && state.value.groups.size >= com.chronolock.domain.FreeTier.GROUPS) {
            showError(PRO_GROUPS); onFinished(); return@launch
        }
        try {
            require(group.members.keys.none { it in environment.safetyPackages() })
            val clock = environment.clock()
            if (repository.requestGroup(group, strictDelay(clock), clock.epochMillis, clock) != null) showError(HELD)
            onSaved()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            showError(failure(error, "그룹을 저장하지 못했습니다. 다시 시도해 주세요."))
        }
        finally { onFinished() }
    }

    fun removeGroup(id: Long, onSaved: () -> Unit) = viewModelScope.launch {
        try {
            val clock = environment.clock()
            if (repository.requestDeleteGroup(id, strictDelay(clock), clock.epochMillis, clock) != null) showError(HELD)
            onSaved()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(failure(error, "그룹을 삭제하지 못했습니다.")) }
    }

    fun cancelPendingGroup(id: Long) = viewModelScope.launch {
        try { repository.cancelPendingGroup(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("설정을 저장하지 못했습니다. 다시 시도해 주세요.") }
    }

    /** Adds the site typed as [input]; [onAdded] clears the field. */
    fun addSite(input: String, onAdded: () -> Unit) = viewModelScope.launch {
        if (!state.value.pro && state.value.sites.size >= com.chronolock.domain.FreeTier.SITES) { showError(PRO_SITES); return@launch }
        val domain = try { SiteDomains.normalize(input) } catch (invalid: IllegalArgumentException) { showError(SiteDomains.INVALID); return@launch }
        try {
            val clock = environment.clock()
            repository.requestSite(BlockedSite(domain), strictDelay(clock), clock.epochMillis, clock)
            onAdded()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            showError(if (error.message == LocalRepository.SITES_FULL) LocalRepository.SITES_FULL else "사이트를 저장하지 못했습니다. 다시 시도해 주세요.")
        }
    }

    /** Adds a preset's sites that are not blocked yet, up to the free tier's room; existing entries are left alone. */
    fun addSites(domains: List<String>) = viewModelScope.launch {
        val existing = state.value.sites.map { it.domain }.toSet()
        val missing = domains.filter { it !in existing }
        if (missing.isEmpty()) { showError("이미 모두 추가되어 있어요."); return@launch }
        val room = if (state.value.pro) com.chronolock.domain.SiteDomains.MAX_SITES - existing.size
            else com.chronolock.domain.FreeTier.SITES - existing.size
        if (room <= 0) { showError(if (state.value.pro) LocalRepository.SITES_FULL else PRO_SITES); return@launch }
        try {
            val clock = environment.clock()
            missing.take(room).forEach { repository.requestSite(BlockedSite(it), strictDelay(clock), clock.epochMillis, clock) }
            if (missing.size > room) showError(if (state.value.pro) LocalRepository.SITES_FULL else PRO_SITES)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(failure(error, "사이트를 저장하지 못했습니다. 다시 시도해 주세요.")) }
    }

    fun toggleSite(site: BlockedSite, enabled: Boolean) = viewModelScope.launch {
        try {
            val clock = environment.clock()
            if (repository.requestSite(site.copy(enabled = enabled), strictDelay(clock), clock.epochMillis, clock) != null) showError(HELD)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(failure(error, "사이트를 저장하지 못했습니다. 다시 시도해 주세요.")) }
    }

    fun removeSite(domain: String) = viewModelScope.launch {
        try {
            val clock = environment.clock()
            if (repository.requestDeleteSite(domain, strictDelay(clock), clock.epochMillis, clock) != null) showError(HELD)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError(failure(error, "사이트를 삭제하지 못했습니다.")) }
    }

    fun cancelPendingSite(domain: String) = viewModelScope.launch {
        try { repository.cancelPendingSite(domain) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("설정을 저장하지 못했습니다. 다시 시도해 주세요.") }
    }

    fun setStrict(minutes: Int) = viewModelScope.launch {
        val now = environment.clock().epochMillis
        if (locked(now) && minutes < preferences.strict.first().delayAt(environment.clock())) { showError(LocalRepository.LOCKED); return@launch }
        if (!state.value.pro && minutes > com.chronolock.domain.FreeTier.STRICT_MINUTES) { showError(PRO_STRICT); return@launch }
        try { preferences.updateStrict { it.request(minutes, environment.clock()) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("설정을 저장하지 못했습니다. 다시 시도해 주세요.") }
    }

    fun cancelStrictChange() = viewModelScope.launch {
        try { preferences.updateStrict { it.cancel(environment.clock()) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("설정을 저장하지 못했습니다. 다시 시도해 주세요.") }
    }

    fun redeemCode(code: String): Boolean = billing.redeem(code)

    fun exportBackup(uri: android.net.Uri) = viewModelScope.launch {
        try { backups.export(uri, environment.clock().epochMillis); showError("백업 파일을 저장했습니다.") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { android.util.Log.w("ChronoLock", "Backup failed", error); showError("백업 파일을 저장하지 못했습니다.") }
    }

    /** Keeps access to the picked folder across restarts and writes the first weekly backup there at once. */
    fun startAutoBackup(folder: android.net.Uri) = viewModelScope.launch {
        try {
            environment.context.contentResolver.takePersistableUriPermission(folder,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            preferences.setAutoBackupFolder(folder.toString())
            backups.autoBackup(environment.clock().epochMillis, force = true)
            showError("백업 파일을 저장했습니다.")
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { android.util.Log.w("ChronoLock", "Auto backup setup failed", error); showError("백업 파일을 저장하지 못했습니다.") }
    }

    /** Gives the picked folder back; weekly backups return to the phone's Download folder. */
    fun stopAutoBackup() = viewModelScope.launch {
        preferences.autoBackup.first().folder?.let { folder ->
            runCatching { environment.context.contentResolver.releasePersistableUriPermission(folder.toUri(),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        preferences.setAutoBackupFolder(null)
        if (com.chronolock.data.BackupRepository.PHONE_BACKUP_SUPPORTED) backupNow()
    }

    /** Writes the weekly backup now, wherever it goes, so the person can see the file is there. */
    fun backupNow() = viewModelScope.launch {
        try {
            if (backups.autoBackup(environment.clock().epochMillis, force = true)) showError("백업 파일을 저장했습니다.")
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { android.util.Log.w("ChronoLock", "Backup failed", error); showError("백업 파일을 저장하지 못했습니다.") }
    }

    /** Replaces rules and groups with a backup's. Strict mode must be off, as restoring can remove limits at once. */
    fun restoreBackup(uri: android.net.Uri) = viewModelScope.launch {
        try {
            val now = environment.clock().epochMillis
            if (locked(now)) { showError(LocalRepository.LOCKED); return@launch }
            if (preferences.strict.first().delayAt(environment.clock()) > 0) { showError("엄격 모드를 끈 뒤에 백업을 복원할 수 있습니다."); return@launch }
            backups.restore(uri, environment.safetyPackages(), now)
            showError("백업을 복원했습니다.")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            android.util.Log.w("ChronoLock", "Restore failed", error)
            showError(if (error.message == BackupCodec.INVALID) BackupCodec.INVALID else "백업을 복원하지 못했습니다.")
        }
    }

    /** Read from storage rather than the screen state, which may not have loaded yet. A lock overrides strict mode. */
    private suspend fun strictDelay(clock: com.chronolock.domain.ClockReading) =
        if (locked(clock.epochMillis)) LocalRepository.LOCKED_DELAY else preferences.strict.first().delayMillisAt(clock)
    /** Read from storage on the boot clock too, so a changed wall clock does not end a lock. */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun locked(now: Long) = preferences.lock.first().lockedAt(environment.clock())
    /** The lock message when a change was refused by a lock, else [fallback]. */
    private fun failure(error: Exception, fallback: String) =
        error.message?.takeIf { it == LocalRepository.LOCKED || it == LocalRepository.GROUP_TAKEN } ?: fallback

    /** Starts or extends a time lock by [hours]. */
    fun lockFor(hours: Int) = viewModelScope.launch {
        try {
            preferences.extendLock(environment.clock(), hours * 3_600_000L)
            // Relaxations strict mode was still holding would otherwise apply in the middle of the lock.
            repository.cancelAllPending()
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("설정을 저장하지 못했습니다. 다시 시도해 주세요.") }
    }

    /**
     * Starts a goal lock of [days] kept days, with the person's password or, when [pieces], an app-made code; with [partner]
     * that code also goes to a friend, offered through [MainState.partnerCode]. [done] closes the form.
     */
    fun startGoalLock(days: Int, pieces: Boolean, pin: String, confirm: String, done: () -> Unit, partner: Boolean = false) = viewModelScope.launch {
        when {
            !state.value.pro -> showError(PRO_GOAL)
            !pieces && !partner && !pin.matches(Regex("[0-9]{4,12}")) -> showError("비밀번호는 숫자 4~12자리로 정해 주세요.")
            !pieces && !partner && pin != confirm -> showError("두 비밀번호가 다릅니다.")
            else -> try {
                val code = preferences.startGoalLock(days, environment.clock().date, pin.takeUnless { pieces || partner }, partner)
                repository.cancelAllPending(); done()
                if (partner && code != null) local.update { it.copy(partnerCode = code) }
            }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { showError("설정을 저장하지 못했습니다. 다시 시도해 주세요.") }
        }
    }

    /** Ends a goal lock once its goal is reached and [pin] matches. [keptStreak] comes from the screen's records. */
    fun endGoalLock(pin: String, keptStreak: Int) = viewModelScope.launch {
        try {
            val lock = preferences.lock.first()
            if (!lock.canUnlock(keptStreak, environment.clock().date)) { showError("아직 목표를 이루지 않았어요."); return@launch }
            val result = preferences.endGoalLock(pin, environment.clock())
            if (!result.ok) { refused(result); return@launch }
            showError("목표 잠금을 풀었어요. 수고했어요!")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("설정을 저장하지 못했습니다. 다시 시도해 주세요.") }
    }

    /** Removes all rules, groups and sites; history stays. Strict mode must be off, as this lifts every limit at once. */
    fun resetRules() = viewModelScope.launch {
        try {
            val now = environment.clock().epochMillis
            if (locked(now)) { showError(LocalRepository.LOCKED); return@launch }
            if (preferences.strict.first().delayAt(environment.clock()) > 0) { showError("엄격 모드를 끈 뒤에 모든 규칙을 초기화할 수 있습니다."); return@launch }
            repository.resetRules(now)
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("규칙을 초기화하지 못했습니다.") }
    }

    /** Sets the history password; [done] closes the form. */
    fun setHistoryPin(pin: String, confirm: String, done: () -> Unit) = viewModelScope.launch {
        when {
            !pin.matches(Regex("[0-9]{4,12}")) -> showError("비밀번호는 숫자 4~12자리로 정해 주세요.")
            pin != confirm -> showError("두 비밀번호가 다릅니다.")
            else -> try { preferences.setHistoryPin(pin); done() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { showError("설정을 저장하지 못했습니다. 다시 시도해 주세요.") }
        }
    }

    /** Explains a refused password: how many tries are left, or until when it is paused. */
    private fun refused(result: com.chronolock.data.PinResult) {
        local.update { it.copy(pinBlockedUntil = result.blockedUntil, pinTriesLeft = result.triesLeft.takeIf { result.blockedUntil == null }) }
        showError(if (result.blockedUntil != null) "비밀번호를 5번 틀려 10분 동안 입력할 수 없습니다." else "비밀번호가 맞지 않습니다.")
    }

    /** Changes the history password after the current one is confirmed. */
    fun changeHistoryPin(current: String, next: String, confirm: String, done: () -> Unit) = viewModelScope.launch {
        when {
            !next.matches(Regex("[0-9]{4,12}")) -> showError("비밀번호는 숫자 4~12자리로 정해 주세요.")
            next != confirm -> showError("두 비밀번호가 다릅니다.")
            else -> try {
                val result = preferences.changeHistoryPin(current, next, environment.clock())
                if (result.ok) { done(); showError("비밀번호를 바꿨습니다.") } else refused(result)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showError("설정을 저장하지 못했습니다. 다시 시도해 주세요.") }
        }
    }

    /** Changes a goal lock's own password after the current one is confirmed. */
    fun changeGoalPin(current: String, next: String, confirm: String, done: () -> Unit) = viewModelScope.launch {
        when {
            !next.matches(Regex("[0-9]{4,12}")) -> showError("비밀번호는 숫자 4~12자리로 정해 주세요.")
            next != confirm -> showError("두 비밀번호가 다릅니다.")
            else -> try {
                val result = preferences.changeGoalPin(current, next, environment.clock())
                if (result.ok) { done(); showError("비밀번호를 바꿨습니다.") } else refused(result)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showError("설정을 저장하지 못했습니다. 다시 시도해 주세요.") }
        }
    }

    /** Hands out the next digit of a piece code when another kept day has passed. */
    fun collectPiece(keptStreak: Int) = viewModelScope.launch {
        try {
            if (!preferences.lock.first().canReveal(keptStreak, environment.clock().date)) { showError("오늘의 조각은 이미 받았어요. 내일도 한도를 지켜 보세요."); return@launch }
            preferences.revealNextPiece()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("설정을 저장하지 못했습니다. 다시 시도해 주세요.") }
    }

    /** Deletes kept history after the password matches. Today's counts used by limits are kept. */
    fun deleteHistory(pin: String, done: () -> Unit) = viewModelScope.launch {
        try {
            val check = preferences.checkHistoryPin(pin, environment.clock())
            if (!check.ok) { refused(check); return@launch }
            historyRepository.clearHistory(environment.clock().epochMillis)
            done()
            showError("기록을 삭제했습니다.")
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { showError("기록을 삭제하지 못했습니다.") }
    }
    /** Reads, at first run, the goals a friend's link or a store listing brought along. */
    fun checkInvite() = viewModelScope.launch {
        if (local.value.invite != null) return@launch
        val invite = runCatching { com.chronolock.domain.Invite.parse(com.chronolock.data.InstallInvite.referrer(environment.context)) }
            .onFailure { if (it is CancellationException) throw it }.getOrNull() ?: return@launch
        local.update { it.copy(invite = invite) }
    }

    fun finishOnboarding(goals: Set<String> = emptySet()) = viewModelScope.launch {
        try { preferences.finishOnboarding(goals) }
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
    private companion object {
        const val HELD = "엄격 모드라 완화한 설정은 대기 시간이 지난 뒤 적용됩니다."
        const val PRO_SITES = "무료 버전은 사이트를 5개까지 차단할 수 있어요. 프로에서 50개까지 늘어나요."
        const val PRO_GROUPS = "무료 버전은 앱 그룹을 2개까지 만들 수 있어요. 프로에서는 제한이 없어요."
        const val PRO_GOAL = "목표 잠금은 프로 기능이에요."
        const val PRO_MASCOT = "햄스터 외의 캐릭터는 프로에서 쓸 수 있어요."
        const val PRO_STRICT = "6시간·24시간 대기는 프로에서 쓸 수 있어요."
        const val PRO_THEMES = "기본 테마 외의 테마와 나만의 테마는 프로에서 쓸 수 있어요."
    }
    fun showError(message: String) { local.update { it.copy(error = message) } }
    fun dismissError() { local.update { it.copy(error = null) } }
    fun clock() = environment.clock()
    fun updateDownloaded() { local.update { it.copy(updateReady = true) } }
}
