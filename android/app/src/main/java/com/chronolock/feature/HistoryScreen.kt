package com.chronolock.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.chronolock.data.HistoryYearEntity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.saveable.listSaver
import com.chronolock.data.HistoryDailyEntity
import com.chronolock.data.HistoryEstimateEntity
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.chronolock.domain.RuleRecipe
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Picks a starting recipe for an app from how it was used: many short visits call for fewer opens, otherwise short sessions with a pause. */
internal fun recommendRecipe(usageMillis: Long, opens: Int, recordedDays: Int): RuleRecipe {
    val sessionMillis = if (opens > 0) usageMillis / opens else usageMillis
    val opensPerDay = if (recordedDays > 0) opens.toDouble() / recordedDays else opens.toDouble()
    return if (opensPerDay >= 8 && sessionMillis < 3 * 60_000L) RuleRecipe.LESS_CHECKING else RuleRecipe.SHORT_BREAK
}

/** Period and ordering of the history tab; saved across rotation and process death. */
@Stable
internal class HistoryFilter(days: Int = 1, byOpens: Boolean = false, showDays: Boolean = false) {
    var days by mutableIntStateOf(days)
    var byOpens by mutableStateOf(byOpens)
    var showDays by mutableStateOf(showDays)

    companion object {
        val Saver = listSaver<HistoryFilter, Int>(
            save = { listOf(it.days, if (it.byOpens) 1 else 0, if (it.showDays) 1 else 0) },
            restore = { HistoryFilter(it[0], it[1] == 1, it[2] == 1) })
    }
}

@Composable internal fun rememberHistoryFilter(): HistoryFilter = rememberSaveable(saver = HistoryFilter.Saver) { HistoryFilter() }

/**
 * Records in the selected period and one summed row per app, in the selected order. Days before the first event record
 * may carry [estimates] from OS usage buckets; they add time but no opens, and the screen labels them.
 */
internal class HistoryView(val first: LocalDate, val records: List<HistoryDailyEntity>, val apps: List<HistoryDailyEntity>,
    val estimates: List<HistoryEstimateEntity> = emptyList()) {
    val estimatedByApp: Map<String, Long> = estimates.groupBy { it.packageName }.mapValues { (_, rows) -> rows.sumOf { it.usageMillis } }
    val estimatedByDay: Map<String, Long> = estimates.groupBy { it.date }.mapValues { (_, rows) -> rows.sumOf { it.usageMillis } }
    val totalMillis = records.sumOf { it.usageMillis } + estimates.sumOf { it.usageMillis }
    /** Days with either records or estimates, for per-day averages. */
    val coveredDays = (records.map { it.date } + estimates.map { it.date }).distinct().size
}

@Composable internal fun rememberHistoryView(state: MainState, today: LocalDate, filter: HistoryFilter): HistoryView =
    remember(state.history, today, filter.days, filter.byOpens) {
        val first = today.minusDays(filter.days - 1L)
        val records = state.history.filter { it.date >= first.toString() && it.date <= today.toString() }
        val estimates = state.historyEstimates.filter { it.date >= first.toString() && it.date <= today.toString() }
        val recordedApps = records.groupBy { it.packageName }.mapValues { (_, rows) -> rows.first().copy(
            usageMillis = rows.sumOf { it.usageMillis }, opens = rows.sumOf { it.opens }) }
        val estimatedApps = estimates.groupBy { it.packageName }
        val apps = (recordedApps.keys + estimatedApps.keys).map { pkg ->
            val extra = estimatedApps[pkg].orEmpty().sumOf { it.usageMillis }
            recordedApps[pkg]?.let { it.copy(usageMillis = it.usageMillis + extra) }
                ?: estimatedApps.getValue(pkg).first().let { HistoryDailyEntity(pkg, it.date, it.appName, extra, 0) }
        }
            .sortedWith(if (filter.byOpens) compareByDescending<HistoryDailyEntity> { it.opens }.thenByDescending { it.usageMillis }
                else compareByDescending<HistoryDailyEntity> { it.usageMillis }.thenByDescending { it.opens })
        HistoryView(first, records, apps, estimates)
    }

/**
 * The history tab as grid items: the summary is one item and each app card its own keyed item, so a month with
 * dozens of apps composes only the cards on screen.
 */
