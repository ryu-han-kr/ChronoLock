package com.chronolock.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
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
    /** Look and feel beyond the day theme (version 3); null in older files, which leaves the current setting. */
    val nightTheme: String? = null,
    val followSystem: Boolean? = null,
    val favoriteThemes: Set<String>? = null,
    val mascot: String? = null,
    val reduceMotion: Boolean? = null,
)

object BackupCodec {
    private const val FORMAT = "chronolock-backup"
    // Version 2 adds blocked sites; version 3 the night theme, favourites, character and motion. Older files restore without them.
    private const val VERSION = 3

    fun encode(backup: Backup, exportedAt: Long): String = JSONObject()
        .put("format", FORMAT).put("version", VERSION).put("exportedAt", exportedAt)
        .put("rules", JSONArray(backup.rules.map { rule ->
            val entity = rule.copy(focusUntilEpochMillis = null, pausedUntilEpochMillis = null).toEntity()
            JSONObject().put("packageName", entity.packageName).put("appName", entity.appName)
                .putOpt("dailyLimitSeconds", entity.dailyLimitSeconds).putOpt("maxOpenCount", entity.maxOpenCount)
                .putOpt("cooldownSeconds", entity.cooldownSeconds).putOpt("sessionLimitSeconds", entity.sessionLimitSeconds)
                .putOpt("schedules", entity.schedules).put("enabled", entity.enabled)
                .putOpt("pauseSeconds", entity.pauseSeconds).putOpt("checkInMinutes", entity.checkInMinutes)
                .putOpt("weekendLimitSeconds", entity.weekendLimitSeconds)
        }))
        .put("groups", JSONArray(backup.groups.map { group ->
            JSONObject().put("name", group.name)
                .put("members", JSONArray(group.members.map { (app, name) -> JSONObject().put("packageName", app).put("appName", name) }))
                .putOpt("dailyLimitSeconds", group.dailyLimitSeconds).putOpt("maxOpenCount", group.maxOpenCount).put("enabled", group.enabled)
                .putOpt("schedules", group.toEntity().schedules)
        }))
        .put("sites", JSONArray(backup.sites.map { JSONObject().put("domain", it.domain).put("enabled", it.enabled) }))
        .put("settings", JSONObject().putOpt("theme", backup.theme).putOpt("language", backup.language).putOpt("strictMinutes", backup.strictMinutes)
            .putOpt("nightTheme", backup.nightTheme).putOpt("followSystem", backup.followSystem)
            .putOpt("favoriteThemes", backup.favoriteThemes?.let { JSONArray(it.sorted()) })
            .putOpt("mascot", backup.mascot).putOpt("reduceMotion", backup.reduceMotion))
        .toString(2)

