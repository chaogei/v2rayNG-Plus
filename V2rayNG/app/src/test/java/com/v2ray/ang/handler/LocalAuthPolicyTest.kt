package com.v2ray.ang.handler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAuthPolicyTest {

    @Test
    fun incompleteCredentials_neverDisplayEnabled() {
        assertFalse(LocalAuthPolicy.displayEnabled(toggleOn = true, username = "user", password = ""))
        assertFalse(LocalAuthPolicy.displayEnabled(toggleOn = true, username = "", password = "pass"))
        assertFalse(LocalAuthPolicy.displayEnabled(toggleOn = false, username = "user", password = "pass"))
        assertTrue(LocalAuthPolicy.displayEnabled(toggleOn = true, username = "user", password = "pass"))
    }

    @Test
    fun persistEnabled_rejectsIncompleteTurnOn() {
        assertNull(LocalAuthPolicy.persistEnabled(wantOn = true, username = "user", password = " "))
        assertEquals(true, LocalAuthPolicy.persistEnabled(wantOn = true, username = "user", password = "pass"))
        assertEquals(false, LocalAuthPolicy.persistEnabled(wantOn = false, username = "user", password = "pass"))
    }
}
