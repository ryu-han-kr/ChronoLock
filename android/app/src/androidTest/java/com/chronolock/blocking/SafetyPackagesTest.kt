package com.chronolock.blocking

import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/** Calling and texting are basic functions: their apps are never limit targets and never blocked. */
class SafetyPackagesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val environment = AndroidEnvironment(context)

    @Test fun defaultDialerAndSmsApps_areSafety() {
        val safety = environment.safetyPackages()
        context.getSystemService(TelecomManager::class.java).defaultDialerPackage?.let { assertTrue(it, it in safety) }
        Telephony.Sms.getDefaultSmsPackage(context)?.let { assertTrue(it, it in safety) }
    }

    @Test fun dialerAndSmsApps_areNotOfferedAsLimitTargets() = kotlinx.coroutines.runBlocking {
        val offered = environment.installedApps().map { it.packageName }
        listOfNotNull(context.getSystemService(TelecomManager::class.java).defaultDialerPackage, Telephony.Sms.getDefaultSmsPackage(context))
            .forEach { assertTrue(it, it !in offered) }
    }
}
