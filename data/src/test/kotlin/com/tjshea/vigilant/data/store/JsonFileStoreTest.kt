package com.tjshea.vigilant.data.store

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JsonFileStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun store(f: File) = JsonFileStore(f, ListSerializer(String.serializer()), { emptyList() })

    @Test
    fun `values survive a restart`() = runTest {
        val f = File(tmp.root, "bets.json")
        store(f).update { it + "a" }
        assertEquals(listOf("a"), store(f).read())
        assertTrue(!File(tmp.root, "bets.json.tmp").exists())
    }

    @Test
    fun `concurrent updates never lose a write`() = runTest {
        val f = File(tmp.root, "bets.json")
        val s = store(f)
        (1..50).map { i -> async { s.update { it + "b$i" } } }.awaitAll()
        assertEquals(50, store(f).read().size)
    }

    @Test
    fun `a corrupt file is set aside, not deleted, and the app still starts`() = runTest {
        val f = File(tmp.root, "bets.json").apply { writeText("{not json") }
        assertEquals(emptyList<String>(), store(f).read())
        assertEquals("{not json", File(tmp.root, "bets.json.corrupt").readText())
    }
}
