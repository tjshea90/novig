package com.tjshea.vigilant.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

/** The splitter must see exactly the elements a full parse sees (Tj, 2026-10-10: a reply parsed as one tree filled the heap), and give up, not guess, on anything odd. */
class JsonSplitTest {
    private val json = Json

    @Test
    fun `a root array's elements are exactly the full parse's, strings with brackets, quotes and escapes included`() {
        val raw = """ [ {"a":"x]},\"[{", "b":[1,2,{"c":null}]} , "plain", 12.5e3, true, null, [], {} , [ [1], {"k":"v"} ] ] """
        val s = JsonSplit.elements(raw)!!
        val expected = json.parseToJsonElement(raw) as JsonArray
        assertEquals(expected.size, s.size)
        for (i in 0 until s.size) assertEquals(expected[i], json.parseToJsonElement(s.element(i)))
        assertEquals("[]", s.rest)
    }

    @Test
    fun `the array under a top-level key is split, the other keys come back in a small rest`() {
        val raw = """{"nextCursor":"abc","success":true,"notice":"a \"note\"","data":[{"eventID":"1","odds":{"x":{"y":[1,2,3]}}},{"eventID":"2"}],"after":{"data":[9]}}"""
        val s = JsonSplit.elements(raw, "data")!!
        assertEquals(2, s.size)
        assertEquals("""{"eventID":"2"}""", s.element(1))
        val rest = json.parseToJsonElement(s.rest) as JsonObject
        assertEquals("abc", (rest["nextCursor"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals(JsonArray(emptyList()), rest["data"])
        assertEquals(json.parseToJsonElement(raw).let { (it as JsonObject)["after"] }, rest["after"])
    }

    @Test
    fun `random nested documents split the way the full parse reads them`() {
        val rnd = Random(7)
        fun value(depth: Int): String = when (if (depth > 3) rnd.nextInt(4) else rnd.nextInt(7)) {
            0 -> "null"; 1 -> rnd.nextInt(-5, 500).toString(); 2 -> "\"" + "ab]}\\\",[".substring(0, rnd.nextInt(1, 9)).replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            3 -> if (rnd.nextBoolean()) "true" else "1.5e2"
            4, 5 -> "[" + (0 until rnd.nextInt(0, 4)).joinToString(" , ") { value(depth + 1) } + "]"
            else -> "{" + (0 until rnd.nextInt(0, 4)).joinToString(",") { "\"k$it\" : ${value(depth + 1)}" } + "}"
        }
        repeat(300) {
            val raw = "[" + (0 until rnd.nextInt(0, 6)).joinToString(",\n") { value(0) } + "]"
            val s = JsonSplit.elements(raw)!!
            val expected = json.parseToJsonElement(raw) as JsonArray
            assertEquals(raw, expected.size, s.size)
            for (i in 0 until s.size) assertEquals(expected[i], json.parseToJsonElement(s.element(i)))
        }
    }

    @Test
    fun `anything odd is null, so the caller falls back to the whole parse`() {
        assertNull(JsonSplit.elements("""[{"a":1},{"b":"""))                 // cut short
        assertNull(JsonSplit.elements("""{"data":{"a":1}}""", "data"))       // not an array
        assertNull(JsonSplit.elements("""{"other":[1]}""", "data"))          // no such key
        assertNull(JsonSplit.elements("not json"))
        assertNull(JsonSplit.elements(""))
        assertNotNull(JsonSplit.elements("[]"))
        assertEquals(0, JsonSplit.elements("""{"data":[]}""", "data")!!.size)
    }
}
