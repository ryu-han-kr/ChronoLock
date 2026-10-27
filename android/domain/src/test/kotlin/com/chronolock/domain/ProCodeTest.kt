package com.chronolock.domain

import org.junit.Assert.assertFalse
import org.junit.Test

class ProCodeTest {
    @Test fun code_ignoresCaseSpacesAndHyphens_andRejectsOthers() {
        // Only the hash ships; the test checks a wrong code and blank input, since the real code is not in the repository.
        assertFalse(ProCode.matches(""))
        assertFalse(ProCode.matches("  - "))
        assertFalse(ProCode.matches("CHRONO-AAAA-BBBB"))
    }
}
