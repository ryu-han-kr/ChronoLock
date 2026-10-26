package com.chronolock.feature

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chronolock.data.PendingSiteEntity
import com.chronolock.domain.BlockedSite

/** Blocked websites with an address field; a change strict mode holds back shows under its site. */
@Composable
internal fun SitesCard(sites: List<BlockedSite>, pending: List<PendingSiteEntity>, add: (String, () -> Unit) -> Unit,
    toggle: (BlockedSite, Boolean) -> Unit, remove: (String) -> Unit, cancelHeld: (String) -> Unit, addPreset: (List<String>) -> Unit = {},
    installed: Map<String, String> = emptyMap(), limited: Set<String> = emptySet(), limitApp: (String) -> Unit = {}) {
    var input by rememberSaveable { mutableStateOf("") }
    val submit = { if (input.isNotBlank()) add(input) { input = "" } }
    CardBox {
        Text(tr("Chrome·삼성 인터넷 등 브라우저에서 아래 사이트를 열면 차단 화면을 띄웁니다. 주소창의 도메인(주소가 보이지 않는 앱 내 브라우저는 페이지 제목과 링크 도메인)만 확인하고 방문 기록은 저장하지 않습니다."),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f).testTag("site-input"),
                label = { Text(tr("사이트 주소")) }, placeholder = { Text("youtube.com") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }))
            Button(onClick = submit, enabled = input.isNotBlank(), modifier = Modifier.testTag("site-add")) { Text(tr("추가")) }
        }
        Text(tr("한 번에 추가"), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.chronolock.domain.SitePreset.entries.forEach { preset ->
                AssistChip(onClick = { addPreset(preset.domains) }, label = { Text(tr(preset.name_)) }, modifier = Modifier.testTag("site-preset-${preset.name}"))
            }
        }
        sites.forEach { site ->
            Row(Modifier.fillMaxWidth().toggleable(value = site.enabled, role = Role.Switch, onValueChange = { toggle(site, it) })
                .testTag("site-${site.domain}"), verticalAlignment = Alignment.CenterVertically) {
                Text(site.domain, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (site.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = site.enabled, onCheckedChange = null)
                TextButton(onClick = { remove(site.domain) }) { Text(tr("삭제")) }
            }
            // The service's own app is not a website: point it out while it is installed and not limited yet.
            com.chronolock.domain.SiteApps.of(site.domain).firstOrNull { it in installed && it !in limited }?.takeIf { site.enabled }?.let { app ->
                Row(Modifier.fillMaxWidth().testTag("site-app-$app"), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("%1\$s 앱은 사이트 차단으로 막히지 않아요.", installed.getValue(app)), Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { limitApp(app) }) { Text(tr("앱도 제한하기")) }
                }
            }
            pending.firstOrNull { it.domain == site.domain }?.let { held ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("엄격 모드: %1\$s에 %2\$s 적용 예정", timeText(held.applyAtEpochMillis), if (held.remove) tr("사이트 삭제") else tr("차단 끄기")),
                        Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { cancelHeld(site.domain) }) { Text(tr("취소")) }
                }
            }
        }
        Text(tr("지원 브라우저: Chrome, 삼성 인터넷, Edge, Firefox, Brave, Opera, 웨일, DuckDuckGo, Vivaldi, 카카오톡·네이버 앱 내 브라우저"),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(tr("카카오톡·네이버 외의 앱 안에서 열리는 페이지와 각 서비스의 앱(유튜브 앱 등)은 막히지 않아요. 앱은 앱 제한으로 막아 주세요."),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}
