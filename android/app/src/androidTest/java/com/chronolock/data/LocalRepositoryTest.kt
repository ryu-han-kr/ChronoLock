package com.chronolock.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronolock.domain.AppRule
import com.chronolock.domain.ClockReading
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalRepositoryTest {
    private lateinit var database: ChronoDatabase
    private val origin = Instant.parse("2026-10-08T09:00:00Z").toEpochMilli()
    private fun clock(ms: Long) = ClockReading(origin + ms, ms, 1, ZoneId.of("Asia/Seoul"))

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ChronoDatabase::class.java).build()
    }
    @After fun close() { database.close() }

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
}
