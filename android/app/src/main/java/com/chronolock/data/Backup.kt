package com.chronolock.data

import android.content.Context
import android.net.Uri
import com.chronolock.domain.AppGroup
import com.chronolock.domain.AppRule
import com.chronolock.domain.BlockedSite
import com.chronolock.domain.Strictness
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Rules, groups, blocked sites and preferences moved between devices. Usage history, focus and pauses stay behind. */
data class Backup(
    val rules: List<AppRule>,
    val groups: List<AppGroup>,
    val theme: String? = null,
    val language: String? = null,
    val strictMinutes: Int? = null,
    val sites: List<BlockedSite> = emptyList(),
)

object BackupCodec {
    private const val FORMAT = "chronolock-backup"
    // Version 2 adds blocked sites; version 1 files restore with none.
    private const val VERSION = 2

    fun encode(backup: Backup, exportedAt: Long): String = JSONObject()
        .put("format", FORMAT).put("version", VERSION).put("exportedAt", exportedAt)
        .put("rules", JSONArray(backup.rules.map { rule ->
            val entity = rule.copy(focusUntilEpochMillis = null, pausedUntilEpochMillis = null).toEntity()
            JSONObject().put("packageName", entity.packageName).put("appName", entity.appName)
                .putOpt("dailyLimitSeconds", entity.dailyLimitSeconds).putOpt("maxOpenCount", entity.maxOpenCount)
                .putOpt("cooldownSeconds", entity.cooldownSeconds).putOpt("sessionLimitSeconds", entity.sessionLimitSeconds)
                .putOpt("schedules", entity.schedules).put("enabled", entity.enabled)
        }))
        .put("groups", JSONArray(backup.groups.map { group ->
            JSONObject().put("name", group.name)
                .put("members", JSONArray(group.members.map { (app, name) -> JSONObject().put("packageName", app).put("appName", name) }))
                .putOpt("dailyLimitSeconds", group.dailyLimitSeconds).putOpt("maxOpenCount", group.maxOpenCount).put("enabled", group.enabled)
        }))
        .put("sites", JSONArray(backup.sites.map { JSONObject().put("domain", it.domain).put("enabled", it.enabled) }))
        .put("settings", JSONObject().putOpt("theme", backup.theme).putOpt("language", backup.language).putOpt("strictMinutes", backup.strictMinutes))
        .toString(2)

    /** Throws IllegalArgumentException for anything that is not a valid backup of a known version. */
    fun decode(text: String): Backup = try {
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT && root.optInt("version") in 1..VERSION)
        val rules = root.optJSONArray("rules").objects().map { item ->
            RuleEntity(item.getString("packageName"), item.getString("appName"), item.longOrNull("dailyLimitSeconds"),
                item.longOrNull("maxOpenCount")?.toInt(), item.longOrNull("cooldownSeconds"), item.optBoolean("enabled", true),
                item.longOrNull("sessionLimitSeconds"), schedules = item.stringOrNull("schedules")).toDomain()
        }
        val groups = root.optJSONArray("groups").objects().map { item ->
            AppGroup(0, item.getString("name"), item.getJSONArray("members").objects().associate { it.getString("packageName") to it.getString("appName") },
                item.longOrNull("dailyLimitSeconds"), item.longOrNull("maxOpenCount")?.toInt(), item.optBoolean("enabled", true))
        }
        val sites = root.optJSONArray("sites").objects().map { BlockedSite(it.getString("domain"), it.optBoolean("enabled", true)) }
        require(sites.map { it.domain }.toSet().size == sites.size)
        require(rules.map { it.packageName }.toSet().size == rules.size)
        val settings = root.optJSONObject("settings") ?: JSONObject()
        Backup(rules, groups, settings.stringOrNull("theme"), settings.stringOrNull("language"),
            settings.longOrNull("strictMinutes")?.toInt()?.takeIf { it in Strictness.DELAYS }, sites)
    } catch (invalid: org.json.JSONException) {
        throw IllegalArgumentException(INVALID, invalid)
    } catch (invalid: IllegalArgumentException) {
        throw IllegalArgumentException(INVALID, invalid)
    }

    const val INVALID = "ChronoLock 백업 파일이 아니거나 손상되었습니다."

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.longOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
    private fun JSONObject.stringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
}

/** Writes and restores backups through files the person picks with the system file picker. */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LocalRepository,
    private val preferences: Preferences,
) {
    suspend fun export(uri: Uri, nowEpochMillis: Long) {
        val backup = Backup(repository.rules.first().map { it.toDomain() }, repository.groups.first().map { it.toDomain() },
            preferences.theme.first().name, preferences.language.first().name, preferences.strict.first().delayAt(nowEpochMillis),
            repository.sites.first().map { it.toDomain() })
        val text = BackupCodec.encode(backup, nowEpochMillis)
        withContext(Dispatchers.IO) {
            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(text.toByteArray()) }
        }
    }

    /** Replaces rules, groups and sites with the backup's; [excluded] apps (safety apps on this device) are left out. */
    suspend fun restore(uri: Uri, excluded: Set<String>, nowEpochMillis: Long) {
        val text = withContext(Dispatchers.IO) {
            requireNotNull(context.contentResolver.openInputStream(uri)).use { stream ->
                // A backup is a few kilobytes; refuse anything far larger instead of reading it all into memory.
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    require(out.size() <= MAX_BYTES) { BackupCodec.INVALID }
                }
                out.toString(Charsets.UTF_8.name())
            }
        }
        val backup = BackupCodec.decode(text)
        val groups = backup.groups.mapNotNull { group ->
            // Filter first: a group left with fewer than two members cannot even be constructed.
            group.members.filterKeys { it !in excluded }.takeIf { it.size >= 2 }?.let { group.copy(members = it) }
        }
        repository.replaceAll(backup.rules.filter { it.packageName !in excluded }, groups, backup.sites, nowEpochMillis)
        backup.theme?.let { preferences.setTheme(AppTheme.fromStored(it)) }
        backup.language?.let { preferences.setLanguage(AppLanguage.fromStored(it)) }
        backup.strictMinutes?.let { minutes -> preferences.updateStrict { it.request(minutes, nowEpochMillis) } }
    }

    private companion object { const val MAX_BYTES = 1_000_000 }
}
