package com.tjshea.vigilant.data.novig

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Who asked Novig for books (the v0.89.34 file had 651,623 reads and no caller in them): a read counts under the tag of the code that made it, "other" with none. */
class NovigReadsTest {
    @Test
    fun `reads are counted under the caller's tag, most first, and printed for the diagnostics file`() = runBlocking<Unit> {
        NovigReads.reset()
        assertNull(NovigReads.line())
        withContext(NovigReadTag("scan")) { NovigReads.add(kotlin.coroutines.coroutineContext[NovigReadTag]!!.tag, 300) }
        NovigReads.add("cno-live", 40)
        NovigReads.add("scan", 20)
        NovigReads.add("other", 0)
        assertEquals(listOf("scan" to 320L, "cno-live" to 40L), NovigReads.snapshot())
        val line = NovigReads.line()!!
        assertTrue(line, line.contains("(360 requests)") && line.contains("scan 320 · cno-live 40"))
        NovigReads.reset()
    }
}
