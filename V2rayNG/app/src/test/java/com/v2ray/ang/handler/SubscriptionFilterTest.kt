package com.v2ray.ang.handler

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionFilterTest {

    @Test
    fun `a blank pattern keeps everything`() {
        listOf(null, "", "   ").forEach { pattern ->
            assertNull(SubscriptionFilter.compile(pattern))
            assertFalse(SubscriptionFilter.isUnusable(pattern))
            assertTrue(SubscriptionFilter.accepts(SubscriptionFilter.compile(pattern), "HK 01"))
        }
    }

    @Test
    fun `a usable pattern selects by remarks`() {
        val filter = SubscriptionFilter.compile("HK|JP")

        assertNotNull(filter)
        assertTrue(SubscriptionFilter.accepts(filter, "HK 01"))
        assertTrue(SubscriptionFilter.accepts(filter, "Tokyo JP premium"))
        assertFalse(SubscriptionFilter.accepts(filter, "US 03"))
    }

    @Test
    fun `an invalid pattern is reported and falls back to keeping everything`() {
        val pattern = "HK("

        assertTrue(SubscriptionFilter.isUnusable(pattern))
        assertNull(SubscriptionFilter.compile(pattern))
        assertTrue(SubscriptionFilter.accepts(SubscriptionFilter.compile(pattern), "US 03"))
    }

    @Test
    fun `profiles without remarks are not filtered out`() {
        val filter = SubscriptionFilter.compile("HK")

        assertTrue(SubscriptionFilter.accepts(filter, null))
        assertTrue(SubscriptionFilter.accepts(filter, ""))
    }

    @Test
    fun `surrounding whitespace does not change the pattern`() {
        val filter = SubscriptionFilter.compile("  HK  ")

        assertNotNull(filter)
        assertTrue(SubscriptionFilter.accepts(filter, "HK 01"))
        assertFalse(SubscriptionFilter.accepts(filter, "US 03"))
    }
}
