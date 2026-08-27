package com.v2ray.ang.core

/**
 * Rules for the "local proxy · direct" run: no remote node, freedom outbound.
 *
 * Starting that run while the app is in VPN mode would create a system-wide
 * tun that just sends every packet out directly. That surprises people who
 * only wanted a local SOCKS/HTTP port, so a direct start in VPN mode is
 * rewritten to proxy-only unless root mode is on (root does not use VpnService).
 */
object LocalProxyDirectPolicy {

    fun isDirectOnly(explicitFlag: Boolean, selectedGuid: String?): Boolean {
        return explicitFlag || selectedGuid.isNullOrEmpty()
    }

    fun shouldSwitchVpnToProxyOnly(
        directOnly: Boolean,
        vpnMode: Boolean,
        rootMode: Boolean,
    ): Boolean {
        return directOnly && vpnMode && !rootMode
    }
}
