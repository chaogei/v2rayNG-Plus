package com.v2ray.ang.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ZipEntryPathTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val destination: File
        get() = temporaryFolder.root

    @Test
    fun `a plain entry resolves inside the destination`() {
        val resolved = ZipEntryPath.resolveWithin(destination, "MAIN")

        assertNotNull(resolved)
        assertEquals(File(destination.canonicalFile, "MAIN").path, resolved?.path)
    }

    @Test
    fun `a nested entry resolves inside the destination`() {
        val resolved = ZipEntryPath.resolveWithin(destination, "sub/dir/MAIN.crc")

        assertNotNull(resolved)
        assertEquals(File(destination.canonicalFile, "sub/dir/MAIN.crc").path, resolved?.path)
    }

    @Test
    fun `a traversing entry is rejected`() {
        assertNull(ZipEntryPath.resolveWithin(destination, "../escaped"))
        assertNull(ZipEntryPath.resolveWithin(destination, "sub/../../escaped"))
        assertNull(ZipEntryPath.resolveWithin(destination, "../../shared_prefs/x.xml"))
    }

    @Test
    fun `an absolute entry is re-rooted under the destination`() {
        val resolved = ZipEntryPath.resolveWithin(destination, "/etc/passwd")

        assertEquals(File(destination.canonicalFile, "etc/passwd").path, resolved?.path)
    }

    @Test
    fun `an empty entry is rejected`() {
        assertNull(ZipEntryPath.resolveWithin(destination, ""))
        assertNull(ZipEntryPath.resolveWithin(destination, "   "))
    }

    @Test
    fun `traversal that returns into the destination is allowed`() {
        val resolved = ZipEntryPath.resolveWithin(destination, "sub/../MAIN")

        assertNotNull(resolved)
        assertEquals(File(destination.canonicalFile, "MAIN").path, resolved?.path)
    }
}
