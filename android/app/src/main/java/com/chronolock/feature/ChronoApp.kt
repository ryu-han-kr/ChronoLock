package com.chronolock.feature

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronolock.blocking.InstalledApp
import com.chronolock.data.ClockAnchors
import com.chronolock.data.RuleEntity
import com.chronolock.data.toDomain
import com.chronolock.domain.AppRule
import com.chronolock.domain.RuleDraft
import com.chronolock.domain.ScheduleDraft
import com.chronolock.domain.takenBy
import com.chronolock.domain.AppRule.Companion.MAX_SCHEDULES
import com.chronolock.data.PendingRuleEntity
import com.chronolock.ads.BannerAd
import com.chronolock.domain.AdPolicy
import com.chronolock.domain.DailyTimeInput
import com.chronolock.domain.RuleRecipe
import com.chronolock.domain.DailyKey
import com.chronolock.domain.DailyUsage
import com.chronolock.domain.DayResult
import com.chronolock.domain.dayResults
import com.chronolock.domain.compareWeeks
import com.chronolock.domain.keptStreak
import com.chronolock.domain.suggestUsageRule
import com.chronolock.domain.suggestedDraft
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun ChronoApp(model: MainViewModel, openSettings: (Boolean) -> Unit, signIn: () -> Unit = {}, privacyOptions: () -> Unit = {},
    manageSubscription: () -> Unit = {}, installUpdate: () -> Unit = {},
    buyPro: () -> Unit = {}, managePro: () -> Unit = {}) {
    val modelState by model.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editPackage by rememberSaveable { mutableStateOf<String?>(null) }
    /** 0 creates a new group. */
    var editGroupId by rememberSaveable { mutableStateOf<Long?>(null) }
    var suggestedRecipe by rememberSaveable { mutableStateOf<String?>(null) }
    /** A recipe chosen from the Today suggestions; the next app picked in the app list opens with it. */
    var pickRecipe by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionDialog by rememberSaveable { mutableStateOf<Boolean?>(null) }
    // The notification prompt rides along with accessibility: one consent, then the settings screen. Registered here,
    // not in the dialog, because the dialog has closed by the time the result arrives.
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { openSettings(true) }
    var themeGallery by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var restoreFrom by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(model::exportBackup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreFrom = uri }
    val autoBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(model::startAutoBackup)
    }
    var confirmDeleteAccount by rememberSaveable { mutableStateOf(false) }
    var confirmFocus by rememberSaveable { mutableStateOf<Int?>(null) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var clock by remember { mutableStateOf(model.clock()) }
    val nowEpoch = clock.epochMillis
    // Strict mode, the lock, focus, pauses and held changes as the boot clock has them, so changing the wall clock does not show them ending
    // early (or a pause lasting longer) before the repository catches up.
    val state = remember(modelState, clock) {
        fun held(key: String, stored: Long) = ClockAnchors.heldUntil(key, stored, modelState.anchors, clock)
        modelState.copy(strict = modelState.strict.shownAt(clock), lock = modelState.lock.shownAt(clock),
            rules = com.chronolock.data.ClockAnchors.shown(modelState.rules, modelState.anchors, clock),
            pending = modelState.pending.map { it.copy(applyAtEpochMillis = held(ClockAnchors.rule(it.packageName), it.applyAtEpochMillis)) },
            pendingGroups = modelState.pendingGroups.map { it.copy(applyAtEpochMillis = held(ClockAnchors.group(it.groupId), it.applyAtEpochMillis)) },
            pendingSites = modelState.pendingSites.map { it.copy(applyAtEpochMillis = held(ClockAnchors.site(it.domain), it.applyAtEpochMillis)) })
    }
    val focusActive = state.rules.any { rule -> rule.enabled && (rule.focusUntilEpochMillis ?: 0) > nowEpoch }
    // A one-second tick recomposes the whole screen; only the focus countdown needs it. Otherwise refresh the date each minute.
    LaunchedEffect(focusActive) { while (true) { today = LocalDate.now(); clock = model.clock(); delay(if (focusActive) 1_000 else 60_000) } }
    val snackbar = remember { SnackbarHostState() }
    // The theme on screen: the night one while the device is dark and the person asked to follow it.
    val shown = if (state.followSystem && androidx.compose.foundation.isSystemInDarkTheme()) state.nightTheme else state.theme
    val editing = if (state.followSystem && state.editingNight) state.nightTheme else state.theme
    androidx.compose.runtime.CompositionLocalProvider(LocalMascot provides state.mascot, LocalReduceMotion provides state.reduceMotion) {
    LocalizedApp(state.language) {
        val text = uiText()
        LaunchedEffect(state.error, state.language) { state.error?.let { snackbar.showSnackbar(text(it)); model.dismissError() } }
        LaunchedEffect(state.updateReady, state.language) {
            if (state.updateReady && snackbar.showSnackbar(text("새 버전을 내려받았습니다."), actionLabel = text("다시 시작"),
                    duration = SnackbarDuration.Indefinite) == SnackbarResult.ActionPerformed) installUpdate()
        }
        if (state.onboarded != true) {
            ChronoTheme(shown) {
                if (state.onboarded == false) OnboardingScreen(state.language, model::selectLanguage, model::finishOnboarding, state.mascot, { model.selectMascot(it) }, state.pro)
                else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
            return@LocalizedApp
        }
        ChronoTheme(shown) {
            Box {
            ChronoScreen(
                state = state, today = today, nowEpoch = nowEpoch, tab = tab,
                selectTab = { tab = it; if (it == 2) model.refreshHistory() }, snackbar = snackbar,
                edit = { app, recipe -> editPackage = app; suggestedRecipe = (recipe ?: pickRecipe?.let(RuleRecipe::valueOf))?.name; pickRecipe = null },
                pickRecipe = pickRecipe?.let(RuleRecipe::valueOf), choosePickRecipe = { pickRecipe = it?.name },
                startFocus = { confirmFocus = it }, stopFocus = model::stopFocus,
                usagePermission = { permissionDialog = false },
                accessibilityPermission = { permissionDialog = true },
                clear = { confirmClear = true },
                selectTheme = model::selectTheme,
                toggleRule = { rule, on -> model.save(rule.toDomain().copy(enabled = on), {}) },
                selectLanguage = model::selectLanguage, refreshHistory = model::refreshHistory,
                banner = state.ads.takeIf {
                    AdPolicy.showBanner(tab, it.configured, state.adFree || (com.chronolock.BuildConfig.PRO_GATING && state.billing.proAccess), it.canRequestAds,
                        focusActive = focusActive)
                }?.let { { BannerAd(model.bannerId) } },
                signIn = signIn, signOut = model::signOut, deleteAccount = { confirmDeleteAccount = true },
                privacyOptions = privacyOptions, manageSubscription = manageSubscription,
                openThemeGallery = { themeGallery = true },
                pause = model::pause, resume = model::resume, cancelPending = model::cancelPending,
                setStrict = model::setStrict, cancelStrict = model::cancelStrictChange,
                editGroup = { editGroupId = it }, cancelPendingGroup = model::cancelPendingGroup, selectMascot = { model.selectMascot(it) }, setReduceMotion = { model.setReduceMotion(it) },
                addSite = { input, added -> model.addSite(input, added) }, toggleSite = model::toggleSite,
                removeSite = model::removeSite, cancelPendingSite = model::cancelPendingSite, addSitePreset = model::addSites, dismissMonitorGap = { model.dismissMonitorGap() },
                setHistoryPin = model::setHistoryPin, deleteHistory = model::deleteHistory, changeHistoryPin = model::changeHistoryPin,
                lockFor = model::lockFor, startGoalLock = model::startGoalLock, endGoalLock = model::endGoalLock,
                collectPiece = model::collectPiece, changeGoalPin = model::changeGoalPin, buyPro = buyPro, managePro = managePro,
                setFollowSystem = model::setFollowSystem, editNightTheme = model::editNightTheme,
                exportBackup = { exportLauncher.launch("chronolock-backup-$today.json") },
                restoreBackup = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                startAutoBackup = { autoBackupLauncher.launch(null) }, stopAutoBackup = { model.stopAutoBackup() }, backupNow = { model.backupNow() },
                redeemCode = model::redeemCode,
            )
            if (themeGallery) ThemeGallery(editing, model::selectTheme, state.favoriteThemes, model::toggleFavoriteTheme) { themeGallery = false }
            }
            editPackage?.let { appId ->
                val saved = state.rules.firstOrNull { it.packageName == appId }
                val app = state.apps.firstOrNull { it.packageName == appId } ?: InstalledApp(appId, saved?.appName ?: appId)
                key(appId) { RuleEditor(app, saved, onDismiss = { editPackage = null },
                    onSave = { rule, finished -> model.save(rule, { editPackage = null }, finished) },
                    onDelete = { model.remove(appId) { editPackage = null } },
                    initialRecipe = suggestedRecipe?.let(RuleRecipe::valueOf), strictMinutes = state.strict.delayAt(nowEpoch)) }
            }
            editGroupId?.let { id ->
                val saved = state.groups.firstOrNull { it.id == id }
                key(id) { GroupEditor(saved, state.apps, state.groups.takenBy(id), state.strict.delayAt(nowEpoch), onDismiss = { editGroupId = null },
                    onSave = { group, finished -> model.saveGroup(group, { editGroupId = null }, finished) },
                    onDelete = { model.removeGroup(id) { editGroupId = null } }) }
            }
            confirmFocus?.let { minutes ->
                val targets = state.rules.filter { it.enabled }
                AlertDialog(onDismissRequest = { confirmFocus = null }, title = { Text(tr("%1\$d분 동안 집중할까요?", minutes)) },
                    text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(tr("지금 제한이 켜진 %1\$d개 앱을 집중 종료까지 차단합니다. 나중에 추가한 앱은 이번 집중에 포함되지 않습니다.", targets.size))
                        Spacer(Modifier.height(12.dp))
                        Text(targets.joinToString(" · ") { it.appName })
                        Spacer(Modifier.height(12.dp))
                        Text(tr("기존 규칙은 유지되며 언제든 집중을 종료할 수 있습니다."))
                    } },
                    confirmButton = { TextButton(enabled = targets.isNotEmpty() && !state.focusBusy,
                        onClick = { confirmFocus = null; model.startFocus(minutes) }) { Text(tr("집중 시작")) } },
                    dismissButton = { TextButton(onClick = { confirmFocus = null }) { Text(tr("취소")) } })
            }
            permissionDialog?.let { accessibility ->
                val askNotifications = accessibility && notificationsMissing()
                AlertDialog(onDismissRequest = { permissionDialog = null }, title = { Text(if (accessibility) tr("접근성 권한 안내") else tr("사용 정보 접근 안내")) },
                    text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(if (accessibility)
                        tr("ChronoLock은 앱 전환 이벤트로 실행 중인 앱을 확인하고, 직접 설정한 제한에 따라 다른 앱 위에 차단 화면을 표시합니다. 웹사이트 차단을 쓰면 지원 브라우저 주소창의 도메인을 확인하고, 주소가 보이지 않는 앱 내 브라우저(카카오톡·네이버)에서는 페이지 제목과 페이지 속 링크의 도메인으로 사이트를 판단합니다. 그 밖의 화면 텍스트, 비밀번호, 입력 내용은 읽지 않습니다. 선택한 앱의 패키지 이름, 차단할 사이트 목록과 사용·차단 기록은 이 기기에만 저장되며 전송되지 않고, 방문한 주소는 저장하지 않습니다. Android 설정에서 언제든 해제할 수 있습니다. 동의하면 다음 설정 화면에서 ChronoLock을 직접 활성화해 주세요.")
                    else tr("사용 정보 접근 권한으로 앱별 사용 시간과 실행 횟수를 확인합니다. 차단 규칙 없이도 히스토리에 기록을 모으며 최대 1년간 이 기기에만 저장합니다. 서버로 전송하지 않습니다. 기록만 보려면 접근성 권한은 필요하지 않습니다. Android 설정에서 언제든 권한을 해제할 수 있습니다."))
                        if (askNotifications) Text(tr("먼저 알림 허용을 물어봐요. 허용하면 쓰고 있는 앱의 남은 시간을 알림으로 보여 주고, 하루 한도가 5분 남으면 알려 드려요. 허용하지 않아도 차단은 그대로 동작합니다."))
                        if (accessibility) Text(tr("설정 화면이 열리면 ① '설치된 앱'(또는 '다운로드한 앱')에서 ChronoLock을 누르고 ② 'ChronoLock 사용'을 켠 뒤 ③ '허용'을 누르세요. 기기에 따라 항목 이름과 위치가 다를 수 있습니다."), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    } },
                    confirmButton = { TextButton(onClick = { model.acknowledge { permissionDialog = null
                        if (askNotifications) notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS) else openSettings(accessibility) } }) { Text(tr("동의하고 설정 열기")) } },
                    dismissButton = { TextButton(onClick = { permissionDialog = null }) { Text(tr("나중에")) } })
            }
            if (confirmDeleteAccount) AlertDialog(onDismissRequest = { confirmDeleteAccount = false }, title = { Text(tr("계정을 삭제할까요?")) },
                text = { Text(tr("서버의 계정 정보가 삭제되고 로그아웃됩니다. 이 기기의 제한 규칙과 사용 기록은 그대로 남습니다. 되돌릴 수 없습니다.")) },
                confirmButton = { TextButton(onClick = { model.deleteAccount(); confirmDeleteAccount = false }) { Text(tr("계정 삭제")) } },
                dismissButton = { TextButton(onClick = { confirmDeleteAccount = false }) { Text(tr("취소")) } })
            restoreFrom?.let { uri ->
                AlertDialog(onDismissRequest = { restoreFrom = null }, title = { Text(tr("백업으로 바꿀까요?")) },
                    text = { Text(tr("지금 있는 제한 규칙과 앱 그룹이 모두 백업 내용으로 바뀝니다. 사용 기록은 그대로 남습니다.")) },
                    confirmButton = { TextButton(onClick = { model.restoreBackup(uri); restoreFrom = null }) { Text(tr("복원")) } },
                    dismissButton = { TextButton(onClick = { restoreFrom = null }) { Text(tr("취소")) } })
            }
            if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text(tr("모든 규칙을 초기화할까요?")) },
                text = { Text(tr("앱 제한, 앱 그룹, 웹사이트 차단이 모두 지워집니다. 사용 기록은 그대로 남습니다.")) },
                confirmButton = { TextButton(onClick = { model.resetRules(); confirmClear = false }, modifier = Modifier.testTag("rules-reset-confirm")) { Text(tr("초기화")) } },
                dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(tr("취소")) } })
        }
    }
    }
}

