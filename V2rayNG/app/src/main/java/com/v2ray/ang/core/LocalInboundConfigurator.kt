package com.v2ray.ang.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.LocalInboundSnapshot
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.enums.LocalInboundMode
import com.v2ray.ang.util.JsonUtil

/**
 * Pure inbound-layout rules used by both the unified config path and CUSTOM
 * JSON profiles. No MMKV / Android dependencies: every decision is taken from
 * [LocalInboundSnapshot] plus [InboundRuntimeFlags].
 */
object LocalInboundConfigurator {

    data class InboundRuntimeFlags(
        val vpn: Boolean = false,
        val useHev: Boolean = false,
        val rootMode: Boolean = false,
        val rootLanSharing: Boolean = false,
        val enableLocalProxyPref: Boolean = true,
        val isXray: Boolean = true,
        val fakeDns: Boolean = false,
        val sniffAllTlsAndHttp: Boolean = true,
        val routeOnly: Boolean = false,
        val vpnMtu: Int = AppConfig.VPN_MTU,
    ) {
        val forcedByHev: Boolean get() = vpn && useHev
        val forcedBySocksRoot: Boolean get() = rootMode || rootLanSharing
        val enableLocalProxy: Boolean get() = forcedByHev || forcedBySocksRoot || enableLocalProxyPref

        /**
         * When the SOCKS inbound is the tunnel target (hev-tun VPN or root mode),
         * every UDP packet of the device - including DNS - arrives as a SOCKS UDP
         * associate. Honoring a disabled "SOCKS UDP" preference there would break
         * DNS and kill the whole tunnel, so UDP is forced on for those runs.
         */
        val forceSocksUdp: Boolean get() = forcedByHev || forcedBySocksRoot
        val needTun: Boolean get() = vpn && !useHev
    }

    data class ApplyResult(
        val inbounds: List<V2rayConfig.InboundBean>,
    )

    /**
     * Build the inbound list that should be present after applying the snapshot.
     *
     * [existing] is the current inbound list (template or CUSTOM). SOCKS / HTTP /
     * dokodemo-door entries are replaced; every other inbound (tun, dokodemo
     * used as something else, etc.) is kept.
     */
    fun apply(
        existing: List<V2rayConfig.InboundBean>,
        snapshot: LocalInboundSnapshot,
        flags: InboundRuntimeFlags,
    ): ApplyResult {
        val kept = existing.filterNot { isManagedLocalInbound(it) }.map { copyInbound(it) }.toMutableList()
        val template = existing.firstOrNull { it.protocol == AppConfig.PROTOCOL_SOCKS }
            ?: existing.firstOrNull()
        val sniffing = buildSniffing(template?.sniffing, flags)
        val userLevel = template?.settings?.userLevel ?: 8
        val authAccounts = authAccounts(snapshot)

        val socksRequired = flags.forcedByHev || flags.forcedBySocksRoot || snapshot.mode != LocalInboundMode.HTTP
        val httpRequired = when (snapshot.mode) {
            LocalInboundMode.MIXED -> !flags.isXray
            LocalInboundMode.SOCKS_HTTP, LocalInboundMode.HTTP -> true
            LocalInboundMode.SOCKS -> false
        }

        if (flags.enableLocalProxy && socksRequired) {
            val socksIsInternalOnly = snapshot.mode == LocalInboundMode.HTTP
            kept.add(
                0,
                V2rayConfig.InboundBean(
                    tag = AppConfig.TAG_SOCKS_INBOUND,
                    port = snapshot.socksPort,
                    protocol = AppConfig.PROTOCOL_SOCKS,
                    listen = if (socksIsInternalOnly) AppConfig.LOOPBACK else snapshot.listenAddress,
                    settings = V2rayConfig.InboundBean.InSettingsBean(
                        auth = if (authAccounts != null) "password" else "noauth",
                        udp = snapshot.socksUdpEnabled || flags.forceSocksUdp,
                        userLevel = userLevel,
                        accounts = authAccounts,
                    ),
                    sniffing = sniffing?.let { copySniffing(it) },
                )
            )
        }

        if (flags.enableLocalProxy && httpRequired) {
            val httpPort = if (snapshot.mode == LocalInboundMode.MIXED) {
                LocalInboundSnapshot.neighborPort(snapshot.socksPort)
            } else {
                snapshot.httpInboundPort
            }
            kept.add(
                V2rayConfig.InboundBean(
                    tag = AppConfig.TAG_HTTP_INBOUND,
                    port = httpPort,
                    protocol = AppConfig.PROTOCOL_HTTP,
                    listen = snapshot.listenAddress,
                    settings = V2rayConfig.InboundBean.InSettingsBean(
                        userLevel = userLevel,
                        accounts = authAccounts,
                    ),
                    sniffing = sniffing?.let { copySniffing(it) },
                )
            )
        }

        if (flags.enableLocalProxy && snapshot.redirEnabled) {
            val redirPort = snapshot.redirPort
            if (kept.any { it.port == redirPort }) {
                error("Transparent redirect port $redirPort conflicts with another local inbound port")
            }
            kept.add(
                V2rayConfig.InboundBean(
                    tag = AppConfig.TAG_REDIR_INBOUND,
                    port = redirPort,
                    protocol = AppConfig.PROTOCOL_DOKODEMO,
                    listen = AppConfig.LOOPBACK,
                    settings = V2rayConfig.InboundBean.InSettingsBean(
                        network = "tcp,udp",
                        followRedirect = true,
                        userLevel = userLevel,
                    ),
                    sniffing = sniffing?.let { copySniffing(it) },
                )
            )
        }

        if (flags.needTun) {
            kept.firstOrNull { it.tag == "tun" }?.let { tun ->
                val settings = tun.settings ?: V2rayConfig.InboundBean.InSettingsBean().also { tun.settings = it }
                settings.mtu = flags.vpnMtu
                tun.sniffing = sniffing?.let { copySniffing(it) }
            }
        }

        return ApplyResult(kept)
    }

