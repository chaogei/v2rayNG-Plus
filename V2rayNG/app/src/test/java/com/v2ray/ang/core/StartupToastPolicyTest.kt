package com.v2ray.ang.core

import com.v2ray.ang.R
import org.junit.Assert.assertEquals
import org.junit.Test

class StartupToastPolicyTest {

    @Test
    fun normalStart_showsStartLine() {
        assertEquals(
            listOf(R.string.toast_services_start),
            StartupToastPolicy.startMessages(
                proxySharing = false,
                directOnly = false,
                switchedToProxyOnly = false,
            )
        )
    }

    @Test
    fun sharingWithoutDirect_showsOnlySharingWarning() {
        assertEquals(
            listOf(R.string.toast_warning_pref_proxysharing_short),
            StartupToastPolicy.startMessages(
                proxySharing = true,
                directOnly = false,
                switchedToProxyOnly = false,
            )
        )
    }

    @Test
    fun directStart_showsDirectLine() {
        assertEquals(
            listOf(R.string.title_local_proxy_direct),
            StartupToastPolicy.startMessages(
                proxySharing = false,
                directOnly = true,
                switchedToProxyOnly = false,
            )
        )
    }

    @Test
    fun directSwitchedToProxyOnly_showsSwitchNotice() {
        assertEquals(
            listOf(R.string.toast_local_direct_switched_to_proxy),
            StartupToastPolicy.startMessages(
                proxySharing = false,
                directOnly = true,
                switchedToProxyOnly = true,
            )
        )
    }

    @Test
    fun sharingNeverSwallowsDirectNotices() {
        assertEquals(
            listOf(
                R.string.toast_local_direct_switched_to_proxy,
                R.string.toast_warning_pref_proxysharing_short,
            ),
            StartupToastPolicy.startMessages(
                proxySharing = true,
                directOnly = true,
                switchedToProxyOnly = true,
            )
        )
        assertEquals(
            listOf(
                R.string.title_local_proxy_direct,
                R.string.toast_warning_pref_proxysharing_short,
            ),
            StartupToastPolicy.startMessages(
                proxySharing = true,
                directOnly = true,
                switchedToProxyOnly = false,
            )
        )
    }
}
