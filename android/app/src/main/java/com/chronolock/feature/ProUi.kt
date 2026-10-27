package com.chronolock.feature

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/** What Pro adds and how to get it; shown only while the free/Pro split is switched on. */
@Composable
internal fun ProCard(state: MainState, buy: () -> Unit, manage: () -> Unit = {}) {
    CardBox {
        Text(tr("ChronoLock 프로"), style = MaterialTheme.typography.titleLarge)
        listOf("앱 그룹 무제한", "웹사이트 차단 50개", "목표 잠금과 조각 비밀번호", "3개월·1년 기록과 1년 달력", "모든 테마와 나만의 테마", "캐릭터 5종 전부", "광고 없음").forEach {
            Text("• " + tr(it), style = MaterialTheme.typography.bodyMedium)
        }
        when {
            state.billing.pro -> {
                Text(tr("프로를 사용하고 있어요. 고마워요!"), color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = manage) { Text(tr("구독 관리")) }
            }
            state.billing.code -> Text(tr("코드로 프로를 사용하고 있어요."), color = MaterialTheme.colorScheme.primary)
            state.billing.proPending -> Text(tr("결제 확인을 기다리고 있습니다. 완료되면 프로가 열립니다."), color = MaterialTheme.colorScheme.primary)
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = buy, enabled = state.billing.proPrice != null, modifier = Modifier.testTag("pro-buy")) {
                    Text(state.billing.proPrice?.let { tr("프로 월 구독 · 월 %1\$s", it) } ?: tr("프로 상품을 불러오는 중"))
                }
            }
        }
        Text(tr("매달 자동으로 갱신되며 Google Play에서 언제든 해지할 수 있어요. 같은 Google 계정의 기기에서 함께 쓸 수 있어요."), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
    }
}

/** The app version; tapping it seven times reveals the field for a shared Pro code, which is not advertised anywhere. */
@Composable
internal fun VersionLine(state: MainState, redeem: (String) -> Boolean) {
    var taps by remember { mutableIntStateOf(0) }
    var code by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ChronoLock ${com.chronolock.BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { taps++ }.testTag("app-version"))
        when {
            state.billing.code -> Text(tr("코드로 프로를 사용하고 있어요."), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            taps >= 7 -> {
                OutlinedTextField(code, { code = it; wrong = false }, label = { Text(tr("프로 코드")) }, singleLine = true, isError = wrong,
                    supportingText = if (wrong) { { Text(tr("코드가 맞지 않습니다.")) } } else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters), modifier = Modifier.fillMaxWidth().testTag("pro-code"))
                Button(onClick = { wrong = !redeem(code) }, enabled = code.isNotBlank()) { Text(tr("적용")) }
            }
        }
    }
}