    /** Throws IllegalArgumentException for anything that is not a valid backup of a known version. */
    fun decode(text: String): Backup = try {
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT && root.optInt("version") in 1..VERSION)
        val rules = root.optJSONArray("rules").objects().map { item ->
            RuleEntity(item.getString("packageName"), item.getString("appName"), item.longOrNull("dailyLimitSeconds"),
                item.longOrNull("maxOpenCount")?.toInt(), item.longOrNull("cooldownSeconds"), item.optBoolean("enabled", true),
                item.longOrNull("sessionLimitSeconds"), schedules = item.stringOrNull("schedules"),
                pauseSeconds = item.longOrNull("pauseSeconds")?.toInt(), checkInMinutes = item.longOrNull("checkInMinutes")?.toInt(),
                weekendLimitSeconds = item.longOrNull("weekendLimitSeconds")).toDomain()
        }
        val groups = root.optJSONArray("groups").objects().map { item ->
            AppGroup(0, item.getString("name"), item.getJSONArray("members").objects().associate { it.getString("packageName") to it.getString("appName") },
                item.longOrNull("dailyLimitSeconds"), item.longOrNull("maxOpenCount")?.toInt(), item.optBoolean("enabled", true),
                item.stringOrNull("schedules")?.let(::decodeSchedules).orEmpty())
        }
        val sites = root.optJSONArray("sites").objects().map { BlockedSite(it.getString("domain"), it.optBoolean("enabled", true)) }
        require(sites.map { it.domain }.toSet().size == sites.size)
        require(rules.map { it.packageName }.toSet().size == rules.size)
        val settings = root.optJSONObject("settings") ?: JSONObject()
        Backup(rules, groups, settings.stringOrNull("theme"), settings.stringOrNull("language"),
            settings.longOrNull("strictMinutes")?.toInt()?.takeIf { it in Strictness.DELAYS }, sites,
            nightTheme = settings.stringOrNull("nightTheme"), followSystem = settings.booleanOrNull("followSystem"),
            favoriteThemes = settings.optJSONArray("favoriteThemes")?.let { list -> (0 until list.length()).map { list.getString(it) }.toSet() },
            mascot = settings.stringOrNull("mascot"), reduceMotion = settings.booleanOrNull("reduceMotion"))
    } catch (invalid: org.json.JSONException) {
        throw IllegalArgumentException(INVALID, invalid)
    } catch (invalid: IllegalArgumentException) {
        throw IllegalArgumentException(INVALID, invalid)
    }

    const val INVALID = "ChronoLock 백업 파일이 아니거나 손상되었습니다."

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.longOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
    private fun JSONObject.stringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
    private fun JSONObject.booleanOrNull(key: String): Boolean? = if (has(key) && !isNull(key)) getBoolean(key) else null
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
            repository.sites.first().map { it.toDomain() }, nightTheme = preferences.nightTheme.first().name,
            followSystem = preferences.followSystem.first(), favoriteThemes = preferences.favoriteThemes.first(),
            mascot = preferences.mascot.first().name, reduceMotion = preferences.reduceMotion.first())
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
        backup.nightTheme?.let { preferences.setNightTheme(AppTheme.fromStored(it)) }
        backup.followSystem?.let { preferences.setFollowSystem(it) }
        backup.favoriteThemes?.let { preferences.setFavoriteThemes(it) }
        backup.mascot?.let { preferences.setMascot(MascotKind.fromStored(it)) }
        backup.reduceMotion?.let { preferences.setReduceMotion(it) }
    }

    /**
     * Writes the weekly backup into the picked folder, or the phone's Download/ChronoLock folder when none is picked,
     * replacing the previous one, once a week has passed or when [force]d. Returns false when nothing was due or the
     * phone (Android 9) cannot be written without a picked folder.
     */
    suspend fun autoBackup(nowEpochMillis: Long, force: Boolean = false): Boolean {
        val auto = preferences.autoBackup.first()
        if (!force && !auto.due(nowEpochMillis)) return false
        if (auto.folder == null) {
            if (android.os.Build.VERSION.SDK_INT < 29) return false
            val file = writePhoneFile(auto.phoneFile?.toUri(), nowEpochMillis)
            preferences.autoBackupWritten(nowEpochMillis, phoneFile = file.toString())
            return true
        }
        val tree = auto.folder.toUri()
        val file = withContext(Dispatchers.IO) { existingAutoFile(tree) ?: createAutoFile(tree) }
        export(file, nowEpochMillis)
        preferences.autoBackupWritten(nowEpochMillis)
        return true
    }

    /**
     * Replaces the Download file written last time, or adds one when it is gone, for example after the app was
     * reinstalled: Android then no longer lets the app open the old file, which stays for the person to restore.
     */
    @androidx.annotation.RequiresApi(29)
    private suspend fun writePhoneFile(previous: Uri?, nowEpochMillis: Long): Uri {
        if (previous != null) try { export(previous, nowEpochMillis); return previous }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { }
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, AUTO_FILE)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_DOWNLOADS}/ChronoLock")
        }
        val uri = withContext(Dispatchers.IO) {
            requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        }
        export(uri, nowEpochMillis)
        return uri
    }

    private fun existingAutoFile(tree: Uri): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { rows ->
            while (rows.moveToNext()) if (rows.getString(1) == AUTO_FILE) return DocumentsContract.buildDocumentUriUsingTree(tree, rows.getString(0))
        }
        return null
    }

    private fun createAutoFile(tree: Uri): Uri = requireNotNull(DocumentsContract.createDocument(context.contentResolver,
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)), "application/json", AUTO_FILE))

    companion object {
        /** Android 10 and later let the app add files to Download without a storage permission. */
        val PHONE_BACKUP_SUPPORTED = android.os.Build.VERSION.SDK_INT >= 29
        private const val MAX_BYTES = 1_000_000
        private const val AUTO_FILE = "chronolock-backup-auto.json"
    }
}
