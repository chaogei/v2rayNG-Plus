package com.v2ray.ang.handler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileIndexMaintenanceTest {

    private val prefix = "SUB_SERVERS_"

    @Test
    fun `group index keys ignore unrelated main storage keys`() {
        val keys = arrayOf(
            "SELECTED_SERVER",
            "SUB_IDS",
            "WEBDAV_CONFIG",
            "SUB_SERVERS___default_subscription__",
            "SUB_SERVERS_abc",
        )

        assertEquals(
            listOf("SUB_SERVERS___default_subscription__", "SUB_SERVERS_abc"),
            ProfileIndexMaintenance.groupIndexKeys(keys, prefix),
        )
    }

    @Test
    fun `group index keys tolerate an unreadable key list`() {
        assertEquals(emptyList<String>(), ProfileIndexMaintenance.groupIndexKeys(null, prefix))
        assertEquals(emptyList<String>(), ProfileIndexMaintenance.groupIndexKeys(emptyArray(), prefix))
    }

    @Test
    fun `group index keys include indexes no subscription owns anymore`() {
        val keys = arrayOf("SUB_SERVERS_gone", "SUB_SERVERS_live")

        assertEquals(
            listOf("SUB_SERVERS_gone", "SUB_SERVERS_live"),
            ProfileIndexMaintenance.groupIndexKeys(keys, prefix),
        )
    }

    @Test
    fun `selection is invalidated when it is part of the removal`() {
        assertTrue(ProfileIndexMaintenance.selectionRemoved("guid-2", listOf("guid-1", "guid-2")))
        assertTrue(ProfileIndexMaintenance.selectionRemoved("guid-2", setOf("guid-2")))
    }

    @Test
    fun `selection survives a removal that does not touch it`() {
        assertFalse(ProfileIndexMaintenance.selectionRemoved("guid-3", listOf("guid-1", "guid-2")))
        assertFalse(ProfileIndexMaintenance.selectionRemoved("guid-3", emptyList()))
    }

    @Test
    fun `an absent selection is never reported as removed`() {
        assertFalse(ProfileIndexMaintenance.selectionRemoved(null, listOf("guid-1")))
        assertFalse(ProfileIndexMaintenance.selectionRemoved("", listOf("guid-1", "")))
        assertFalse(ProfileIndexMaintenance.selectionRemoved("   ", listOf("   ")))
    }
}
