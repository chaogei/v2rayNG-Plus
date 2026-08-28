package com.v2ray.ang.ui.userasset

import com.v2ray.ang.ui.userasset.UserAssetViewModel.GeoDownloadResult
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoDownloadResultTest {

    @Test
    fun `a clean run reports success`() {
        val result = GeoDownloadResult(successCount = 3, failureCount = 0, failedAssets = emptyList())

        assertEquals(GeoDownloadOutcome.ALL_SUCCEEDED, result.outcome())
    }

    @Test
    fun `one failed file is not hidden behind the successes`() {
        val result = GeoDownloadResult(
            successCount = 2,
            failureCount = 1,
            failedAssets = listOf("geoip.dat"),
        )

        assertEquals(GeoDownloadOutcome.PARTIALLY_FAILED, result.outcome())
    }

    @Test
    fun `a run where nothing landed reports failure`() {
        val result = GeoDownloadResult(
            successCount = 0,
            failureCount = 2,
            failedAssets = listOf("geoip.dat", "geosite.dat"),
        )

        assertEquals(GeoDownloadOutcome.ALL_FAILED, result.outcome())
    }

    @Test
    fun `an empty asset list is distinguishable from a failure`() {
        val result = GeoDownloadResult(successCount = 0, failureCount = 0, failedAssets = emptyList())

        assertEquals(GeoDownloadOutcome.NOTHING_ATTEMPTED, result.outcome())
    }
}
