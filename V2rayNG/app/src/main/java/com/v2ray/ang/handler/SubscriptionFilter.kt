package com.v2ray.ang.handler

/**
 * The remarks filter a subscription can carry.
 *
 * Compiled once per import batch rather than once per line: a large subscription
 * otherwise recompiles the same pattern for every node it contains.
 */
internal object SubscriptionFilter {

    /**
     * A blank pattern means "keep everything".
     *
     * An unparseable pattern also keeps everything. Letting it throw per node made every
     * single profile fail to parse, so a typo in the filter box turned into "update
     * succeeded, imported 0 nodes" with nothing pointing at the filter.
     */
    fun compile(pattern: String?): Regex? {
        val trimmed = pattern?.trim()
        if (trimmed.isNullOrEmpty()) return null
        return try {
            Regex(trimmed)
        } catch (_: Exception) {
            null
        }
    }

    /** True when the pattern was usable but matched nothing in the subscription. */
    fun isUnusable(pattern: String?): Boolean {
        val trimmed = pattern?.trim()
        if (trimmed.isNullOrEmpty()) return false
        return compile(trimmed) == null
    }

    /** Profiles without remarks cannot be judged by a remarks filter, so they pass. */
    fun accepts(filter: Regex?, remarks: String?): Boolean {
        if (filter == null) return true
        if (remarks.isNullOrEmpty()) return true
        return filter.containsMatchIn(remarks)
    }
}
