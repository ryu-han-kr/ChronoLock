package com.chronolock.feature

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronolock.blocking.Health
import com.chronolock.blocking.InstalledApp
import com.chronolock.data.DailyEntity
import com.chronolock.data.AppTheme
import com.chronolock.data.RuleEntity
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
    buyAdFree: () -> Unit = {},
    manageSubscription: () -> Unit = {},
) {
    val destinations = listOf(tr("오늘"), tr("앱 제한"), tr("히스토리"), tr("설정"))
    var query by rememberSaveable { mutableStateOf("") }
    var onlyConfigured by rememberSaveable { mutableStateOf(false) }
    val ready = state.permissions.usage && state.permissions.accessibility && state.health.connected
    val rulesByPackage = remember(state.rules) { state.rules.associateBy { it.packageName } }
    val filteredApps = remember(state.apps, rulesByPackage, query, onlyConfigured) {
        state.apps.filter { app ->
            (!onlyConfigured || app.packageName in rulesByPackage) &&
                (app.label.contains(query.trim(), ignoreCase = true) ||
                    app.packageName.contains(query.trim(), ignoreCase = true))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 840.dp
        Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = { Column {
            banner?.invoke()
            if (!expanded) NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                destinations.forEachIndexed { index, title ->
                    NavigationBarItem(selected = tab == index, onClick = { selectTab(index) },
                        label = { Text(title) }, icon = { DestinationIcon(index) })
                }
            }
        } }) { insets ->
            Row(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) {
                if (expanded) NavigationRail(containerColor = MaterialTheme.colorScheme.background, windowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.fillMaxHeight(), header = {
                        Text("CL", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 24.dp))
                    }) {
                    destinations.forEachIndexed { index, title ->
                        NavigationRailItem(selected = tab == index, onClick = { selectTab(index) },
                            label = { Text(title) }, icon = { DestinationIcon(index) })
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    // Each destination keeps its own position, including after a window resize.
                    val todayScroll = rememberLazyGridState()
                    val appsScroll = rememberLazyGridState()
                    val settingsScroll = rememberLazyGridState()
                    val historyScroll = rememberLazyGridState()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (expanded && tab < 2) 2 else 1),
                        state = when (tab) { 0 -> todayScroll; 1 -> appsScroll; 2 -> historyScroll; else -> settingsScroll },
                        modifier = Modifier.widthIn(max = 1120.dp).fillMaxSize(),
                        contentPadding = PaddingValues(if (expanded) 32.dp else 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(destinations[tab], fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
                                Text(when (tab) {
                                    0 -> tr("%1\$s · 내 시간을, 내 뜻대로.", today.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(uiText().locale)))
                                    1 -> tr("작은 제한으로 더 여유로운 하루를 만드세요.")
                                    2 -> tr("차단 없이도 내 앱 사용 습관을 확인하세요.")
                                    else -> tr("내 데이터와 권한을 관리하세요.")
                                }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (tab == 0) StatusPill(ready)
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                        if (tab < 2 && !ready) item(span = { GridItemSpan(maxLineSpan) }) {
                            PermissionCard(state, usagePermission, accessibilityPermission)
                        }
                        when (tab) {
                            0 -> {
                                val stats = state.daily.filter { it.date == today.toString() }
                                item { UsageSummaryCard(stats, state.rules.count { it.enabled }) }
                                item { QuickFocusCard(state, nowEpoch, startFocus, stopFocus) }
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(tr("내 앱 제한"), Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        TextButton(onClick = { selectTab(1) }) { Text(tr("앱 추가")) }
                                    }
                                }
                                if (state.rules.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                                    CardBox {
                                        Text(tr("집중을 방해하는 앱이 있나요?"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Text(tr("앱을 선택하고 나에게 맞는 사용시간과 실행 횟수를 정해 보세요."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Button(onClick = { selectTab(1) }) { Text(tr("앱 선택하기")) }
                                    }
                                }
                                items(state.rules, key = { "rule:${it.packageName}" }) { rule ->
                                    val usage = stats.firstOrNull { it.packageName == rule.packageName }
                                    CardBox {
                                        AppHeading(rule.appName, rule.packageName) { edit(rule.packageName, null) }
                                        Text(ruleSummary(rule), color = if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                        rule.dailyLimitSeconds?.let { limit ->
                                            val remaining = remainingDailySeconds(limit, usage?.usageMillis ?: 0)
                                            val tint = if (remaining == 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            LinearProgressIndicator(progress = {
                                                ((usage?.usageMillis ?: 0).toFloat() / (limit.coerceAtLeast(1) * 1000f)).coerceIn(0f, 1f)
                                            }, modifier = Modifier.fillMaxWidth().height(6.dp), color = tint,
                                                trackColor = tint.copy(alpha = .16f))
                                            if (rule.enabled) Text(if (remaining == 0L) tr("오늘 한도 도달") else tr("오늘 %1\$s 남음", duration(remaining)),
                                                color = tint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        }
                                        Row(Modifier.fillMaxWidth().toggleable(value = rule.enabled, role = Role.Switch,
                                            onValueChange = { toggleRule(rule, it) }), verticalAlignment = Alignment.CenterVertically) {
                                            Text(tr("오늘 %1\$s · %2\$d회 실행", duration((usage?.usageMillis ?: 0) / 1000), usage?.opens ?: 0),
                                                Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                            Text(tr("제한 사용"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                            Spacer(Modifier.width(8.dp))
                                            Switch(checked = rule.enabled, onCheckedChange = null)
                                        }
                                    }
                                }
                                item(span = { GridItemSpan(maxLineSpan) }) { WeeklyInsightsCard(state, today) { app, recipe -> edit(app, recipe) } }
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(tr("선택한 앱의 기록만 표시합니다. 감지 중단 중 실행 횟수는 누락될 수 있습니다."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                            }
                            1 -> {
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
                                        Text(tr("%1\$d개 앱 · 설정·홈·전화 등 필수 앱은 제외합니다.", filteredApps.size), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
                                            color = if (rule?.enabled == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                    }
                                }
                            }
                            2 -> {
                                item(span = { GridItemSpan(maxLineSpan) }) { HistoryPanel(state, today, refreshHistory, usagePermission) }
                            }
                            3 -> {
                                if (state.account.configured) item { AccountCard(state, signIn, signOut, deleteAccount) }
                                if (state.ads.configured || state.adFree) item { AdsCard(state, privacyOptions, buyAdFree, manageSubscription) }
                                item { LanguagePicker(state.language, selectLanguage) }
                                item { ThemePicker(state.theme, selectTheme) }
                                item { PermissionCard(state, usagePermission, accessibilityPermission) }
                                item { CardBox {
                                    Text(tr("나를 위한 제한"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text(tr("현재는 일반 모드입니다. 언제든 규칙을 수정하거나 삭제할 수 있습니다. Android 설정에서 권한 해제와 앱 삭제도 가능합니다."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(tr("기기·배터리 설정에 따라 감지와 차단이 늦어질 수 있습니다. 분할 화면과 화면 속 화면은 아직 지원하지 않습니다."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } }
                                item { CardBox {
                                    Text(tr("로컬 기록 관리"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text(tr("앱 사용 기록을 최대 30일간 보관합니다. 기록은 이 기기에만 저장되며 서버로 전송하지 않습니다."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedButton(onClick = clear) { Text(tr("모든 규칙과 사용 기록 삭제")) }
                                } }
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
        Text(tr("계정"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
private fun AdsCard(state: MainState, privacyOptions: () -> Unit, buy: () -> Unit, manage: () -> Unit) {
    CardBox {
        Text(tr("광고"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(if (state.adFree) tr("광고 없이 이용 중입니다.")
            else tr("오늘·히스토리 화면 하단에 배너 광고가 표시됩니다. 차단 화면과 집중 중에는 표시하지 않습니다."),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            state.adFree -> OutlinedButton(onClick = manage) { Text(tr("구독 관리")) }
            state.billing.pending -> Text(tr("결제 확인을 기다리고 있습니다. 완료되면 광고가 사라집니다."), color = MaterialTheme.colorScheme.primary)
            state.billing.available -> {
                Button(onClick = buy) { Text(tr("광고 제거 · 월 %1\$s", state.billing.price ?: "990원")) }
                Text(tr("Google Play 정기 결제입니다. Play 스토어에서 언제든 해지할 수 있습니다."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            else -> Text(tr("광고 제거 상품을 불러오지 못했습니다. Play 스토어에서 설치한 앱인지 확인해 주세요."),
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        if (state.ads.privacyOptionsRequired) OutlinedButton(onClick = privacyOptions) { Text(tr("광고 개인정보 설정")) }
    }
}

@Composable
private fun UsageSummaryCard(stats: List<DailyEntity>, enabledRules: Int) {
    CardBox {
        Text(tr("오늘 선택한 앱 사용"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(duration(stats.sumOf { it.usageMillis } / 1000), fontSize = 36.sp,
            lineHeight = 44.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text(tr("%1\$d개 앱 제한 켜짐", enabledRules), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
        Text(if (ready) tr("앱 제한 감지 중") else tr("감지 준비가 필요해요"), color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
