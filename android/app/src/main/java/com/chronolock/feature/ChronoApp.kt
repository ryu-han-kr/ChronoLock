package com.chronolock.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.chronolock.domain.AppRule
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
    var permissionDialog by remember { mutableStateOf<Boolean?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) { while (true) { today = LocalDate.now(); delay(1000) } }
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
                        Text("LOCAL · 0.1", color = Muted, fontSize = 11.sp)
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
                                AppHeading(rule.appName, rule.packageName, { editPackage = rule.packageName })
                                Text(ruleSummary(rule), color = Lime)
                                Text("오늘 ${duration((usage?.usageMillis ?: 0) / 1000)} · ${usage?.opens ?: 0}회 실행", color = Muted)
                                rule.dailyLimitSeconds?.let { limit ->
                                    LinearProgressIndicator(progress = { ((usage?.usageMillis ?: 0).toFloat() / (limit * 1000)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                        item { Text("전체 화면 사용시간이 아닌 선택한 앱의 기록입니다. 감지 중단 중 실행 횟수는 누락될 수 있습니다.", color = Muted, fontSize = 12.sp) }
                    }
                    1 -> {
                        item { Text("설정·홈·전화 등 필수 앱은 제한 목록에서 제외합니다.", color = Muted, fontSize = 13.sp) }
                        if (state.loading) item { CircularProgressIndicator() }
                        if (!state.loading && state.apps.isEmpty()) item { Text("선택 가능한 앱이 없습니다.") }
                        items(state.apps, key = { it.packageName }) { app ->
                            val rule = state.rules.firstOrNull { it.packageName == app.packageName }
                            CardBox {
                                AppHeading(app.label, app.packageName, { editPackage = app.packageName })
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
                onSave = { model.save(it) { editPackage = null } }, onDelete = { model.remove(appId) { editPackage = null } }) }
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

@Composable
private fun RuleEditor(app: InstalledApp, saved: RuleEntity?, onDismiss: () -> Unit, onSave: (AppRule) -> Unit, onDelete: () -> Unit) {
    var daily by rememberSaveable { mutableStateOf(saved?.dailyLimitSeconds?.toString() ?: if (saved == null) "60" else "") }
    var opens by rememberSaveable { mutableStateOf(saved?.maxOpenCount?.toString() ?: "") }
    var cooldown by rememberSaveable { mutableStateOf(saved?.cooldownSeconds?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    // Keep editable if a repository operation fails; ViewModel reports the error separately.
    LaunchedEffect(saving) { if (saving) { delay(1500); saving = false } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(app.label) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("빈 항목은 제한하지 않습니다. 여러 항목을 설정하면 함께 적용됩니다.", color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { daily = "60"; opens = ""; cooldown = "" }) { Text("1분") }
                TextButton(onClick = { daily = ""; opens = "3"; cooldown = "" }) { Text("3회") }
                TextButton(onClick = { daily = ""; opens = ""; cooldown = "30" }) { Text("30초 대기") }
            }
            NumberField("하루 허용 시간 (초)", daily) { daily = it }
            NumberField("하루 허용 실행 횟수", opens) { opens = it }
            NumberField("종료 후 재실행 대기 (초)", cooldown) { cooldown = it }
            Text("60초 = 1분 · 3,600초 = 1시간\n대기는 허용된 사용을 마친 시점부터 시작합니다.", fontSize = 12.sp, color = Muted)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (saved != null) TextButton(onClick = onDelete, enabled = !saving) { Text("이 앱의 제한 삭제") }
        }
    }, confirmButton = { TextButton(enabled = !saving, onClick = {
        val d = daily.trim().takeIf(String::isNotEmpty)?.toLongOrNull()
        val o = opens.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
        val c = cooldown.trim().takeIf(String::isNotEmpty)?.toLongOrNull()
        error = when {
            daily.isNotBlank() && (d == null || d !in 1..86400) -> "하루 시간은 1~86,400초로 입력해 주세요."
            opens.isNotBlank() && (o == null || o !in 1..10000) -> "실행 횟수는 1~10,000회로 입력해 주세요."
            cooldown.isNotBlank() && (c == null || c !in 1..86400) -> "재실행 대기는 1~86,400초로 입력해 주세요."
            d == null && o == null && c == null -> "한 가지 이상의 제한을 설정해 주세요."
            else -> null
        }
        if (error == null) { saving = true; onSave(AppRule(app.packageName, app.label, d, o, c)) }
    }) { Text(if (saving) "저장 중" else "저장") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}
@Composable private fun NumberField(label: String, value: String, changed: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = { if (it.length <= 10) changed(it) }, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
}
private fun duration(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600}시간 ${(seconds % 3600) / 60}분"
    seconds >= 60 -> "${seconds / 60}분 ${seconds % 60}초"
    else -> "${seconds}초"
}
private fun ruleSummary(rule: RuleEntity): String = buildList {
    rule.dailyLimitSeconds?.let { add("하루 ${duration(it)}") }
    rule.maxOpenCount?.let { add("${it}회 실행") }
    rule.cooldownSeconds?.let { add("${duration(it)} 대기") }
}.joinToString(" · ")
