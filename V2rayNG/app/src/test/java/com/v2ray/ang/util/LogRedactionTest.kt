package com.v2ray.ang.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LogRedactionTest {

    @Test
    fun `a subscription token in the path does not survive`() {
        assertEquals(
            "https://sub.example.com/${LogRedaction.MASK}",
            LogRedaction.url("https://sub.example.com/link/abcdef123456/clash"),
        )
    }

    @Test
    fun `a token in the query does not survive`() {
        assertEquals(
            "https://sub.example.com/${LogRedaction.MASK}",
            LogRedaction.url("https://sub.example.com/?token=abcdef123456"),
        )
    }

    @Test
    fun `embedded basic auth does not survive`() {
        assertEquals(
            "https://sub.example.com/${LogRedaction.MASK}",
            LogRedaction.url("https://user:secret@sub.example.com/feed"),
        )
    }

    @Test
    fun `a share link keeps only the endpoint`() {
        assertEquals(
            "vless://node.example.com:443/${LogRedaction.MASK}",
            LogRedaction.url("vless://uuid-secret@node.example.com:443?type=tcp#HK%2001"),
        )
    }

    @Test
    fun `a bare host is kept in full`() {
        assertEquals("https://sub.example.com", LogRedaction.url("https://sub.example.com"))
        assertEquals("https://sub.example.com", LogRedaction.url("https://sub.example.com/"))
        assertEquals("https://sub.example.com:8443", LogRedaction.url("https://sub.example.com:8443"))
    }

    @Test
    fun `unusable input is masked rather than passed through`() {
        assertEquals(LogRedaction.MASK, LogRedaction.url(null))
        assertEquals(LogRedaction.MASK, LogRedaction.url(""))
        assertEquals(LogRedaction.MASK, LogRedaction.url("   "))
        assertEquals(LogRedaction.MASK, LogRedaction.url("not a url at all"))
        assertEquals(LogRedaction.MASK, LogRedaction.url("vmess://eyJhZGQiOiJ4In0="))
    }

    @Test
    fun `header names are logged without their value`() {
        assertEquals("Authorization", LogRedaction.headerName(" Authorization "))
        assertEquals(LogRedaction.MASK, LogRedaction.headerName(null))
        assertEquals(LogRedaction.MASK, LogRedaction.headerName(""))
    }
}
