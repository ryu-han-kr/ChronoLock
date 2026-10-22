package com.chronolock.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chronolock.data.AppLanguage

/** First-run welcome: what the app does, where data lives, and that the user stays in control. Shown once. */
@Composable
internal fun OnboardingScreen(language: AppLanguage, selectLanguage: (AppLanguage) -> Unit, start: () -> Unit) {
    // Surface supplies onBackground as the content color; without it untinted Text falls back to black.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().systemBarsPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.values().forEach { option ->
                FilterChip(selected = option == language, onClick = { selectLanguage(option) }, label = { Text(if (option == AppLanguage.SYSTEM) tr("기기 언어 따르기") else option.nativeName) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(tr("ChronoLock에 오신 걸 환영해요"), style = MaterialTheme.typography.headlineMedium)
        Text(tr("내가 정한 기준으로 앱 사용을 조절하는 도구예요."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        listOf(
            "앱별로 기준 정하기" to "하루 시간, 한 번에 쓰는 시간, 실행 횟수, 시간대를 원하는 대로 조합하세요.",
            "기록은 이 기기에만" to "사용 기록과 규칙은 기기에 저장되고 서버로 보내지 않아요.",
            "언제든 바꿀 수 있어요" to "규칙은 언제든 끄거나 지울 수 있고, 권한도 Android 설정에서 해제할 수 있어요.",
        ).forEachIndexed { index, (title, body) ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .14f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center) {
                    Text("${index + 1}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr(title), style = MaterialTheme.typography.titleMedium)
                    Text(tr(body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = start, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(tr("시작하기"), style = MaterialTheme.typography.bodyLarge) }
    }
    }
}
