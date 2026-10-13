package com.chronolock.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronolock.blocking.InstalledApp
import com.chronolock.data.RuleEntity
import com.chronolock.data.toDomain
import com.chronolock.domain.AppRule
import com.chronolock.domain.RuleDraft
import com.chronolock.domain.RuleRecipe
import com.chronolock.domain.DailyKey
import com.chronolock.domain.DailyUsage
import com.chronolock.domain.suggestUsageRule
import com.chronolock.domain.suggestedDraft
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val Ink = Color(0xFF10141F)
private val Surface = Color(0xFF1B2231)
private val Lime = Color(0xFFB8F27E)
private val Muted = Color(0xFFACB7CB)

@Composable
fun ChronoApp(model: MainViewModel, openSettings: (Boolean) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var suggestedRecipe by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionDialog by remember { mutableStateOf<Boolean?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmFocus by rememberSaveable { mutableStateOf<Int?>(null) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var nowEpoch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { today = LocalDate.now(); nowEpoch = System.currentTimeMillis(); delay(1000) } }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); model.dismissError() } }
    MaterialTheme(colorScheme = darkColorScheme(primary = Lime, onPrimary = Ink, background = Ink, surface = Surface,
        onBackground = Color.White, onSurface = Color.White, secondary = Muted)) {
        Scaffold(containerColor = Ink, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            NavigationBar(containerColor = Ink) {
                listOf("오늘", "앱 제한", "설정").forEachIndexed { index, title ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, label = { Text(title) },
                        icon = { Text(listOf("◷", "▦", "⚙")[index], fontSize = 23.sp) })
                }
            }
        }) { insets ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(insets), contentPadding = PaddingValues(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("CHRONOLOCK", color = Lime, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("LOCAL · 0.2", color = Muted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(listOf("내 시간을, 내 뜻대로.", "작은 제한, 더 나은 하루.", "내 데이터와 권한")[tab], fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(listOf(today.format(DateTimeFormatter.ofPattern("M월 d일")) + " · 선택한 앱의 사용 기록", "사용할 앱과 쉬어갈 기준을 직접 정하세요.", "기록은 이 기기에만 저장됩니다.")[tab], color = Muted)
                }
                if (tab != 2 && (!state.permissions.usage || !state.permissions.accessibility || !state.health.connected)) {
                    item { PermissionCard(state, { permissionDialog = false }, { permissionDialog = true }) }
                }
                when (tab) {
                    0 -> {
                        val stats = state.daily.filter { it.date == today.toString() }
                        item { QuickFocusCard(state, nowEpoch, { confirmFocus = it }, model::stopFocus) }
                        item {
                            CardBox {
                                Text("오늘 선택한 앱 사용", color = Muted)
                                Text(duration(stats.sumOf { it.usageMillis } / 1000), fontSize = 44.sp, color = Lime, fontWeight = FontWeight.SemiBold)
                                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Muted.copy(alpha = .2f))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Metric("허용한 실행", "${stats.sumOf { it.opens }}회")
                                    Metric("제한 도달·차단", "${stats.sumOf { it.blockedAttempts }}회")
                                }
                            }
                        }
                        item { Text("내 앱 제한", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                        if (state.rules.isEmpty()) item {
                            CardBox {
                                Text("첫 번째 제한을 만들어 보세요.", fontWeight = FontWeight.Bold)
                                Text("하루 사용시간, 실행 횟수, 재실행 대기를 조합할 수 있습니다.", color = Muted)
                                Button(onClick = { tab = 1 }) { Text("앱 선택하기") }
                            }
                        }
                        items(state.rules, key = { it.packageName }) { rule ->
                            val usage = stats.firstOrNull { it.packageName == rule.packageName }
                            CardBox {
                                AppHeading(rule.appName, rule.packageName, { suggestedRecipe = null; editPackage = rule.packageName })
                                Text(ruleSummary(rule), color = Lime)
                                Text("오늘 ${duration((usage?.usageMillis ?: 0) / 1000)} · ${usage?.opens ?: 0}회 실행", color = Muted)
                                rule.dailyLimitSeconds?.let { limit ->
                                    LinearProgressIndicator(progress = { ((usage?.usageMillis ?: 0).toFloat() / (limit * 1000)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                        item { WeeklyInsightsCard(state, today) { app, recipe -> editPackage = app; suggestedRecipe = recipe.name } }
                        item { Text("전체 화면 사용시간이 아닌 선택한 앱의 기록입니다. 감지 중단 중 실행 횟수는 누락될 수 있습니다.", color = Muted, fontSize = 12.sp) }
                    }
                    1 -> {
                        item { Text("설정·홈·전화 등 필수 앱은 제한 목록에서 제외합니다.", color = Muted, fontSize = 13.sp) }
                        if (state.loading) item { CircularProgressIndicator() }
                        if (!state.loading && state.apps.isEmpty()) item { Text("선택 가능한 앱이 없습니다.") }
                        items(state.apps, key = { it.packageName }) { app ->
                            val rule = state.rules.firstOrNull { it.packageName == app.packageName }
                            CardBox {
                                AppHeading(app.label, app.packageName, { suggestedRecipe = null; editPackage = app.packageName })
                                Text(rule?.let(::ruleSummary) ?: "제한 없음", color = if (rule != null) Lime else Muted, fontSize = 13.sp)
                            }
                        }
                    }
                    2 -> {
                        item { PermissionCard(state, { permissionDialog = false }, { permissionDialog = true }) }
                        item { CardBox {
                            Text("나를 위한 제한", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("현재는 일반 모드입니다. 언제든 규칙을 수정하거나 삭제할 수 있습니다. Android 설정에서 권한 해제와 앱 삭제도 가능합니다.", color = Muted)
                            Text("기기·배터리 설정에 따라 감지와 차단이 늦어질 수 있습니다. 분할 화면과 화면 속 화면은 아직 지원하지 않습니다.", color = Muted)
                        } }
                        item { CardBox {
                            Text("로컬 기록 관리", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("선택한 앱의 사용 기록을 최대 30일간 보관합니다. 서버 전송과 AI 분석은 아직 포함되어 있지 않습니다.", color = Muted)
                            OutlinedButton(onClick = { confirmClear = true }) { Text("모든 규칙과 사용 기록 삭제") }
                        } }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
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
            AlertDialog(onDismissRequest = { confirmFocus = null }, title = { Text("${minutes}분 동안 집중할까요?") },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("지금 제한이 켜진 ${targets.size}개 앱을 집중 종료까지 차단합니다. 나중에 추가한 앱은 이번 집중에 포함되지 않습니다.")
                    Spacer(Modifier.height(12.dp))
                    Text(targets.joinToString(" · ") { it.appName })
                    Spacer(Modifier.height(12.dp))
                    Text("기존 규칙은 유지되며 언제든 집중을 종료할 수 있습니다.")
                } },
                confirmButton = { TextButton(enabled = targets.isNotEmpty() && !state.focusBusy,
                    onClick = { confirmFocus = null; model.startFocus(minutes) }) { Text("집중 시작") } },
                dismissButton = { TextButton(onClick = { confirmFocus = null }) { Text("취소") } })
        }
        permissionDialog?.let { accessibility ->
            AlertDialog(onDismissRequest = { permissionDialog = null }, title = { Text(if (accessibility) "접근성 권한 안내" else "사용 정보 접근 안내") },
                text = { Text(if (accessibility)
                    "ChronoLock은 앱 전환 이벤트로 실행 중인 앱을 확인하고, 직접 설정한 제한에 따라 다른 앱 위에 차단 화면을 표시합니다. 화면 텍스트, 비밀번호, 입력 내용은 읽지 않습니다. 선택한 앱의 패키지 이름과 사용·차단 기록은 이 기기에만 저장되며 전송되지 않습니다. Android 설정에서 언제든 해제할 수 있습니다. 동의하면 다음 설정 화면에서 ChronoLock을 직접 활성화해 주세요."
                else "사용 정보 접근 권한으로 선택한 앱의 오늘 사용시간을 확인하고, 감지 서비스가 다시 시작될 때 저장된 사용량을 보완합니다. 기록은 이 기기에만 저장됩니다. 권한 없이도 설정을 둘러볼 수 있지만 정확한 제한을 위해서는 권한이 필요합니다.") },
                confirmButton = { TextButton(onClick = { model.acknowledge { permissionDialog = null; openSettings(accessibility) } }) { Text("동의하고 설정 열기") } },
                dismissButton = { TextButton(onClick = { permissionDialog = null }) { Text("나중에") } })
        }
        if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("로컬 데이터를 삭제할까요?") },
            text = { Text("모든 제한 규칙과 기록이 삭제되고 차단이 해제됩니다. 되돌릴 수 없습니다.") },
            confirmButton = { TextButton(onClick = { model.clear(); confirmClear = false }) { Text("모두 삭제") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } })
    }
}

@Composable private fun CardBox(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(22.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
@Composable private fun Metric(label: String, value: String) {
    Column { Text(label, color = Muted, fontSize = 12.sp); Text(value, fontWeight = FontWeight.Bold, fontSize = 24.sp) }
}
@Composable private fun AppHeading(name: String, packageName: String, edit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(packageName, color = Muted, fontSize = 11.sp) }
        TextButton(onClick = edit) { Text("설정") }
    }
}
@Composable private fun PermissionCard(state: MainState, usage: () -> Unit, accessibility: () -> Unit) {
    CardBox {
        val ready = state.permissions.usage && state.permissions.accessibility && state.health.connected
        Text(if (ready) "● 앱 제한 감지 중" else "앱 제한을 위한 준비", color = Lime, fontWeight = FontWeight.Bold)
        Text("사용 정보: ${if (state.permissions.usage) "허용됨" else "필요"} · 접근성: ${if (state.permissions.accessibility) "허용됨" else "필요"}", color = Muted)
        state.health.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.permissions.accessibility && !state.health.connected) Text("서비스 연결을 확인해 주세요. 현재 차단 동작을 보장할 수 없습니다.", color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = usage) { Text("사용 정보 권한") }
            OutlinedButton(onClick = accessibility) { Text("접근성 권한") }
        }
    }
}

private val RuleDraftSaver = listSaver<RuleDraft, Any>(
    save = { listOf(it.daily, it.opens, it.cooldown, it.session, it.scheduleEnabled, it.start, it.end, it.daysMask, it.enabled) },
    restore = { RuleDraft(it[0] as String, it[1] as String, it[2] as String, it[3] as String,
        it[4] as Boolean, it[5] as String, it[6] as String, it[7] as Int, it[8] as Boolean) },
)

@Composable
internal fun RuleEditor(app: InstalledApp, saved: RuleEntity?, onDismiss: () -> Unit,
    onSave: (AppRule, () -> Unit) -> Unit, onDelete: () -> Unit, initialRecipe: RuleRecipe? = null) {
    var draft by rememberSaveable(stateSaver = RuleDraftSaver) { mutableStateOf(
        if (saved != null && initialRecipe != null) suggestedDraft(saved.toDomain(), initialRecipe)
        else saved?.toDomain()?.let(RuleDraft::from) ?: RuleDraft()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val editorScroll = rememberScrollState()
    LaunchedEffect(error) { if (error != null) editorScroll.animateScrollTo(editorScroll.maxValue) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(app.label) }, text = {
        Column(Modifier.verticalScroll(editorScroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("제한 사용")
                Switch(checked = draft.enabled, onCheckedChange = { draft = draft.copy(enabled = it) }, enabled = !saving)
            }
            Text("집중 레시피", fontWeight = FontWeight.Bold)
            if (initialRecipe != null) Text("기록 기반 제안이 준비됐습니다. 기존 제한은 유지하고 비어 있는 항목만 채웠습니다. 확인 후 저장하세요.", fontSize = 12.sp)
            Text("레시피는 아래 입력값을 바꿉니다. 저장 전까지 적용되지 않습니다.", fontSize = 12.sp)
            RuleRecipe.values().forEach { recipe ->
                OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !saving,
                    onClick = { draft = recipe.draft.copy(enabled = draft.enabled); error = null }) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(recipe.title, fontWeight = FontWeight.Bold)
                        Text(recipe.description, fontSize = 11.sp)
                    }
                }
            }
            HorizontalDivider()
            Text("빈 항목은 제한하지 않습니다. 여러 항목을 설정하면 함께 적용됩니다.")
            NumberField("하루 허용 시간 (초)", draft.daily, !saving) { draft = draft.copy(daily = it) }
            NumberField("한 번에 허용할 시간 (초)", draft.session, !saving) { draft = draft.copy(session = it) }
            NumberField("하루 허용 실행 횟수", draft.opens, !saving) { draft = draft.copy(opens = it) }
            NumberField("종료 후 재실행 대기 (초)", draft.cooldown, !saving) { draft = draft.copy(cooldown = it) }
            Text("60초 = 1분 · 300초 = 5분\n1회 한도 도달 시 앱을 떠나야 다음 사용을 시작합니다. 대기를 함께 설정하면 바로 재실행하는 것도 줄일 수 있습니다.", fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("요일·시간 예약")
                Switch(checked = draft.scheduleEnabled, onCheckedChange = { draft = draft.copy(scheduleEnabled = it) }, enabled = !saving)
            }
            if (draft.scheduleEnabled) {
                Text("선택한 요일에 시작하는 차단 시간입니다. 종료가 시작보다 이르면 다음 날까지 이어집니다.", fontSize = 12.sp)
                listOf("월", "화", "수", "목", "금", "토", "일").chunked(4).forEachIndexed { row, days ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        days.forEachIndexed { column, label ->
                            val bit = 1 shl (row * 4 + column)
                            FilterChip(selected = draft.daysMask and bit != 0,
                                onClick = { draft = draft.copy(daysMask = draft.daysMask xor bit) }, label = { Text(label) }, enabled = !saving)
                        }
                    }
                }
                OutlinedTextField(value = draft.start, onValueChange = { if (it.length <= 5) draft = draft.copy(start = it) },
                    label = { Text("차단 시작 (HH:mm)") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = draft.end, onValueChange = { if (it.length <= 5) draft = draft.copy(end = it) },
                    label = { Text("차단 종료 (HH:mm)") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth())
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (saved != null) TextButton(onClick = onDelete, enabled = !saving) { Text("이 앱의 제한 삭제") }
        }
    }, confirmButton = { TextButton(enabled = !saving, onClick = {
        try {
            val rule = draft.build(app.packageName, app.label)
            error = null
            saving = true
            onSave(rule) { saving = false }
        } catch (invalid: IllegalArgumentException) {
            error = invalid.message
            saving = false
        }
    }) { Text(if (saving) "저장 중" else "저장") } }, dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("취소") } })
}
@Composable private fun NumberField(label: String, value: String, enabled: Boolean = true, changed: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = { if (it.length <= 10) changed(it) }, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
}
private fun duration(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600}시간 ${(seconds % 3600) / 60}분"
    seconds >= 60 -> "${seconds / 60}분 ${seconds % 60}초"
    else -> "${seconds}초"
}
private fun ruleSummary(rule: RuleEntity): String = buildList {
    if (!rule.enabled) add("일시 중지")
    rule.dailyLimitSeconds?.let { add("하루 ${duration(it)}") }
    rule.sessionLimitSeconds?.let { add("한 번에 ${duration(it)}") }
    rule.maxOpenCount?.let { add("${it}회 실행") }
    rule.cooldownSeconds?.let { add("${duration(it)} 대기") }
    rule.toDomain().schedule?.let { schedule ->
        val labels = listOf("월", "화", "수", "목", "금", "토", "일")
        val days = schedule.days.sortedBy { it.value }.joinToString("") { labels[it.value - 1] }
        add("$days ${schedule.start}~${if (schedule.start > schedule.end) "다음 날 " else ""}${schedule.end} 차단")
    }
}.joinToString(" · ")

@Composable
internal fun QuickFocusCard(state: MainState, nowEpoch: Long, start: (Int) -> Unit, stop: () -> Unit) {
    val enabled = state.rules.filter { it.enabled }
    val active = enabled.filter { (it.focusUntilEpochMillis ?: 0) > nowEpoch }
    CardBox {
        Text("지금부터 집중", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (active.isNotEmpty()) {
            val remaining = ((active.maxOf { it.focusUntilEpochMillis!! } - nowEpoch + 999) / 1000).coerceAtLeast(0)
            Text(duration(remaining), color = Lime, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            val monitoring = state.permissions.usage && state.permissions.accessibility && state.health.connected
            Text("${active.size}개 앱 ${if (monitoring) "차단 중" else "제한 예정 · 감지 연결을 확인하세요"} · ${active.joinToString(" · ") { it.appName }}", color = Muted)
            OutlinedButton(onClick = stop, enabled = !state.focusBusy) { Text("집중 종료") }
            Text("집중이 끝나도 기존 시간·횟수·예약 제한은 유지됩니다.", fontSize = 12.sp, color = Muted)
        } else {
            Text(if (enabled.isEmpty()) "앱 제한 탭에서 집중할 때 차단할 앱을 먼저 설정하세요."
                else "제한이 켜진 ${enabled.size}개 앱을 잠시 쉬고, 지금 할 일에 집중하세요.", color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 50, 90).forEach { minutes ->
                    FilledTonalButton(enabled = enabled.isNotEmpty() && !state.focusBusy && state.permissions.usage &&
                        state.permissions.accessibility && state.health.connected, onClick = { start(minutes) }) { Text("${minutes}분") }
                }
            }
            if (enabled.isNotEmpty() && (!state.permissions.usage || !state.permissions.accessibility || !state.health.connected)) {
                Text("권한을 켜고 감지 연결을 확인하면 시작할 수 있습니다.", fontSize = 12.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun WeeklyInsightsCard(state: MainState, today: LocalDate, review: (String, RuleRecipe) -> Unit) {
    val firstDay = today.minusDays(6).toString()
    val records = state.daily.filter { it.date >= firstDay && it.date <= today.toString() }
    val totals = records.groupBy { it.date }.mapValues { (_, rows) -> rows.sumOf { it.usageMillis } }
    val maximum = (totals.values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val suggestion = remember(state.daily, state.rules, today) {
        suggestUsageRule(records.associate { DailyKey(it.packageName, LocalDate.parse(it.date)) to DailyUsage(it.usageMillis, it.opens, it.blockedAttempts) },
            state.rules.map { it.toDomain() }, today)
    }
    CardBox {
        Text("최근 7일 돌아보기", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("기록된 사용 ${duration(records.sumOf { it.usageMillis } / 1000)} · ${records.sumOf { it.opens }}회 실행", color = Lime)
        (6L downTo 0L).forEach { offset ->
            val date = today.minusDays(offset)
            val total = totals[date.toString()]
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(date.format(DateTimeFormatter.ofPattern("M/d")), modifier = Modifier.width(40.dp), fontSize = 12.sp)
                LinearProgressIndicator(progress = { ((total ?: 0L).toFloat() / maximum).coerceIn(0f, 1f) }, modifier = Modifier.weight(1f))
                Text(total?.let { duration(it / 1000) } ?: "기록 없음", modifier = Modifier.width(100.dp), fontSize = 11.sp)
            }
        }
        if (suggestion != null) {
            HorizontalDivider()
            Text("${suggestion.rule.appName} · ${suggestion.recipe.title}", fontWeight = FontWeight.Bold)
            Text("${suggestion.recordedDays}일 기록에서 ${suggestion.opens}회 실행, 실행당 기록 사용시간은 평균 ${duration(suggestion.usageMillis / suggestion.opens / 1000)}입니다.", fontSize = 12.sp, color = Muted)
            OutlinedButton(onClick = { review(suggestion.rule.packageName, suggestion.recipe) }) { Text("제안 살펴보기") }
        } else {
            Text("3일 이상 기록을 모으면 사용 패턴에 맞는 제한을 제안합니다. 이미 설정한 제한도 고려합니다.", color = Muted, fontSize = 12.sp)
        }
        Text("선택한 앱의 기기 내 기록만 사용합니다. 감지 중단 중 기록은 빠질 수 있으며, 제안은 저장해야 적용됩니다.", color = Muted, fontSize = 11.sp)
    }
}
