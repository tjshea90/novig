package com.tjshea.vigilant.data.keys

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileApiKeyStoreTest {

    private val file = File.createTempFile("keys", ".json").also { it.delete() }

    @Test
    fun `keys persist in order across a restart (an app update keeps this file)`() = runTest {
        FileApiKeyStore(file).setKeys(ApiProvider.THE_ODDS_API, listOf(" first ", "second", "first", ""))
        FileApiKeyStore(file).setKeys(ApiProvider.PINNAPI, listOf("pinn-1"))
        val reopened = FileApiKeyStore(file)
        assertEquals(listOf("first", "second"), reopened.getKeys(ApiProvider.THE_ODDS_API))
        assertEquals(listOf("pinn-1"), reopened.getKeys(ApiProvider.PINNAPI))
        assertTrue(file.readText().contains("\"first\""))
    }

    @Test
    fun `an export imports into a fresh install, merging without duplicates`() = runTest {
        val a = FileApiKeyStore(file)
        a.setKeys(ApiProvider.THE_ODDS_API, listOf("k1", "k2"))
        a.setKeys(ApiProvider.PINNAPI, listOf("p1"))
        val exported = a.exportJson()

        val other = FileApiKeyStore(File.createTempFile("keys2", ".json").also { it.delete() })
        other.setKeys(ApiProvider.THE_ODDS_API, listOf("k2", "k9"))
        assertEquals(2, other.importJson(exported))
        assertEquals(listOf("k2", "k9", "k1"), other.getKeys(ApiProvider.THE_ODDS_API))
        assertEquals(listOf("p1"), other.getKeys(ApiProvider.PINNAPI))
    }

    @Test
    fun `a file that isn't an export is refused, keys untouched`() = runTest {
        val s = FileApiKeyStore(file)
        s.setKeys(ApiProvider.THE_ODDS_API, listOf("k1"))
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { s.importJson("not json") } }
        assertEquals(listOf("k1"), s.getKeys(ApiProvider.THE_ODDS_API))
    }
}
