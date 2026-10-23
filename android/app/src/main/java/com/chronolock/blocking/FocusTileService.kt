package com.chronolock.blocking

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.chronolock.MainActivity
import com.chronolock.R
import com.chronolock.data.LocalRepository
import com.chronolock.data.Preferences
import com.chronolock.data.RuleEntity
import com.chronolock.feature.forLanguage
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

/** Quick settings toggle: starts a focus of the last length used in the app, or ends the running one outside strict mode. */
@AndroidEntryPoint
class FocusTileService : TileService() {
    @Inject lateinit var repository: LocalRepository
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var environment: AndroidEnvironment
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null

    override fun onStartListening() {
        listening?.cancel()
        listening = scope.launch {
            try {
                combine(repository.rules, preferences.focusMinutes, preferences.language) { rules, minutes, language -> Triple(rules, minutes, language) }
                    .collectLatest { (rules, minutes, language) ->
                        while (true) {
                            val end = render(rules, minutes, forLanguage(language)) ?: break
                            delay((end - System.currentTimeMillis()).coerceAtLeast(0) + 500) // Turn the tile off once the focus ends.
                        }
                    }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("ChronoLock", "Could not update the focus tile", error) }
        }
    }

    override fun onStopListening() { listening?.cancel(); listening = null }

    override fun onClick() { if (isLocked) unlockAndRun { toggle() } else toggle() }

    private fun toggle() {
        scope.launch {
            val localized = forLanguage(preferences.language.first())
            try {
                val now = environment.clock().epochMillis
                val rules = repository.rules.first()
                when {
                    focusEnd(rules, now) != null -> if (preferences.strict.first().delayAt(now) > 0 || preferences.lock.first().lockedAt(environment.clock())) {
                        Toast.makeText(this@FocusTileService, localized.getString(R.string.strict_focus_locked), Toast.LENGTH_SHORT).show()
                    } else repository.stopFocus(now)
                    // Without a rule or the permissions a focus cannot start; the app explains what is missing.
                    rules.none { it.enabled } || !environment.hasUsageAccess() || !environment.accessibilityEnabled() -> openApp()
                    else -> repository.startFocus(preferences.focusMinutes.first(), now)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                android.util.Log.w("ChronoLock", "Focus tile action failed", error)
                Toast.makeText(this@FocusTileService, localized.getString(R.string.tile_focus_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Draws the tile and returns when the running focus ends, or null without one. */
    private fun render(rules: List<RuleEntity>, minutes: Int, localized: android.content.Context): Long? {
        val tile = qsTile ?: return null
        val end = focusEnd(rules, System.currentTimeMillis())
        tile.label = localized.getString(R.string.tile_focus_label)
        tile.state = if (end != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (end != null) {
                val time = Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).toLocalTime()
                    .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(localized.resources.configuration.locales[0]))
                localized.getString(R.string.tile_focus_until, time)
            } else localized.getString(R.string.tile_focus_minutes, minutes)
        }
        tile.updateTile()
        return end
    }

    private fun focusEnd(rules: List<RuleEntity>, now: Long) =
        rules.filter { it.enabled }.mapNotNull { it.focusUntilEpochMillis }.filter { it > now }.maxOrNull()

    // The Intent overload is only reached below Android 14, where it is the sole option.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else @Suppress("DEPRECATION") startActivityAndCollapse(intent)
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
