package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionIntervalTest {

    @Test
    fun anIntervalBelowTheWorkManagerFloorIsRaisedToIt() {
        listOf(-1L, 0L, 1L, 14L).forEach { requested ->
            assertEquals(
                AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES,
                SubscriptionUpdater.effectiveIntervalMinutes(requested)
            )
        }
    }

    @Test
    fun anIntervalAtOrAboveTheFloorIsKept() {
        assertEquals(15L, SubscriptionUpdater.effectiveIntervalMinutes(15L))
        assertEquals(1440L, SubscriptionUpdater.effectiveIntervalMinutes(1440L))
    }
}
