package com.chronolock.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.chronolock.domain.LockSetting
import java.time.LocalDate

/** Digits-only password field used by the lock and history forms. */
@Composable
internal fun PinField(value: String, label: String, tag: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.filter(Char::isDigit).take(12)) }, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth().testTag(tag))
}

/** Tries left after a wrong password, or until when it is paused. */
@Composable
internal fun PinStatus(state: MainState, nowEpoch: Long) {
    val blocked = state.pinBlockedUntil?.takeIf { it > nowEpoch }
    when {
        blocked != null -> Text(tr("%1\$s까지 비밀번호를 입력할 수 없어요.", timeText(blocked)), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
        state.pinTriesLeft != null -> Text(tr("남은 시도: %1\$d회", state.pinTriesLeft), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
    }
}

/** Current, new and confirm fields for changing a password; [change] gets them in that order. */
@Composable
internal fun ChangePinDialog(state: MainState, nowEpoch: Long, close: () -> Unit, change: (String, String, String, () -> Unit) -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text(tr("비밀번호 바꾸기")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PinField(current, tr("지금 비밀번호"), "pin-current") { current = it }
            PinField(next, tr("비밀번호 (숫자 4~12자리)"), "pin-new") { next = it }
            PinField(confirm, tr("비밀번호 확인"), "pin-new-confirm") { confirm = it }
            PinStatus(state, nowEpoch)
        } },
        confirmButton = { TextButton(onClick = { change(current, next, confirm, close) }, modifier = Modifier.testTag("pin-change-ok")) { Text(tr("저장")) } },
        dismissButton = { TextButton(onClick = close) { Text(tr("취소")) } })
}

/**
 * Locks that refuse every loosening change: for a time, or until a streak goal is met and its password (the person's
 * own, or an app-made code handed out a digit per kept day) is entered.
 */
