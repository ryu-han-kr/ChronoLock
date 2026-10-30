package com.chronolock.feature

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chronolock.domain.Exam
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Settings: the exam to count down to, picked from a calendar, with an optional short name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColumnScope.ExamSettings(exam: Exam?, today: LocalDate, set: (Exam?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(exam) { mutableStateOf(exam?.name.orEmpty()) }
    Text(tr("시험 D-day"), style = MaterialTheme.typography.titleSmall)
    Text(tr("시험 날짜를 정하면 오늘 화면과 차단 화면에 남은 날을 보여 줘요. 2주 전부터는 한도를 줄이자고 알려 드려요."),
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(value = name, onValueChange = { name = it.take(Exam.MAX_NAME) }, singleLine = true,
        label = { Text(tr("시험 이름 (선택)")) }, placeholder = { Text(tr("예: 수능")) },
        modifier = Modifier.fillMaxWidth().testTag("exam-name"))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.testTag("exam-pick")) {
            Text(exam?.let { tr("%1\$s · %2\$s", it.date.toString(), it.label(today)) } ?: tr("날짜 고르기"))
        }
        if (exam != null) {
            if (name.trim() != exam.name) OutlinedButton(onClick = { set(exam.copy(name = name.trim())) }) { Text(tr("저장")) }
            TextButton(onClick = { set(null) }, modifier = Modifier.testTag("exam-clear")) { Text(tr("지우기")) }
        }
    }
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (exam?.date ?: today.plusDays(30)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
            })
        DatePickerDialog(onDismissRequest = { picking = false }, confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { set(Exam(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate(), name.trim())) }
                picking = false
            }) { Text(tr("확인")) }
        }, dismissButton = { TextButton(onClick = { picking = false }) { Text(tr("취소")) } }) { DatePicker(state) }
    }
}

/** Today: days left, and in the last two weeks a nudge toward tighter limits (never changed by itself). */
@Composable
internal fun ExamCard(exam: Exam, today: LocalDate, openLimits: () -> Unit) {
    CardBox {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(exam.label(today), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("exam-dday"))
            Text(if (exam.name.isBlank()) tr("시험까지") else tr("%1\$s까지", exam.name), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.alignByBaseline())
        }
        if (exam.close(today)) {
            Text(tr("시험이 2주 안으로 다가왔어요. 하루 한도를 조금 줄여 보면 어때요?"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = openLimits, modifier = Modifier.testTag("exam-limits")) { Text(tr("한도 줄이러 가기")) }
        }
    }
}