@Composable internal fun CardBox(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
        .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .14f), RoundedCornerShape(20.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
@Composable internal fun Metric(label: String, value: String) {
    Column { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall); Text(value, style = MaterialTheme.typography.headlineSmall) }
}
@Composable internal fun AppHeading(name: String, packageName: String, edit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(packageName, name, 42.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(packageName, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = edit) { Text(tr("설정")) }
    }
}
@Composable internal fun PermissionCard(state: MainState, usage: () -> Unit, accessibility: () -> Unit) {
    CardBox {
        val ready = state.permissions.usage && state.permissions.accessibility && state.health.connected
        Text(if (ready) tr("앱 제한 감지 중") else tr("앱 제한을 위한 준비"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(tr("사용 정보: %1\$s · 접근성: %2\$s", if (state.permissions.usage) tr("허용됨") else tr("필요"), if (state.permissions.accessibility) tr("허용됨") else tr("필요")), color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.health.error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
        if (state.permissions.accessibility && !state.health.connected) Text(tr("서비스 연결을 확인해 주세요. 현재 차단 동작을 보장할 수 없습니다."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = usage, modifier = Modifier.fillMaxWidth()) { Text(tr("사용 정보 권한")) }
            OutlinedButton(onClick = accessibility, modifier = Modifier.fillMaxWidth()) { Text(tr("접근성 권한")) }
            if (!state.permissions.accessibility) {
                Text(tr("접근성 설정 → 설치된 앱 → ChronoLock → 사용 켜기 → 허용"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(tr("스위치가 회색이라 켜지지 않으면: 앱 정보 → 오른쪽 위 ⋮ → '제한된 설정 허용' 후 다시 켜 주세요."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            NotificationPermissionButton()
        }
    }
}

/** Detection the user turned off, shown once on Today so a gap in limits is never silent. */
@Composable internal fun MonitorGapCard(gap: com.chronolock.data.MonitorGap, dismiss: () -> Unit) {
    CardBox {
        Text(tr("앱 감지가 꺼진 적이 있어요"), Modifier.testTag("monitor-gap"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        Text(gap.backAt?.let { tr("%1\$s부터 %2\$s까지 앱 제한이 작동하지 않았어요.", timeText(gap.offAt), timeText(it)) }
            ?: tr("%1\$s부터 앱 제한이 작동하지 않고 있어요.", timeText(gap.offAt)), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (gap.locked) Text(tr("잠금이나 엄격 모드가 켜져 있을 때 꺼졌어요."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = dismiss) { Text(tr("확인했어요")) }
    }
}

/** True on Android 13+ while the notification permission is not granted; older versions grant it implicitly. */
@Composable private fun notificationsMissing(): Boolean {
    val context = LocalContext.current
    return Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
}

/**
 * Asks for the notification permission; hidden once granted. After a refusal Android may stop showing its prompt,
 * so the button then opens the app's notification settings instead.
 */
@Composable internal fun NotificationPermissionButton(hint: Boolean = true) {
    if (!notificationsMissing()) return
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(false) }
    var refused by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it; refused = !it }
    if (allowed) return
    OutlinedButton(onClick = {
        if (refused) runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        else request.launch(Manifest.permission.POST_NOTIFICATIONS)
    }, modifier = Modifier.fillMaxWidth()) { Text(tr("알림 권한")) }
    if (hint) Text(tr("알림을 켜면 쓰고 있는 앱의 남은 시간을 보여 주고, 하루 한도가 5분 남으면 알려 드려요."),
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    if (refused) Text(tr("허용 창이 뜨지 않으면 이 버튼으로 알림 설정을 열어 ChronoLock 알림을 켜 주세요."),
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

private val RuleDraftSaver = listSaver<RuleDraft, Any>(
    save = { listOf(it.daily, it.opens, it.cooldown, it.session, it.scheduleEnabled, it.enabled, it.pauseSeconds, it.checkInMinutes) +
        it.schedules.flatMap { window -> listOf(window.start, window.end, window.daysMask) } },
    restore = { RuleDraft(it[0] as String, it[1] as String, it[2] as String, it[3] as String, it[4] as Boolean,
        it.drop(8).chunked(3).map { (start, end, mask) -> ScheduleDraft(start as String, end as String, mask as Int) }, it[5] as Boolean,
        it[6] as Int, it[7] as Int) },
)

internal val DailyTimeInputSaver = listSaver<DailyTimeInput, String>(
    save = { listOf(it.hours, it.minutes, it.seconds) },
    restore = { DailyTimeInput(it[0], it[1], it[2]) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuleEditor(app: InstalledApp, saved: RuleEntity?, onDismiss: () -> Unit,
    onSave: (AppRule, () -> Unit) -> Unit, onDelete: () -> Unit, initialRecipe: RuleRecipe? = null, strictMinutes: Int = 0) {
    var draft by rememberSaveable(stateSaver = RuleDraftSaver) { mutableStateOf(when {
        saved != null && initialRecipe != null -> suggestedDraft(saved.toDomain(), initialRecipe)
        initialRecipe != null -> initialRecipe.draft // Suggested from history for an app that has no rule yet.
        else -> saved?.toDomain()?.let(RuleDraft::from) ?: RuleDraft()
    }) }
    var dailyTime by rememberSaveable(stateSaver = DailyTimeInputSaver) {
        mutableStateOf(DailyTimeInput.fromSeconds(draft.daily))
    }
    var sessionTime by rememberSaveable(stateSaver = DailyTimeInputSaver) {
        mutableStateOf(DailyTimeInput.fromSeconds(draft.session))
    }
    var cooldownTime by rememberSaveable(stateSaver = DailyTimeInputSaver) {
        mutableStateOf(DailyTimeInput.fromSeconds(draft.cooldown))
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val editorScroll = rememberScrollState()
    LaunchedEffect(error) { if (error != null) editorScroll.animateScrollTo(editorScroll.maxValue) }
    fun submit() {
        try {
            val rule = draft.copy(daily = dailyTime.toSecondsText(), session = sessionTime.toSecondsText("1회 허용 시간"),
                cooldown = cooldownTime.toSecondsText("재실행 대기 시간")).build(app.packageName, app.label)
            error = null
            saving = true
            onSave(rule) { saving = false }
        } catch (invalid: IllegalArgumentException) {
            error = invalid.message
            saving = false
        }
    }
    // A full-height sheet leaves room for the long form; it cannot be swiped away while a save is in flight.
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !saving })
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = sheet) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(app.packageName, app.label, 36.dp)
            Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(enabled = !saving, onClick = onDismiss) { Text(tr("취소")) }
            Button(enabled = !saving, onClick = ::submit) { Text(if (saving) tr("저장 중") else tr("저장")) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .15f))
        Column(Modifier.verticalScroll(editorScroll).padding(horizontal = 24.dp, vertical = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().toggleable(value = draft.enabled, enabled = !saving, role = Role.Switch) { draft = draft.copy(enabled = it) },
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("제한 사용"))
                Switch(checked = draft.enabled, onCheckedChange = null, enabled = !saving)
            }
            if (strictMinutes > 0) Text(tr("엄격 모드: 제한을 끄거나 느슨하게 바꾸면 %1\$s 뒤에 적용됩니다.", waitText(strictMinutes)),
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            Text(tr("집중 레시피"), fontWeight = FontWeight.Bold)
            if (initialRecipe != null) Text(tr("기록 기반 제안이 준비됐습니다. 기존 제한은 유지하고 비어 있는 항목만 채웠습니다. 확인 후 저장하세요."), style = MaterialTheme.typography.bodySmall)
            Text(tr("레시피는 아래 입력값을 바꿉니다. 저장 전까지 적용되지 않습니다."), style = MaterialTheme.typography.bodySmall)
            RuleRecipe.values().forEach { recipe ->
                OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !saving,
                    onClick = {
                        draft = recipe.draft.copy(enabled = draft.enabled)
                        dailyTime = DailyTimeInput.fromSeconds(draft.daily)
                        sessionTime = DailyTimeInput.fromSeconds(draft.session)
                        cooldownTime = DailyTimeInput.fromSeconds(draft.cooldown)
                        error = null
                    }) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(tr(recipe.title), fontWeight = FontWeight.Bold)
                        Text(tr(recipe.description), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            HorizontalDivider()
            Text(tr("빈 항목은 제한하지 않습니다. 여러 항목을 설정하면 함께 적용됩니다."))
            TimeFields("하루 허용 시간", "daily", dailyTime, !saving,
                hint = "최대 24시간 · 분/초는 0~59\n세 칸을 모두 비우면 하루 시간 제한이 해제됩니다.") { dailyTime = it; error = null }
            TimeFields("1회 허용 시간", "session", sessionTime, !saving, presetMinutes = listOf(5, 10, 30)) { sessionTime = it; error = null }
            NumberField(tr("하루 허용 실행 횟수"), draft.opens, !saving) { draft = draft.copy(opens = it) }
            TimeFields("재실행 대기 시간", "cooldown", cooldownTime, !saving, presetMinutes = listOf(5, 10, 30)) { cooldownTime = it; error = null }
            // Gentle friction: a short pause before opening, and a check-in while the app stays open.
            Text(tr("열기 전 멈춤"), style = MaterialTheme.typography.titleSmall)
            Text(tr("열 때마다 숨 한 번 쉬는 화면을 보여 줘요. 그날 여러 번 열수록 조금씩 길어져요."),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 3, 5, 10).forEach { seconds ->
                    FilterChip(selected = draft.pauseSeconds == seconds, onClick = { draft = draft.copy(pauseSeconds = seconds); error = null },
                        enabled = !saving, label = { Text(if (seconds == 0) tr("끄기") else tr("%1\$d초", seconds)) }, modifier = Modifier.testTag("pause-$seconds"))
                }
            }
            Text(tr("사용 중 확인"), style = MaterialTheme.typography.titleSmall)
            Text(tr("계속 쓰고 있으면 정한 시간마다 계속할지 물어봐요."),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 5, 10, 20).forEach { minutes ->
                    FilterChip(selected = draft.checkInMinutes == minutes, onClick = { draft = draft.copy(checkInMinutes = minutes); error = null },
                        enabled = !saving, label = { Text(if (minutes == 0) tr("끄기") else tr("%1\$d분", minutes)) }, modifier = Modifier.testTag("checkin-$minutes"))
                }
            }
            Text(tr("1회 한도 도달 시 앱을 떠나야 다음 사용을 시작합니다. 대기를 함께 설정하면 바로 재실행하는 것도 줄일 수 있습니다."), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().toggleable(value = draft.scheduleEnabled, enabled = !saving, role = Role.Switch,
                onValueChange = { draft = draft.copy(scheduleEnabled = it) }),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("요일·시간 예약"))
                Switch(checked = draft.scheduleEnabled, onCheckedChange = null, enabled = !saving)
            }
            if (draft.scheduleEnabled) {
                Text(tr("선택한 요일에 시작하는 차단 시간입니다. 종료가 시작보다 이르면 다음 날까지 이어집니다."), style = MaterialTheme.typography.bodySmall)
                draft.schedules.forEachIndexed { index, window ->
                    ScheduleFields(index, window, removable = draft.schedules.size > 1, enabled = !saving,
                        change = { next -> draft = draft.copy(schedules = draft.schedules.toMutableList().also { it[index] = next }); error = null },
                        remove = { draft = draft.copy(schedules = draft.schedules.filterIndexed { at, _ -> at != index }) })
                }
                if (draft.schedules.size < MAX_SCHEDULES) OutlinedButton(onClick = { draft = draft.copy(schedules = draft.schedules + ScheduleDraft()) },
                    enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("schedule-add")) { Text(tr("예약 구간 추가")) }
            }
            error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
            if (saved != null) TextButton(onClick = onDelete, enabled = !saving) {
                Text(tr("이 앱의 제한 삭제"), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
/** One schedule window: quick day sets, single days and the block's start and end. */
@Composable
internal fun ScheduleFields(index: Int, value: ScheduleDraft, removable: Boolean, enabled: Boolean,
    change: (ScheduleDraft) -> Unit, remove: () -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .15f))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("예약 %1\$d", index + 1), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        if (removable) TextButton(onClick = remove, enabled = enabled) { Text(tr("구간 삭제")) }
    }
    Text(if (value.daysMask == 0) tr("예약할 요일을 선택해 주세요.") else tr("선택한 요일에만 예약이 적용됩니다."),
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("매일" to ScheduleDraft.EVERY_DAY, "평일" to ScheduleDraft.WEEKDAYS, "주말" to ScheduleDraft.WEEKEND).forEach { (label, mask) ->
            FilterChip(selected = value.daysMask == mask, onClick = { change(value.copy(daysMask = mask)) }, label = { Text(tr(label)) },
                enabled = enabled, modifier = Modifier.testTag("schedule-$index-$mask"))
        }
    }
    listOf(tr("월"), tr("화"), tr("수"), tr("목"), tr("금"), tr("토"), tr("일")).chunked(4).forEachIndexed { row, days ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEachIndexed { column, label ->
                val bit = 1 shl (row * 4 + column)
                FilterChip(selected = value.daysMask and bit != 0,
                    onClick = { change(value.copy(daysMask = value.daysMask xor bit)) }, label = { Text(label) }, enabled = enabled)
            }
        }
    }
    OutlinedTextField(value = value.start, onValueChange = { if (it.length <= 5) change(value.copy(start = it)) },
        label = { Text(tr("차단 시작 (HH:mm)")) }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = value.end, onValueChange = { if (it.length <= 5) change(value.copy(end = it)) },
        label = { Text(tr("차단 종료 (HH:mm)")) }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
}

/** Hours, minutes and seconds for one limit; [tag] prefixes each field's test tag, e.g. "daily-hours". */
@Composable
internal fun TimeFields(title: String, tag: String, value: DailyTimeInput, enabled: Boolean,
    hint: String = "최대 24시간 · 분/초는 0~59\n세 칸을 모두 비우면 이 제한을 사용하지 않습니다.",
    presetMinutes: List<Int> = emptyList(), change: (DailyTimeInput) -> Unit) {
    Text(tr(title), fontWeight = FontWeight.SemiBold)
    if (presetMinutes.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        presetMinutes.forEach { minutes ->
            FilterChip(selected = value.toSecondsTextOrNull() == (minutes * 60).toString(), enabled = enabled,
                onClick = { change(DailyTimeInput.fromSeconds((minutes * 60).toString())) },
                label = { Text(tr("%1\$d분", minutes)) }, modifier = Modifier.testTag("$tag-preset-$minutes"))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(tr("시") to value.hours, tr("분") to value.minutes, tr("초") to value.seconds).forEachIndexed { index, (label, text) ->
            OutlinedTextField(value = text, onValueChange = { input ->
                if (input.length <= 2 && input.all { it in '0'..'9' }) change(when (index) {
                    0 -> value.copy(hours = input)
                    1 -> value.copy(minutes = input)
                    else -> value.copy(seconds = input)
                })
            }, label = { Text(label) }, placeholder = { Text("0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).testTag("$tag-" + listOf("hours", "minutes", "seconds")[index]),
                enabled = enabled, singleLine = true)
        }
    }
    Text(tr(hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}
private fun DailyTimeInput.toSecondsTextOrNull() = runCatching { toSecondsText() }.getOrNull()
@Composable internal fun NumberField(label: String, value: String, enabled: Boolean = true, changed: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = { if (it.length <= 10) changed(it) }, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
}
@Composable internal fun duration(seconds: Long): String {
    val text = uiText()
    return formatDuration(seconds, text)
}
internal fun formatDuration(seconds: Long, text: UiText): String = when {
    seconds >= 3600 -> text("%1\$d시간 %2\$d분", seconds / 3600, seconds % 3600 / 60) +
        if (seconds % 60 != 0L) " " + text("%1\$d초", seconds % 60) else ""
    seconds >= 60 -> text("%1\$d분 %2\$d초", seconds / 60, seconds % 60)
    else -> text("%1\$d초", seconds)
}
@Composable internal fun ruleSummary(rule: RuleEntity): String {
    val text = uiText()
    val labels = listOf("월", "화", "수", "목", "금", "토", "일").map { text(it) }
    fun days(days: Set<java.time.DayOfWeek>) = when (days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }) {
        ScheduleDraft.EVERY_DAY -> text("매일")
        ScheduleDraft.WEEKDAYS -> text("평일")
        ScheduleDraft.WEEKEND -> text("주말")
        else -> days.sortedBy { it.value }.joinToString(" ") { labels[it.value - 1] }
    }
    return buildList {
        if (!rule.enabled) add(text("일시 중지"))
        rule.dailyLimitSeconds?.let { add(text("하루 %1\$s", formatDuration(it, text))) }
        rule.sessionLimitSeconds?.let { add(text("한 번에 %1\$s", formatDuration(it, text))) }
        rule.maxOpenCount?.let { add(text("%1\$d회 실행", it)) }
        rule.cooldownSeconds?.let { add(text("%1\$s 대기", formatDuration(it, text))) }
        rule.pauseSeconds?.let { add(text("열기 전 %1\$d초 멈춤", it)) }
        rule.checkInMinutes?.let { add(text("%1\$d분마다 확인", it)) }
        rule.toDomain().schedules.forEach { schedule ->
            add(text("%1\$s %2\$s~%3\$s 차단", days(schedule.days), schedule.start.toString(),
                (if (schedule.start > schedule.end) text("다음 날 ") else "") + schedule.end.toString()))
        }
    }.joinToString(" · ")
}

/** Free-form focus length; the confirmation dialog that follows still applies. */
@Composable
private fun CustomFocusDialog(onDismiss: () -> Unit, start: (Int) -> Unit) {
    var minutes by rememberSaveable { mutableStateOf("") }
    val value = minutes.toIntOrNull()?.takeIf { it in 1..180 }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("몇 분 동안 집중할까요?")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(tr("집중 시간(분, 1~180)"), minutes) { input -> if (input.length <= 3 && input.all { it in '0'..'9' }) minutes = input }
            if (minutes.isNotEmpty() && value == null) Text(tr("집중 시간은 1~180분으로 설정해 주세요."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = value != null, onClick = { value?.let(start) }) { Text(tr("다음")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("취소")) } })
}

@Composable
internal fun QuickFocusCard(state: MainState, nowEpoch: Long, start: (Int) -> Unit, stop: () -> Unit) {
    val enabled = state.rules.filter { it.enabled }
    val active = enabled.filter { (it.focusUntilEpochMillis ?: 0) > nowEpoch }
    CardBox {
        MascotTitle(tr("지금부터 집중"), if (active.isNotEmpty()) com.chronolock.data.Pose.FOCUS else com.chronolock.data.Pose.TIMER)
        if (active.isNotEmpty()) {
            val remaining = ((active.maxOf { it.focusUntilEpochMillis!! } - nowEpoch + 999) / 1000).coerceAtLeast(0)
            Text(duration(remaining), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineLarge)
            val monitoring = state.permissions.usage && state.permissions.accessibility && state.health.connected
            Text(tr("%1\$d개 앱 · %2\$s · %3\$s", active.size, if (monitoring) tr("차단 중") else tr("제한 예정 · 감지 연결을 확인하세요"), active.joinToString(" · ") { it.appName }), color = MaterialTheme.colorScheme.onSurfaceVariant)
            val locked = state.strict.delayAt(nowEpoch) > 0 || state.lock.lockedAt(nowEpoch)
            OutlinedButton(onClick = stop, enabled = !state.focusBusy && !locked) { Text(tr("집중 종료")) }
            if (locked) Text(tr("엄격 모드에서는 집중을 중간에 끝낼 수 없습니다."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text(tr("집중이 끝나도 기존 시간·횟수·예약 제한은 유지됩니다."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(if (enabled.isEmpty()) tr("앱 제한 탭에서 집중할 때 차단할 앱을 먼저 설정하세요.")
                else tr("제한이 켜진 %1\$d개 앱을 잠시 쉬고, 지금 할 일에 집중하세요.", enabled.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
            val canStart = enabled.isNotEmpty() && !state.focusBusy && state.permissions.usage &&
                state.permissions.accessibility && state.health.connected
            var askMinutes by rememberSaveable { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 50, 90).forEach { minutes ->
                    FilledTonalButton(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                        enabled = canStart, onClick = { start(minutes) }) { Text(tr("%1\$d분", minutes)) }
                }
                OutlinedButton(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                    enabled = canStart, onClick = { askMinutes = true }) { Text(tr("직접")) }
            }
            if (askMinutes) CustomFocusDialog(onDismiss = { askMinutes = false }) { minutes -> askMinutes = false; start(minutes) }
            if (enabled.isNotEmpty() && (!state.permissions.usage || !state.permissions.accessibility || !state.health.connected)) {
                Text(tr("권한을 켜고 감지 연결을 확인하면 시작할 수 있습니다."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One dot per day: filled when limits held, error-tinted when one was reached, hollow when nothing was recorded. */
@Composable
private fun WeekDots(days: List<Pair<LocalDate, DayResult>>) {
    val kept = days.count { it.second == DayResult.KEPT }
    val summary = tr("최근 7일 중 %1\$d일 한도를 지켰어요", kept)
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = summary }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { (date, result) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val color = when (result) {
                        DayResult.KEPT -> MaterialTheme.colorScheme.primary
                        DayResult.REACHED -> MaterialTheme.colorScheme.error
                        DayResult.NO_RECORD -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .35f)
                    }
                    Box(Modifier.size(14.dp).then(if (result == DayResult.NO_RECORD) Modifier.border(1.5.dp, color, CircleShape)
                        else Modifier.background(color, CircleShape)))
                    Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, uiText().locale),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
internal fun WeeklyInsightsCard(state: MainState, today: LocalDate, review: (String, RuleRecipe) -> Unit) {
    val firstDay = today.minusDays(6).toString()
    val records = state.daily.filter { it.date >= firstDay && it.date <= today.toString() }
    val totals = records.groupBy { it.date }.mapValues { (_, rows) -> rows.sumOf { it.usageMillis } }
    val maximum = (totals.values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val suggestion = remember(state.daily, state.rules, today) {
        suggestUsageRule(records.associate { DailyKey(it.packageName, LocalDate.parse(it.date)) to DailyUsage(it.usageMillis, it.opens, it.blockedAttempts) },
            state.rulesWithGroups, today)
    }
    CardBox {
        Text(tr("최근 7일 돌아보기"), style = MaterialTheme.typography.titleLarge)
        WeekDots(dayResults(records.associate { DailyKey(it.packageName, LocalDate.parse(it.date)) to DailyUsage(it.usageMillis, it.opens, it.blockedAttempts) },
            state.rulesWithGroups, today))
        // Streaks and the week-over-week change look further back than the seven bars, over everything still stored.
        val (streak, weeks) = remember(state.daily, state.rules, state.groups, today) {
            val all = state.daily.associate { DailyKey(it.packageName, LocalDate.parse(it.date)) to DailyUsage(it.usageMillis, it.opens, it.blockedAttempts) }
            keptStreak(all, state.rulesWithGroups, today) to compareWeeks(all, today)
        }
        if (streak > 0) Text(tr("%1\$d일 연속 한도를 지켰어요", streak), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        // A few kept days in a row is a good moment to ask for a Play review; the prompt itself throttles and Play may skip it.
        val askReview = LocalAskReview.current
        LaunchedEffect(streak >= REVIEW_STREAK_DAYS) { if (streak >= REVIEW_STREAK_DAYS) askReview() }
        if (streak > 0 || (weeks.changePercent ?: 0) < 0) {
            val context = LocalContext.current
            val message = tr("ChronoLock으로 스마트폰 사용 시간을 줄이고 있어요. 같이 해 볼래요?") + "\n" + SHARE_LINK
            TextButton(onClick = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, message)
                runCatching { context.startActivity(android.content.Intent.createChooser(send, null)) }
            }, modifier = Modifier.testTag("share-app")) { Text(tr("친구에게 추천하기")) }
        }
        weeks.changePercent?.let { change ->
            Text(when {
                change < 0 -> tr("지난주보다 %1\$d%% 덜 썼어요", -change)
                change > 0 -> tr("지난주보다 %1\$d%% 더 썼어요", change)
                else -> tr("지난주와 비슷하게 썼어요")
            } + " · " + tr("지난주 %1\$s", duration(weeks.previousMillis / 1000)), style = MaterialTheme.typography.bodyMedium,
                color = if (change <= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(tr("기록된 사용 %1\$s · %2\$d회 실행", duration(records.sumOf { it.usageMillis } / 1000), records.sumOf { it.opens }), color = MaterialTheme.colorScheme.primary)
        (6L downTo 0L).forEach { offset ->
            val date = today.minusDays(offset)
            val total = totals[date.toString()]
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(dateText(date), modifier = Modifier.width(40.dp), style = MaterialTheme.typography.bodySmall)
                // Today's bar carries the accent; earlier days recede so the comparison reads at a glance.
                val bar = if (offset == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                LinearProgressIndicator(progress = { ((total ?: 0L).toFloat() / maximum).coerceIn(0f, 1f) }, modifier = Modifier.weight(1f),
                    color = bar, trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .12f))
                Text(total?.let { duration(it / 1000) } ?: tr("기록 없음"), modifier = Modifier.width(100.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (suggestion != null) {
            HorizontalDivider()
            Text("${suggestion.rule.appName} · ${tr(suggestion.recipe.title)}", fontWeight = FontWeight.Bold)
            Text(tr("%1\$d일 기록에서 %2\$d회 실행, 실행당 기록 사용시간은 평균 %3\$s입니다.", suggestion.recordedDays, suggestion.opens, duration(suggestion.usageMillis / suggestion.opens / 1000)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { review(suggestion.rule.packageName, suggestion.recipe) }) { Text(tr("제안 살펴보기")) }
        } else {
            Text(tr("3일 이상 기록을 모으면 사용 패턴에 맞는 제한을 제안합니다. 이미 설정한 제한도 고려합니다."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Text(tr("선택한 앱의 기기 내 기록만 사용합니다. 감지 중단 중 기록은 빠질 수 있으며, 제안은 저장해야 적용됩니다."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable internal fun timeText(epochMillis: Long) =
    dateTimeText(java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())

/** Pause state and any change strict mode is holding back for one rule, with the actions that only tighten or pause. */
@Composable
internal fun RuleStatus(rule: RuleEntity, held: PendingRuleEntity?, nowEpoch: Long, strictMinutes: Int,
    pause: (Int?) -> Unit, resume: () -> Unit, cancelHeld: () -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val pausedUntil = rule.pausedUntilEpochMillis?.takeIf { rule.enabled && it > nowEpoch }
    if (pausedUntil != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("%1\$s까지 쉬는 중", timeText(pausedUntil)), Modifier.weight(1f), color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = resume) { Text(tr("다시 켜기")) }
    } else if (rule.enabled && held == null) {
        TextButton(onClick = { choosing = true }, modifier = Modifier.testTag("pause-${rule.packageName}")) { Text(tr("잠시 쉬기")) }
    }
    held?.let { change ->
        val what = when {
            change.remove -> tr("제한 삭제")
            !change.rule.enabled -> tr("제한 끄기")
            (change.rule.pausedUntilEpochMillis ?: 0) > (rule.pausedUntilEpochMillis ?: 0) -> tr("잠시 쉬기")
            else -> tr("설정 변경")
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("엄격 모드: %1\$s에 %2\$s 적용 예정", timeText(change.applyAtEpochMillis), what), Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = cancelHeld) { Text(tr("취소")) }
        }
    }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false }, title = { Text(tr("얼마 동안 쉴까요?")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("선택한 시간이 지나면 제한이 자동으로 다시 켜집니다."), style = MaterialTheme.typography.bodySmall)
            if (strictMinutes > 0) Text(tr("엄격 모드라 %1\$s 뒤부터 쉬기가 시작됩니다.", waitText(strictMinutes)),
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            listOf<Pair<Int?, String>>(10 to tr("%1\$d분", 10), 30 to tr("%1\$d분", 30), 60 to tr("1시간"), null to tr("오늘만")).forEach { (minutes, label) ->
                OutlinedButton(onClick = { choosing = false; pause(minutes) }, modifier = Modifier.fillMaxWidth()
                    .testTag("pause-option-${minutes ?: "today"}")) { Text(label) }
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = { choosing = false }) { Text(tr("취소")) } })
}

/** A strict-mode wait in whole hours when it is one (1 h, 24 h), otherwise in minutes. */
@Composable internal fun waitText(minutes: Int): String =
    if (minutes >= 60 && minutes % 60 == 0) tr("%1\$d시간", minutes / 60) else tr("%1\$d분", minutes)

@Composable
internal fun StrictModeCard(state: MainState, nowEpoch: Long, select: (Int) -> Unit, cancel: () -> Unit) {
    val current = state.strict.delayAt(nowEpoch)
    CardBox {
        Text(tr("엄격 모드"), style = MaterialTheme.typography.titleLarge)
        Text(tr("제한을 끄거나 느슨하게 바꾸면 정한 시간이 지난 뒤에 적용됩니다. 더 엄격하게 바꾸는 것은 바로 적용되고, 집중 중에는 집중을 끝낼 수 없습니다."),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Six choices do not fit one row on a phone; they wrap.
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            com.chronolock.domain.Strictness.DELAYS.forEach { minutes ->
                val locked = !state.pro && minutes > com.chronolock.domain.FreeTier.STRICT_MINUTES
                FilterChip(selected = current == minutes, onClick = { select(minutes) }, modifier = Modifier.testTag("strict-$minutes"),
                    label = { Text(if (minutes == 0) tr("끄기") else waitText(minutes) + if (locked) " · PRO" else "") })
            }
        }
        state.strict.waitingAt(nowEpoch)?.let { minutes ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (minutes == 0) tr("%1\$s에 엄격 모드가 꺼집니다.", timeText(state.strict.pendingAtEpochMillis!!))
                    else tr("%1\$s에 대기 시간이 %2\$s(으)로 바뀝니다.", timeText(state.strict.pendingAtEpochMillis!!), waitText(minutes)),
                    Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = cancel) { Text(tr("취소")) }
            }
        }
        Text(tr("Android 설정에서 접근성 권한을 끄거나 앱을 삭제하는 것은 막을 수 없습니다."),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

/** Asks for a Play review; provided by the activity, a no-op in previews and tests. */
internal val LocalAskReview = androidx.compose.runtime.staticCompositionLocalOf<() -> Unit> { {} }
private const val REVIEW_STREAK_DAYS = 3
/** Store link with a source tag, so installs from shares show up separately in Play Console acquisition reports. */
private const val SHARE_LINK = "https://play.google.com/store/apps/details?id=com.chronolock.app&referrer=utm_source%3Dshare%26utm_medium%3Dapp"
