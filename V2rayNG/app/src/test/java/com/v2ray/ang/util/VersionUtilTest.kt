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

    /**
     * CI stamps PR artifacts as 2.3.5.<last released N>-pr.<sha>-plus
     * (.github/scripts/compute-version.sh). The suffix must be dropped by
     * compare(): a sideloaded PR build reads as exactly the release it was cut
     * from, so the updater still offers the next real release.
     */
    @Test
    fun prVersionName_neverReadsNewerThanReleases() {
        assertEquals(0, VersionUtil.compare("2.3.5.3-pr.abc1234-plus", "2.3.5.3-plus"))
        assertTrue(VersionUtil.compare("2.3.5.4-plus", "2.3.5.3-pr.abc1234-plus") > 0)
        // Before the first release PR builds use segment 0, below every release.
        assertTrue(VersionUtil.compare("2.3.5.1-plus", "2.3.5.0-pr.abc1234-plus") > 0)
    }
}
