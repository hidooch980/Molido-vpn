package com.molido.vpn.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun newerVersionDetection() {
        assertTrue(UpdateManager.isNewerVersion("0.3.0", "0.2.1"))
        assertTrue(UpdateManager.isNewerVersion("0.2.0", "0.1.0"))
        assertTrue(UpdateManager.isNewerVersion("0.1.1", "0.1.0"))
        assertTrue(UpdateManager.isNewerVersion("1.0.0", "0.1.0"))
        assertTrue(UpdateManager.isNewerVersion("0.10.0", "0.9.0"))
    }

    @Test
    fun sameOrOlderVersionNotFlagged() {
        assertFalse(UpdateManager.isNewerVersion("0.1.0", "0.1.0"))
        assertFalse(UpdateManager.isNewerVersion("0.0.9", "0.1.0"))
        assertFalse(UpdateManager.isNewerVersion("0.0.1", "0.1.0"))
        assertFalse(UpdateManager.isNewerVersion("", "0.1.0"))
    }
}
