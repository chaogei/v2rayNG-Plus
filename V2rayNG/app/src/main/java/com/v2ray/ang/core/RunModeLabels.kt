package com.v2ray.ang.core

import androidx.annotation.StringRes
import com.v2ray.ang.R

/**
 * One vocabulary for "how is this going to run", shared by the settings mode row and
 * the main status line.
 *
 * VPN / Proxy only / Root / Local proxy · Direct were each described in their own
 * words wherever they happened to be shown, so the same run looked like four different
 * features. Resolving the wording here keeps the settings row, the start toast and the
 * status line saying the same thing.
 */
object RunModeLabels {

    /** Short name of the transport the next start would use. */
    @StringRes
    fun modeLabel(rootMode: Boolean, vpnMode: Boolean): Int = when {
        rootMode -> R.string.run_mode_root
        vpnMode -> R.string.run_mode_vpn
        else -> R.string.run_mode_proxy_only
    }

    /** One line saying what that transport does, for the settings mode row. */
    @StringRes
    fun modeSummary(rootMode: Boolean, vpnMode: Boolean): Int = when {
        rootMode -> R.string.summary_run_mode_root
        vpnMode -> R.string.summary_run_mode_vpn
        else -> R.string.summary_run_mode_proxy_only
    }

    /**
     * The outbound half of the status line.
     *
     * @return the resource to show, or null when the profile remarks should be shown as is.
     */
    @StringRes
    fun outboundLabel(directOnly: Boolean, remarks: String?): Int? = when {
        directOnly -> R.string.title_local_proxy_direct
        remarks.isNullOrBlank() -> R.string.run_state_no_profile
        else -> null
    }
}
