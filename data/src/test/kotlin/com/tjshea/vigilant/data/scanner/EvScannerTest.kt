package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigRepository
import com.tjshea.vigilant.data.novig.SampleNovigRepository
import com.tjshea.vigilant.data.reference.ReferenceEvent
import com.tjshea.vigilant.data.reference.ReferenceOddsRepository
import com.tjshea.vigilant.data.reference.SampleReferenceOddsRepository
import com.tjshea.vigilant.engine.Consensus
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.EvOpportunity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvScannerTest {

    private val scanner = EvScanner(
        novigRepository = SampleNovigRepository(),
        referenceOddsRepository = SampleReferenceOddsRepository(),
        sportKeys = listOf("sample"),
        devigMethod = DevigMethod.MULTIPLICATIVE,
    )

    private fun EvOpportunity.matches(outcomeLabel: String, eventContains: String) =
        outcomeLabel == this.outcomeLabel && this.eventName.contains(eventContains)

    @Test
    fun `scans the whole sample board and evaluates every matched outcome`() = runTest {
        val opportunities = scanner.scan()
        // 3 sample events x 2 outcomes each, all matched by team name.
        assertEquals(6, opportunities.size)
    }

    @Test
    fun `an underpriced pregame favorite-vs-dog leg is flagged positive EV using the sharp book alone`() = runTest {
        val opportunities = scanner.scan()
        val dolphins = opportunities.first { it.matches("Miami Dolphins", "Miami Dolphins") }

        assertEquals(listOf("Pinnacle"), dolphins.referenceBooks)
        val expectedFair = Consensus.computeReferenceProbabilities(
            SampleReferenceOddsRepository.SAMPLE_EVENTS[0].quotesByMarket.getValue("h2h"),
            DevigMethod.MULTIPLICATIVE,
        ).fairProbabilityByOutcome[0]
        assertEquals(expectedFair, dolphins.fairProbability, 1e-9)
        assertTrue("pregame Dolphins leg should show a real, modest positive edge", dolphins.isPositiveEv)
        assertTrue("edge should be small and believable, not an OddsAssist-style 18% outlier", dolphins.roi < 0.10)
    }

    @Test
    fun `the other side of that same market is not flagged`() = runTest {
        val opportunities = scanner.scan()
        val niners = opportunities.first { it.matches("San Francisco 49ers", "Miami Dolphins") }
        assertFalse(niners.isPositiveEv)
    }

    @Test
    fun `falls back to averaging every book when no sharp book is fetched`() = runTest {
        val opportunities = scanner.scan()
        val southCarolina = opportunities.first { it.matches("South Carolina", "South Carolina") }
        assertEquals(setOf("DraftKings", "FanDuel", "BetMGM"), southCarolina.referenceBooks.toSet())
    }

    @Test
    fun `neither side of a fairly-priced market is flagged positive EV`() = runTest {
        val opportunities = scanner.scan()
        val scAndBama = opportunities.filter { it.eventName.contains("South Carolina") }
        assertEquals(2, scAndBama.size)
        assertTrue(scAndBama.none { it.isPositiveEv })
    }

    @Test
    fun `a live market's tiny raw edge gets wiped out by Novig's own taker fee`() = runTest {
        val opportunities = scanner.scan()
        val liberty = opportunities.first { it.matches("New York Liberty", "New York Liberty") }

        assertTrue("raw edge should still be (barely) positive before fees", liberty.rawEdge > 0.0)
        assertFalse("net EV must go negative once the live taker fee is netted out", liberty.isPositiveEv)
        assertTrue(liberty.netEv != null && liberty.netEv!! < 0.0)
    }

    @Test
    fun `results are ranked by net EV, highest first`() = runTest {
        val opportunities = scanner.scan()
        val netEvs = opportunities.map { it.netEv ?: Double.NEGATIVE_INFINITY }
        assertEquals(netEvs.sortedDescending(), netEvs)
    }

    @Test
    fun `an empty sport-key list scans nothing and never touches either repository`() = runTest {
        var novigCalled = false
        var referenceCalled = false
        val noSportScanner = EvScanner(
            novigRepository = object : NovigRepository {
                override suspend fun getOpenMarkets(limit: Int): List<NovigEvent> {
                    novigCalled = true
                    return SampleNovigRepository().getOpenMarkets(limit)
                }
            },
            referenceOddsRepository = object : ReferenceOddsRepository {
                override suspend fun getOddsForSport(sportKey: String, marketKeys: List<String>): List<ReferenceEvent> {
                    referenceCalled = true
                    return SampleReferenceOddsRepository().getOddsForSport(sportKey, marketKeys)
                }
            },
            sportKeys = emptyList(),
        )

        val opportunities = noSportScanner.scan()

        assertTrue(opportunities.isEmpty())
        assertFalse("no sport selected must not fetch the Novig leg", novigCalled)
        assertFalse("no sport selected must not fetch the reference leg", referenceCalled)
    }
}