internal fun LazyGridScope.historyItems(state: MainState, today: LocalDate, filter: HistoryFilter, view: HistoryView,
    refresh: () -> Unit, permission: () -> Unit, suggest: (String, RuleRecipe) -> Unit,
    setPin: (String, String, () -> Unit) -> Unit = { _, _, _ -> }, deleteHistory: (String, () -> Unit) -> Unit = { _, _ -> },
    changePin: (String, String, String, () -> Unit) -> Unit = { _, _, _, _ -> }, nowEpoch: Long = System.currentTimeMillis()) {
    item(key = "history-summary", span = { GridItemSpan(maxLineSpan) }) { HistorySummary(state, today, filter, view, refresh, permission, suggest) }
    itemsIndexed(view.apps, key = { _, app -> "history-" + app.packageName }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { rank, app ->
        HistoryAppCard(rank, app, view.totalMillis, view.estimatedByApp[app.packageName] ?: 0)
    }
    item(key = "history-delete", span = { GridItemSpan(maxLineSpan) }) { HistoryDeleteCard(state, nowEpoch, setPin, deleteHistory, changePin) }
}

/** Standalone history tab (UI tests); the app uses [historyItems] inside its grid. */
@Composable
internal fun HistoryPanel(state: MainState, today: LocalDate, refresh: () -> Unit, permission: () -> Unit,
    suggest: (String, RuleRecipe) -> Unit = { _, _ -> }, setPin: (String, String, () -> Unit) -> Unit = { _, _, _ -> },
    deleteHistory: (String, () -> Unit) -> Unit = { _, _ -> }) {
    val filter = rememberHistoryFilter()
    val view = rememberHistoryView(state, today, filter)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HistorySummary(state, today, filter, view, refresh, permission, suggest)
        view.apps.forEachIndexed { rank, app -> key(app.packageName) { HistoryAppCard(rank, app, view.totalMillis, view.estimatedByApp[app.packageName] ?: 0) } }
        HistoryDeleteCard(state, System.currentTimeMillis(), setPin, deleteHistory)
    }
}

