package com.v2ray.ang.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionUtilTest {

    @Test
    fun plusSuffix_doesNotBreakNumericCompare() {
        assertEquals(0, VersionUtil.compare("2.3.5-plus", "2.3.5"))
        assertTrue(VersionUtil.compare("2.3.6", "2.3.5-plus") > 0)
        assertTrue(VersionUtil.compare("v2.3.5-plus", "2.3.4") > 0)
    }
}
