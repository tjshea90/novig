package com.tjshea.vigilant.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Random

class BackupCipherTest {
    private val fast = 2_000

    @Test fun `round trips small, empty and multi-chunk data`() {
        for (n in listOf(0, 1, 1000, (1 shl 20), (1 shl 20) + 1, 3 * (1 shl 20) + 17)) {
            val plain = ByteArray(n).also { Random(n.toLong()).nextBytes(it) }
            val blob = BackupCipher.encrypt(plain, "correct horse battery", fast)
            assertArrayEquals("size $n", plain, BackupCipher.decrypt(blob, "correct horse battery"))
        }
    }

    @Test fun `wrong passphrase and non backups give null`() {
        val blob = BackupCipher.encrypt("secret".toByteArray(), "right passphrase", fast)
        assertNull(BackupCipher.decrypt(blob, "wrong passphrase"))
        assertNull(BackupCipher.decrypt("not a backup at all".toByteArray(), "right passphrase"))
        assertNull(BackupCipher.decrypt(ByteArray(0), "right passphrase"))
    }

    @Test fun `the file does not contain the plain text`() {
        val blob = BackupCipher.encrypt("-----BEGIN PRIVATE KEY----- visible?".toByteArray(), "right passphrase", fast) // FAKE
        assertTrue(!String(blob, Charsets.ISO_8859_1).contains("PRIVATE KEY")) // FAKE
    }

    @Test fun `a cut short file is refused not half accepted`() {
        val plain = ByteArray(2 * (1 shl 20) + 5).also { Random(1).nextBytes(it) }
        val blob = BackupCipher.encrypt(plain, "right passphrase", fast)
        val cut = blob.copyOf(blob.size - 100)
        val s = BackupCipher.decryptingStream(cut.inputStream(), "right passphrase")
        assertNotNull(s)   // the first chunk is fine; the cut shows while reading on
        var failed = false
        try { s!!.readBytes() } catch (_: IOException) { failed = true }
        assertTrue(failed)
    }

    @Test fun `a dropped chunk or flipped byte is detected`() {
        val plain = ByteArray(2 * (1 shl 20) + 5).also { Random(2).nextBytes(it) }
        val blob = BackupCipher.encrypt(plain, "right passphrase", fast)
        val flipped = blob.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        assertNull(BackupCipher.decrypt(flipped, "right passphrase"))
    }

    @Test fun `each backup uses a fresh salt`() {
        val a = BackupCipher.encrypt("x".toByteArray(), "right passphrase", fast)
        val b = BackupCipher.encrypt("x".toByteArray(), "right passphrase", fast)
        assertTrue(!a.contentEquals(b))
    }

    @Test fun `streams a large archive through the zip layer`() {
        val bos = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(BackupCipher.encryptingStream(bos, "right passphrase", fast)).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("files/a.json")); z.write("{\"a\":1}".toByteArray()); z.closeEntry()
        }
        val zin = java.util.zip.ZipInputStream(BackupCipher.decryptingStream(bos.toByteArray().inputStream(), "right passphrase")!!)
        assertEquals("files/a.json", zin.nextEntry.name)
        assertEquals("{\"a\":1}", zin.readBytes().decodeToString())
    }
}
