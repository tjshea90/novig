package com.tjshea.vigilant.data.novig.signing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * The management key is entered once and kept (Tj, 2026-09-29: "Make it so I only input the API key and file one time ... Make this and all
 * keys persist even through app updates"). An update is a new process reading the same app files: a fresh store on the same file stands in.
 */
class ManagementKeyStoreTest {

    /** Stands in for the Keystore: reversible, and never the plain text. */
    private class TestBox : SecretBox {
        var broken = false
        override fun seal(plain: String) = "sealed:" + Base64.getEncoder().encodeToString(plain.reversed().toByteArray())
        override fun open(sealed: String): String {
            check(!broken) { "the Keystore key is gone" }
            return String(Base64.getDecoder().decode(sealed.removePrefix("sealed:"))).reversed()
        }
    }

    private val pem = "-----BEGIN PRIVATE KEY-----\nMC4CAQAwBQYDK2VwBCIEIFAKEFAKEFAKE\n-----END PRIVATE KEY-----\n" // FAKE
    private fun file() = File.createTempFile("mgmt", ".json").also { it.delete() }

    @Test
    fun `a saved key comes back in a new process on the same file, as after an app update`() = runBlocking {
        val f = file()
        val box = TestBox()
        val hint = ManagementKeyStore(f, box, clock = { 42L }).save(ManagementKey(" mgmt-key-12345678 ", pem))
        assertEquals("5678", hint.keyIdEnd)
        val reopened = ManagementKeyStore(f, box)
        val key = reopened.load()
        assertNotNull(key)
        assertEquals("mgmt-key-12345678", key!!.keyId)
        assertEquals(pem, key.pem)
        assertEquals(ManagementKeyHint("5678", 42L), reopened.hint())
    }

    @Test
    fun `the file never holds the key text in the clear`() = runBlocking {
        val f = file()
        ManagementKeyStore(f, TestBox()).save(ManagementKey("mgmt-key-12345678", pem))
        val text = f.readText()
        assertFalse(text.contains("PRIVATE KEY"))
        assertFalse(text.contains("FAKEFAKE"))
        assertTrue(text.contains("mgmt-key-12345678")) // the ID alone can't sign
    }

    @Test
    fun `a key this phone can no longer unlock reads as none, and the hint says so`() = runBlocking {
        val f = file()
        val box = TestBox()
        ManagementKeyStore(f, box).save(ManagementKey("mgmt-key-12345678", pem))
        box.broken = true
        val store = ManagementKeyStore(f, box)
        assertNull(store.load())
        assertTrue(store.hint()!!.unreadable)
    }

    @Test
    fun `saving again replaces the key, and forget removes it`() = runBlocking {
        val f = file()
        val store = ManagementKeyStore(f, TestBox())
        assertNull(store.load())
        assertNull(store.hint())
        store.save(ManagementKey("mgmt-key-aaaaaaaa", pem))
        store.save(ManagementKey("mgmt-key-bbbbbbbb", pem))
        assertEquals("mgmt-key-bbbbbbbb", ManagementKeyStore(f, TestBox()).load()!!.keyId)
        store.clear()
        assertNull(ManagementKeyStore(f, TestBox()).load())
        assertNull(store.hint())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an incomplete key is never saved`() {
        runBlocking { ManagementKeyStore(file(), TestBox()).save(ManagementKey("short", "no key here")) }
    }

    @Test
    fun `the key never prints itself`() {
        val k = ManagementKey("mgmt-key-12345678", pem)
        assertEquals("ManagementKey(••••5678)", k.toString())
        assertTrue(k.complete)
        assertFalse(ManagementKey("mgmt-key-12345678", "").complete)
    }
}
