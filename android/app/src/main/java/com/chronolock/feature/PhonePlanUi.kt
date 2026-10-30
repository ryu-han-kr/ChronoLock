package com.chronolock.feature

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chronolock.blocking.InstalledApp
import com.chronolock.data.PhonePlan
import com.chronolock.domain.ScheduleDraft
import java.time.LocalDate

/**
 * Settings: limits over the whole phone that the person sets for themselves. A total daily budget for every app they
 * have not set aside, times when only those set-aside apps open, and the set-aside (always allowed) apps themselves.
 * Changes are made here and saved together; strict mode and locks refuse saving ones that loosen.
 */
@Composable
internal fun PhonePlanCard(saved: PhonePlan, apps: List<InstalledApp>, save: (PhonePlan) -> Unit) {
    var plan by rememberSaveable(saved, stateSaver = PlanSaver) { mutableStateOf(saved) }
    var addingWindow by rememberSaveable { mutableStateOf(false) }
    var pickingExempt by rememberSaveable { mutableStateOf(false) }
    CardBox {
        Text(tr("휴대폰 전체 한도"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("phone-plan"))
        Text(tr("앱마다 정하는 한도와 별개로 휴대폰 전체에 내가 정하는 한도예요. 항상 허용한 앱과 전화·문자는 빠져요."),
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text(tr("하루 전체 시간"), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf<Long?>(null, 1, 2, 3, 4, 6).forEach { hours ->
                val seconds = hours?.times(3600)
                FilterChip(selected = plan.budgetSeconds == seconds, onClick = { plan = plan.copy(budgetSeconds = seconds) },
                    label = { Text(if (hours == null) tr("끄기") else tr("%1\$d시간", hours.toInt())) },
                    modifier = Modifier.testTag("budget-${hours ?: 0}"))
            }
        }

        Text(tr("허용한 앱만 쓰는 시간"), style = MaterialTheme.typography.titleSmall)
        Text(tr("이 시간에는 항상 허용한 앱만 열려요. 공부·업무 시간이나 잠들기 전에 써 보세요."),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        plan.windows.forEachIndexed { index, window ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${window.start}–${window.end} · ${daysLabel(window.daysMask)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { plan = plan.copy(windows = plan.windows.filterIndexed { i, _ -> i != index }) },
                    modifier = Modifier.testTag("window-remove-$index")) { Text(tr("지우기")) }
            }
        }
        OutlinedButton(onClick = { addingWindow = true }, modifier = Modifier.testTag("window-add")) { Text(tr("시간대 추가")) }

        Text(tr("항상 허용 앱"), style = MaterialTheme.typography.titleSmall)
        Text(if (plan.exempt.isEmpty()) tr("없음") else apps.filter { it.packageName in plan.exempt }.joinToString(", ") { it.label },
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { pickingExempt = true }, modifier = Modifier.testTag("exempt-pick")) { Text(tr("항상 허용 앱 고르기")) }

        if (plan != saved) Button(onClick = { save(plan) }, modifier = Modifier.testTag("phone-plan-save")) { Text(tr("저장")) }
    }
    if (addingWindow) WindowDialog({ plan = plan.copy(windows = plan.windows + it); addingWindow = false }) { addingWindow = false }
    if (pickingExempt) ExemptDialog(apps, plan.exempt, { plan = plan.copy(exempt = it); pickingExempt = false }) { pickingExempt = false }
}

/** Today's time in the covered apps against the phone-wide budget. */
@Composable
internal fun PhoneBudgetCard(usedMillis: Long, budgetSeconds: Long) {
    CardBox {
        Text(tr("오늘 휴대폰 %1\$s / %2\$s", duration(usedMillis / 1000), duration(budgetSeconds)),
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("phone-budget"))
        LinearProgressIndicator(progress = { (usedMillis / 1000f / budgetSeconds).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun daysLabel(mask: Int): String = when (mask) {
    ScheduleDraft.EVERY_DAY -> tr("매일")
    ScheduleDraft.WEEKDAYS -> tr("평일")
    ScheduleDraft.WEEKEND -> tr("주말")
    else -> tr("직접")
}

@Composable
private fun WindowDialog(add: (ScheduleDraft) -> Unit, close: () -> Unit) {
    var start by rememberSaveable { mutableStateOf("09:00") }
    var end by rememberSaveable { mutableStateOf("18:00") }
    var days by rememberSaveable { mutableIntStateOf(ScheduleDraft.WEEKDAYS) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = close, title = { Text(tr("시간대 추가")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(start, { start = it.take(5); error = null }, label = { Text(tr("시작")) }, singleLine = true,
                    modifier = Modifier.weight(1f).testTag("window-start"))
                OutlinedTextField(end, { end = it.take(5); error = null }, label = { Text(tr("끝")) }, singleLine = true,
                    modifier = Modifier.weight(1f).testTag("window-end"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(ScheduleDraft.WEEKDAYS, ScheduleDraft.WEEKEND, ScheduleDraft.EVERY_DAY).forEach { mask ->
                    FilterChip(selected = days == mask, onClick = { days = mask }, label = { Text(daysLabel(mask)) })
                }
            }
            error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = {
        TextButton(onClick = {
            val draft = ScheduleDraft(start.trim(), end.trim(), days)
            runCatching { draft.build() }.onSuccess { add(draft) }.onFailure { error = it.message }
        }, modifier = Modifier.testTag("window-ok")) { Text(tr("추가")) }
    }, dismissButton = { TextButton(onClick = close) { Text(tr("취소")) } })
}

@Composable
private fun ExemptDialog(apps: List<InstalledApp>, chosen: Set<String>, done: (Set<String>) -> Unit, close: () -> Unit) {
    var picked by remember { mutableStateOf(chosen) }
    AlertDialog(onDismissRequest = close, title = { Text(tr("항상 허용 앱")) }, text = {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(apps.size) { index ->
                val app = apps[index]
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(app.packageName in picked, { on -> picked = if (on) picked + app.packageName else picked - app.packageName },
                        modifier = Modifier.testTag("exempt-" + app.packageName))
                    Text(app.label)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { done(picked) }, modifier = Modifier.testTag("exempt-ok")) { Text(tr("확인")) } },
        dismissButton = { TextButton(onClick = close) { Text(tr("취소")) } })
}

private val PlanSaver = androidx.compose.runtime.saveable.Saver<PhonePlan, String>({ it.encode() }, { PhonePlan.decode(it) })

/** Today's time in the apps the plan covers, as far as the screen knows them: every app with a record except the set-aside ones. */
internal fun phoneUsedToday(daily: List<com.chronolock.data.DailyEntity>, plan: PhonePlan, apps: List<InstalledApp>, today: LocalDate): Long {
    val covered = apps.map { it.packageName }.toSet() - plan.exempt
    return daily.filter { it.date == today.toString() && it.packageName in covered }.sumOf { it.usageMillis }
}
