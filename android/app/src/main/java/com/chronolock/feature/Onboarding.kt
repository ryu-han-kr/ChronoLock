package com.chronolock.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chronolock.data.AppLanguage
import com.chronolock.domain.OnboardingGoal

/**
 * First run in three steps: what the app is (with the language), what to cut down on, and the two permissions ahead.
 * [start] receives the chosen goals; they shape the first suggestions on the Today tab.
 */
@Composable
internal fun OnboardingScreen(language: AppLanguage, selectLanguage: (AppLanguage) -> Unit, start: (Set<String>) -> Unit,
    mascot: com.chronolock.data.MascotKind = LocalMascot.current, selectMascot: (com.chronolock.data.MascotKind) -> Unit = {}, pro: Boolean = true) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var goals by rememberSaveable { mutableStateOf(emptyList<String>()) }
    // Surface supplies onBackground as the content color; without it untinted Text falls back to black.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().systemBarsPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { index ->
                Box(Modifier.height(4.dp).width(if (index == step) 28.dp else 12.dp)
                    .background(if (index <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .15f), RoundedCornerShape(2.dp)))
            }
        }
        when (step) {
            0 -> {
                MascotArt(com.chronolock.data.Pose.DEFAULT, Modifier.align(Alignment.CenterHorizontally), size = 140.dp, kind = mascot)
                Text(tr("ChronoLock에 오신 걸 환영해요"), style = MaterialTheme.typography.headlineMedium)
                Text(tr("내가 정한 기준으로 앱 사용을 조절하는 도구예요."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.values().forEach { option ->
                        FilterChip(selected = option == language, onClick = { selectLanguage(option) },
                            label = { Text(if (option == AppLanguage.SYSTEM) tr("기기 언어 따르기") else option.nativeName) })
                    }
                }
                listOf(
                    "앱별로 기준 정하기" to "하루 시간, 한 번에 쓰는 시간, 실행 횟수, 시간대를 원하는 대로 조합하세요.",
                    "기록은 이 기기에만" to "사용 기록과 규칙은 기기에 저장되고 서버로 보내지 않아요.",
                    "언제든 바꿀 수 있어요" to "규칙은 언제든 끄거나 지울 수 있고, 권한도 Android 설정에서 해제할 수 있어요.",
                ).forEachIndexed { index, (title, body) -> Step(index + 1, tr(title), tr(body)) }
                MascotPicker(mascot, selectMascot, pro)
            }
            1 -> {
                Text(tr("무엇을 줄이고 싶나요?"), style = MaterialTheme.typography.headlineMedium)
                Text(tr("고른 것에 맞춰 첫 제한을 추천해 드려요. 여러 개 골라도 되고, 나중에 바꿀 수 있어요."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                OnboardingGoal.entries.forEach { goal ->
                    val chosen = goal.name in goals
                    FilterChip(selected = chosen, onClick = { goals = if (chosen) goals - goal.name else goals + goal.name },
                        label = { Text(goal.emoji + "  " + tr(goal.label), style = MaterialTheme.typography.titleMedium) },
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("goal-${goal.name}"))
                }
            }
            else -> {
                Text(tr("두 가지 권한이 필요해요"), style = MaterialTheme.typography.headlineMedium)
                Step(1, tr("사용 정보 접근"), tr("앱별 사용 시간과 실행 횟수를 확인해 기록과 한도에 써요."))
                Step(2, tr("접근성 서비스"), tr("한도에 닿은 앱을 열 때 차단 화면을 띄워요. 화면 내용이나 입력은 읽지 않아요."))
                Step(3, tr("알림 (선택)"), tr("쓰고 있는 앱의 남은 시간과 한도 5분 전 알림을 보여 줘요. 허용하지 않아도 차단은 동작해요."))
                NotificationPermissionButton(hint = false)
                Text(tr("다음 화면의 '오늘' 탭에서 안내를 보고 바로 켤 수 있어요."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) OutlinedButton(onClick = { step-- }, modifier = Modifier.height(52.dp)) { Text(tr("뒤로")) }
            Button(onClick = { if (step < 2) step++ else start(goals.toSet()) }, modifier = Modifier.weight(1f).height(52.dp).testTag("onboarding-next")) {
                Text(tr(if (step < 2) "다음" else "시작하기"), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    }
}

@Composable
private fun Step(number: Int, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .14f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center) {
            Text("$number", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