    fun applyToConfig(
        v2rayConfig: V2rayConfig,
        snapshot: LocalInboundSnapshot,
        flags: InboundRuntimeFlags,
    ) {
        val result = apply(v2rayConfig.inbounds, snapshot, flags)
        v2rayConfig.inbounds.clear()
        v2rayConfig.inbounds.addAll(result.inbounds)
    }

    /**
     * Replace managed local inbounds inside a CUSTOM JSON document while
     * leaving every other inbound (and every other top-level field) intact.
     */
    fun applyToCustomJson(
        json: JsonObject,
        snapshot: LocalInboundSnapshot,
        flags: InboundRuntimeFlags,
        tunTemplate: V2rayConfig.InboundBean? = null,
    ) {
        val source = json.get("inbounds")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
        val keptCustom = JsonArray()
        var hasTun = false
        source.forEach { elem ->
            val obj = elem.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            if (isManagedLocalInboundJson(obj)) return@forEach
            if (jsonString(obj, "protocol") == "tun" || jsonString(obj, "tag") == "tun") {
                hasTun = true
            }
            keptCustom.add(obj)
        }
        if (flags.needTun && !hasTun) {
            tunTemplate?.let { template ->
                JsonUtil.parseString(JsonUtil.toJson(template))?.takeIf { it.isJsonObject }?.let { keptCustom.add(it) }
            }
        }

        val existingBeans = keptCustom.mapNotNull { elem ->
            elem.takeIf { it.isJsonObject }?.let {
                JsonUtil.fromJson(it.toString(), V2rayConfig.InboundBean::class.java)
            }
        }
        val result = apply(existingBeans, snapshot, flags)
        val array = JsonArray()
        result.inbounds.forEach { inbound ->
            when {
                isManagedLocalInbound(inbound) -> {
                    inboundToJson(inbound)?.let { array.add(it) }
                }
                inbound.protocol == "tun" || inbound.tag == "tun" -> {
                    val original = keptCustom.firstOrNull { elem ->
                        val obj = elem.asJsonObject
                        jsonString(obj, "protocol") == "tun" || jsonString(obj, "tag") == "tun"
                    }
                    if (original != null) {
                        array.add(applyTunOverlay(original.asJsonObject, inbound))
                    } else {
                        inboundToJson(inbound)?.let { array.add(it) }
                    }
                }
                else -> {
                    val original = findOriginalInbound(keptCustom, inbound)
                    if (original != null) {
                        array.add(original)
                    } else {
                        inboundToJson(inbound)?.let { array.add(it) }
                    }
                }
            }
        }
        json.add("inbounds", array)
    }

