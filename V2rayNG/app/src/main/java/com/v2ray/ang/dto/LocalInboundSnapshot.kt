package com.v2ray.ang.dto

import com.v2ray.ang.enums.LocalInboundMode
import com.v2ray.ang.util.Utils

/**
 * Immutable snapshot of every local-inbound related preference.
 *
 * Hot paths (config generation on connect/switch/reload, service startup)
 * consume this snapshot so each MMKV key is read exactly once and the
 * mode/port/auth layout is derived in a single place, instead of every call
 * site re-reading and re-deriving the same state.
 */
data class LocalInboundSnapshot(
    val mode: LocalInboundMode,
    val listenAddress: String,
    val socksPort: Int,
    /** Dedicated HTTP inbound port with SOCKS-port collisions already resolved. */
    val httpInboundPort: Int,
    /** True only when the auth toggle is on and both credentials are non-empty. */
    val authEnabled: Boolean,
    val username: String?,
    val password: String?,
    val socksUdpEnabled: Boolean,
    val redirEnabled: Boolean,
    val redirPort: Int,
) {
    /**
     * The local port that serves HTTP proxy requests in this mode, used by the
     * app's own HTTP-through-proxy features and the VPN "append HTTP proxy"
     * option. Returns 0 when no local port serves HTTP (SOCKS-only mode on
     * non-Xray cores).
     */
    val effectiveHttpPort: Int
        get() = when (mode) {
            // Xray's socks inbound natively accepts HTTP on the same port;
            // other cores get a dedicated HTTP inbound next to it.
            LocalInboundMode.MIXED -> if (Utils.isXray()) socksPort else neighborPort(socksPort)
            LocalInboundMode.SOCKS_HTTP, LocalInboundMode.HTTP -> httpInboundPort
            LocalInboundMode.SOCKS -> if (Utils.isXray()) socksPort else 0
        }

    companion object {
        /**
         * A distinct port next to [port], used when a derived inbound would
         * otherwise collide with the SOCKS port. Steps down at the top of the
         * range so 65535 does not derive the invalid port 65536.
         */
        fun neighborPort(port: Int): Int = if (port >= 65535) port - 1 else port + 1
    }
}
