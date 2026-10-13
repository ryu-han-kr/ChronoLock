package com.chronolock.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronolock.domain.AppRule
import com.chronolock.domain.ClockReading
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalRepositoryTest {
    private lateinit var database: ChronoDatabase
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "repository-test-${UUID.randomUUID()}.db"
    private val origin = Instant.parse("2026-10-08T09:00:00Z").toEpochMilli()
    private fun clock(ms: Long, epoch: Long = origin + ms, boot: Int = 1) =
        ClockReading(epoch, ms, boot, ZoneId.of("Asia/Seoul"))

    @Before fun setup() {
        database = openDatabase()
    }
    @After fun close() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase() = Room.databaseBuilder(context, ChronoDatabase::class.java, databaseName).build()

    @Test fun databaseReopen_restoresRuleCountersAndCooldown() = runBlocking {
        val rule = AppRule("test.app", "Test", dailyLimitSeconds = 60, maxOpenCount = 3, cooldownSeconds = 30)
        val first = LocalRepository(database)
        first.saveRule(rule)
        first.foreground("test.app", clock(0))
        first.foreground(null, clock(2000))
        database.close()

        database = openDatabase()
        assertEquals(rule, database.dao().rules().single().toDomain())
        val restored = LocalRepository(database)
        restored.reconnect(clock(3000), emptyMap())
        val blocked = restored.foreground("test.app", clock(4000))
        assertTrue(blocked.decision.denied)
        assertEquals(28L, blocked.decision.retryAfterSeconds)
        assertEquals(2000L, database.dao().daily().single().usageMillis)
        assertEquals(1, database.dao().daily().single().opens)
        assertEquals(1, database.dao().daily().single().blockedAttempts)
        assertFalse(restored.tick(clock(32000)).decision.denied)
        assertEquals(2, database.dao().daily().single().opens)
    }

    @Test fun newRepository_restoresCountersAndDoesNotDoubleCountActiveOpen() = runBlocking {
        val first = LocalRepository(database)
        first.saveRule(AppRule("test.app", "Test", dailyLimitSeconds = 60, maxOpenCount = 3))
        first.foreground("test.app", clock(0))
        first.tick(clock(1500))
        val second = LocalRepository(database)
        val restored = second.reconnect(clock(2000), mapOf("test.app" to 500L), "test.app")
        assertEquals(2000L, restored.daily.values.single().usageMillis)
        assertEquals(1, restored.daily.values.single().opens)
        second.tick(clock(3000))
        assertEquals(3000L, database.dao().daily().single().usageMillis)
    }

    @Test fun newRepository_restoresCooldownAndBlockedAttempts() = runBlocking {
        val first = LocalRepository(database)
        first.saveRule(AppRule("test.app", "Test", cooldownSeconds = 30))
        first.foreground("test.app", clock(0))
        first.foreground(null, clock(1000))
        val second = LocalRepository(database)
        second.reconnect(clock(2000), emptyMap())
        val blocked = second.foreground("test.app", clock(3000))
        assertTrue(blocked.decision.denied)
        assertEquals(1, database.dao().daily().single().blockedAttempts)
        assertFalse(second.tick(clock(31000)).decision.denied)
    }

    @Test fun clearAll_removesRulesEventsCheckpointsAndActiveState() = runBlocking {
        val repository = LocalRepository(database)
        repository.saveRule(AppRule("test.app", "Test", dailyLimitSeconds = 1))
        repository.foreground("test.app", clock(0))
        repository.tick(clock(1000))
        repository.clearAll()
        assertTrue(database.dao().rules().isEmpty())
        assertTrue(database.dao().daily().isEmpty())
        assertNull(database.dao().checkpoint())
        assertFalse(repository.tick(clock(2000)).decision.denied)
    }

    @Test fun databaseReopen_afterRebootDoesNotReuseActiveOpen() = runBlocking {
        val first = LocalRepository(database)
        first.saveRule(AppRule("test.app", "Test", maxOpenCount = 1))
        first.foreground("test.app", clock(0))
        first.tick(clock(1000))
        database.close()
        database = openDatabase()

        val restored = LocalRepository(database).reconnect(clock(100, origin + 10000, 2), emptyMap(), "test.app")
        assertTrue(restored.decision.denied)
        assertNull(database.dao().checkpoint()?.activePackage)
        assertEquals(1, database.dao().daily().single().opens)
        assertEquals(1, database.dao().daily().single().blockedAttempts)
        assertEquals(1000L, database.dao().daily().single().usageMillis)
    }

    @Test fun recoveredDailyLimit_isBlockedAndPersistedImmediately() = runBlocking {
        val first = LocalRepository(database)
        first.saveRule(AppRule("test.app", "Test", dailyLimitSeconds = 2))
        first.foreground("test.app", clock(0))
        first.tick(clock(1000))

        val restored = LocalRepository(database).reconnect(clock(2000), mapOf("test.app" to 1000L), "test.app")
        assertTrue(restored.decision.denied)
        assertNull(database.dao().checkpoint()?.activePackage)
        assertEquals(2000L, database.dao().daily().single().usageMillis)
        assertEquals(1, database.dao().daily().single().blockedAttempts)
    }
}
