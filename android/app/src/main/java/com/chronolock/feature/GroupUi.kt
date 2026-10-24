package com.chronolock.feature

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chronolock.blocking.InstalledApp
import com.chronolock.data.DailyEntity
import com.chronolock.data.PendingGroupEntity
import com.chronolock.domain.AppGroup
import com.chronolock.domain.DailyTimeInput
import com.chronolock.domain.GroupDraft
import com.chronolock.domain.AppRule
import com.chronolock.domain.ScheduleDraft

/** Today's shared usage of one group against its limits, with a change strict mode may be holding back. */
@Composable
internal fun GroupCard(group: AppGroup, today: List<DailyEntity>, held: PendingGroupEntity?, edit: () -> Unit, cancelHeld: () -> Unit) {
    val stats = today.filter { it.packageName in group.members }
    val used = stats.sumOf { it.usageMillis }
    val opens = stats.sumOf { it.opens }
    CardBox {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(group.members.values.sorted().joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = edit, modifier = Modifier.testTag("group-edit-${group.id}")) { Text(tr("설정")) }
        }
        Text(groupSummary(group), color = if (group.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
        group.dailyLimitSeconds?.takeIf { group.enabled }?.let { limit ->
            val remaining = remainingDailySeconds(limit, used)
            val tint = if (remaining == 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            val progress by animateFloatAsState((used.toFloat() / (limit * 1000f)).coerceIn(0f, 1f), label = "group")
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp), color = tint, trackColor = tint.copy(alpha = .16f))
            Text(if (remaining == 0L) tr("오늘 한도 도달") else tr("오늘 %1\$s 남음", duration(remaining)), color = tint, style = MaterialTheme.typography.labelMedium)
        }
        Text(tr("함께 오늘 %1\$s · %2\$d회 실행", duration(used / 1000), opens), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        held?.let { change ->
            val what = when {
                change.remove -> tr("그룹 삭제")
                !change.group.enabled -> tr("제한 끄기")
                else -> tr("설정 변경")
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("엄격 모드: %1\$s에 %2\$s 적용 예정", timeText(change.applyAtEpochMillis), what), Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = cancelHeld) { Text(tr("취소")) }
            }
        }
    }
}

@Composable internal fun groupSummary(group: AppGroup): String {
    val text = uiText()
    return buildList {
        if (!group.enabled) add(text("일시 중지"))
        add(text("%1\$d개 앱", group.members.size))
        group.dailyLimitSeconds?.let { add(text("함께 하루 %1\$s", formatDuration(it, text))) }
        group.maxOpenCount?.let { add(text("함께 %1\$d회 실행", it)) }
        if (group.schedules.isNotEmpty()) add(text("차단 시간대 %1\$d개", group.schedules.size))
    }.joinToString(" · ")
}

// Name, limits, flags, the schedule count and windows (3 each), then member pairs.
private val GroupDraftSaver = listSaver<GroupDraft, String>(
    save = { listOf(it.name, it.daily, it.opens, it.enabled.toString(), it.scheduleEnabled.toString(), it.schedules.size.toString()) +
        it.schedules.flatMap { window -> listOf(window.start, window.end, window.daysMask.toString()) } +
        it.members.flatMap { (app, name) -> listOf(app, name) } },
    restore = {
        val windows = it[5].toInt()
        GroupDraft(it[0], it.drop(6 + windows * 3).chunked(2).associate { (app, name) -> app to name }, it[1], it[2], it[3].toBoolean(),
            it[4].toBoolean(), it.drop(6).take(windows * 3).chunked(3).map { (start, end, mask) -> ScheduleDraft(start, end, mask.toInt()) })
    },
)

