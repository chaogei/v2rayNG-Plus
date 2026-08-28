package com.v2ray.ang.core

import com.v2ray.ang.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunModeLabelsTest {

    @Test
    fun rootModeWinsOverTheVpnFlag() {
        assertEquals(
            R.string.run_mode_root,
            RunModeLabels.modeLabel(rootMode = true, vpnMode = true)
        )
        assertEquals(
            R.string.summary_run_mode_root,
            RunModeLabels.modeSummary(rootMode = true, vpnMode = true)
        )
    }

    @Test
    fun vpnAndProxyOnlyAreTheRemainingTransports() {
        assertEquals(
            R.string.run_mode_vpn,
            RunModeLabels.modeLabel(rootMode = false, vpnMode = true)
        )
        assertEquals(
            R.string.run_mode_proxy_only,
            RunModeLabels.modeLabel(rootMode = false, vpnMode = false)
        )
        assertEquals(
            R.string.summary_run_mode_vpn,
            RunModeLabels.modeSummary(rootMode = false, vpnMode = true)
        )
        assertEquals(
            R.string.summary_run_mode_proxy_only,
            RunModeLabels.modeSummary(rootMode = false, vpnMode = false)
        )
    }

    @Test
    fun directRunIsNamedEvenWhenAProfileIsStillSelected() {
        assertEquals(
            R.string.title_local_proxy_direct,
            RunModeLabels.outboundLabel(directOnly = true, remarks = "Tokyo 01")
        )
    }

    @Test
    fun missingRemarksReadAsNoProfileSelected() {
        listOf(null, "", "  ").forEach { remarks ->
            assertEquals(
                R.string.run_state_no_profile,
                RunModeLabels.outboundLabel(directOnly = false, remarks = remarks)
            )
        }
    }

    @Test
    fun aSelectedProfileIsShownByItsOwnRemarks() {
        assertNull(RunModeLabels.outboundLabel(directOnly = false, remarks = "Tokyo 01"))
    }
}
