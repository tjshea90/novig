package com.tjshea.vigilant.data.keys

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tj, 2026-10-19: The Odds API stays on the key that last answered until it is spent, then goes to the next in his order. */
class StickyKeyPoolTest {
    private fun meter() = UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), { 1_000L })

    @Test fun `stays on the working key and moves on only when it is spent`() = runBlocking {
        val pool = KeyPool(QuotaPolicy.ODDS_API, { listOf("a", "b", "c") }, meter(), sticky = true)
        val used = mutableListOf<String>()
        repeat(3) { pool.execute(1) { k -> used += k; KeyAttemptResult.Success(Unit) } }
        pool.execute(1) { k -> used += k; if (k == "a") KeyAttemptResult.Depleted("spent") else KeyAttemptResult.Success(Unit) }
        repeat(2) { pool.execute(1) { k -> used += k; KeyAttemptResult.Success(Unit) } }
        assertEquals(listOf("a", "a", "a", "a", "b", "b", "b"), used)
    }
}
