package com.chronolock.feature

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Every way out of a limit, gentlest first; ChronoLock is a self-set tool, so the way back is never hidden. */
internal val unlockSteps = listOf(
    "잠깐만 쓰기: 오늘 탭의 앱 카드에서 '잠시 쉬기'를 누르면 10분·30분·1시간·오늘만 제한을 멈출 수 있어요. 시간이 지나면 자동으로 다시 켜집니다.",
    "한 앱만 끄기: 오늘 탭 '내 앱 제한'에서 '제한 사용' 스위치를 끄거나, 앱 이름을 눌러 규칙을 고치거나 '이 앱의 제한 삭제'를 누르세요.",
    "그룹·사이트 끄기: 앱 그룹은 그룹 카드의 '설정'에서 '제한 사용'을 끄거나 삭제하고, 웹사이트 차단은 사이트 옆 스위치나 '삭제'를 누르세요.",
    "집중 끝내기: 집중이 진행 중이면 오늘 탭의 '집중 종료'로 끝낼 수 있어요. 엄격 모드에서는 정한 시간이 끝날 때까지 기다려야 해요.",
    "엄격 모드가 켜져 있다면: 제한을 끄거나 느슨하게 바꾸면 정한 대기 시간(10분~1시간) 뒤에 적용돼요. 기다리면 그대로 풀리고, 엄격 모드를 끄는 것도 같은 시간만큼 기다리면 됩니다.",
    "한 번에 모두 지우기: 엄격 모드를 끈 뒤 설정의 '모든 규칙 초기화'를 누르면 앱 제한·그룹·웹사이트 차단이 모두 지워져요. 사용 기록은 남습니다.",
    "잠금 모드가 켜져 있다면: 기간 잠금은 정한 시간이 끝나야, 목표 잠금은 목표를 이룬 뒤 비밀번호를 넣어야 풀려요. 그 전에는 아래 '앱 밖에서 멈추기'만 남습니다.",
    "앱 밖에서 멈추기: Android 설정 → 접근성 → 설치된 앱 → 'ChronoLock 앱 제한'을 끄면 모든 차단이 바로 멈춰요. 앱을 삭제해도 되지만 규칙과 기록도 함께 지워집니다.",
)

@Composable
internal fun UnlockHelpCard() {
    var open by rememberSaveable { mutableStateOf(false) }
    CardBox {
        Text(tr("제한을 풀고 싶을 때"), style = MaterialTheme.typography.titleLarge)
        Text(tr("ChronoLock의 제한은 언제든 직접 풀 수 있어요. 가벼운 방법부터 순서대로 안내합니다."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { open = !open }, modifier = Modifier.testTag("unlock-help")) { Text(tr(if (open) "방법 접기" else "방법 보기")) }
        if (open) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            unlockSteps.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    Text(tr(step), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
