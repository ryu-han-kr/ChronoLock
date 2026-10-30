package com.chronolock.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronolock.domain.AppRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** A focus that runs out banks ten minutes once; a stopped one banks nothing; spending takes them out. */
@RunWith(AndroidJUnit4::class)
class TimeBankStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val minute = 60_000L

    @Test fun finishedFocus_banksOnce_andStoppedFocusBanksNothing(): Unit = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ChronoDatabase::class.java).build()
        val preferences = Preferences(context)
        try {
            preferences.spendTimeBank(preferences.timeBank.first())
            val repository = LocalRepository(database)
            repository.saveRule(AppRule("com.video", "Video", maxOpenCount = 3))
            val start = System.currentTimeMillis() - 60 * minute

            repository.startFocus(25, start)
            TimeBankStore.settle(repository, preferences, start + 24 * minute)
            assertEquals("still running", 0, preferences.timeBank.first())
            TimeBankStore.settle(repository, preferences, start + 25 * minute)
            TimeBankStore.settle(repository, preferences, start + 30 * minute)
            assertEquals("counted once", 10, preferences.timeBank.first())

            repository.startFocus(30, start + 31 * minute)
            repository.stopFocus(start + 40 * minute)
            TimeBankStore.settle(repository, preferences, start + 70 * minute)
            assertEquals("stopped early", 10, preferences.timeBank.first())

            assertTrue(preferences.spendTimeBank(10))
            assertFalse(preferences.spendTimeBank(10))
            assertEquals(0, preferences.timeBank.first())
        } finally {
            preferences.spendTimeBank(preferences.timeBank.first())
            database.close()
        }
    }
}
