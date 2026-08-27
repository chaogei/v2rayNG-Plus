package com.v2ray.ang.core

import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.LocalInboundSnapshot
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.enums.LocalInboundMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInboundConfiguratorTest {

    @Test
    fun mixedOnXray_singleSocksInbound() {
        val result = applyLayout(snapshot(mode = LocalInboundMode.MIXED))

        assertEquals(listOf("socks"), result.inbounds.map { it.protocol })
        assertEquals(10808, result.inbounds[0].port)
        assertEquals("127.0.0.1", result.inbounds[0].listen)
        assertEquals("noauth", result.inbounds[0].settings?.auth)
    }

    @Test
    fun mixedOnNonXray_addsNeighborHttpPort() {
        val result = applyLayout(
            snapshot(mode = LocalInboundMode.MIXED),
            flags = LocalInboundConfigurator.InboundRuntimeFlags(isXray = false),
        )

        assertEquals(listOf("socks", "http"), result.inbounds.map { it.protocol })
        assertEquals(10809, result.inbounds[1].port)
    }

    @Test
    fun socksHttp_twoPortsAndAuth() {
        val result = applyLayout(
            snapshot(
                mode = LocalInboundMode.SOCKS_HTTP,
                authEnabled = true,
                username = "user",
                password = "secret",
                listenAddress = "0.0.0.0",
            )
        )

        assertEquals(listOf("socks", "http"), result.inbounds.map { it.protocol })
        assertEquals("0.0.0.0", result.inbounds[0].listen)
        assertEquals("0.0.0.0", result.inbounds[1].listen)
        assertEquals("password", result.inbounds[0].settings?.auth)
        assertEquals("user", result.inbounds[0].settings?.accounts?.single()?.user)
        assertEquals("secret", result.inbounds[1].settings?.accounts?.single()?.pass)
    }

    @Test
    fun httpOnly_withoutForcedSocks_dropsSocks() {
        val result = applyLayout(snapshot(mode = LocalInboundMode.HTTP))

        assertEquals(listOf("http"), result.inbounds.map { it.protocol })
        assertEquals(10809, result.inbounds[0].port)
    }

    @Test
    fun httpOnly_hevForcesLoopbackSocks() {
        val result = applyLayout(
            snapshot(mode = LocalInboundMode.HTTP, listenAddress = "0.0.0.0"),
            flags = LocalInboundConfigurator.InboundRuntimeFlags(vpn = true, useHev = true),
        )

        val socks = result.inbounds.single { it.protocol == "socks" }
        assertEquals("127.0.0.1", socks.listen)
        assertTrue(result.inbounds.any { it.protocol == "http" && it.listen == "0.0.0.0" })
    }

    @Test
    fun redir_bindsLoopbackAndRejectsCollision() {
        val ok = applyLayout(snapshot(redirEnabled = true, redirPort = 10810))
        assertEquals("127.0.0.1", ok.inbounds.single { it.tag == "redir" }.listen)

        try {
            applyLayout(snapshot(redirEnabled = true, redirPort = 10808))
            throw AssertionError("expected port collision")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("10808"))
        }
    }

    @Test
    fun incompleteAuth_staysNoauth() {
        val result = applyLayout(
            snapshot(authEnabled = true, username = "user", password = " "),
        )
        assertEquals(listOf("socks"), result.inbounds.map { it.protocol })
        assertEquals("noauth", result.inbounds[0].settings?.auth)
    }

    @Test
    fun localProxyDisabled_removesSocksHttp_keepsTun() {
        val existing = listOf(
            socksTemplate(),
            tunInbound(),
        )
        val result = LocalInboundConfigurator.apply(
            existing = existing,
            snapshot = snapshot(),
            flags = LocalInboundConfigurator.InboundRuntimeFlags(enableLocalProxyPref = false),
        )

        assertEquals(listOf("tun"), result.inbounds.map { it.protocol })
    }

    @Test
    fun mixedWithExistingTun_keepsTunAfterSocks() {
        val result = LocalInboundConfigurator.apply(
            existing = listOf(socksTemplate(), tunInbound()),
            snapshot = snapshot(),
            flags = LocalInboundConfigurator.InboundRuntimeFlags(),
        )
        assertEquals(listOf("socks", "tun"), result.inbounds.map { it.protocol })
    }

    @Test
    fun customJson_replacesManagedInbounds_keepsExtraFields() {
        val json = JsonParser.parseString(
            """
            {
              "inbounds": [
                {"tag":"socks","protocol":"socks","port":1},
                {"tag":"mine","protocol":"dokodemo-door","port":9,"settings":{"address":"1.1.1.1"}}
              ]
            }
            """.trimIndent()
        ).asJsonObject

        LocalInboundConfigurator.applyToCustomJson(json, snapshot(mode = LocalInboundMode.SOCKS_HTTP), LocalInboundConfigurator.InboundRuntimeFlags())

        val inbounds = json.getAsJsonArray("inbounds")
        val tags = inbounds.map { it.asJsonObject.get("tag").asString }
        assertTrue(tags.contains("socks"))
        assertTrue(tags.contains("http"))
        assertTrue(tags.contains("mine"))
        val custom = inbounds.first { it.asJsonObject.get("tag").asString == "mine" }.asJsonObject
        assertEquals("1.1.1.1", custom.getAsJsonObject("settings").get("address").asString)
    }

    @Test
    fun customJson_tunOverlay_keepsUnknownTunFields() {
        val json = JsonParser.parseString(
            """
            {
              "inbounds": [
                {"tag":"tun","protocol":"tun","settings":{"mtu":1200,"stack":"gvisor","name":"tun0"}}
              ]
            }
            """.trimIndent()
        ).asJsonObject

        LocalInboundConfigurator.applyToCustomJson(
            json,
            snapshot(),
            LocalInboundConfigurator.InboundRuntimeFlags(vpn = true, vpnMtu = 1400),
        )

        val tun = json.getAsJsonArray("inbounds").first {
            it.asJsonObject.get("tag").asString == "tun"
        }.asJsonObject
        val settings = tun.getAsJsonObject("settings")
        assertEquals(1400, settings.get("mtu").asInt)
        assertEquals("gvisor", settings.get("stack").asString)
        assertEquals("tun0", settings.get("name").asString)
    }

    private fun applyLayout(
        snapshot: LocalInboundSnapshot,
        flags: LocalInboundConfigurator.InboundRuntimeFlags = LocalInboundConfigurator.InboundRuntimeFlags(),
    ) = LocalInboundConfigurator.apply(listOf(socksTemplate()), snapshot, flags)

    private fun snapshot(
        mode: LocalInboundMode = LocalInboundMode.MIXED,
        listenAddress: String = "127.0.0.1",
        socksPort: Int = 10808,
        httpInboundPort: Int = 10809,
        authEnabled: Boolean = false,
        username: String? = null,
        password: String? = null,
        redirEnabled: Boolean = false,
        redirPort: Int = 10810,
    ) = LocalInboundSnapshot(
        mode = mode,
        listenAddress = listenAddress,
        socksPort = socksPort,
        httpInboundPort = httpInboundPort,
        authEnabled = authEnabled,
        username = username,
        password = password,
        socksUdpEnabled = true,
        redirEnabled = redirEnabled,
        redirPort = redirPort,
    )

    private fun socksTemplate() = V2rayConfig.InboundBean(
        tag = "socks",
        port = 10808,
        protocol = AppConfig.PROTOCOL_SOCKS,
        listen = "127.0.0.1",
        settings = V2rayConfig.InboundBean.InSettingsBean(userLevel = 8),
        sniffing = V2rayConfig.InboundBean.SniffingBean(
            enabled = true,
            destOverride = arrayListOf("http", "tls", "quic"),
        ),
    )

    private fun tunInbound() = V2rayConfig.InboundBean(
        tag = "tun",
        port = null,
        protocol = "tun",
        settings = V2rayConfig.InboundBean.InSettingsBean(mtu = 1500),
    )
}
