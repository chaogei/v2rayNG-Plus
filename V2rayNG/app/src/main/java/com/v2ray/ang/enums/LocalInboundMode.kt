package com.v2ray.ang.enums

/**
 * Local inbound (local server proxy) listening modes.
 *
 * MIXED      - single port serving SOCKS and HTTP together.
 *              On Xray the "socks" inbound natively accepts SOCKS4/4a/5 and HTTP
 *              on the same port; on other cores a separate HTTP inbound is opened
 *              on port+1 to emulate the same behavior.
 * SOCKS_HTTP - two inbounds on two ports: SOCKS on the SOCKS port and
 *              HTTP on a separately configurable HTTP port.
 * SOCKS      - SOCKS inbound only.
 * HTTP       - HTTP inbound only. Note that VPN (hev-tun) and root modes still
 *              require an internal SOCKS inbound which is kept automatically.
 */
enum class LocalInboundMode(val value: String) {
    MIXED("mixed"),
    SOCKS_HTTP("socks_http"),
    SOCKS("socks"),
    HTTP("http");

    companion object {
        fun fromValue(value: String?): LocalInboundMode =
            entries.firstOrNull { it.value == value } ?: MIXED
    }
}
