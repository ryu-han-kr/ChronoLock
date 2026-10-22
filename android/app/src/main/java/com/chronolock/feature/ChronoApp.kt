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
import com.chronolock.data.RuleEntity
import com.chronolock.data.toDomain
import com.chronolock.domain.AppRule
import com.chronolock.domain.RuleDraft
import com.chronolock.ads.BannerAd
import com.chronolock.domain.AdPolicy
import com.chronolock.domain.DailyTimeInput
import com.chronolock.domain.RuleRecipe
import com.chronolock.domain.DailyKey
import com.chronolock.domain.DailyUsage
import com.chronolock.domain.DayResult
import com.chronolock.domain.dayResults
import com.chronolock.domain.suggestUsageRule
import com.chronolock.domain.suggestedDraft
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun ChronoApp(model: MainViewModel, openSettings: (Boolean) -> Unit, signIn: () -> Unit = {}, privacyOptions: () -> Unit = {},
    buyAdFree: () -> Unit = {}, manageSubscription: () -> Unit = {}, retryBilling: () -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var suggestedRecipe by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionDialog by remember { mutableStateOf<Boolean?>(null) }
    var themeGallery by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDeleteAccount by remember { mutableStateOf(false) }
    var confirmFocus by rememberSaveable { mutableStateOf<Int?>(null) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var nowEpoch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { today = LocalDate.now(); nowEpoch = System.currentTimeMillis(); delay(1000) } }
    val snackbar = remember { SnackbarHostState() }
    LocalizedApp(state.language) {
        val text = uiText()
        LaunchedEffect(state.error, state.language) { state.error?.let { snackbar.showSnackbar(text(it)); model.dismissError() } }
        if (state.onboarded != true) {
            ChronoTheme(state.theme) {
                if (state.onboarded == false) OnboardingScreen(state.language, model::selectLanguage, model::finishOnboarding)
                else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
            return@LocalizedApp
        }
        ChronoTheme(state.theme) {
            Box {
            ChronoScreen(
                state = state, today = today, nowEpoch = nowEpoch, tab = tab,
                selectTab = { tab = it; if (it == 2) model.refreshHistory() }, snackbar = snackbar,
                edit = { app, recipe -> editPackage = app; suggestedRecipe = recipe?.name },
                startFocus = { confirmFocus = it }, stopFocus = model::stopFocus,
                usagePermission = { permissionDialog = false },
                accessibilityPermission = { permissionDialog = true },
                clear = { confirmClear = true },
                selectTheme = model::selectTheme,
                toggleRule = { rule, on -> model.save(rule.toDomain().copy(enabled = on), {}) },
                selectLanguage = model::selectLanguage, refreshHistory = model::refreshHistory,
                banner = state.ads.takeIf {
                    AdPolicy.showBanner(tab, it.configured, state.adFree, it.canRequestAds,
                        focusActive = state.rules.any { rule -> rule.enabled && (rule.focusUntilEpochMillis ?: 0) > nowEpoch })
                }?.let { { BannerAd(model.bannerId) } },
                signIn = signIn, signOut = model::signOut, deleteAccount = { confirmDeleteAccount = true },
                privacyOptions = privacyOptions, buyAdFree = buyAdFree, manageSubscription = manageSubscription, retryBilling = retryBilling,
                openThemeGallery = { themeGallery = true },
            )
            if (themeGallery) ThemeGallery(state.theme, model::selectTheme) { themeGallery = false }
            }
            editPackage?.let { appId ->
                val saved = state.rules.firstOrNull { it.packageName == appId }
                val app = state.apps.firstOrNull { it.packageName == appId } ?: InstalledApp(appId, saved?.appName ?: appId)
                key(appId) { RuleEditor(app, saved, onDismiss = { editPackage = null },
                    onSave = { rule, finished -> model.save(rule, { editPackage = null }, finished) },
                    onDelete = { model.remove(appId) { editPackage = null } },
                    initialRecipe = suggestedRecipe?.let(RuleRecipe::valueOf)) }
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
                AlertDialog(onDismissRequest = { permissionDialog = null }, title = { Text(if (accessibility) tr("접근성 권한 안내") else tr("사용 정보 접근 안내")) },
                    text = { Text(if (accessibility)
                        tr("ChronoLock은 앱 전환 이벤트로 실행 중인 앱을 확인하고, 직접 설정한 제한에 따라 다른 앱 위에 차단 화면을 표시합니다. 화면 텍스트, 비밀번호, 입력 내용은 읽지 않습니다. 선택한 앱의 패키지 이름과 사용·차단 기록은 이 기기에만 저장되며 전송되지 않습니다. Android 설정에서 언제든 해제할 수 있습니다. 동의하면 다음 설정 화면에서 ChronoLock을 직접 활성화해 주세요.")
                    else tr("사용 정보 접근 권한으로 앱별 사용 시간과 실행 횟수를 확인합니다. 차단 규칙 없이도 히스토리에 기록을 모으며 최대 30일간 이 기기에만 저장합니다. 서버로 전송하지 않습니다. 기록만 보려면 접근성 권한은 필요하지 않습니다. Android 설정에서 언제든 권한을 해제할 수 있습니다.")) },
                    confirmButton = { TextButton(onClick = { model.acknowledge { permissionDialog = null; openSettings(accessibility) } }) { Text(tr("동의하고 설정 열기")) } },
                    dismissButton = { TextButton(onClick = { permissionDialog = null }) { Text(tr("나중에")) } })
            }
            if (confirmDeleteAccount) AlertDialog(onDismissRequest = { confirmDeleteAccount = false }, title = { Text(tr("계정을 삭제할까요?")) },
                text = { Text(tr("서버의 계정 정보가 삭제되고 로그아웃됩니다. 이 기기의 제한 규칙과 사용 기록은 그대로 남습니다. 되돌릴 수 없습니다.")) },
                confirmButton = { TextButton(onClick = { model.deleteAccount(); confirmDeleteAccount = false }) { Text(tr("계정 삭제")) } },
                dismissButton = { TextButton(onClick = { confirmDeleteAccount = false }) { Text(tr("취소")) } })
            if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text(tr("로컬 데이터를 삭제할까요?")) },
                text = { Text(tr("모든 제한 규칙과 기록이 삭제되고 차단이 해제됩니다. 되돌릴 수 없습니다.")) },
                confirmButton = { TextButton(onClick = { model.clear(); confirmClear = false }) { Text(tr("모두 삭제")) } },
                dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(tr("취소")) } })
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
            NotificationPermissionButton()
        }
    }
}