@Composable
internal fun LockCard(state: MainState, today: LocalDate, nowEpoch: Long, keptStreak: Int,
    lockFor: (Int) -> Unit, startGoal: (Int, Boolean, String, String, () -> Unit) -> Unit, endGoal: (String) -> Unit,
    collectPiece: () -> Unit, changeGoalPin: (String, String, String, () -> Unit) -> Unit) {
    val lock = state.lock
    var confirmHours by rememberSaveable { mutableStateOf<Int?>(null) }
    var goalDays by rememberSaveable { mutableIntStateOf(LockSetting.GOAL_DAYS[1]) }
    var pieces by rememberSaveable { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var confirmGoal by rememberSaveable { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var changing by rememberSaveable { mutableStateOf(false) }
    CardBox {
        Text(tr("잠금 모드"), style = MaterialTheme.typography.titleLarge)
        Text(tr("정한 기간 동안이나 목표를 이룰 때까지, 제한을 풀거나 느슨하게 바꾸는 모든 변경을 막습니다. 더 엄격하게 바꾸는 것은 언제든 됩니다."),
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text(tr("기간 잠금"), style = MaterialTheme.typography.titleMedium)
        lock.untilEpochMillis?.takeIf { lock.timeLockedAt(nowEpoch) }?.let {
            Text(tr("%1\$s까지 잠겨 있어요. 기간은 늘릴 수만 있어요.", timeText(it)), color = MaterialTheme.colorScheme.primary)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LockSetting.HOURS.forEach { hours ->
                FilterChip(selected = false, onClick = { confirmHours = hours }, modifier = Modifier.testTag("lock-$hours"),
                    label = { Text(if (hours < 24) tr("%1\$d시간", hours) else tr("%1\$d일", hours / 24)) })
            }
        }

        HorizontalDivider()
        Text(tr("목표 잠금"), style = MaterialTheme.typography.titleMedium)
        val days = lock.goalDays
        if (days != null) {
            val progress = lock.goalProgress(keptStreak, today)
            Text(tr("목표: %1\$d일 연속 한도 지키기 · 지금 %2\$d일째", days, progress), color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(progress = { (progress.toFloat() / days).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            if (lock.pieceCode) {
                Text(tr("모은 조각"), style = MaterialTheme.typography.labelLarge)
                Text((lock.revealed + "_".repeat(days - lock.revealed.length)).chunked(1).joinToString(" "),
                    fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("lock-pieces"))
                Button(onClick = collectPiece, enabled = lock.canReveal(keptStreak, today), modifier = Modifier.testTag("lock-collect")) { Text(tr("조각 받기")) }
                Text(tr("한도를 지킨 날마다 비밀번호 한 자리를 받아요. 모두 모으면 그 번호로 풀 수 있어요."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (lock.goalReached(keptStreak, today)) {
                PinField(code, tr(if (lock.pieceCode) "모은 비밀번호" else "비밀번호"), "lock-code") { code = it }
                Button(onClick = { endGoal(code) }, modifier = Modifier.testTag("lock-unlock")) { Text(tr("잠금 풀기")) }
                PinStatus(state, nowEpoch)
            } else Text(tr("목표를 이루면 비밀번호로 풀 수 있어요."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (!lock.pieceCode) TextButton(onClick = { changing = true }, modifier = Modifier.testTag("lock-change-pin")) { Text(tr("비밀번호 바꾸기")) }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LockSetting.GOAL_DAYS.forEach { option ->
                    FilterChip(selected = goalDays == option, onClick = { goalDays = option }, label = { Text(tr("%1\$d일", option)) },
                        modifier = Modifier.testTag("goal-$option"))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !pieces, onClick = { pieces = false }, label = { Text(tr("내 비밀번호")) }, modifier = Modifier.testTag("goal-own"))
                FilterChip(selected = pieces, onClick = { pieces = true }, label = { Text(tr("조각 비밀번호")) }, modifier = Modifier.testTag("goal-pieces"))
            }
            Text(if (pieces) tr("앱이 만든 비밀번호를 한도를 지킨 날마다 한 자리씩 받아요. 목표 날짜를 다 채워야 전체 번호를 알 수 있어요.")
                else tr("직접 정한 비밀번호는 목표를 이룬 뒤에만 통해요."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (!pieces) {
                PinField(pin, tr("비밀번호 (숫자 4~12자리)"), "goal-pin") { pin = it }
                PinField(confirm, tr("비밀번호 확인"), "goal-pin-confirm") { confirm = it }
            }
            Button(onClick = { confirmGoal = true }, modifier = Modifier.testTag("goal-start")) { Text(tr("목표 잠금 시작")) }
        }
        Text(tr("Android 설정에서 접근성 권한을 끄거나 앱을 삭제하는 것은 막을 수 없습니다."),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
    val warning = tr("잠금 중에는 앱 안에서 제한을 풀거나 느슨하게 바꿀 수 없어요(쉬기·끄기·삭제·초기화·복원·엄격 모드 낮추기·집중 종료). 시작하면 중간에 되돌릴 수 없습니다.")
    confirmHours?.let { hours ->
        AlertDialog(onDismissRequest = { confirmHours = null }, title = { Text(tr("잠금을 시작할까요?")) }, text = { Text(warning) },
            confirmButton = { TextButton(onClick = { lockFor(hours); confirmHours = null }, modifier = Modifier.testTag("lock-confirm")) { Text(tr("잠금 시작")) } },
            dismissButton = { TextButton(onClick = { confirmHours = null }) { Text(tr("취소")) } })
    }
    if (confirmGoal) AlertDialog(onDismissRequest = { confirmGoal = false }, title = { Text(tr("잠금을 시작할까요?")) }, text = { Text(warning) },
        confirmButton = { TextButton(onClick = { startGoal(goalDays, pieces, pin, confirm) { pin = ""; confirm = "" }; confirmGoal = false },
            modifier = Modifier.testTag("goal-confirm")) { Text(tr("잠금 시작")) } },
        dismissButton = { TextButton(onClick = { confirmGoal = false }) { Text(tr("취소")) } })
    if (changing) ChangePinDialog(state, nowEpoch, { changing = false }, changeGoalPin)
}
