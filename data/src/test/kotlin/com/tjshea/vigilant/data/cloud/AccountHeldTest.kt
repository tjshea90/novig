package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.trading.NovigPosition
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountHeldTest {
    private val start = 1_800_000_000_000L
    private val info = CloudTrader.OutcomeInfo("e-a", "Boston Celtics @ New York Knicks", start, "Player Points", "Jalen Brunson Over 25.5")

    @Test fun `positions the caller does not count are returned with their family, counted ones are dropped`() = runBlocking {
        var rows: List<NovigPosition>? = listOf(NovigPosition("m1", "o1", 100L, 40.0), NovigPosition("m2", "o2", 50L, 20.0), NovigPosition("m3", "o3", 0L, 0.0))
        val h = AccountHeld({ rows }, { id -> if (id == "o1") info else null })
        h.refresh()
        val extra = h.extra(setOf("o2|m2"))
        assertEquals(listOf("o1"), extra.map { it.outcomeId })
        assert(extra.single().family.isNotEmpty())
        // a failed read keeps the last good snapshot
        rows = null
        h.refresh()
        assertEquals(2, h.extra(emptySet()).size)
    }
}
