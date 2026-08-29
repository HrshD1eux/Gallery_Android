package com.hrshd1eux.imava.core.util

import org.junit.Assert.assertNotNull
import org.junit.Test

class RootDetectionUtilTest {

    @Test
    fun testIsDeviceRooted_executesWithoutCrash() {
        // Runs on local JVM test environment safely
        val isRooted = RootDetectionUtil.isDeviceRooted()
        assertNotNull(isRooted)
    }
}
