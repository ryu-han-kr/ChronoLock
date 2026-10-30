package com.chronolock.feature

import com.chronolock.billing.BillingStatus
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chronolock.blocking.Health
import com.chronolock.blocking.InstalledApp
import com.chronolock.data.DailyEntity
import com.chronolock.data.AppTheme
import com.chronolock.data.RuleEntity
import com.chronolock.data.toDomain
import com.chronolock.domain.RuleRecipe
import java.time.LocalDate
import java.time.format.DateTimeFormatter



@Composable
internal fun ChronoScreen(
    state: MainState,
    today: LocalDate,
    nowEpoch: Long,
    tab: Int,
    selectTab: (Int) -> Unit,
    snackbar: SnackbarHostState,
    edit: (String, RuleRecipe?) -> Unit,
    startFocus: (Int) -> Unit,
    stopFocus: () -> Unit,
    usagePermission: () -> Unit,
    accessibilityPermission: () -> Unit,
    clear: () -> Unit,
    selectTheme: (AppTheme) -> Unit = {},
    toggleRule: (RuleEntity, Boolean) -> Unit = { _, _ -> },
    selectLanguage: (com.chronolock.data.AppLanguage) -> Unit = {},
    refreshHistory: () -> Unit = {},
    banner: (@Composable () -> Unit)? = null,
    signIn: () -> Unit = {},
    signOut: () -> Unit = {},
    deleteAccount: () -> Unit = {},
    privacyOptions: () -> Unit = {},
    manageSubscription: () -> Unit = {},
    openThemeGallery: () -> Unit = {},
    pause: (String, Int?) -> Unit = { _, _ -> },
    resume: (String) -> Unit = {},
    cancelPending: (String) -> Unit = {},
    setStrict: (Int) -> Unit = {},
    cancelStrict: () -> Unit = {},
    selectMascot: (com.chronolock.data.MascotKind) -> Unit = {},
    setReduceMotion: (Boolean) -> Unit = {},
    setBedtime: (Boolean) -> Unit = {},
    spendTimeBank: (String) -> Unit = {},
    setDeskQr: (Boolean) -> Unit = {},
    setExam: (com.chronolock.domain.Exam?) -> Unit = {},
    editGroup: (Long) -> Unit = {},
    cancelPendingGroup: (Long) -> Unit = {},
    addSite: (String, () -> Unit) -> Unit = { _, _ -> },
    toggleSite: (com.chronolock.domain.BlockedSite, Boolean) -> Unit = { _, _ -> },
    removeSite: (String) -> Unit = {},
    cancelPendingSite: (String) -> Unit = {},
    addSitePreset: (List<String>) -> Unit = {},
    dismissMonitorGap: () -> Unit = {},
    setHistoryPin: (String, String, () -> Unit) -> Unit = { _, _, _ -> },
    changeHistoryPin: (String, String, String, () -> Unit) -> Unit = { _, _, _, _ -> },
    lockFor: (Int) -> Unit = {},
    startGoalLock: (Int, Boolean, String, String, () -> Unit, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    endGoalLock: (String, Int) -> Unit = { _, _ -> },
    collectPiece: (Int) -> Unit = {},
    changeGoalPin: (String, String, String, () -> Unit) -> Unit = { _, _, _, _ -> },
    buyPro: () -> Unit = {},
    managePro: () -> Unit = {},
    setFollowSystem: (Boolean) -> Unit = {},
    editNightTheme: (Boolean) -> Unit = {},
    pickRecipe: RuleRecipe? = null,
    choosePickRecipe: (RuleRecipe?) -> Unit = {},
    deleteHistory: (String, () -> Unit) -> Unit = { _, _ -> },
    exportBackup: () -> Unit = {},
    restoreBackup: () -> Unit = {},
    startAutoBackup: () -> Unit = {},
    stopAutoBackup: () -> Unit = {},
    backupNow: () -> Unit = {},
    redeemCode: (String) -> Boolean = { false },
) {
    val destinations = listOf(tr("오늘"), tr("앱 제한"), tr("히스토리"), tr("설정"))
    var query by rememberSaveable { mutableStateOf("") }
    var onlyConfigured by rememberSaveable { mutableStateOf(false) }
    var sortName by rememberSaveable { mutableStateOf(AppSort.NAME.name) }
    val sort = AppSort.entries.firstOrNull { it.name == sortName } ?: AppSort.NAME
    val usage = remember(state.history, today) { summarizeUsage(state.history, today.minusDays(6)) }
    val historyFilter = rememberHistoryFilter()
    val historyView = rememberHistoryView(state, today, historyFilter)
    val ready = state.permissions.usage && state.permissions.accessibility && state.health.connected
    val rulesByPackage = remember(state.rules) { state.rules.associateBy { it.packageName } }
    val filteredApps = remember(state.apps, rulesByPackage, query, onlyConfigured, sort, usage) {
        sortApps(state.apps.filter { app ->
            (!onlyConfigured || app.packageName in rulesByPackage) &&
                (app.label.contains(query.trim(), ignoreCase = true) ||
                    app.packageName.contains(query.trim(), ignoreCase = true))
        }, sort, usage)
    }
    BoxWithConstraints(Modifier.fillMaxSize().themeBackground(state.theme)) {
        val expanded = maxWidth >= 840.dp
        Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = { Column {
            if (!expanded) NavigationBar(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
                destinations.forEachIndexed { index, title ->
                    NavigationBarItem(selected = tab == index, onClick = { selectTab(index) },
                        label = { Text(title) }, icon = { DestinationIcon(index) }, modifier = Modifier.testTag("nav-$index"))
                }
            }
        } }) { insets ->
            Row(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) {
                if (expanded) NavigationRail(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground, windowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.fillMaxHeight(), header = {
                        Text("CL", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 24.dp))
                    }) {
                    destinations.forEachIndexed { index, title ->
                        NavigationRailItem(selected = tab == index, onClick = { selectTab(index) },
                            label = { Text(title) }, icon = { DestinationIcon(index) }, modifier = Modifier.testTag("nav-$index"))
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    // Each destination keeps its own position, including after a window resize.
                    val todayScroll = rememberLazyGridState()
                    val appsScroll = rememberLazyGridState()
                    val settingsScroll = rememberLazyGridState()
                    val historyScroll = rememberLazyGridState()
                    LazyVerticalGrid(
                        // History stays one column (wide charts); the other tabs use two on large screens.
                        columns = GridCells.Fixed(if (expanded && tab != 2) 2 else 1),
                        state = when (tab) { 0 -> todayScroll; 1 -> appsScroll; 2 -> historyScroll; else -> settingsScroll },
                        modifier = Modifier.widthIn(max = 1120.dp).fillMaxSize().testTag("main-list"),
                        contentPadding = PaddingValues(if (expanded) 32.dp else 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(destinations[tab], style = MaterialTheme.typography.headlineMedium)
                                Text(when (tab) {
                                    0 -> tr("%1\$s · 내 시간을, 내 뜻대로.", today.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(uiText().locale)))
                                    1 -> tr("작은 제한으로 더 여유로운 하루를 만드세요.")
                                    2 -> tr("차단 없이도 내 앱 사용 습관을 확인하세요.")
                                    else -> tr("내 데이터와 권한을 관리하세요.")
                                }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (tab == 0) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatusPill(ready)
                                    // A running lock is easy to forget; it decides whether anything can be relaxed.
                                    LockPill(state.lock, nowEpoch)
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                        if (tab < 2 && !ready) item(span = { GridItemSpan(maxLineSpan) }) {
                            PermissionCard(state, usagePermission, accessibilityPermission)
                        }
                        when (tab) {
                            0 -> {
                                val stats = state.daily.filter { it.date == today.toString() }
                                state.monitorGap?.let { gap -> item(span = { GridItemSpan(maxLineSpan) }) { MonitorGapCard(gap, dismissMonitorGap) } }
                                item { UsageSummaryCard(stats, state, nowEpoch) }
                                item { QuickFocusCard(state, nowEpoch, startFocus, stopFocus) }
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(tr("내 앱 제한"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                                        TextButton(onClick = { selectTab(1) }) { Text(tr("앱 추가")) }
                                    }
                                }
                                if (state.timeBank >= com.chronolock.domain.TimeBank.REWARD_MINUTES && state.rules.any { it.enabled })
                                    item(span = { GridItemSpan(maxLineSpan) }) { TimeBankCard(state, spendTimeBank) }
                                state.exam?.takeIf { it.showing(today) }?.let { exam ->
                                    item(span = { GridItemSpan(maxLineSpan) }) { ExamCard(exam, today) { selectTab(1) } }
                                }
                                if (state.rules.isEmpty() && state.goals.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                                    GoalStartCard(state.goals, pick = { choosePickRecipe(it); selectTab(1) }, addSites = addSitePreset)
                                } else if (state.rules.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                                    CardBox {
                                        Text(tr("집중을 방해하는 앱이 있나요?"), style = MaterialTheme.typography.titleMedium)
                                        Text(tr("앱을 선택하고 나에게 맞는 사용시간과 실행 횟수를 정해 보세요."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Button(onClick = { selectTab(1) }) { Text(tr("앱 선택하기")) }
                                    }
                                }
                                items(state.rules, key = { "rule:${it.packageName}" }) { rule ->
                                    val usage = stats.firstOrNull { it.packageName == rule.packageName }
                                    CardBox {
                                        AppHeading(rule.appName, rule.packageName) { edit(rule.packageName, null) }
                                        Text(ruleSummary(rule), color = if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                        rule.dailyLimitSeconds?.let { limit ->
                                            val remaining = remainingDailySeconds(limit, usage?.usageMillis ?: 0)
                                            val tint = if (remaining == 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            val progress by animateFloatAsState(
                                                ((usage?.usageMillis ?: 0).toFloat() / (limit.coerceAtLeast(1) * 1000f)).coerceIn(0f, 1f), label = "daily")
                                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp), color = tint,
                                                trackColor = tint.copy(alpha = .16f))
                                            if (rule.enabled) Text(if (remaining == 0L) tr("오늘 한도 도달") else tr("오늘 %1\$s 남음", duration(remaining)),
                                                color = tint, style = MaterialTheme.typography.labelMedium)
                                        }
                                        Row(Modifier.fillMaxWidth().toggleable(value = rule.enabled, role = Role.Switch,
                                            onValueChange = { toggleRule(rule, it) }), verticalAlignment = Alignment.CenterVertically) {
                                            Text(tr("오늘 %1\$s · %2\$d회 실행", duration((usage?.usageMillis ?: 0) / 1000), usage?.opens ?: 0),
                                                Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                            Text(tr("제한 사용"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                            Spacer(Modifier.width(8.dp))
                                            Switch(checked = rule.enabled, onCheckedChange = null)
                                        }
                                        RuleStatus(rule, state.pending.firstOrNull { it.packageName == rule.packageName }, nowEpoch,
                                            state.strict.delayAt(nowEpoch), pause = { pause(rule.packageName, it) },
                                            resume = { resume(rule.packageName) }, cancelHeld = { cancelPending(rule.packageName) })
                                    }
                                }
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(tr("앱 그룹"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                                        TextButton(onClick = { editGroup(0L) }, modifier = Modifier.testTag("group-add")) { Text(tr("그룹 만들기")) }
                                    }
                                }
                                if (state.groups.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(tr("SNS처럼 비슷한 앱을 묶으면 하루 시간과 실행 횟수를 함께 쓰게 할 수 있습니다."),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                                items(state.groups, key = { "group:${it.id}" }) { group ->
                                    GroupCard(group, stats, state.pendingGroups.firstOrNull { it.groupId == group.id },
                                        edit = { editGroup(group.id) }, cancelHeld = { cancelPendingGroup(group.id) })
                                }
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(tr("웹사이트 차단"), style = MaterialTheme.typography.titleLarge)
                                }
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    SitesCard(state.sites, state.pendingSites, addSite, toggleSite, removeSite, cancelPendingSite, addSitePreset,
                                        installed = state.apps.associate { it.packageName to it.label },
                                        limited = state.rules.filter { it.enabled }.mapTo(mutableSetOf()) { it.packageName },
                                        limitApp = { app -> choosePickRecipe(null); edit(app, null) })
                                }
                                item(span = { GridItemSpan(maxLineSpan) }) { WeeklyInsightsCard(state, today) { app, recipe -> edit(app, recipe) } }
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(tr("선택한 앱의 기록만 표시합니다. 감지 중단 중 실행 횟수는 누락될 수 있습니다."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            1 -> {
                                pickRecipe?.let { recipe -> item(span = { GridItemSpan(maxLineSpan) }) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(tr("'%1\$s'을(를) 적용할 앱을 눌러 주세요.", tr(recipe.title)), Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                                        TextButton(onClick = { choosePickRecipe(null) }) { Text(tr("취소")) }
                                    }
                                } }
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(value = query, onValueChange = { query = it },
                                            label = { Text(tr("앱 검색")) }, placeholder = { Text(tr("앱 이름 또는 패키지 이름")) },
                                            singleLine = true, shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth(), trailingIcon = {
                                                if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text(tr("지우기")) }
                                            })
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(selected = !onlyConfigured, onClick = { onlyConfigured = false }, label = { Text(tr("전체 앱")) })
                                            FilterChip(selected = onlyConfigured, onClick = { onlyConfigured = true }, label = { Text(tr("설정한 앱")) })
                                        }
                                        Row(Modifier.horizontalScroll(rememberScrollState()).testTag("app-sort"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(AppSort.NAME to "이름순", AppSort.USAGE to "사용량순", AppSort.RECENT to "최근순").forEach { (option, label) ->
                                                FilterChip(selected = sort == option, onClick = { sortName = option.name }, label = { Text(tr(label)) })
                                            }
                                        }
                                        Text(tr("%1\$d개 앱 · 설정·홈·전화 등 필수 앱은 제외합니다.", filteredApps.size), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                if (state.loading) item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                                }
                                if (!state.loading && filteredApps.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                                    CardBox {
                                        Text(if (query.isNotBlank()) tr("검색 결과가 없습니다.") else if (onlyConfigured) tr("아직 설정한 앱이 없습니다.") else tr("선택 가능한 앱이 없습니다."), fontWeight = FontWeight.Bold)
                                        Text(tr("다른 검색어를 입력하거나 전체 앱 목록을 확인해 보세요."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                items(filteredApps, key = { "app:${it.packageName}" }) { app ->
                                    val rule = rulesByPackage[app.packageName]
                                    CardBox {
                                        AppHeading(app.label, app.packageName) { edit(app.packageName, null) }
                                        Text(rule?.let { ruleSummary(it) } ?: tr("내 사용 기준을 설정해 보세요"),
                                            color = if (rule?.enabled == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                        if (sort != AppSort.NAME) Text(usage[app.packageName]?.let { summary ->
                                            tr("최근 7일 %1\$s · %2\$d회 실행", duration(summary.usageMillis / 1000), summary.opens) +
                                                if (sort == AppSort.RECENT) " · " + tr("마지막 사용 %1\$s", dateText(LocalDate.parse(summary.lastDate))) else ""
                                        } ?: tr("최근 7일 기록 없음"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            2 -> {
                                historyItems(state, today, historyFilter, historyView, refreshHistory, usagePermission, suggest = { app, recipe -> edit(app, recipe) },
                                    limit = { app -> choosePickRecipe(null); edit(app, null) },
                                    setPin = setHistoryPin, deleteHistory = deleteHistory, changePin = changeHistoryPin, nowEpoch = nowEpoch)
                            }
                            3 -> {
                                if (state.account.configured) item { AccountCard(state, signIn, signOut, deleteAccount) }
                                if (com.chronolock.BuildConfig.PRO_GATING) item { ProCard(state, buyPro, managePro) }
                                if (state.ads.configured || state.adFree) item { AdsCard(state, privacyOptions, manageSubscription) }
                                item { LanguagePicker(state.language, selectLanguage) }
                                item { CardBox { MascotPicker(state.mascot, selectMascot, state.pro); MotionToggle(state.reduceMotion, setReduceMotion) } }
                                item { CardBox { BedtimeToggle(state.bedtime, setBedtime) } }
                                item { CardBox { ExamSettings(state.exam, today, setExam) } }
                                item { CardBox { DeskQrCard(state.deskQr, state.rules.any { (it.focusUntilEpochMillis ?: 0) > nowEpoch }, setDeskQr) } }
                                item {
                                    ThemePicker(if (state.followSystem && state.editingNight) state.nightTheme else state.theme, selectTheme, openThemeGallery,
                                        state.favoriteThemes, state.followSystem, state.editingNight, setFollowSystem, editNightTheme)
                                }
                                item { StrictModeCard(state, nowEpoch, setStrict, cancelStrict) }
                                item {
                                    // Completed days since the goal lock began; a day without any use of the limited apps counts as kept.
                                    val kept = remember(state.daily, state.rules, state.groups, state.lock.goalStart, today) {
                                        state.lock.goalStart?.let { start ->
                                            com.chronolock.domain.goalKeptDays(state.daily.associate { com.chronolock.domain.DailyKey(it.packageName, java.time.LocalDate.parse(it.date)) to
                                                com.chronolock.domain.DailyUsage(it.usageMillis, it.opens, it.blockedAttempts) }, state.rulesWithGroups, start, today)
                                        } ?: 0
                                    }
                                    LockCard(state, today, nowEpoch, kept, lockFor, startGoalLock, { endGoalLock(it, kept) }, { collectPiece(kept) }, changeGoalPin)
                                }
                                item { PermissionCard(state, usagePermission, accessibilityPermission) }
                                item { CardBox {
                                    Text(tr("나를 위한 제한"), style = MaterialTheme.typography.titleLarge)
                                    Text(if (state.strict.delayAt(nowEpoch) > 0) tr("엄격 모드가 켜져 있습니다. 느슨하게 바꾸는 변경은 대기 시간이 지난 뒤 적용됩니다.")
                                        else tr("현재는 일반 모드입니다. 언제든 규칙을 수정하거나 삭제할 수 있습니다. Android 설정에서 권한 해제와 앱 삭제도 가능합니다."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(tr("기기·배터리 설정에 따라 감지와 차단이 늦어질 수 있습니다. 분할 화면과 화면 속 화면에 띄운 앱도 제한합니다."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } }
                                item { CardBox {
                                    Text(tr("백업과 복원"), style = MaterialTheme.typography.titleLarge)
                                    Text(tr("제한 규칙, 앱 그룹, 테마·언어·엄격 모드 설정을 파일로 저장하고 새 기기에서 되살릴 수 있습니다. 사용 기록은 포함하지 않습니다."),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val strict = state.strict.delayAt(nowEpoch) > 0
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = exportBackup, modifier = Modifier.testTag("backup-export")) { Text(tr("백업 파일 만들기")) }
                                        OutlinedButton(onClick = restoreBackup, enabled = !strict, modifier = Modifier.testTag("backup-restore")) { Text(tr("백업에서 복원")) }
                                    }
                                    if (strict) Text(tr("엄격 모드를 끈 뒤에 백업을 복원할 수 있습니다."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    HorizontalDivider()
                                    Text(tr("매주 자동 백업"), style = MaterialTheme.typography.titleMedium)
                                    val auto = state.autoBackup
                                    // Without a picked folder, Android 10+ backs up to Download/ChronoLock, which stays after the app is removed.
                                    val onPhone = auto?.folder == null && com.chronolock.data.BackupRepository.PHONE_BACKUP_SUPPORTED
                                    Text(if (onPhone) tr("휴대폰의 다운로드/ChronoLock 폴더에 일주일마다 백업해요. 앱을 지워도 파일은 남아요.")
                                        else tr("고른 폴더에 일주일마다 백업 파일 하나를 새로 덮어써 저장해요."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (auto != null && (onPhone || auto.folder != null)) {
                                        Text(auto.lastAt?.let { tr("마지막 자동 백업: %1\$s", timeText(it)) } ?: tr("아직 자동 백업하지 않았어요."),
                                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = backupNow, modifier = Modifier.testTag("auto-backup-now")) { Text(tr("지금 백업")) }
                                            if (auto.folder != null) OutlinedButton(onClick = stopAutoBackup, modifier = Modifier.testTag("auto-backup-stop")) {
                                                Text(if (com.chronolock.data.BackupRepository.PHONE_BACKUP_SUPPORTED) tr("다운로드 폴더로 되돌리기") else tr("자동 백업 끄기"))
                                            } else OutlinedButton(onClick = startAutoBackup, modifier = Modifier.testTag("auto-backup-start")) { Text(tr("다른 폴더 고르기")) }
                                        }
                                    } else OutlinedButton(onClick = startAutoBackup, modifier = Modifier.testTag("auto-backup-start")) { Text(tr("자동 백업 폴더 고르기")) }
                                } }
                                item { UnlockHelpCard() }
                                item { CardBox {
                                    Text(tr("규칙 초기화"), style = MaterialTheme.typography.titleLarge)
                                    Text(tr("앱 제한, 앱 그룹, 웹사이트 차단을 모두 지웁니다. 사용 기록은 남고, 기록 삭제는 히스토리 탭에서 비밀번호로 할 수 있습니다."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val strict = state.strict.delayAt(nowEpoch) > 0
                                    OutlinedButton(onClick = clear, enabled = !strict, modifier = Modifier.testTag("rules-reset")) { Text(tr("모든 규칙 초기화")) }
                                    if (strict) Text(tr("엄격 모드를 끈 뒤에 모든 규칙을 초기화할 수 있습니다."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                } }
                                item { VersionLine(state, redeemCode) }
                                // The one ad: last in Settings, scrolling with the list.
                                banner?.let { ad -> item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().testTag("settings-ad"), contentAlignment = Alignment.Center) { ad() } } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(state: MainState, signIn: () -> Unit, signOut: () -> Unit, deleteAccount: () -> Unit) {
    CardBox {
        Text(tr("계정"), style = MaterialTheme.typography.titleLarge)
        if (state.account.signedIn) {
            Text(tr("로그인됨: %1\$s", state.account.email ?: tr("Google 계정")), color = MaterialTheme.colorScheme.primary)
            Text(tr("계정에는 로그인 정보만 저장됩니다. 제한 규칙과 사용 기록은 계속 이 기기에만 보관합니다."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = signOut, enabled = !state.accountBusy) { Text(tr("로그아웃")) }
                TextButton(onClick = deleteAccount, enabled = !state.accountBusy) { Text(tr("계정 삭제"), color = MaterialTheme.colorScheme.error) }
            }
        } else {
            Text(tr("로그인은 선택 사항입니다. 로그인하지 않아도 모든 제한 기능을 사용할 수 있습니다."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = signIn, enabled = !state.accountBusy) { Text(tr("Google로 로그인")) }
        }
    }
}

@Composable
private fun AdsCard(state: MainState, privacyOptions: () -> Unit, manage: () -> Unit) {
    CardBox {
        Text(tr("광고"), style = MaterialTheme.typography.titleLarge)
        Text(when {
            state.adFree -> tr("광고 없이 이용 중입니다.")
            com.chronolock.BuildConfig.PRO_GATING && state.billing.proAccess -> tr("프로라서 광고가 표시되지 않아요.")
            else -> tr("설정 목록 맨 아래에만 작은 배너 광고가 하나 표시됩니다. 오늘·히스토리·차단 화면과 집중 중에는 표시하지 않아요.")
        }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // The ad-free subscription is no longer sold; existing subscribers keep it until they cancel.
        if (state.adFree) {
            OutlinedButton(onClick = manage) { Text(tr("구독 관리")) }
            Text(tr("광고 제거 구독은 새로 판매하지 않아요. 지금 구독은 해지하기 전까지 그대로 유지돼요."),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (state.ads.privacyOptionsRequired) OutlinedButton(onClick = privacyOptions) { Text(tr("광고 개인정보 설정")) }
    }
}

@Composable
private fun UsageSummaryCard(stats: List<DailyEntity>, state: MainState, nowEpoch: Long) {
    val enabled = state.rules.filter { it.enabled }
    val left = enabled.mapNotNull { rule ->
        rule.dailyLimitSeconds?.let { limit -> limit * 1000 - (stats.firstOrNull { it.packageName == rule.packageName }?.usageMillis ?: 0) }
    }
    val mood = todayMood(locked = state.lock.lockedAt(nowEpoch), focusing = enabled.any { (it.focusUntilEpochMillis ?: 0) > nowEpoch },
        limitReached = left.any { it <= 0 }, nearLimit = left.any { it in 1..10 * 60_000L },
        paused = enabled.any { (it.pausedUntilEpochMillis ?: 0) > nowEpoch },
        hour = java.time.Instant.ofEpochMilli(nowEpoch).atZone(java.time.ZoneId.systemDefault()).hour, hasRules = enabled.isNotEmpty())
    CardBox {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("오늘 선택한 앱 사용"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text(duration(stats.sumOf { it.usageMillis } / 1000), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                Text(tr("%1\$d개 앱 제한 켜짐", enabled.size), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            MascotArt(mood.pose, Modifier.testTag("today-mascot-${mood.pose.name.lowercase()}"), size = 96.dp)
        }
        Text(tr(mood.line), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .15f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) { Metric(tr("허용한 실행"), tr("%1\$d회", stats.sumOf { it.opens })) }
            Column(Modifier.weight(1f)) { Metric(tr("제한 도달·차단"), tr("%1\$d회", stats.sumOf { it.blockedAttempts })) }
        }
    }
}

/** Whole seconds left of a daily limit; partial seconds round up so 0 only appears once the limit is fully used. */
internal fun remainingDailySeconds(limitSeconds: Long, usedMillis: Long): Long =
    ((limitSeconds * 1000 - usedMillis).coerceAtLeast(0) + 999) / 1000

@Composable
private fun StatusPill(ready: Boolean) {
    val tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.background(tint.copy(alpha = .12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(6.dp).background(tint, CircleShape))
        Text(if (ready) tr("앱 제한 감지 중") else tr("감지 준비가 필요해요"), color = tint, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun LockPill(lock: com.chronolock.domain.LockSetting, nowEpoch: Long) {
    val text = when {
        lock.goalLocked -> tr("목표 잠금 중")
        lock.timeLockedAt(nowEpoch) -> tr("%1\$s까지 잠금", timeText(lock.untilEpochMillis!!))
        else -> return
    }
    val tint = MaterialTheme.colorScheme.error
    Row(Modifier.background(tint.copy(alpha = .12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp).testTag("lock-pill"),
        verticalAlignment = Alignment.CenterVertically) {
        Text("🔒 " + text, color = tint, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DestinationIcon(index: Int) {
    val color = LocalContentColor.current
    val background = MaterialTheme.colorScheme.background
    Canvas(Modifier.size(24.dp)) {
        val unit = size.width / 24f
        val stroke = Stroke(1.8f * unit)
        when (index) {
            0 -> {
                drawCircle(color, 9 * unit, style = stroke)
                drawLine(color, Offset(12 * unit, 6 * unit), center, 1.8f * unit)
                drawLine(color, center, Offset(16 * unit, 14 * unit), 1.8f * unit)
            }
            1 -> for (x in listOf(3f, 14f)) for (y in listOf(3f, 14f)) {
                drawRect(color, Offset(x * unit, y * unit), Size(7 * unit, 7 * unit), style = stroke)
            }
            2 -> for ((x, height) in listOf(4f to 8f, 10f to 16f, 16f to 12f)) {
                drawRect(color, Offset(x * unit, (21 - height) * unit), Size(4 * unit, height * unit))
            }
            else -> for ((x, y) in listOf(6f to 8f, 12f to 16f, 18f to 10f)) {
                drawLine(color, Offset(x * unit, 3 * unit), Offset(x * unit, 21 * unit), 1.8f * unit)
                drawCircle(background, 3 * unit, Offset(x * unit, y * unit))
                drawCircle(color, 3 * unit, Offset(x * unit, y * unit), style = stroke)
            }
        }
    }
}

@Preview(name = "오늘 · 휴대폰", widthDp = 390, heightDp = 844, locale = "ko")
@Preview(name = "오늘 · 태블릿", widthDp = 1100, heightDp = 800, locale = "ko")
@Preview(name = "오늘 · 큰 글씨", widthDp = 360, heightDp = 800, fontScale = 1.5f, locale = "ko")
@Composable
private fun TodayPreview() = LayoutPreview(0)

@Preview(name = "앱 제한", widthDp = 390, heightDp = 844, locale = "ko")
@Composable
private fun AppsPreview() = LayoutPreview(1)

@Composable
private fun LayoutPreview(initialTab: Int) {
    val today = LocalDate.of(2026, 10, 14)
    val state = MainState(
        apps = listOf(InstalledApp("sample.video", "동영상"), InstalledApp("sample.social", "소셜")),
        rules = listOf(RuleEntity("sample.video", "동영상", 3600, null, null)),
        daily = listOf(DailyEntity("sample.video", today.toString(), 1440000, 6, 2)),
        permissions = PermissionState(true, true), health = Health(connected = true), loading = false,
    )
    var tab by remember { mutableIntStateOf(initialTab) }
    ChronoTheme {
        ChronoScreen(state, today, 0, tab, { tab = it }, remember { SnackbarHostState() },
            { _, _ -> }, {}, {}, {}, {}, {})
    }
}

/** Minutes earned by finishing focuses, spent as a 10-minute pause of one limited app. */
@Composable
private fun TimeBankCard(state: MainState, spend: (String) -> Unit) {
    CardBox {
        Text(tr("모은 시간 %1\$d분", state.timeBank), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("time-bank"))
        Text(tr("집중을 끝까지 해서 모았어요. 앱 하나를 10분 쉬게 할 수 있어요."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.rules.filter { it.enabled }.forEach { rule ->
                AssistChip(onClick = { spend(rule.packageName) }, label = { Text(rule.appName) },
                    modifier = Modifier.testTag("bank-${rule.packageName}"))
            }
        }
    }
}

/** First suggestions from the goals picked at first run: a recipe to apply to an app, and a site set to block. */
@Composable
private fun GoalStartCard(goals: Set<String>, pick: (RuleRecipe?) -> Unit, addSites: (List<String>) -> Unit) {
    CardBox {
        Text(tr("맞춤 시작"), style = MaterialTheme.typography.titleLarge)
        Text(tr("처음에 고른 목표에 맞춰 추천해요. 앱을 고르면 추천 설정이 미리 채워져요."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        com.chronolock.domain.OnboardingGoal.entries.filter { it.name in goals }.forEach { goal ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(goal.emoji + "  " + tr(goal.label), style = MaterialTheme.typography.titleMedium)
                Text(goal.recipe?.let { tr(it.title) + " · " + tr(it.description) } ?: tr("하루 1시간부터 시작해 보세요."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pick(goal.recipe) }, modifier = Modifier.testTag("goal-pick-${goal.name}")) { Text(tr("앱 고르기")) }
                    goal.preset?.let { preset -> OutlinedButton(onClick = { addSites(preset.domains) }) { Text(tr("사이트도 막기")) } }
                }
            }
        }
    }
}