/** Asks for the reminder permission on Android 13+; hidden once granted or on older versions where it is implicit. */
@Composable private fun NotificationPermissionButton() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var allowed by remember { mutableStateOf(granted()) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    if (allowed) return
    OutlinedButton(onClick = { request.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) { Text(tr("알림 권한")) }
    Text(tr("하루 한도가 5분 남으면 알려 드립니다."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

private val RuleDraftSaver = listSaver<RuleDraft, Any>(
    save = { listOf(it.daily, it.opens, it.cooldown, it.session, it.scheduleEnabled, it.start, it.end, it.daysMask, it.enabled) },
    restore = { RuleDraft(it[0] as String, it[1] as String, it[2] as String, it[3] as String,
        it[4] as Boolean, it[5] as String, it[6] as String, it[7] as Int, it[8] as Boolean) },
)

private val DailyTimeInputSaver = listSaver<DailyTimeInput, String>(
    save = { listOf(it.hours, it.minutes, it.seconds) },
    restore = { DailyTimeInput(it[0], it[1], it[2]) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuleEditor(app: InstalledApp, saved: RuleEntity?, onDismiss: () -> Unit,
    onSave: (AppRule, () -> Unit) -> Unit, onDelete: () -> Unit, initialRecipe: RuleRecipe? = null) {
    var draft by rememberSaveable(stateSaver = RuleDraftSaver) { mutableStateOf(
        if (saved != null && initialRecipe != null) suggestedDraft(saved.toDomain(), initialRecipe)
        else saved?.toDomain()?.let(RuleDraft::from) ?: RuleDraft()) }
    var dailyTime by rememberSaveable(stateSaver = DailyTimeInputSaver) {
        mutableStateOf(DailyTimeInput.fromSeconds(draft.daily))
    }
    var sessionTime by rememberSaveable(stateSaver = DailyTimeInputSaver) {
        mutableStateOf(DailyTimeInput.fromSeconds(draft.session))
    }
    var cooldownTime by rememberSaveable(stateSaver = DailyTimeInputSaver) {
        mutableStateOf(DailyTimeInput.fromSeconds(draft.cooldown))
    }
    var error by remember { mutableStateOf<String?>(null) }
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("제한 사용"))
                Switch(checked = draft.enabled, onCheckedChange = { draft = draft.copy(enabled = it) }, enabled = !saving)
            }
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
            Text(tr("1회 한도 도달 시 앱을 떠나야 다음 사용을 시작합니다. 대기를 함께 설정하면 바로 재실행하는 것도 줄일 수 있습니다."), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().toggleable(value = draft.scheduleEnabled, enabled = !saving, role = Role.Switch,
                onValueChange = { draft = draft.copy(scheduleEnabled = it) }),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("요일·시간 예약"))
                Switch(checked = draft.scheduleEnabled, onCheckedChange = null, enabled = !saving)
            }
            if (draft.scheduleEnabled) {
                Text(if (draft.daysMask == 0) tr("예약할 요일을 선택해 주세요.") else tr("선택한 요일에만 예약이 적용됩니다."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(tr("선택한 요일에 시작하는 차단 시간입니다. 종료가 시작보다 이르면 다음 날까지 이어집니다."), style = MaterialTheme.typography.bodySmall)
                listOf(tr("월"), tr("화"), tr("수"), tr("목"), tr("금"), tr("토"), tr("일")).chunked(4).forEachIndexed { row, days ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        days.forEachIndexed { column, label ->
                            val bit = 1 shl (row * 4 + column)
                            FilterChip(selected = draft.daysMask and bit != 0,
                                onClick = { draft = draft.copy(daysMask = draft.daysMask xor bit) }, label = { Text(label) }, enabled = !saving)
                        }
                    }
                }
                OutlinedTextField(value = draft.start, onValueChange = { if (it.length <= 5) draft = draft.copy(start = it) },
                    label = { Text(tr("차단 시작 (HH:mm)")) }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = draft.end, onValueChange = { if (it.length <= 5) draft = draft.copy(end = it) },
                    label = { Text(tr("차단 종료 (HH:mm)")) }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth())
            }
            error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
            if (saved != null) TextButton(onClick = onDelete, enabled = !saving) {
                Text(tr("이 앱의 제한 삭제"), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
/** Hours, minutes and seconds for one limit; [tag] prefixes each field's test tag, e.g. "daily-hours". */
@Composable
private fun TimeFields(title: String, tag: String, value: DailyTimeInput, enabled: Boolean,
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
@Composable private fun NumberField(label: String, value: String, enabled: Boolean = true, changed: (String) -> Unit) {
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
    return buildList {
        if (!rule.enabled) add(text("일시 중지"))
        rule.dailyLimitSeconds?.let { add(text("하루 %1\$s", formatDuration(it, text))) }
        rule.sessionLimitSeconds?.let { add(text("한 번에 %1\$s", formatDuration(it, text))) }
        rule.maxOpenCount?.let { add(text("%1\$d회 실행", it)) }
        rule.cooldownSeconds?.let { add(text("%1\$s 대기", formatDuration(it, text))) }
        rule.toDomain().schedule?.let { schedule ->
            val days = schedule.days.sortedBy { it.value }.joinToString(" ") { labels[it.value - 1] }
            add(text("%1\$s %2\$s~%3\$s 차단", days, schedule.start.toString(),
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
        Text(tr("지금부터 집중"), style = MaterialTheme.typography.titleLarge)
        if (active.isNotEmpty()) {
            val remaining = ((active.maxOf { it.focusUntilEpochMillis!! } - nowEpoch + 999) / 1000).coerceAtLeast(0)
            Text(duration(remaining), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineLarge)
            val monitoring = state.permissions.usage && state.permissions.accessibility && state.health.connected
            Text(tr("%1\$d개 앱 · %2\$s · %3\$s", active.size, if (monitoring) tr("차단 중") else tr("제한 예정 · 감지 연결을 확인하세요"), active.joinToString(" · ") { it.appName }), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = stop, enabled = !state.focusBusy) { Text(tr("집중 종료")) }
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
            state.rules.map { it.toDomain() }, today)
    }
    CardBox {
        Text(tr("최근 7일 돌아보기"), style = MaterialTheme.typography.titleLarge)
        WeekDots(dayResults(records.associate { DailyKey(it.packageName, LocalDate.parse(it.date)) to DailyUsage(it.usageMillis, it.opens, it.blockedAttempts) },
            state.rules.map { it.toDomain() }, today))
        Text(tr("기록된 사용 %1\$s · %2\$d회 실행", duration(records.sumOf { it.usageMillis } / 1000), records.sumOf { it.opens }), color = MaterialTheme.colorScheme.primary)
        (6L downTo 0L).forEach { offset ->
            val date = today.minusDays(offset)
            val total = totals[date.toString()]
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(date.format(DateTimeFormatter.ofPattern("M/d")), modifier = Modifier.width(40.dp), style = MaterialTheme.typography.bodySmall)
                // Today's bar carries the accent; earlier days recede so the comparison reads at a glance.
                val bar = if (offset == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f)
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
