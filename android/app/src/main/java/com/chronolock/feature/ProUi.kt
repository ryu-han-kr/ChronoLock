package com.chronolock.feature

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** What Pro adds and how to get it; shown only while the free/Pro split is switched on. */
@Composable
internal fun ProCard(state: MainState, buy: () -> Unit) {
    CardBox {
        Text(tr("ChronoLock 프로"), style = MaterialTheme.typography.titleLarge)
        listOf("앱 그룹 무제한", "웹사이트 차단 50개", "목표 잠금과 조각 비밀번호", "3개월·1년 기록과 1년 달력", "모든 테마와 나만의 테마").forEach {
            Text("• " + tr(it), style = MaterialTheme.typography.bodyMedium)
        }
        when {
            state.billing.pro -> Text(tr("프로를 사용하고 있어요. 고마워요!"), color = MaterialTheme.colorScheme.primary)
            state.billing.proPending -> Text(tr("결제 확인을 기다리고 있습니다. 완료되면 프로가 열립니다."), color = MaterialTheme.colorScheme.primary)
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = buy, enabled = state.billing.proPrice != null, modifier = Modifier.testTag("pro-buy")) {
                    Text(state.billing.proPrice?.let { tr("프로 평생 이용권 · %1\$s", it) } ?: tr("프로 상품을 불러오는 중"))
                }
            }
        }
        Text(tr("한 번 결제하면 같은 Google 계정의 기기에서 계속 쓸 수 있어요."), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
    }
}
