package com.v2ray.ang.fmt

/**
 * Splits the raw query string of a share link into its parameters.
 *
 * Kept free of Android APIs so the splitting rules can be unit tested directly;
 * the value decoder is injected because the production one logs through MMKV.
 *
 * The rules matter because share links in the wild are not tidy:
 * - a value may contain further '=' characters (`path=/proxy?ed=2048`,
 *   base64 payloads with padding, `plugin=obfs-local;obfs=http;obfs-host=...`),
 *   so only the first '=' separates key from value;
 * - a parameter may carry no value at all (`...&udp&...`);
 * - empty segments appear from trailing or doubled '&'.
 * Any of these used to abort the whole parse, and the importer turns a failed
 * parse into a silently skipped node.
 */
object UriQueryParser {

    fun parse(rawQuery: String?, decodeValue: (String) -> String): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()

        val params = LinkedHashMap<String, String>()
        for (segment in rawQuery.split('&')) {
            if (segment.isEmpty()) continue
            val separator = segment.indexOf('=')
            if (separator < 0) {
                params[segment] = ""
                continue
            }
            val key = segment.substring(0, separator)
            if (key.isEmpty()) continue
            params[key] = decodeValue(segment.substring(separator + 1))
        }
        return params
    }
}
