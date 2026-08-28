package com.v2ray.ang.handler

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTitlePolicyTest {

    private val direct = "Local proxy · Direct"

    @Test
    fun aNamedProfileIsShownByName() {
        assertEquals("Tokyo", NotificationTitlePolicy.title("Tokyo", direct))
    }

    @Test
    fun noProfileReadsAsDirect() {
        assertEquals(direct, NotificationTitlePolicy.title(null, direct))
    }

    @Test
    fun anEmptyRemarkReadsAsDirectRatherThanAsABlankTitle() {
        assertEquals(direct, NotificationTitlePolicy.title("", direct))
        assertEquals(direct, NotificationTitlePolicy.title("   ", direct))
    }

    @Test
    fun surroundingSpaceIsTrimmedOffTheName() {
        assertEquals("Tokyo", NotificationTitlePolicy.title("  Tokyo  ", direct))
    }
}