/**
 * Creates or edits a group: its name, shared limits, block windows and member apps. Apps in [taken] (another group's,
 * mapped to its name) cannot be chosen, so each app belongs to one group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupEditor(saved: AppGroup?, apps: List<InstalledApp>, taken: Map<String, String>, strictMinutes: Int, onDismiss: () -> Unit,
    onSave: (AppGroup, () -> Unit) -> Unit, onDelete: () -> Unit) {
    var draft by rememberSaveable(stateSaver = GroupDraftSaver) { mutableStateOf(saved?.let(GroupDraft::from) ?: GroupDraft()) }
    var dailyTime by rememberSaveable(stateSaver = DailyTimeInputSaver) { mutableStateOf(DailyTimeInput.fromSeconds(draft.daily)) }
    var query by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    // The message sits above the form, next to the fields it is about; the app list below can be very long.
    LaunchedEffect(error) { if (error != null) scroll.animateScrollTo(0) }
    fun submit() {
        try {
            val group = draft.copy(daily = dailyTime.toSecondsText("함께 쓰는 하루 시간")).build(saved?.id ?: 0)
            error = null
            saving = true
            onSave(group) { saving = false }
        } catch (invalid: IllegalArgumentException) { error = invalid.message; saving = false }
    }
    // Saved members first, so they are visible above a long list; the order stays put while boxes are ticked.
    // An uninstalled member still shows by its saved name.
    val savedMembers = saved?.members.orEmpty()
    var confirmAll by rememberSaveable { mutableStateOf(false) }
    val choices = remember(apps, query) {
        (savedMembers + apps.associate { it.packageName to it.label }.filterKeys { it !in savedMembers }).entries
            .filter { (app, label) -> query.isBlank() || label.contains(query.trim(), true) || app.contains(query.trim(), true) }
            .sortedWith(compareBy({ it.key !in savedMembers }, { it.key in taken && it.key !in savedMembers }, { it.value.lowercase() }))
    }
    // Apps of another group stay listed (greyed, with that group's name) so it is clear why they cannot be ticked.
    fun free(app: String) = app !in taken || app in savedMembers
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !saving })
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = sheet) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(saved?.name ?: tr("새 앱 그룹"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(enabled = !saving, onClick = onDismiss) { Text(tr("취소")) }
            Button(enabled = !saving, onClick = ::submit, modifier = Modifier.testTag("group-save")) { Text(if (saving) tr("저장 중") else tr("저장")) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .15f))
        Column(Modifier.verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("묶은 앱들은 하루 시간과 실행 횟수를 함께 씁니다. 앱마다 정한 제한도 그대로 적용됩니다."), style = MaterialTheme.typography.bodySmall)
            Text(tr("한 앱은 그룹 하나에만 넣을 수 있어요."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().toggleable(value = draft.enabled, enabled = !saving, role = Role.Switch) { draft = draft.copy(enabled = it) },
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("제한 사용"))
                Switch(checked = draft.enabled, onCheckedChange = null, enabled = !saving)
            }
            if (strictMinutes > 0) Text(tr("엄격 모드: 그룹을 끄거나 느슨하게 바꾸면 %1\$s 뒤에 적용됩니다.", waitText(strictMinutes)),
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            error?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("group-error")) }
            OutlinedTextField(value = draft.name, onValueChange = { if (it.length <= 30) { draft = draft.copy(name = it); error = null } },
                label = { Text(tr("그룹 이름")) }, placeholder = { Text(tr("예: SNS")) }, singleLine = true, enabled = !saving,
                isError = error != null && draft.name.isBlank(),
                modifier = Modifier.fillMaxWidth().testTag("group-name"))
            TimeFields("함께 쓰는 하루 시간", "group-daily", dailyTime, !saving, presetMinutes = listOf(30, 60, 120)) { dailyTime = it; error = null }
            NumberField(tr("함께 쓰는 하루 실행 횟수"), draft.opens, !saving) { draft = draft.copy(opens = it); error = null }
            Row(Modifier.fillMaxWidth().toggleable(value = draft.scheduleEnabled, enabled = !saving, role = Role.Switch,
                onValueChange = { draft = draft.copy(scheduleEnabled = it); error = null }).testTag("group-schedule"),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("그룹 차단 시간대"))
                Switch(checked = draft.scheduleEnabled, onCheckedChange = null, enabled = !saving)
            }
            if (draft.scheduleEnabled) {
                Text(tr("정한 시간에는 그룹의 모든 앱이 함께 막혀요. 종료가 시작보다 이르면 다음 날까지 이어집니다."), style = MaterialTheme.typography.bodySmall)
                draft.schedules.forEachIndexed { index, window ->
                    ScheduleFields(index, window, removable = draft.schedules.size > 1, enabled = !saving,
                        change = { next -> draft = draft.copy(schedules = draft.schedules.toMutableList().also { it[index] = next }); error = null },
                        remove = { draft = draft.copy(schedules = draft.schedules.filterIndexed { at, _ -> at != index }) })
                }
                if (draft.schedules.size < AppRule.MAX_SCHEDULES) OutlinedButton(onClick = { draft = draft.copy(schedules = draft.schedules + ScheduleDraft()) },
                    enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("group-schedule-add")) { Text(tr("예약 구간 추가")) }
            }
            HorizontalDivider()
            Text(tr("묶을 앱 · %1\$d개 선택", draft.members.size), fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(tr("앱 검색")) }, singleLine = true,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            // Bulk selection for limiting many apps at once; it asks first, as essential apps could end up limited too.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { confirmAll = true }, enabled = !saving && choices.any { it.key !in draft.members && free(it.key) },
                    modifier = Modifier.testTag("group-select-all")) { Text(tr("검색 결과 모두 선택")) }
                TextButton(onClick = { draft = draft.copy(members = emptyMap()); error = null }, enabled = !saving && draft.members.isNotEmpty(),
                    modifier = Modifier.testTag("group-select-none")) { Text(tr("모두 해제")) }
            }
            choices.forEach { (app, label) ->
                val chosen = app in draft.members
                val open = free(app)
                Row(Modifier.fillMaxWidth().toggleable(value = chosen, enabled = !saving && (open || chosen), role = Role.Checkbox) { on ->
                    draft = draft.copy(members = if (on) draft.members + (app to label) else draft.members - app); error = null
                }.testTag("group-app-$app").padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Checkbox(checked = chosen, onCheckedChange = null, enabled = !saving && (open || chosen))
                    AppIcon(app, label, 28.dp)
                    Column(Modifier.weight(1f)) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (open) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!open) Text(tr("%1\$s 그룹에 들어 있음", taken.getValue(app)), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (saved != null) TextButton(onClick = onDelete, enabled = !saving) { Text(tr("이 그룹 삭제"), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmAll) AlertDialog(onDismissRequest = { confirmAll = false }, title = { Text(tr("검색된 앱을 모두 선택할까요?")) },
        text = { Text(tr("선택한 앱은 모두 이 그룹의 하루 시간과 실행 횟수를 함께 쓰게 됩니다. 메신저·지도·인증 앱처럼 꼭 필요한 앱까지 막힐 수 있으니 목록을 꼭 확인해 주세요. 전화·설정 등 필수 앱은 목록에서 빠져 있어요.")) },
        confirmButton = { TextButton(onClick = {
            val room = AppGroup.MAX_MEMBERS - draft.members.size
            draft = draft.copy(members = draft.members + choices.filter { it.key !in draft.members && free(it.key) }.take(room).associate { it.key to it.value })
            error = null; confirmAll = false
        }, modifier = Modifier.testTag("group-select-all-confirm")) { Text(tr("모두 선택")) } },
        dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(tr("취소")) } })
}
