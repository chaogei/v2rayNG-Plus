package com.v2ray.ang.fmt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/**
 * Unit tests for [UriQueryParser].
 *
 * The parser is what stands between a subscription line and a silently dropped
 * node: the importer swallows every parse exception, so a query string that
 * makes the splitter throw removes the profile without telling anyone.
 */
class UriQueryParserTest {

    /** Mirrors Utils.decodeURIComponent without its MMKV-backed logging. */
    private val decode: (String) -> String = { value ->
        runCatching { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)
    }

    private fun parse(rawQuery: String?) = UriQueryParser.parse(rawQuery, decode)

    @Test
    fun test_parse_nullQuery_returnsEmptyMap() {
        assertTrue(parse(null).isEmpty())
    }

    @Test
    fun test_parse_emptyQuery_returnsEmptyMap() {
        assertTrue(parse("").isEmpty())
    }

    @Test
    fun test_parse_simplePairs() {
        val result = parse("type=ws&security=tls&sni=example.com")

        assertEquals("ws", result["type"])
        assertEquals("tls", result["security"])
        assertEquals("example.com", result["sni"])
    }

    @Test
    fun test_parse_valueKeepsEverythingAfterFirstEquals() {
        val result = parse("type=ws&path=/proxy?ed=2048&host=example.com")

        assertEquals("/proxy?ed=2048", result["path"])
        assertEquals("example.com", result["host"])
    }

    @Test
    fun test_parse_shadowsocksPluginValueSurvives() {
        val result = parse("plugin=obfs-local;obfs=http;obfs-host=cloud.example.com")

        assertEquals("obfs-local;obfs=http;obfs-host=cloud.example.com", result["plugin"])
    }

    @Test
    fun test_parse_base64PaddedValueSurvives() {
        val result = parse("pbk=bWluZS1rZXk=&sid=ab12")

        assertEquals("bWluZS1rZXk=", result["pbk"])
        assertEquals("ab12", result["sid"])
    }

    @Test
    fun test_parse_valuelessParameterBecomesEmptyString() {
        val result = parse("type=tcp&udp&security=tls")

        assertEquals("", result["udp"])
        assertEquals("tcp", result["type"])
        assertEquals("tls", result["security"])
    }

    @Test
    fun test_parse_emptySegmentsAreSkipped() {
        val result = parse("&type=ws&&security=tls&")

        assertEquals(2, result.size)
        assertEquals("ws", result["type"])
        assertEquals("tls", result["security"])
    }

    @Test
    fun test_parse_emptyKeyIsSkipped() {
        val result = parse("=orphan&type=ws")

        assertEquals(1, result.size)
        assertEquals("ws", result["type"])
    }

    @Test
    fun test_parse_emptyValueIsKept() {
        val result = parse("sni=&type=ws")

        assertEquals("", result["sni"])
        assertEquals("ws", result["type"])
    }

    @Test
    fun test_parse_percentEncodedValueIsDecoded() {
        val result = parse("path=%2Fray%3Fed%3D2048&alpn=h2%2Chttp%2F1.1")

        assertEquals("/ray?ed=2048", result["path"])
        assertEquals("h2,http/1.1", result["alpn"])
    }

    @Test
    fun test_parse_plusIsALiteralPlusNotASpace() {
        val result = parse("obfs-password=a+b")

        assertEquals("a+b", result["obfs-password"])
    }

    @Test
    fun test_parse_duplicateKeyKeepsLastValue() {
        val result = parse("security=tls&security=reality")

        assertEquals("reality", result["security"])
    }

    @Test
    fun test_parse_keysAreNotDecoded() {
        // Keys are plain identifiers in every supported scheme; leaving them raw
        // keeps lookups predictable for callers.
        val result = parse("head%65rType=none")

        assertEquals("none", result["head%65rType"])
    }
}
