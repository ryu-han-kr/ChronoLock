package com.chronolock.feature

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun HistoryPanel(state: MainState, today: LocalDate, refresh: () -> Unit, permission: () -> Unit) {
    var days by rememberSaveable { mutableIntStateOf(1) }
    var byOpens by rememberSaveable { mutableStateOf(false) }
    var showDays by rememberSaveable { mutableStateOf(false) }
    val first = today.minusDays(days - 1L)
    val records = remember(state.history, first, today) {
        state.history.filter { it.date >= first.toString() && it.date <= today.toString() }
    }
    val apps = remember(records, byOpens) {
        records.groupBy { it.packageName }.map { (_, rows) -> rows.first().copy(
            usageMillis = rows.sumOf { it.usageMillis }, opens = rows.sumOf { it.opens }) }
            .sortedWith(if (byOpens) compareByDescending<com.chronolock.data.HistoryDailyEntity> { it.opens }.thenByDescending { it.usageMillis }
                else compareByDescending<com.chronolock.data.HistoryDailyEntity> { it.usageMillis }.thenByDescending { it.opens })
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to tr("하루"), 7 to tr("일주일"), 30 to tr("한 달")).forEach { (count, label) ->
                FilterChip(selected = days == count, onClick = { days = count }, label = { Text(label) })
            }
        }
        Text(if (days == 1) today.toString() else "$first ~ $today", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!state.permissions.usage) CardBox {
            Text(tr("사용 기록을 시작하세요"), style = MaterialTheme.typography.titleLarge)
            Text(tr("사용 정보 권한만 허용하면 차단 규칙이나 접근성 권한 없이 앱별 시간과 실행 횟수를 볼 수 있습니다."))
            Button(onClick = permission) { Text(tr("사용 정보 권한")) }
        }
        CardBox {
            Text(tr("총 사용 시간"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(duration(records.sumOf { it.usageMillis } / 1000), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f)) { Metric(tr("실행 횟수"), records.sumOf { it.opens }.toString()) }
                Column(Modifier.weight(1f)) { Metric(tr("사용한 앱"), apps.size.toString()) }
            }
            Text(tr("실행 횟수는 앱 화면으로 들어온 횟수입니다. 같은 앱 안의 빠른 화면 전환은 한 번으로 계산합니다."),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (state.historyUpdatedAt > 0) Text(tr("최근 갱신: %1\$s", Instant.ofEpochMilli(state.historyUpdatedAt)
                .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = refresh, enabled = state.permissions.usage && !state.historyBusy) {
                Text(tr(if (state.historyBusy) "갱신 중…" else "새로고침"))
            }
            if (state.historyBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.historyError) Text(tr("기록을 불러오지 못했습니다. 권한을 확인하고 다시 시도해 주세요."), color = MaterialTheme.colorScheme.error)
        }
        Text(tr("기록은 이 기기에 최대 30일간 누적됩니다. 첫 실행 전 기록과 수집이 중단된 기간은 누락될 수 있습니다. 홈 화면은 제외하며 분할 화면·화면 속 화면의 수치는 다를 수 있습니다."),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (days > 1 && records.isNotEmpty()) CardBox {
            TextButton(onClick = { showDays = !showDays }) { Text(tr(if (showDays) "일별 기록 접기" else "일별 기록 보기")) }
            if (showDays) {
                val totals = records.groupBy { it.date }
                val maximum = (totals.values.maxOfOrNull { rows -> rows.sumOf { it.usageMillis } } ?: 1).coerceAtLeast(1)
                (0 until days).forEach { offset ->
                    val date = first.plusDays(offset.toLong()).toString()
                    val rows = totals[date].orEmpty()
                    Text(date, style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { (rows.sumOf { it.usageMillis }.toFloat() / maximum).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(if (rows.isEmpty()) tr("기록 없음") else tr("%1\$s · %2\$d회 실행", duration(rows.sumOf { it.usageMillis } / 1000), rows.sumOf { it.opens }), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(tr("앱별 사용 기록"), style = MaterialTheme.typography.titleLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !byOpens, onClick = { byOpens = false }, label = { Text(tr("시간순")) })
            FilterChip(selected = byOpens, onClick = { byOpens = true }, label = { Text(tr("횟수순")) })
        }
        if (apps.isEmpty() && !state.historyBusy) CardBox {
            Text(tr("이 기간에 저장된 사용 기록이 없습니다."))
            Text(tr("권한을 켠 뒤 다른 앱을 사용하고 새로고침해 보세요."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        apps.forEachIndexed { rank, app -> key(app.packageName) { CardBox {
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
            LinearProgressIndicator(progress = { (app.usageMillis.toFloat() / records.sumOf { it.usageMillis }.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        } } }
    }
}