@Composable
private fun HistorySummary(state: MainState, today: LocalDate, filter: HistoryFilter, view: HistoryView,
    refresh: () -> Unit, permission: () -> Unit, suggest: (String, RuleRecipe) -> Unit) {
    val first = view.first
    val records = view.records
    val apps = view.apps
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf(1 to tr("하루"), 7 to tr("일주일"), 30 to tr("한 달")) + listOfNotNull((90 to tr("3개월")).takeIf { state.pro })).forEach { (count, label) ->
                FilterChip(selected = filter.days == count, onClick = { filter.days = count }, label = { Text(label) })
            }
        }
        Text(if (filter.days == 1) dateText(today, "yMd") else "${dateText(first, "yMd")} ~ ${dateText(today, "yMd")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        val earliest = remember(state.history) { state.history.minOfOrNull { it.date } }
        if (filter.days > 1 && earliest != null && earliest > first.toString()) Text(
            if (view.estimates.isNotEmpty()) tr("%1\$s 이전은 기기 사용 통계로 추정한 시간입니다. 추정 기간의 실행 횟수는 포함되지 않습니다.", dateText(LocalDate.parse(earliest), "yMd"))
            else tr("%1\$s부터 모은 기록입니다. 기기는 그 이전 사용 이벤트를 보관하지 않아 기간 합계가 실제보다 적을 수 있습니다.", dateText(LocalDate.parse(earliest), "yMd")),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (!state.permissions.usage) CardBox {
            Text(tr("사용 기록을 시작하세요"), style = MaterialTheme.typography.titleLarge)
            Text(tr("사용 정보 권한만 허용하면 차단 규칙이나 접근성 권한 없이 앱별 시간과 실행 횟수를 볼 수 있습니다."))
            Button(onClick = permission) { Text(tr("사용 정보 권한")) }
        }
        val total = view.totalMillis
        val recordedDays = view.coveredDays
        val mood = moodFor(if (recordedDays == 0) 0 else total / recordedDays, recordedDays > 0)
        CardBox {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("총 사용 시간"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(duration(total / 1000), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Text(tr(mood.line(today)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Mascot(mood, size = 92.dp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f)) { Metric(tr("실행 횟수"), records.sumOf { it.opens }.toString()) }
                Column(Modifier.weight(1f)) { Metric(tr("사용한 앱"), apps.size.toString()) }
            }
            Text(tr("실행 횟수는 앱 화면으로 들어온 횟수입니다. 같은 앱 안의 빠른 화면 전환은 한 번으로 계산합니다."),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (state.historyUpdatedAt > 0) Text(tr("최근 갱신: %1\$s", Instant.ofEpochMilli(state.historyUpdatedAt)
                .atZone(ZoneId.systemDefault()).toLocalDateTime().let { dateTimeText(it) }), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = refresh, enabled = state.permissions.usage && !state.historyBusy) {
                Text(tr(if (state.historyBusy) "갱신 중…" else "새로고침"))
            }
            if (state.historyBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.historyError) Text(tr("기록을 불러오지 못했습니다. 권한을 확인하고 다시 시도해 주세요."), color = MaterialTheme.colorScheme.error)
        }
        Text(tr("기록은 이 기기에 최대 3개월(90일)간 누적됩니다. 첫 실행 전 기록과 수집이 중단된 기간은 누락될 수 있습니다. 홈 화면은 제외하며 분할 화면·화면 속 화면의 수치는 다를 수 있습니다."),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (filter.days > 1 && (records.isNotEmpty() || view.estimates.isNotEmpty())) CardBox {
            TextButton(onClick = { filter.showDays = !filter.showDays }) { Text(tr(if (filter.showDays) "일별 기록 접기" else "일별 기록 보기")) }
            if (filter.showDays) {
                // Tapping a day lists its apps underneath; tapping it again closes the list.
                var openDay by rememberSaveable { mutableStateOf<String?>(null) }
                val totals = records.groupBy { it.date }
                fun dayMillis(day: String) = totals[day].orEmpty().sumOf { it.usageMillis } + (view.estimatedByDay[day] ?: 0)
                val maximum = ((0 until filter.days).maxOfOrNull { dayMillis(first.plusDays(it.toLong()).toString()) } ?: 1).coerceAtLeast(1)
                (0 until filter.days).forEach { offset ->
                    val day = first.plusDays(offset.toLong())
                    val rows = totals[day.toString()].orEmpty()
                    val estimated = view.estimatedByDay[day.toString()] ?: 0
                    val key = day.toString()
                    val open = openDay == key
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = dayMillis(key) > 0) {
                        openDay = if (open) null else key
                    }.testTag("history-day-$key").padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(dateText(day), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            if (dayMillis(key) > 0) Text(if (open) "▴" else "▾", color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(progress = { (dayMillis(key).toFloat() / maximum).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text(when {
                            rows.isNotEmpty() -> tr("%1\$s · %2\$d회 실행", duration(rows.sumOf { it.usageMillis } / 1000), rows.sumOf { it.opens })
                            estimated > 0 -> tr("추정 %1\$s", duration(estimated / 1000))
                            else -> tr("기록 없음")
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                    if (open) DayApps(rows, view.estimates.filter { it.date == key })
                }
            }
        }
        val ownPackage = LocalContext.current.packageName
        val candidates = remember(apps, state.rules) {
            apps.filter { app -> app.packageName != ownPackage && state.rules.none { it.packageName == app.packageName } }.take(3)
        }
        if (candidates.isNotEmpty()) CardBox {
            Text(tr("추천 앱 잠금"), style = MaterialTheme.typography.titleLarge)
            Text(tr("이 기간에 많이 쓴 앱 중 아직 제한이 없는 앱이에요. 레시피를 고른 뒤 저장하면 바로 적용돼요."),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            candidates.forEach { app ->
                val recipe = recommendRecipe(app.usageMillis, app.opens, recordedDays)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(app.packageName, app.appName, 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(app.appName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(tr("%1\$s · %2\$d회 실행", duration(app.usageMillis / 1000), app.opens),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(tr(recipe.title), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                    FilledTonalButton(onClick = { suggest(app.packageName, recipe) }) { Text(tr("제한 추가")) }
                }
            }
        }
        if (state.pro) YearCalendar(state.historyYear, today)
        else Text(tr("3개월 기록과 1년 달력은 프로에서 볼 수 있어요."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(tr("앱별 사용 기록"), style = MaterialTheme.typography.titleLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !filter.byOpens, onClick = { filter.byOpens = false }, label = { Text(tr("시간순")) })
            FilterChip(selected = filter.byOpens, onClick = { filter.byOpens = true }, label = { Text(tr("횟수순")) })
        }
        if (apps.isEmpty() && !state.historyBusy) CardBox {
            Text(tr("이 기간에 저장된 사용 기록이 없습니다."))
            Text(tr("권한을 켠 뒤 다른 앱을 사용하고 새로고침해 보세요."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A year of day totals as a week-by-column grid, newest at the right. Shades are relative to the person's own days
 * (quartiles of the days with use), so a light user and a heavy user both see contrast. Tapping a square shows its total.
 */
@Composable
internal fun YearCalendar(days: List<HistoryYearEntity>, today: LocalDate) {
    val totals = remember(days) { days.associate { it.date to it.usageMillis } }
    val first = today.minusDays(364).let { it.minusDays((it.dayOfWeek.value % 7).toLong()) } // weeks start on Sunday
    val weeks = ((today.toEpochDay() - first.toEpochDay()) / 7 + 1).toInt()
    val bounds = remember(totals) {
        val used = totals.values.filter { it > 0 }.sorted()
        if (used.isEmpty()) emptyList() else listOf(1, 2, 3).map { used[(used.size * it / 4).coerceAtMost(used.size - 1)] }
    }
    fun level(millis: Long) = if (millis <= 0) 0 else 1 + bounds.count { millis > it }
    val shades = listOf(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)) + listOf(.3f, .5f, .75f, 1f).map { MaterialTheme.colorScheme.primary.copy(alpha = it) }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    CardBox {
        Text(tr("1년 기록"), style = MaterialTheme.typography.titleLarge)
        Text(tr("하루 합계를 색으로 보여 줍니다. 칸을 누르면 그날 사용 시간이 나옵니다."), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        val scroll = rememberScrollState()
        LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
        val locale = uiText().locale
        val tiny = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp)
        val faint = MaterialTheme.colorScheme.onSurfaceVariant
        // A month name over the week its first day falls in (with the year in January); the first column is named too
        // unless the next month starts within two weeks and the names would overlap.
        val monthStarts = (0 until weeks).filter { week -> (0..6).any { first.plusDays(week * 7L + it).let { day -> day.dayOfMonth == 1 && day <= today } } }
        val labelled = monthStarts + listOfNotNull(0.takeIf { (monthStarts.firstOrNull() ?: weeks) >= 3 })
        Row {
            // Weekday names stay put while the weeks scroll; every other row keeps them readable at this size.
            Column(Modifier.padding(top = 15.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (weekday in 0..6) Box(Modifier.height(12.dp), contentAlignment = Alignment.CenterStart) {
                    if (weekday % 2 == 1) Text(first.plusDays(weekday.toLong()).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale),
                        style = tiny, color = faint, maxLines = 1)
                }
            }
            Row(Modifier.horizontalScroll(scroll).testTag("year-calendar"), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (week in 0 until weeks) Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(12.dp)) {
                        if (week in labelled) {
                            val day = (0..6).map { first.plusDays(week * 7L + it) }.firstOrNull { it.dayOfMonth == 1 } ?: first.plusDays(week * 7L)
                            Text(dateText(day, if (day.monthValue == 1) "yMMM" else "MMM"), style = tiny, color = faint, maxLines = 1, softWrap = false,
                                modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true).testTag("year-month-${day.year}-${day.monthValue}"))
                        }
                    }
                    for (weekday in 0..6) {
                        val day = first.plusDays(week * 7L + weekday)
                        if (day > today) Spacer(Modifier.size(12.dp))
                        else Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(shades[level(totals[day.toString()] ?: 0)])
                            .clickable { picked = day.toString() }.testTag("year-$day"))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("적게"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            shades.forEach { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(it)) }
            Text(tr("많이"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        picked?.let { date ->
            Text(dateText(LocalDate.parse(date), "yMd") + " · " + (totals[date]?.takeIf { it > 0 }?.let { duration(it / 1000) } ?: tr("기록 없음")),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Deleting kept history needs a password the person set; the first tap sets it, later ones ask for it. */
@Composable
private fun HistoryDeleteCard(state: MainState, nowEpoch: Long, setPin: (String, String, () -> Unit) -> Unit, delete: (String, () -> Unit) -> Unit,
    changePin: (String, String, String, () -> Unit) -> Unit = { _, _, _, _ -> }) {
    val pinSet = state.historyPinSet
    var open by rememberSaveable { mutableStateOf(false) }
    var changing by rememberSaveable { mutableStateOf(false) }
    if (changing) ChangePinDialog(state, nowEpoch, { changing = false }, changePin)
    CardBox {
        Text(tr("기록 삭제"), style = MaterialTheme.typography.titleLarge)
        Text(tr("기록은 직접 정한 비밀번호를 입력해야만 지울 수 있어요. 3개월이 지난 기록은 자동으로 지워집니다."),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.testTag("history-delete")) { Text(tr("기록 삭제")) }
            if (pinSet) TextButton(onClick = { changing = true }, modifier = Modifier.testTag("history-change-pin")) { Text(tr("비밀번호 바꾸기")) }
        }
    }
    if (!open) return
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val digits = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    AlertDialog(onDismissRequest = { open = false },
        title = { Text(if (pinSet) tr("기록 삭제") else tr("기록 삭제 비밀번호 정하기")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (pinSet) tr("비밀번호를 입력하면 3개월 기록과 1년 달력이 모두 지워집니다. 되돌릴 수 없습니다.")
                else tr("기록은 이 비밀번호를 알아야만 지울 수 있어요. 잊어버리면 앱을 삭제해야만 지워지니 꼭 기억해 주세요."))
            OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(12) }, label = { Text(if (pinSet) tr("비밀번호") else tr("비밀번호 (숫자 4~12자리)")) },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = digits, singleLine = true, modifier = Modifier.testTag("history-pin"))
            PinStatus(state, nowEpoch)
            if (!pinSet) OutlinedTextField(confirm, { confirm = it.filter(Char::isDigit).take(12) }, label = { Text(tr("비밀번호 확인")) },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = digits, singleLine = true, modifier = Modifier.testTag("history-pin-confirm"))
        } },
        confirmButton = { TextButton(onClick = { if (pinSet) delete(pin) { open = false } else setPin(pin, confirm) { pin = ""; confirm = "" } },
            modifier = Modifier.testTag("history-pin-ok")) { Text(if (pinSet) tr("삭제") else tr("저장"), color = if (pinSet) MaterialTheme.colorScheme.error else Color.Unspecified) } },
        dismissButton = { TextButton(onClick = { open = false }) { Text(tr("취소")) } })
}

/** One day's apps by time: recorded rows with opens, plus estimated time for apps the records do not cover. */
@Composable
private fun DayApps(rows: List<HistoryDailyEntity>, estimates: List<HistoryEstimateEntity>) {
    val estimated = estimates.groupBy { it.packageName }
    // Recorded rows keep their opens even when zero; only apps known solely from estimates are labelled as estimated.
    val apps = (rows.map { Triple(it, it.usageMillis + estimated[it.packageName].orEmpty().sumOf { e -> e.usageMillis }, it.opens) } +
        estimated.filterKeys { pkg -> rows.none { it.packageName == pkg } }.map { (pkg, list) ->
            Triple(HistoryDailyEntity(pkg, list.first().date, list.first().appName, 0, -1), list.sumOf { it.usageMillis }, -1)
        }).sortedByDescending { it.second }
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        apps.forEach { (app, millis, opens) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(app.packageName, app.appName, 24.dp)
                Text(app.appName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (opens >= 0) tr("%1\$s · %2\$d회 실행", duration(millis / 1000), opens) else tr("추정 %1\$s", duration(millis / 1000)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HistoryAppCard(rank: Int, app: HistoryDailyEntity, totalMillis: Long, estimatedMillis: Long = 0) {
    CardBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${rank + 1}", modifier = Modifier.width(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                AppIcon(app.packageName, app.appName, 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(app.appName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(tr("%1\$s · %2\$d회 실행", duration(app.usageMillis / 1000), app.opens), color = MaterialTheme.colorScheme.primary)
            if (estimatedMillis > 0) Text(tr("추정 %1\$s 포함", duration(estimatedMillis / 1000)),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { (app.usageMillis.toFloat() / totalMillis.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    }
}
