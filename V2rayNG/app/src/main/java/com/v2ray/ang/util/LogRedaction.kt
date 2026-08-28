package com.v2ray.ang.util

import java.net.URI

/**
 * Keeps secrets out of logcat.
 *
 * The log level is a user setting and the app can share its own logcat, so anything
 * written at debug or info level can end up in a bug report.
 */
object LogRedaction {

    const val MASK = "<redacted>"

    /**
     * Reduces a URL to the endpoint it talks to.
     *
     * Userinfo, path and query all routinely carry a subscription token or a node
     * password, so only the scheme, host and port survive.
     */
    fun url(raw: String?): String {
        val trimmed = raw?.trim()
        if (trimmed.isNullOrEmpty()) return MASK

        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return MASK
        }

        val host = uri.host?.takeIf { it.isNotEmpty() } ?: return MASK
        val scheme = uri.scheme?.takeIf { it.isNotEmpty() }?.let { "$it://" }.orEmpty()
        val port = if (uri.port > 0) ":${uri.port}" else ""
        val carriesSecret = !uri.rawUserInfo.isNullOrEmpty() ||
                uri.rawPath.orEmpty().trim('/').isNotEmpty() ||
                !uri.rawQuery.isNullOrEmpty() ||
                !uri.rawFragment.isNullOrEmpty()

        return if (carriesSecret) "$scheme$host$port/$MASK" else "$scheme$host$port"
    }

    /**
     * Names a request header without its value; custom subscription headers are the usual
     * place an Authorization token is configured.
     */
    fun headerName(key: String?): String {
        val trimmed = key?.trim()
        return if (trimmed.isNullOrEmpty()) MASK else trimmed
    }
}