    private fun inboundToJson(inbound: V2rayConfig.InboundBean): JsonObject? {
        return JsonUtil.parseString(JsonUtil.toJson(inbound))?.takeIf { it.isJsonObject }?.asJsonObject
    }

    private fun findOriginalInbound(
        keptCustom: JsonArray,
        inbound: V2rayConfig.InboundBean,
    ): JsonObject? {
        val byTag = keptCustom.firstOrNull { elem ->
            jsonString(elem.asJsonObject, "tag") == inbound.tag && inbound.tag.isNotEmpty()
        }?.asJsonObject
        if (byTag != null) return byTag
        return keptCustom.firstOrNull { elem ->
            val obj = elem.asJsonObject
            jsonString(obj, "protocol") == inbound.protocol
                && obj.get("port")?.takeIf { it.isJsonPrimitive }?.asInt == inbound.port
        }?.asJsonObject
    }

    private fun applyTunOverlay(
        original: JsonObject,
        inbound: V2rayConfig.InboundBean,
    ): JsonObject {
        inbound.settings?.mtu?.let { mtu ->
            val settings = original.get("settings")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: JsonObject().also { original.add("settings", it) }
            settings.addProperty("mtu", mtu)
        }
        inbound.sniffing?.let { sniffing ->
            JsonUtil.parseString(JsonUtil.toJson(sniffing))?.takeIf { it.isJsonObject }?.let {
                original.add("sniffing", it)
            }
        }
        return original
    }

    private fun isManagedLocalInbound(inbound: V2rayConfig.InboundBean): Boolean {
        return inbound.protocol == AppConfig.PROTOCOL_SOCKS
            || inbound.protocol == AppConfig.PROTOCOL_HTTP
            || inbound.tag == AppConfig.TAG_REDIR_INBOUND
    }

    private fun isManagedLocalInboundJson(obj: JsonObject): Boolean {
        val protocol = jsonString(obj, "protocol")
        val tag = jsonString(obj, "tag")
        return protocol == AppConfig.PROTOCOL_SOCKS
            || protocol == AppConfig.PROTOCOL_HTTP
            || tag == AppConfig.TAG_REDIR_INBOUND
    }

    private fun jsonString(obj: JsonObject, key: String): String? {
        return obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    }

    private fun authAccounts(snapshot: LocalInboundSnapshot): List<V2rayConfig.InboundBean.InSettingsBean.SocksAccountBean>? {
        if (!snapshot.authEnabled || snapshot.username.isNullOrBlank() || snapshot.password.isNullOrBlank()) {
            return null
        }
        return listOf(
            V2rayConfig.InboundBean.InSettingsBean.SocksAccountBean(
                user = snapshot.username,
                pass = snapshot.password,
            )
        )
    }

    private fun buildSniffing(
        source: V2rayConfig.InboundBean.SniffingBean?,
        flags: InboundRuntimeFlags,
    ): V2rayConfig.InboundBean.SniffingBean? {
        val destOverride = ArrayList(
            source?.destOverride?.takeIf { it.isNotEmpty() } ?: arrayListOf("http", "tls", "quic")
        )
        if (!flags.sniffAllTlsAndHttp) {
            destOverride.clear()
        }
        if (flags.fakeDns && "fakedns" !in destOverride) {
            destOverride.add("fakedns")
        }
        return V2rayConfig.InboundBean.SniffingBean(
            enabled = flags.fakeDns || flags.sniffAllTlsAndHttp,
            destOverride = destOverride,
            metadataOnly = source?.metadataOnly,
            routeOnly = flags.routeOnly,
        )
    }

    private fun copySniffing(source: V2rayConfig.InboundBean.SniffingBean): V2rayConfig.InboundBean.SniffingBean {
        return V2rayConfig.InboundBean.SniffingBean(
            enabled = source.enabled,
            destOverride = ArrayList(source.destOverride),
            metadataOnly = source.metadataOnly,
            routeOnly = source.routeOnly,
        )
    }

    private fun copyInbound(source: V2rayConfig.InboundBean): V2rayConfig.InboundBean {
        return source.copy(
            settings = source.settings?.copy(accounts = source.settings?.accounts?.map { it.copy() }),
            sniffing = source.sniffing?.let { copySniffing(it) },
        )
    }
}
