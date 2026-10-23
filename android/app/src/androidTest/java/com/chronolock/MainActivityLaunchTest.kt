package com.chronolock

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Test

/** The real activity starts and resumes; catches work done before the activity has a context (0.8.0 update manager). */
class MainActivityLaunchTest {
    @Test fun mainActivity_launchesAndResumes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
