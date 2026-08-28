package com.v2ray.ang.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlSchemeRequestTest {

    @Test
    fun installConfigLinkCarriesTheUrl() {
        assertEquals(
            UrlSchemeRequest.Import("vmess://payload", null),
            UrlSchemeRequest.fromViewLink("install-config", "vmess://payload", null)
        )
    }

    @Test
    fun installSubLinkCarriesTheUrlAndFragment() {
        assertEquals(
            UrlSchemeRequest.Import("https://example.com/sub", "Tokyo"),
            UrlSchemeRequest.fromViewLink("install-sub", "https://example.com/sub", "Tokyo")
        )
    }

    @Test
    fun hostMatchingIgnoresCaseAndSurroundingSpace() {
        assertEquals(
            UrlSchemeRequest.Import("vmess://payload", null),
            UrlSchemeRequest.fromViewLink(" Install-Config ", "vmess://payload", null)
        )
    }

    @Test
    fun missingUrlIsDistinctFromAnUnsupportedHost() {
        assertEquals(
            UrlSchemeRequest.MissingUrl,
            UrlSchemeRequest.fromViewLink("install-config", null, null)
        )
        assertEquals(
            UrlSchemeRequest.MissingUrl,
            UrlSchemeRequest.fromViewLink("install-sub", "   ", null)
        )
    }

    @Test
    fun unknownHostKeepsWhatWasAskedForSoItCanBeLogged() {
        assertEquals(
            UrlSchemeRequest.UnsupportedHost("install-everything"),
            UrlSchemeRequest.fromViewLink("install-everything", "vmess://payload", null)
        )
        assertEquals(
            UrlSchemeRequest.UnsupportedHost(null),
            UrlSchemeRequest.fromViewLink(null, "vmess://payload", null)
        )
    }

    @Test
    fun emptyFragmentIsNotTreatedAsARemark() {
        assertEquals(
            UrlSchemeRequest.Import("vmess://payload", null),
            UrlSchemeRequest.fromViewLink("install-config", "vmess://payload", "")
        )
    }

    @Test
    fun sharedTextIsTrimmed() {
        assertEquals(
            UrlSchemeRequest.Import("vmess://payload", null),
            UrlSchemeRequest.fromSharedText("  vmess://payload\n")
        )
    }

    @Test
    fun blankSharedTextIsNothingToDo() {
        assertEquals(UrlSchemeRequest.Empty, UrlSchemeRequest.fromSharedText(null))
        assertEquals(UrlSchemeRequest.Empty, UrlSchemeRequest.fromSharedText("   "))
    }

    @Test
    fun fragmentIsAppendedWhenTheUrlHasNone() {
        assertEquals(
            "vmess://payload#Tokyo",
            UrlSchemeRequest.mergeFragment("vmess://payload", "Tokyo")
        )
    }

    @Test
    fun aRemarkAlreadyInTheUrlWins() {
        assertEquals(
            "vmess://payload#Osaka",
            UrlSchemeRequest.mergeFragment("vmess://payload#Osaka", "Tokyo")
        )
    }

    @Test
    fun noFragmentLeavesTheUrlAlone() {
        assertEquals("vmess://payload", UrlSchemeRequest.mergeFragment("vmess://payload", null))
        assertEquals("vmess://payload", UrlSchemeRequest.mergeFragment("vmess://payload", ""))
    }
}
