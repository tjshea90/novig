package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The key's live feed takes ONE bulk subscribe (up to 2,000 books, no request each) and then needs ~2 minutes before the next (its 512-token
 * bucket refills at 4 a second). Tj's 2026-10-02 Diagnostics: "4,588 Novig prices in 318 s (14.4 a second: 113 by live feed, 4475 through the
 * key)". The plan only holds lines a fair source quotes, so it grows as the sources answer (fair odds took 29 s), and the subscribe went out at
 * ~8 s with the first source's lines; the next ones favoured lines the requests had already read. Now the feed is opened at once but handed
 * its markets when the plan has filled in, and only the lines still unread (RESEARCH.md §63).
 */
class LiveFeedPlanTest {

    private var now = Fixtures.START_MS - 86_400_000L
    private var t = 0L
    private val settings = ScanSettings(leagues = setOf("MLB"), fairSource = FairSource.MARKET_AVERAGE, minBooks = 1, minEvPercent = 0.01, daysAhead = 60)

    private val events = (0 until 60).map { i -> NovigEvent("e$i", "BASEBALL", "MLB", "OPEN_PREGAME", "Away $i @ Home $i", Fixtures.START_MS + i * 3_600_000L) }
    private val markets = (0 until 60).map { i ->
        NovigMarket("m$i", "e$i", "MONEY", "OPEN", "ML", Fixtures.START_MS + i * 3_600_000L, MarketFee.GAME, listOf(NovigOutcome("a$i", "Away $i", "TBD"), NovigOutcome("h$i", "Home $i", "TBD")))
    }

    private fun book(id: String): NovigBook {
        val i = id.drop(1).toInt()
        return NovigBook(id, 1, mapOf("a$i" to listOf(BidLevel(480, 1000)), "h$i" to listOf(BidLevel(480, 1000))), now)
    }

