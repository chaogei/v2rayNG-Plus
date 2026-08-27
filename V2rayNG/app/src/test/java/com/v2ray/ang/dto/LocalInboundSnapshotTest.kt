package com.v2ray.ang.dto

import com.v2ray.ang.enums.LocalInboundMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalInboundSnapshotTest {

    @Test
    fun neighborPort_stepsUpBelowTheTopOfTheRange() {
        assertEquals(10809, LocalInboundSnapshot.neighborPort(10808))
        assertEquals(65535, LocalInboundSnapshot.neighborPort(65534))
    }

    @Test
    fun neighborPort_stepsDownAtTheTopOfTheRange() {
        assertEquals(65534, LocalInboundSnapshot.neighborPort(65535))
    }

    @Test
    fun effectiveHttpPort_usesTheDedicatedPortWhenTheModeHasOne() {
        val snapshot = snapshot(mode = LocalInboundMode.SOCKS_HTTP, socksPort = 10808, httpInboundPort = 10809)

        assertEquals(10809, snapshot.effectiveHttpPort)
    }

    private fun snapshot(mode: LocalInboundMode, socksPort: Int, httpInboundPort: Int) = LocalInboundSnapshot(
        mode = mode,
        listenAddress = "127.0.0.1",
        socksPort = socksPort,
        httpInboundPort = httpInboundPort,
        authEnabled = false,
        username = null,
        password = null,
        socksUdpEnabled = true,
        redirEnabled = false,
        redirPort = 10810,
    )
}
