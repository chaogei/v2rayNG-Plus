package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import org.junit.Assert.assertEquals
import org.junit.Test

class UnusableFilterRemarksTest {

    private fun sub(remarks: String, filter: String?) =
        SubscriptionCache(remarks, SubscriptionItem(remarks = remarks, filter = filter))

    @Test
    fun subscriptionsWithoutAFilterAreNotReported() {
        val subs = listOf(sub("A", null), sub("B", ""), sub("C", "   "))

        assertEquals(emptyList<String>(), unusableFilterRemarks(subs))
    }

    @Test
    fun aCompilingPatternIsNotReported() {
        assertEquals(emptyList<String>(), unusableFilterRemarks(listOf(sub("A", "HK|JP"))))
    }

    @Test
    fun onlyTheGroupsWithABrokenPatternAreNamed() {
        val subs = listOf(sub("Good", "HK"), sub("Broken", "HK("), sub("AlsoBroken", "*"))

        assertEquals(listOf("Broken", "AlsoBroken"), unusableFilterRemarks(subs))
    }
}