    /**
     * Novig with a key: requests read 8 at a time; the live feed holds what it was last handed (up to [room]) and pushes it at once. The plan also
     * holds the moneyline of up to 20 games no fair source quotes yet (Novig's own price, shown unpriced), read last.
     */
    private inner class Keyed(val room: Int = 2_000, val onBooks: (Int) -> Unit = {}, val onWatch: (List<String>) -> Unit = {}) : NovigSource {
        val calls = ArrayList<List<String>>()
        val watched = ArrayList<List<String>>()
        var opened = 0
        var held: Set<String> = emptySet()
        /** Requests made before the first [watch]. */
        var callsBeforeWatch = -1
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = events
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = markets
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?): BookBatch {
            calls += marketIds.toList()
            onBooks(calls.size)
            // A request takes time: the other sources answer meanwhile.
            kotlinx.coroutines.yield()
            val b = marketIds.associateWith { book(it) }
            return BookBatch(b, 0, b.size, 0, viaKey = b.size)
        }
        override suspend fun market(marketId: String): NovigMarket? = null
        override fun openFeed() { opened++ }
        override fun watch(marketIds: Collection<String>) {
            if (watched.isEmpty()) callsBeforeWatch = calls.size
            watched += marketIds.toList()
            held = marketIds.take(room).toSet()
            onWatch(marketIds.toList())
        }
        override fun pushed(marketIds: Collection<String>): Map<String, NovigBook> = marketIds.filter { it in held }.associateWith { book(it) }
    }

    /** A fair source quoting games [from] until [until], answering once [gate] opens (null: at once). */
    private inner class Fair(override val id: String, private val from: Int, private val until: Int, private val gate: CompletableDeferred<Unit>? = null) : ReferenceSource {
        override val displayName = id
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
            gate?.await()
            return RefSnapshot(
                league.oddsApiSportKey,
                (from until until).map { i ->
                    RefEvent(
                        "$id$i", league.oddsApiSportKey, events[i].startsTs, home = "Home $i", away = "Away $i",
                        markets = listOf(RefBookMarket(id, id, LineKind.MONEYLINE, listOf(RefQuote(Side.AWAY, 2.0, null), RefQuote(Side.HOME, 2.0, null)), now)),
                    )
                },
                now,
            )
        }
    }

    private fun scanner(novig: NovigSource, holdMs: Long = Scanner.STREAM_HOLD_MS, room: Int = 2_000) =
        Scanner(novig, clock = { now }, elapsed = { t }, streamHoldMs = holdMs, streamRoom = room)

    @Test
    fun `the live feed is handed the plan once every source has answered, not the first source's few lines`() = runTest {
        val slow = CompletableDeferred<Unit>()
        // Kalshi (games 10-59) answers while the first requests are out, as on the phone (fair odds 29 s, the feed's bucket full at ~8 s).
        val novig = Keyed(onBooks = { if (it == 1) slow.complete(Unit) })
        val r = scanner(novig).scan(settings, listOf(Fair("polymarket", 0, 10), Fair("kalshi", 10, 60, slow)), onProgress = {}, onPartial = {})

        assertTrue("opened before it was handed anything", novig.opened >= 1)
        assertEquals("one bulk subscribe a scan", 1, novig.watched.size)
        val asked = novig.watched.single()
        // Every line still unread when the plan was in: Kalshi's 50 and Polymarket's 2 the first request didn't take; none it already read.
        assertEquals(novig.calls.first().toSet().intersect(asked.toSet()), emptySet<String>())
        assertTrue(asked.containsAll((10 until 60).map { "m$it" }))
        assertEquals(60, r.booksFetched)
        assertEquals(60 - 8, r.booksViaPush)
        assertEquals(asked.size, r.timing!!.liveFeedAsked)
    }

    @Test
    fun `a source that hasn't answered holds the subscribe at most the hold time`() = runTest {
        val slow = CompletableDeferred<Unit>()
        // The first request takes 40 s of the scan; Kalshi answers only once the feed has been handed something.
        val novig = Keyed(onBooks = { t += 40_000L }, onWatch = { slow.complete(Unit) })
        val r = scanner(novig, holdMs = 30_000L).scan(settings, listOf(Fair("polymarket", 0, 10), Fair("kalshi", 10, 60, slow)), onProgress = {}, onPartial = {})
        // At 40 s (past the 30 s hold) it went with what was planned and unread: Polymarket's last 2, and the moneylines of the 20 games
        // nothing quoted yet.
        assertEquals((8 until 30).map { "m$it" }, novig.watched.first())
        assertEquals(40_000L, r.timing!!.liveFeedAtMs)
        assertEquals(1, novig.watched.size)
        // Kalshi's other lines, planned after the one subscribe, were read by request.
        assertEquals((30 until 60).map { "m$it" }.toSet(), novig.calls.drop(1).flatten().toSet())
    }

    @Test
    fun `more unread lines than the feed holds go at once, with no wait`() = runTest {
        val slow = CompletableDeferred<Unit>()
        val novig = Keyed(room = 5, onWatch = { slow.complete(Unit) })
        scanner(novig, room = 5).scan(settings, listOf(Fair("polymarket", 0, 10), Fair("kalshi", 10, 60, slow)), onProgress = {}, onPartial = {})
        // Before any request: the planned lines (Polymarket's 10 first), more than its 5.
        assertEquals(0, novig.callsBeforeWatch)
        assertEquals((0 until 10).map { "m$it" }, novig.watched.first().take(10))
        assertEquals(5, novig.calls.flatten().count { it in (0 until 10).map { i -> "m$i" } })
    }

    @Test
    fun `what the feed already holds from the last scan goes first, so the next scan drops nothing it can use`() = runTest {
        val novig = Keyed()
        val sc = scanner(novig)
        sc.scan(settings, listOf(Fair("polymarket", 0, 60)), onProgress = {}, onPartial = {})
        val first = novig.watched.single()
        // The next scan: the feed still holds those books, current; it reads them with no request and keeps them.
        novig.calls.clear()
        val r = sc.scan(settings, listOf(Fair("polymarket", 0, 60)), onProgress = {}, onPartial = {})
        assertEquals(2, novig.watched.size)
        val second = novig.watched.last()
        assertEquals(first.toSet(), second.take(first.size).toSet())
        assertEquals(60, r.booksFetched)
        assertTrue(r.booksViaPush >= first.size)
    }

    @Test
    fun `a bets-only pass never touches the feed`() = runTest {
        val novig = Keyed()
        Scanner(novig, clock = { now }, elapsed = { t }, betsOnly = true)
            .scan(settings, listOf(Fair("polymarket", 0, 60)), pinned = setOf("m3"), onProgress = {}, onPartial = {})
        assertEquals(0, novig.watched.size)
        assertEquals(0, novig.opened)
    }
}
