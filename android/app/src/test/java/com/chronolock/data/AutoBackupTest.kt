package com.chronolock.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupTest {
    private val week = AutoBackup.WEEK_MILLIS

    @Test fun firstBackup_isDue() {
        assertTrue(AutoBackup("tree", lastAt = null).due(0))
    }

    @Test fun backup_isDueOnceAWeekHasPassed() {
        val auto = AutoBackup("tree", lastAt = 1_000)
        assertFalse(auto.due(1_000))
        assertFalse(auto.due(1_000 + week - 1))
        assertTrue(auto.due(1_000 + week))
    }

    @Test fun clockMovedBack_writesAgain() {
        // Otherwise a clock set back a year would skip backups until it caught up.
        assertTrue(AutoBackup("tree", lastAt = 1_000 + week).due(1_000))
    }
}
