package com.v2ray.ang.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalProxyDirectPolicyTest {

    @Test
    fun emptySelection_isDirectOnly() {
        assertTrue(LocalProxyDirectPolicy.isDirectOnly(explicitFlag = false, selectedGuid = null))
        assertTrue(LocalProxyDirectPolicy.isDirectOnly(explicitFlag = false, selectedGuid = ""))
        assertFalse(LocalProxyDirectPolicy.isDirectOnly(explicitFlag = false, selectedGuid = "guid"))
    }

    @Test
    fun explicitFlag_isDirectOnlyEvenWithSelection() {
        assertTrue(LocalProxyDirectPolicy.isDirectOnly(explicitFlag = true, selectedGuid = "guid"))
    }

    @Test
    fun vpnDirectStart_switchesToProxyOnly_unlessRoot() {
        assertTrue(
            LocalProxyDirectPolicy.shouldSwitchVpnToProxyOnly(
                directOnly = true,
                vpnMode = true,
                rootMode = false,
            )
        )
        assertFalse(
            LocalProxyDirectPolicy.shouldSwitchVpnToProxyOnly(
                directOnly = true,
                vpnMode = true,
                rootMode = true,
            )
        )
        assertFalse(
            LocalProxyDirectPolicy.shouldSwitchVpnToProxyOnly(
                directOnly = true,
                vpnMode = false,
                rootMode = false,
            )
        )
        assertFalse(
            LocalProxyDirectPolicy.shouldSwitchVpnToProxyOnly(
                directOnly = false,
                vpnMode = true,
                rootMode = false,
            )
        )
    }
}
