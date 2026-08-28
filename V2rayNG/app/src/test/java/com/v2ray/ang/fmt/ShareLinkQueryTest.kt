package com.v2ray.ang.fmt

import com.v2ray.ang.enums.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * End-to-end checks for the query-string handling shared by the URI based
 * parsers (VLESS, VMess-std, Trojan, Hysteria2, WireGuard).
 *
 * Every case here used to either drop the profile entirely or silently lose a
 * field, and AngConfigManager turns a parse failure into a skipped node without
 * any user-facing message.
 */
class ShareLinkQueryTest {

    @Test
    fun test_vless_unencodedPathWithQueryIsKeptWhole() {
        val url = "vless://a1b2c3@example.com:443?type=ws&path=/ray?ed=2048&host=cdn.example.com#WS%20Node"

        val result = VlessFmt.parse(url)

        assertNotNull(result)
        assertEquals("/ray?ed=2048", result?.path)
        assertEquals("cdn.example.com", result?.host)
        assertEquals(NetworkType.WS.type, result?.network)
        assertEquals("WS Node", result?.remarks)
    }

    @Test
    fun test_vless_valuelessParameterDoesNotDropTheProfile() {
        val url = "vless://a1b2c3@example.com:443?type=tcp&udp&security=tls&sni=example.com#Flagged"

        val result = VlessFmt.parse(url)

        assertNotNull(result)
        assertEquals("example.com", result?.server)
        assertEquals("tls", result?.security)
        assertEquals("example.com", result?.sni)
    }

    @Test
    fun test_vless_trailingAmpersandDoesNotDropTheProfile() {
        val url = "vless://a1b2c3@example.com:443?type=tcp&security=tls&#Trailing"

        val result = VlessFmt.parse(url)

        assertNotNull(result)
        assertEquals("tls", result?.security)
    }

    @Test
    fun test_vless_realityKeysWithBase64PaddingSurvive() {
        val url = "vless://a1b2c3@example.com:443?type=tcp&security=reality&" +
            "pbk=bWluZS1rZXk=&sid=0123abcd&fp=chrome#Reality"

        val result = VlessFmt.parse(url)

        assertNotNull(result)
        assertEquals("bWluZS1rZXk=", result?.publicKey)
        assertEquals("0123abcd", result?.shortId)
        assertEquals("chrome", result?.fingerPrint)
    }

    @Test
    fun test_vless_wsPathRoundTripsThroughToUri() {
        val original = "vless://a1b2c3@example.com:443?type=ws&path=/ray?ed=2048&host=cdn.example.com#WS%20Node"

        val parsed = VlessFmt.parse(original)
        assertNotNull(parsed)

        val reparsed = VlessFmt.parse("vless://" + VlessFmt.toUri(parsed!!))

        assertNotNull(reparsed)
        assertEquals(parsed.path, reparsed?.path)
        assertEquals(parsed.host, reparsed?.host)
        assertEquals(parsed.network, reparsed?.network)
        assertEquals(parsed.remarks, reparsed?.remarks)
        assertEquals(parsed.password, reparsed?.password)
        assertEquals(parsed.serverPort, reparsed?.serverPort)
    }

    @Test
    fun test_trojan_withoutQueryKeepsTlsDefaults() {
        val result = TrojanFmt.parse("trojan://secret@example.com:443#Plain")

        assertEquals("tls", result.security)
        assertEquals(NetworkType.TCP.type, result.network)
        assertEquals("Plain", result.remarks)
    }

    @Test
    fun test_hysteria2_pinnedCertificateFromPcsIsNotClobbered() {
        val url = "hysteria2://secret@example.com:443?sni=example.com&pcs=sha256/AAAA#Pinned"

        val result = Hysteria2Fmt.parse(url)

        assertEquals("sha256/AAAA", result.pinnedCA256)
    }

    @Test
    fun test_hysteria2_pinSha256StillWins() {
        val url = "hysteria2://secret@example.com:443?sni=example.com&pinSHA256=sha256/BBBB#Pinned"

        val result = Hysteria2Fmt.parse(url)

        assertEquals("sha256/BBBB", result.pinnedCA256)
    }

    @Test
    fun test_hysteria2_obfsPasswordWithSpecialCharactersSurvives() {
        val url = "hysteria2://secret@example.com:443?obfs=salamander&obfs-password=p%40ss%3Dword#Obfs"

        val result = Hysteria2Fmt.parse(url)

        assertEquals("p@ss=word", result.obfsPassword)
    }

    @Test
    fun test_wireguard_reservedAndAddressAreParsed() {
        val url = "wireguard://cHJpdmF0ZQ@example.com:51820?" +
            "publickey=cHVibGlj&address=10.0.0.2/32,fd00::2/128&mtu=1420&reserved=1,2,3#WG"

        val result = WireguardFmt.parse(url)

        assertNotNull(result)
        assertEquals("cHVibGlj", result?.publicKey)
        assertEquals("10.0.0.2/32,fd00::2/128", result?.localAddress)
        assertEquals(1420, result?.mtu)
        assertEquals("1,2,3", result?.reserved)
    }
}
