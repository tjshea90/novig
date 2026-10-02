package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-02: "On the last scan the novig scanning was going very slow, make sure it is set up correctly." It was Novig refusing his key (423 on
 * every signed route): the scan reads the public routes then, at under half the key's pace and with no live feed. A scan that started while the
 * key was already set aside never tried it, so it was slow with no word why; it says so now.
 */
class ScanKeyDownTest {

    private val now = Fixtures.START_MS - 86_400_000L
    private val settings = ScanSettings(leagues = setOf("MLB"), fairSource = FairSource.MARKET_AVERAGE, minBooks = 1, daysAhead = 60)

    private class Novig(val down: String?) : NovigSource {
        private val start = Fixtures.START_MS
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) =
            listOf(NovigEvent("e1", "BASEBALL", "MLB", "OPEN_PREGAME", "Away @ Home", start))
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) =
            listOf(NovigMarket("m1", "e1", "MONEY", "OPEN", "ML", start, MarketFee.GAME, listOf(NovigOutcome("a", "Away", "TBD"), NovigOutcome("h", "Home", "TBD"))))
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch {
            val b = marketIds.associateWith { NovigBook(it, 1, mapOf("a" to listOf(BidLevel(440, 1000)), "h" to listOf(BidLevel(550, 1000))), 0L) }
            return BookBatch(b, 0, b.size, 0)
        }
        override suspend fun market(marketId: String): NovigMarket? = null
        override fun keyDown(at: Long): String? = down
    }

    @Test
    fun `a scan started while Novig's key is set aside says why it read the slower public prices`() = runTest {
        val why = "Novig refused the key (423 ACCOUNT_LOCKED): contact Novig support."
        val report = Scanner(Novig(why), clock = { now }).scan(settings, emptyList(), onProgress = {}, onPartial = {})
        val line = report.errors.single { it.startsWith("Novig key:") }
        assertEquals("Novig key: $why ${Scanner.PUBLIC_PRICES}", line)
        assertTrue(line.contains("up to 6 a second against the key's 14"))
        // With the key reading, nothing is said.
        val ok = Scanner(Novig(null), clock = { now }).scan(settings, emptyList(), onProgress = {}, onPartial = {})
        assertTrue(ok.errors.none { it.startsWith("Novig key:") })
    }
}
