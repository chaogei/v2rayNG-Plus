package com.v2ray.ang.core

import com.v2ray.ang.R

/**
 * Which messages a service start should surface, in order.
 *
 * The old if/else chain showed at most one of them, so with proxy sharing on the
 * "switched to proxy-only" notice never appeared. The snackbar host also replaces
 * the previous message instead of queueing, so the caller must join the returned
 * resources into a single toast rather than firing them one by one.
 */
object StartupToastPolicy {

    fun startMessages(
        proxySharing: Boolean,
        directOnly: Boolean,
        switchedToProxyOnly: Boolean,
    ): List<Int> {
        val messages = mutableListOf<Int>()
        if (directOnly) {
            messages += if (switchedToProxyOnly) {
                R.string.toast_local_direct_switched_to_proxy
            } else {
                R.string.title_local_proxy_direct
            }
        } else if (!proxySharing) {
            // The sharing warning replaces the plain "starting" line, matching the
            // upstream behavior; direct-mode notices are never dropped.
            messages += R.string.toast_services_start
        }
        if (proxySharing) {
            messages += R.string.toast_warning_pref_proxysharing_short
        }
        return messages
    }
}
