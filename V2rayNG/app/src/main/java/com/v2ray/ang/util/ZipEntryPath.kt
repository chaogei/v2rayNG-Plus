package com.v2ray.ang.util

import java.io.File

/**
 * Where a zip entry is allowed to land on disk.
 */
object ZipEntryPath {

    /**
     * Resolves [entryName] under [destination], or null when the entry escapes it.
     *
     * Archive entry names are attacker controlled on the restore path, and a name such as
     * `../../shared_prefs/x` otherwise resolves to a file outside the extraction directory.
     */
    fun resolveWithin(destination: File, entryName: String): File? {
        if (entryName.isBlank()) return null

        val root = destination.canonicalFile
        val target = File(root, entryName).canonicalFile
        val rootPath = root.path.trimEnd(File.separatorChar) + File.separator
        return if (target.path == root.path || target.path.startsWith(rootPath)) target else null
    }
}
