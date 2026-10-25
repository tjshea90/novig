package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.trading.NovigPosition
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertEquals

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

    @Test fun `what the account holds beyond the engine's ledger is Tj's, by hand, paying a cent a contract`() = runBlocking {
        val h = AccountHeld({ listOf(NovigPosition("m1", "o1", 300L, 1.50)) }, { id -> if (id == "o1") info else null })
        h.refresh()
        // the engine's ledger holds 100 of the 300 contracts: the other 200 ($1.00 of cost, $2.00 of payout) are Tj's
        val mine = h.extraBeyond(mapOf("o1|m1" to 100L)).single()
        assertEquals(1.00, mine.dollars, 1e-9)
        assertEquals(2.00, mine.payout, 1e-9)
        assertEquals(com.tjshea.vigilant.data.tracker.RepeatGuard.Who.HAND, mine.who)
        assertEquals("Jalen Brunson Over 25.5", mine.label)
        assertEquals(emptyList<Any>(), h.extraBeyond(mapOf("o1|m1" to 300L)))
        assertEquals(3.00, h.extra(emptySet()).single().payout, 1e-9)
    }
}
