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
    private val event = NovigEvent("ev", "Basketball", "NBA", NovigEvent.STATUS_LIVE, "Miami Heat @ Boston Celtics", 0L)
    private val market = NovigMarket(
        "m1", "ev", "MONEY", "OPEN", "d", 0L, MarketFee.GAME,
        listOf(NovigOutcome("m1-b", "Boston Celtics", "TBD"), NovigOutcome("m1-m", "Miami Heat", "TBD")), null,
    )

    private class Src(val event: NovigEvent, val markets: List<NovigMarket>) : NovigSource {
        var marketCalls = 0
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = listOf(event)
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?): List<NovigMarket> { marketCalls++; return markets }
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

    /** The best bids on Boston (the line's YES: it costs 1 - Miami's bid) and on Miami. */
    private fun book(bostonBid: Int, miamiBid: Int) = NovigBook("m1", 1L, mapOf("m1-b" to listOf(BidLevel(bostonBid, 5_000)), "m1-m" to listOf(BidLevel(miamiBid, 5_000))), 0L)

    private fun scoreboard(home: Int, away: Int, period: Int = 4, clock: String = "2:00") =
        """{"events":[{"status":{"period":$period,"displayClock":"$clock","type":{"state":"in"}},"competitions":[{"competitors":[
        {"homeAway":"home","score":"$home","team":{"displayName":"Boston Celtics","abbreviation":"BOS"}},
        {"homeAway":"away","score":"$away","team":{"displayName":"Miami Heat","abbreviation":"MIA"}}]}]}]}"""

    private val now = 1_800_000_000_000L

    private class Desk(var heldPrice: Double? = null) : LightBidDesk {
        val wants = ArrayList<com.tjshea.vigilant.data.livebid.LiveBidWant>()
        val pulls = ArrayList<Pair<String, String>>()
        val noWants = ArrayList<String>()
        var kept = 0
        override fun want(w: com.tjshea.vigilant.data.livebid.LiveBidWant) { wants += w }
        override fun keepAlive(outcomeId: String, nowMs: Long) { kept++ }
        override fun noWant(outcomeId: String, reason: String) { noWants += reason }
        override fun pull(outcomeId: String, reason: String) { pulls += outcomeId to reason }
        override fun noteFair(outcomeId: String, fair: Double, atMs: Long) {}
        override fun held(outcomeId: String) = heldPrice?.takeIf { outcomeId == "m1-b" }?.let { com.tjshea.vigilant.data.livebid.LiveBidHeld(it, 0L, null) }
    }

    private fun runner(view: View, offers: MutableList<TailOffer>, board: String?, confirm: LiveConfirm? = null, clock: () -> Long = { now }, desk: Desk? = null, journal: (String) -> Unit = {}) =
        LiveLightRunner(
            scope = TestScope(), source = Src(event, listOf(market)), newView = { _: BookListener -> view },
            fetch = { board?.let { Fetched(it, 5L) } }, onOffer = { offers += it }, confirm = { confirm }, leagues = { setOf("NBA") }, clock = clock, bids = { desk }, publishMs = 0L, journal = journal,
        ).also { it.bind() }

    private suspend fun LiveLightRunner.look() { readCatalog(); readScores(); scanGame("ev") }

    @Test
    fun `a moderately stale price on a decided game is offered to the taker with no second source`() = runBlocking {
        // Boston (home) leads by 25 with two minutes left; Novig still sells it at 0.80.
        val view = View(mapOf("m1" to book(bostonBid = 200, miamiBid = 200)))
        val offers = ArrayList<TailOffer>()
        val r = runner(view, offers, scoreboard(105, 80))
        r.look()
        assertEquals(listOf("m1"), view.watched)
        assertTrue(view.opened)
        val o = offers.single()
        assertEquals("m1-b", o.candidate.outcomeId)
        assertNull(o.confirm)
        assertEquals(0.80, o.candidate.ask, 1e-9)
        assertEquals(1L, r.status.value.offers)
        assertEquals(1, r.status.value.games)
        assertEquals(1, r.status.value.withScore)
    }

    @Test
    fun `a fifty-fifty price on a decided game is not believed alone, and is believed when BoltOdds' soft books agree`() = runBlocking {
        val view = View(mapOf("m1" to book(bostonBid = 490, miamiBid = 490)))
        val alone = ArrayList<TailOffer>()
        val r1 = runner(view, alone, scoreboard(105, 80))
        r1.look()
        assertTrue("an edge of 90% is more likely a wrong game than a gift", alone.isEmpty())
        assertTrue(r1.status.value.skipped.keys.any { it.contains("no second live source") })

        val agreeing = ArrayList<TailOffer>()
        val r2 = runner(view, agreeing, scoreboard(105, 80), confirm = LiveConfirm { _, ref, _ -> if (ref == "Boston Celtics") 0.985 else 0.015 })
        r2.look()
        val o = agreeing.single()
        assertEquals(0.985, o.confirm!!, 1e-9)
        assertEquals("priced two points under the second source", 0.965, o.candidate.fair, 1e-9)

        // A second source that does not agree changes nothing: still not offered.
        val split = ArrayList<TailOffer>()
        runner(view, split, scoreboard(105, 80), confirm = LiveConfirm { _, _, _ -> 0.55 }).look()
        assertTrue(split.isEmpty())
    }

    @Test
    fun `nothing is judged on an old score, a close game, a closed market, or a feed with no books`() = runBlocking {
        val offers = ArrayList<TailOffer>()
        var t = now
        val view = View(mapOf("m1" to book(200, 200)))
        val r = runner(view, offers, scoreboard(105, 80), clock = { t })
        r.readCatalog(); r.readScores()
        t += LiveLightRunner.STATE_MAX_AGE_MS + 1
        r.scanGame("ev")
        assertTrue(offers.isEmpty())
        assertTrue(r.status.value.skipped.keys.any { it.startsWith("score older") })
        val close = ArrayList<TailOffer>()
        runner(view, close, scoreboard(82, 80)).look()
        assertTrue(close.isEmpty())
        val noBooks = ArrayList<TailOffer>()
        runner(View(emptyMap()), noBooks, scoreboard(105, 80)).look()
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
        val r = runner(view, offers, scoreboard(105, 80))
        r.look()
        r.scanGame("ev")
        assertEquals(1, offers.size)
    }

    @Test
    fun `with bids on a decided side gets a bid well under its fair and under what Novig offers, and none when bids are off`() = runBlocking {
        // Boston is 25 up with two minutes left; Novig sells it at 0.95 (the taker's edge is thin there), so a bid goes under it.
        val view = View(mapOf("m1" to book(bostonBid = 900, miamiBid = 50)))
        val desk = Desk()
        runner(view, ArrayList(), scoreboard(105, 80), desk = desk).look()
        val w = desk.wants.single()
        assertEquals("m1-b", w.outcomeId)
        assertTrue("price ${w.verdict.price}", w.verdict.price in 0.90..0.945)
        assertTrue(w.verdict.fair > 0.99)
        assertEquals("105-80", w.score)
        // The side nobody can win is not bid on.
        assertTrue(desk.wants.none { it.outcomeId == "m1-m" })
        val off = Desk()
        runner(view, ArrayList(), scoreboard(105, 80), desk = null).look()
        assertTrue(off.wants.isEmpty())
    }

    @Test
    fun `a bid already up is vouched for while the model clears it and pulled when it no longer does`() = runBlocking {
        val view = View(mapOf("m1" to book(bostonBid = 900, miamiBid = 50)))
        val comfortable = Desk(heldPrice = 0.90)
        runner(view, ArrayList(), scoreboard(105, 80), desk = comfortable).look()
        assertTrue(comfortable.pulls.isEmpty())
        assertEquals(1, comfortable.kept)
        val tight = Desk(heldPrice = 0.99)
        runner(view, ArrayList(), scoreboard(105, 80), desk = tight).look()
        assertEquals(listOf("m1-b"), tight.pulls.map { it.first })
        assertTrue(tight.pulls.single().second.contains("no longer clears"))
        // The game becomes unjudgeable (overtime has no readable clock): nothing vouches for the bid and nothing new is wanted; the desk takes an unvouched bid down within 3.5 s by itself.
        val ot = Desk(heldPrice = 0.90)
        runner(view, ArrayList(), scoreboard(105, 105, period = 5, clock = "5:00"), desk = ot).look()
        assertTrue(ot.wants.isEmpty() && ot.kept == 0)
    }

    @Test
    fun `alternate totals are watched near where the game is going, and one priced off its ladder is offered as a curve bet`() = runBlocking {
        val mu = 220.0
        val sigma = 12.0
        fun pOver(t: Double) = 1.0 - TailModel.phi((t - mu) / sigma)
        fun totalMarket(id: String, strike: Double) = NovigMarket(
            id, "ev", "TOTAL", "OPEN", "d", 0L, MarketFee.GAME,
            listOf(NovigOutcome("$id-u", "Under $strike", "TBD"), NovigOutcome("$id-o", "Over $strike", "TBD")), strike,
        )
        fun totalBook(id: String, overAsk: Double, underAsk: Double) =
            NovigBook(id, 1L, mapOf("$id-u" to listOf(BidLevel(((1 - overAsk) * 1000).toInt(), 5_000)), "$id-o" to listOf(BidLevel(((1 - underAsk) * 1000).toInt(), 5_000))), 0L)
        val honest = listOf(204.5, 208.5, 212.5, 216.5, 224.5, 228.5, 232.5, 236.5)
        val far = totalMarket("farout", 330.5)   // nowhere near this game: not watched
        val markets = honest.map { totalMarket("t${it.toInt()}", it) } + totalMarket("alt", 210.5) + far
        val books = honest.associate { "t${it.toInt()}" to totalBook("t${it.toInt()}", pOver(it) + 0.01, 1 - pOver(it) + 0.01) } +
            ("alt" to totalBook("alt", 0.60, 0.43)) + ("farout" to totalBook("farout", 0.5, 0.5))
        val view = View(books)
        val offers = ArrayList<TailOffer>()
        val r = LiveLightRunner(
            scope = TestScope(), source = Src(event, markets), newView = { _: BookListener -> view },
            fetch = { Fetched(scoreboard(60, 60, period = 3, clock = "7:12"), 5L) }, onOffer = { offers += it }, leagues = { setOf("NBA") }, clock = { now }, publishMs = 0L,
        ).also { it.bind() }
        r.readCatalog()
        assertTrue("with no score yet only the moneyline is watched (none here)", view.watched.isEmpty())
        r.readScores()
        r.readCatalog()
        assertTrue("alt" in view.watched && "t204" in view.watched)
        assertTrue("a strike 100 points from the game is not watched", "farout" !in view.watched)
        r.scanGame("ev")
        val o = offers.single()
        assertEquals("alt-o", o.candidate.outcomeId)
        assertEquals(LiveLightCurve.RULE, o.candidate.rule)
    }

    @Test
    fun `every watched game says in words what it is, the score, and its closest call and why it was not bet`() = runBlocking {
        val view = View(mapOf("m1" to book(bostonBid = 490, miamiBid = 490)))
        val r = runner(view, ArrayList(), scoreboard(82, 80))   // a close game: nothing decided
        r.look()
        val line = r.status.value.gameLines.getValue("ev")
        assertTrue(line, line.contains("Miami Heat 80 @ Boston Celtics 82"))
        assertTrue(line, line.contains("closest:") && line.contains("sure (needs 90.0%)"))
        // A game the scoreboard does not have is named, not silently skipped.
        val none = runner(view, ArrayList(), """{"events":[]}""")
        none.readCatalog(); none.readScores()
        assertTrue(none.status.value.gameLines.getValue("ev").contains("no score"))
        // A decided game at a good price: the line says the model's rules are met and the taker's limits decide.
        val good = runner(View(mapOf("m1" to book(bostonBid = 200, miamiBid = 200))), ArrayList(), scoreboard(105, 80))
        good.look()
        assertTrue(good.status.value.gameLines.getValue("ev").contains("meets the model's rules"))
    }

    @Test
    fun `every decision goes to the journal with the game state behind it`() = runBlocking {
        val view = View(mapOf("m1" to book(bostonBid = 200, miamiBid = 200)))
        val lines = ArrayList<String>()
        val r = runner(view, ArrayList(), scoreboard(105, 80), journal = { lines += it })
        r.look()
        val kinds = lines.map { Regex("\"kind\":\"([a-z-]+)\"").find(it)!!.groupValues[1] }.toSet()
        assertTrue("a per-game scan summary and the offer: $kinds", "scan" in kinds && "offer" in kinds)
        val scan = lines.first { it.contains("\"kind\":\"scan\"") }
        assertTrue(scan, scan.contains("\"homeScore\":105") && scan.contains("\"awayScore\":80") && scan.contains("\"fractionLeft\"") && scan.contains("\"sides\""))
        val offer = lines.first { it.contains("\"kind\":\"offer\"") }
        assertTrue(offer, offer.contains("\"ask\"") && offer.contains("\"fair\"") && offer.contains("\"edge\""))
    }

    @Test
    fun `the big market list is read once in ninety seconds, not at every catalog pass, and a status flood is published sparingly`() = runBlocking {
        var t = now
        val src = Src(event, listOf(market))
        val r = LiveLightRunner(
            scope = TestScope(), source = src, newView = { _: BookListener -> View(emptyMap()) }, fetch = { null }, onOffer = {}, leagues = { setOf("NBA") }, clock = { t },
        ).also { it.bind() }
        r.readCatalog(); r.readCatalog(); r.readCatalog()
        assertEquals(1, src.marketCalls)
        t += LiveLightRunner.MARKETS_TTL_MS + 1
        r.readCatalog()
        assertEquals(2, src.marketCalls)
        // Hundreds of changes inside half a second reach whoever reads the status as one.
        val seen = HashSet<Any>()
        repeat(500) { r.scanGame("nope"); seen += r.status.value }
        assertTrue("published ${seen.size} different statuses", seen.size <= 3)
    }
}
