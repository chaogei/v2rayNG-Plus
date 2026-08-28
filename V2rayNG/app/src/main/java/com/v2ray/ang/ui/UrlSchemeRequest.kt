package com.v2ray.ang.ui

/**
 * What an inbound `v2rayng://` link or shared text amounts to, decided before any Android
 * object is touched so every rejection has a reason the caller can be told about.
 *
 * The activity used to swallow malformed links: it opened the main screen and said nothing,
 * which reads like "imported" to whoever tapped the link.
 */
sealed interface UrlSchemeRequest {

    /** A link worth handing to the importer. */
    data class Import(val url: String, val fragment: String?) : UrlSchemeRequest

    /** The host is known but `?url=` was missing or blank. */
    data object MissingUrl : UrlSchemeRequest

    /** A `v2rayng://` link whose host is not one we handle. */
    data class UnsupportedHost(val host: String?) : UrlSchemeRequest

    /** Nothing actionable: an empty share, or an intent we do not answer. */
    data object Empty : UrlSchemeRequest

    companion object {
        const val HOST_INSTALL_CONFIG = "install-config"
        const val HOST_INSTALL_SUB = "install-sub"

        /** Shared plain text, which is a share link pasted from another app. */
        fun fromSharedText(text: String?): UrlSchemeRequest {
            val trimmed = text?.trim()
            return if (trimmed.isNullOrEmpty()) Empty else Import(trimmed, null)
        }

        /**
         * A `v2rayng://install-config?url=` or `v2rayng://install-sub?url=` link.
         *
         * The fragment is carried separately because Android hands it to us stripped from the
         * `url` parameter, and it is the remark the user expects to see on the imported node.
         */
        fun fromViewLink(host: String?, url: String?, fragment: String?): UrlSchemeRequest {
            val normalizedHost = host?.trim()?.lowercase()
            if (normalizedHost != HOST_INSTALL_CONFIG && normalizedHost != HOST_INSTALL_SUB) {
                return UnsupportedHost(host)
            }
            val trimmed = url?.trim()
            if (trimmed.isNullOrEmpty()) return MissingUrl
            return Import(trimmed, fragment?.takeUnless { it.isEmpty() })
        }

        /**
         * Puts the remark back on the link.
         *
         * `Uri.getQueryParameter("url")` stops at the `#`, so a link written as
         * `install-config?url=vmess://...#Tokyo` arrives as two pieces. A remark already in
         * the url wins: it is the one the link author encoded on purpose.
         */
        fun mergeFragment(url: String, fragment: String?): String {
            if (fragment.isNullOrEmpty()) return url
            if (url.contains('#')) return url
            return "$url#$fragment"
        }
    }
}
