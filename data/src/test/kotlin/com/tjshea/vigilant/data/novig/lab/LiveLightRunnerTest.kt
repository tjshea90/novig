package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.live.Fetched
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.novig.stream.BookListener
import com.tjshea.vigilant.data.novig.stream.PushedBooks
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live light's push lane on a fake Novig and a fake scoreboard: a blowout at a stale price becomes an offer for the taker, and only with the right safeguards. */
class LiveLightRunnerTest {
    private val event = NovigEvent("ev", "Basketball", "NBA", NovigEvent.STATUS_LIVE, "Away Team @ Home Team", 0L)
    private val market = NovigMarket(
        "m1", "ev", "MONEY", "OPEN", "d", 0L, MarketFee.GAME,
        listOf(NovigOutcome("m1-h", "Home Team", "TBD"), NovigOutcome("m1-a", "Away Team", "TBD")), null,
    )

    private class Src(val event: NovigEvent, val markets: List<NovigMarket>) : NovigSource {
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = listOf(event)
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = markets
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?) = BookBatch(emptyMap(), 0, marketIds.size, 0)
        override suspend fun market(marketId: String): NovigMarket? = markets.firstOrNull { it.marketId == marketId }
    }

    private class View(val books: Map<String, NovigBook>) : PushedBooks {
        var watched: List<String> = emptyList()
        var opened = false
        override fun watch(marketIds: Collection<String>) { watched = marketIds.toList() }
        override fun watchLive(marketIds: Collection<String>) { watched = marketIds.toList() }
        override fun open() { opened = true }
        override fun live(marketIds: Collection<String>): Map<String, NovigBook> = books.filterKeys { it in marketIds }
        override fun problemSince(sinceMs: Long): String? = null
        override fun close() {}
    }

    /** The home team's best bid and the away team's: the away side (the line's YES) costs 1 - the home bid. */
    private fun book(homeBid: Int, awayBid: Int) = NovigBook("m1", 1L, mapOf("m1-h" to listOf(BidLevel(homeBid, 5_000)), "m1-a" to listOf(BidLevel(awayBid, 5_000))), 0L)

    private fun scoreboard(home: Int, away: Int, period: Int = 4, clock: String = "2:00") =
        """{"events":[{"status":{"period":$period,"displayClock":"$clock","type":{"state":"in"}},"competitions":[{"competitors":[
        {"homeAway":"home","score":"$home","team":{"displayName":"Home Team","abbreviation":"HOM"}},
        {"homeAway":"away","score":"$away","team":{"displayName":"Away Team","abbreviation":"AWY"}}]}]}]}"""

    private val now = 1_800_000_000_000L

    private fun runner(view: View, offers: MutableList<TailOffer>, board: String?, confirm: LiveConfirm? = null, clock: () -> Long = { now }) =
        LiveLightRunner(
            scope = TestScope(), source = Src(event, listOf(market)), newView = { _: BookListener -> view },
            fetch = { board?.let { Fetched(it, 5L) } }, onOffer = { offers += it }, confirm = { confirm }, leagues = { setOf("NBA") }, clock = clock,
        )

    private suspend fun LiveLightRunner.look() { readCatalog(); readScores(); scanGame("ev") }

    @Test
    fun `a moderately stale price on a decided game is offered to the taker with no second source`() = runBlocking {
        // Away leads by 25 with two minutes left; Novig still sells it at 0.80.
        val view = View(mapOf("m1" to book(homeBid = 200, awayBid = 200)))
        val offers = ArrayList<TailOffer>()
        val r = runner(view, offers, scoreboard(80, 105))
        r.look()
        assertEquals(listOf("m1"), view.watched)
        assertTrue(view.opened)
        val o = offers.single()
        assertEquals("m1-a", o.candidate.outcomeId)
        assertNull(o.confirm)
        assertEquals(0.80, o.candidate.ask, 1e-9)
        assertEquals(1L, r.status.value.offers)
        assertEquals(1, r.status.value.games)
        assertEquals(1, r.status.value.withScore)
    }

    @Test
    fun `a fifty-fifty price on a decided game is not believed alone, and is believed when BoltOdds' soft books agree`() = runBlocking {
        val view = View(mapOf("m1" to book(homeBid = 490, awayBid = 490)))
        val alone = ArrayList<TailOffer>()
        val r1 = runner(view, alone, scoreboard(80, 105))
        r1.look()
        assertTrue("an edge of 90% is more likely a wrong game than a gift", alone.isEmpty())
        assertTrue(r1.status.value.skipped.keys.any { it.contains("no second live source") })

        val agreeing = ArrayList<TailOffer>()
        val r2 = runner(view, agreeing, scoreboard(80, 105), confirm = LiveConfirm { _, ref, _ -> if (ref == "Away Team") 0.985 else 0.015 })
        r2.look()
        val o = agreeing.single()
        assertEquals(0.985, o.confirm!!, 1e-9)
        assertEquals("priced two points under the second source", 0.965, o.candidate.fair, 1e-9)

        // A second source that does not agree changes nothing: still not offered.
        val split = ArrayList<TailOffer>()
        runner(view, split, scoreboard(80, 105), confirm = LiveConfirm { _, _, _ -> 0.55 }).look()
        assertTrue(split.isEmpty())
    }

    @Test
    fun `nothing is judged on an old score, a close game, a closed market, or a feed with no books`() = runBlocking {
        val offers = ArrayList<TailOffer>()
        var t = now
        val view = View(mapOf("m1" to book(200, 200)))
        val r = runner(view, offers, scoreboard(80, 105), clock = { t })
        r.readCatalog(); r.readScores()
        t += LiveLightRunner.STATE_MAX_AGE_MS + 1
        r.scanGame("ev")
        assertTrue(offers.isEmpty())
        assertTrue(r.status.value.skipped.keys.any { it.startsWith("score older") })
        val close = ArrayList<TailOffer>()
        runner(view, close, scoreboard(80, 82)).look()
        assertTrue(close.isEmpty())
        val noBooks = ArrayList<TailOffer>()
        runner(View(emptyMap()), noBooks, scoreboard(80, 105)).look()
        assertTrue(noBooks.isEmpty())
        val noScore = ArrayList<TailOffer>()
        val rn = runner(view, noScore, null)
        rn.readCatalog(); rn.readScores(); rn.scanGame("ev")
        assertTrue(noScore.isEmpty())
        assertNotNull(rn.status.value.skipped["no score for the game"])
    }

    @Test
    fun `the same offer is not handed over again within a moment unless the price improves`() = runBlocking {
        val view = View(mapOf("m1" to book(200, 200)))
        val offers = ArrayList<TailOffer>()
        val r = runner(view, offers, scoreboard(80, 105))
        r.look()
        r.scanGame("ev")
        assertEquals(1, offers.size)
    }
}
