package com.v2ray.ang.util

/**
 * Compare dotted numeric versions. A suffix after '-' or '+' (for example
 * "2.3.5-plus") is ignored so a fork tag still compares against the same
 * upstream numbers.
 */
object VersionUtil {

    fun compare(version1: String, version2: String): Int {
        val v1 = numericParts(version1)
        val v2 = numericParts(version2)
        val len = maxOf(v1.size, v2.size)
        for (i in 0 until len) {
            val num1 = v1.getOrElse(i) { 0 }
            val num2 = v2.getOrElse(i) { 0 }
            if (num1 != num2) return num1 - num2
        }
        return 0
    }

    private fun numericParts(version: String): List<Int> {
        val core = version
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .substringBefore("+")
        return core.split(".").map { it.toIntOrNull() ?: 0 }
    }
}
