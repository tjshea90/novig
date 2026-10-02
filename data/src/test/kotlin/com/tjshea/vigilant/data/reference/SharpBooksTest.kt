package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.CreditsHeldBackException
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SharpConfirm
import com.tjshea.vigilant.data.tracker.BetGrader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Where the sharp quote comes from (Tj, 2026-10-02: "can pinnapi or any other Pinnacle api be used in addition to cno to compare the odds?"): the feeds Vigilant
 * already has, cheapest first, the first that has the exact bet answering, a league's board kept a minute, a feed that can't answer skipped and said.
 */
class SharpBooksTest {

    private val start = Instant.parse("2026-10-02T00:15:00Z").toEpochMilli()
    private var now = start - 3_600_000L
    private val rules = SharpConfirm.rules(ScanSettings(sharpConfirmAutoBet = true), autoBet = true)!!

    /** Pittsburgh @ Cleveland: Pinnacle's Cleveland ML 2.31 / 1.676, total 38.5 (O 1.909 / U 1.943), an NFL game Novig would list. */
    private fun game(vararg markets: RefBookMarket) = RefEvent("g", "americanfootball_nfl", start, "Cleveland Browns", "Pittsburgh Steelers", markets.toList())

    private fun ml(book: String = "pinnacle", at: Long? = now - 20_000L) =
        RefBookMarket(book, book.replaceFirstChar { it.uppercase() }, LineKind.MONEYLINE, listOf(RefQuote(Side.HOME, 2.31, null), RefQuote(Side.AWAY, 1.676, null)), at)

    private fun total(book: String = "pinnacle", line: Double = 38.5, at: Long? = now - 20_000L) =
        RefBookMarket(book, book, LineKind.TOTAL, listOf(RefQuote(Side.OVER, 1.909, line), RefQuote(Side.UNDER, 1.943, line)), at)

    private fun spread(line: Double, at: Long? = now - 20_000L) =
        RefBookMarket("pinnacle", "Pinnacle", LineKind.SPREAD, listOf(RefQuote(Side.HOME, 1.926, line), RefQuote(Side.AWAY, 1.893, -line)), at)

    private fun prop(player: String, stat: String, line: Double, over: Double, under: Double, at: Long? = now - 20_000L) =
        RefBookMarket("pinnacle", "Pinnacle", LineKind.PLAYER_PROP, listOf(RefQuote(Side.OVER, over, line), RefQuote(Side.UNDER, under, line)), at, subject = player, stat = stat)

    /** A feed that counts its calls and answers [snap] (or throws [fail]). */
    private inner class Feed(
        override val id: String, override val displayName: String, val snap: () -> RefSnapshot? = { null }, val fail: Exception? = null,
        override val propsOnly: Boolean = false,
    ) : ReferenceSource {
        var calls = 0
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
            calls++
            fail?.let { throw it }
            return snap() ?: RefSnapshot("americanfootball_nfl", emptyList(), now, provider = id)
        }
    }

    private fun snapshot(vararg e: RefEvent, id: String = "x") = RefSnapshot("americanfootball_nfl", e.toList(), now - 5_000L, provider = id)

    private fun sharp(vararg feeds: ReferenceSource, keepMs: Long = 60_000L) = SharpBooks(sources = { feeds.toList() }, settings = { ScanSettings() }, clock = { now }, keepMs = keepMs)

    private fun bet(market: String = "Moneyline", selection: String = "Cleveland Browns") =
        SharpBooks.Bet("NFL", "Pittsburgh Steelers @ Cleveland Browns", start, market, selection)

    @Test
    fun `the exact line and side, with Pinnacle's own time and both prices`() = runBlocking {
        val feed = Feed("pinnacle", "Pinnacle (PinnWire)", { snapshot(game(ml(), total(), spread(2.5))) })
        val b = sharp(feed)
        val homeMl = b.quotes(bet(), rules).quotes.single()
        assertEquals("PN", homeMl.code)
        // The bet's side first, the other side second, in American odds.
        assertEquals(com.tjshea.vigilant.engine.Odds.decimalToAmerican(2.31), homeMl.odds)
        assertEquals(com.tjshea.vigilant.engine.Odds.decimalToAmerican(1.676), homeMl.otherOdds)
        assertEquals(now - 20_000L, homeMl.atMs)
        assertEquals("Pinnacle (PinnWire)", homeMl.via)
        // The away side of the same line is the other way round.
        val awayMl = b.quotes(bet(selection = "Pittsburgh Steelers"), rules).quotes.single()
        assertEquals(homeMl.otherOdds to homeMl.odds, awayMl.odds to awayMl.otherOdds)
        // Over and under at the page's number; another number is another bet.
        val under = b.quotes(bet("Total", "Under 38.5"), rules).quotes.single()
        assertEquals(com.tjshea.vigilant.engine.Odds.decimalToAmerican(1.943), under.odds)
        assertTrue(b.quotes(bet("Total", "Under 39.5"), rules).quotes.isEmpty())
        // A spread at Pinnacle's number is found; at another number it is not (no answer is "no price", not a different line).
        assertEquals(1, b.quotes(bet("Spread", "Cleveland Browns +2.5"), rules).quotes.size)
        val other = b.quotes(bet("Spread", "Cleveland Browns +3.5"), rules)
        assertTrue(other.quotes.isEmpty())
        assertNull(other.unavailable)
    }

    @Test
    fun `a player prop is matched by the player and the stat at the exact line`() = runBlocking {
        val pick = BetGrader.pickOf("Player Receiving Yards", "Justin Jefferson Under 69.5") as BetGrader.Pick.Prop
        val feed = Feed("pinnacle", "PinnWire", {
            snapshot(game(prop("Justin Jefferson", pick.stat, 69.5, 1.8, 2.0), prop("Justin Jefferson", pick.stat, 70.5, 1.7, 2.1)))
        })
        val b = sharp(feed)
        val q = b.quotes(SharpBooks.Bet("NFL", "Pittsburgh Steelers @ Cleveland Browns", start, "Player Receiving Yards", "Justin Jefferson Under 69.5"), rules).quotes.single()
        assertEquals(com.tjshea.vigilant.engine.Odds.decimalToAmerican(2.0), q.odds)
        assertEquals(com.tjshea.vigilant.engine.Odds.decimalToAmerican(1.8), q.otherOdds)
        // Another stat or another player is not this bet.
        assertTrue(b.quotes(SharpBooks.Bet("NFL", "Pittsburgh Steelers @ Cleveland Browns", start, "Player Receptions", "Justin Jefferson Under 5.5"), rules).quotes.isEmpty())
        assertTrue(b.quotes(SharpBooks.Bet("NFL", "Pittsburgh Steelers @ Cleveland Browns", start, "Player Receiving Yards", "Brock Bowers Under 69.5"), rules).quotes.isEmpty())
    }

    @Test
    fun `a quote with no time of its own is dated by when the board was read`() = runBlocking {
        val feed = Feed("pinnacle", "PinnWire", { snapshot(game(ml(at = null))) })
        assertEquals(now - 5_000L, sharp(feed).quotes(bet(), rules).quotes.single().atMs)
    }

    @Test
    fun `feeds are asked cheapest first and the first that has the bet answers, the rest cost nothing`() = runBlocking {
        val pinnwire = Feed("pinnacle", "PinnWire", { snapshot(game(ml())) })
        val propline = Feed("propline", "PropLine", { snapshot(game(ml())) })
        val parlay = Feed("parlay", "ParlayAPI", { snapshot(game(ml())) })
        // Given in the wrong order: the order asked is by cost.
        val b = sharp(parlay, propline, pinnwire)
        val q = b.quotes(bet(), rules).quotes.single()
        assertEquals("PinnWire", q.via)
        assertEquals(listOf(1, 0, 0), listOf(pinnwire.calls, propline.calls, parlay.calls))
        assertEquals(mapOf("PinnWire" to 1), b.answeredBy)
        // The first has no such bet (Pinnacle's board lacks the line): the next is asked.
        val thin = Feed("pinnacle", "PinnWire", { snapshot(game(total())) })
        val b2 = sharp(parlay, propline, thin)
        assertEquals("PropLine", b2.quotes(bet(), rules).quotes.single().via)
        assertEquals(1, thin.calls)
    }

    @Test
    fun `a league's board is kept a minute per feed, so the next bet there is free, and asked again after`() = runBlocking {
        val feed = Feed("pinnacle", "PinnWire", { snapshot(game(ml(), total())) })
        val b = sharp(feed)
        b.quotes(bet(), rules)
        b.quotes(bet("Total", "Over 38.5"), rules)
        b.quotes(bet(selection = "Pittsburgh Steelers"), rules)
        assertEquals(1, feed.calls)
        assertEquals(1, b.calls)
        now += 59_000L
        b.quotes(bet(), rules)
        assertEquals(1, feed.calls)
        now += 2_000L
        b.quotes(bet(), rules)
        assertEquals(2, feed.calls)
        assertEquals(2, b.calls)
    }

    @Test
    fun `a feed that can't answer is skipped and its reason kept, a held-back one says so, and none answering says why`() = runBlocking {
        val down = Feed("pinnacle", "PinnWire", fail = java.io.IOException("connection reset"))
        val held = Feed("parlay", "ParlayAPI", fail = CreditsHeldBackException("today's share is spent"))
        val good = Feed("propline", "PropLine", { snapshot(game(ml())) })
        // One down, one good: the good one answers.
        val b = sharp(down, good)
        assertEquals("PropLine", b.quotes(bet(), rules).quotes.single().via)
        assertEquals(1, b.failures)
        // All down: unavailable, with the first one's reason.
        val b2 = sharp(down, held)
        val a2 = b2.quotes(bet(), rules)
        assertTrue(a2.quotes.isEmpty())
        assertEquals("PinnWire didn't answer (connection reset)", a2.unavailable)
        val a3 = sharp(held).quotes(bet(), rules)
        assertEquals("ParlayAPI's credits are held back for today", a3.unavailable)
        // One feed down and another that answered without this bet: "no price", not "couldn't ask".
        val thin = Feed("propline", "PropLine", { snapshot(game(total())) })
        val a4 = sharp(down, thin).quotes(bet(), rules)
        assertTrue(a4.quotes.isEmpty())
        assertNull(a4.unavailable)
        // A failure is kept too: the same feed isn't hammered again inside the minute.
        val callsBefore = down.calls
        b2.quotes(bet(), rules)
        assertEquals(callsBefore, down.calls)
    }

    @Test
    fun `no feed on, or only exchanges, says so, and a bet it can't read or a league it doesn't know asks nothing`() = runBlocking {
        val none = sharp().quotes(bet(), rules)
        assertEquals("no Pinnacle feed is on with a key (Settings › Fair-odds sources)", none.unavailable)
        val kalshi = Feed("kalshi", "Kalshi", { snapshot(game(ml())) })
        val poly = Feed("polymarket", "Polymarket", { snapshot(game(ml())) })
        assertEquals("no Pinnacle feed is on with a key (Settings › Fair-odds sources)", sharp(kalshi, poly).quotes(bet(), rules).unavailable)
        assertEquals(0, kalshi.calls + poly.calls)
        // A 3-way market, another period's, or a league the app doesn't have: no price, no call.
        val feed = Feed("pinnacle", "PinnWire", { snapshot(game(ml())) })
        val b = sharp(feed)
        assertTrue(b.quotes(bet("Moneyline 3-way", "Cleveland Browns"), rules).quotes.isEmpty())
        assertTrue(b.quotes(SharpBooks.Bet("Nowhere League", "A @ B", start, "Moneyline", "A"), rules).quotes.isEmpty())
        assertEquals(0, feed.calls)
    }

    @Test
    fun `a prop asks the feeds that carry props, a game line the feeds of game lines`() = runBlocking {
        val pick = BetGrader.pickOf("Player Receiving Yards", "Justin Jefferson Under 69.5") as BetGrader.Pick.Prop
        val gameLines = Feed("parlay", "ParlayAPI", { snapshot(game(ml())) })
        val props = Feed("parlay_props", "ParlayAPI props", { snapshot(game(prop("Justin Jefferson", pick.stat, 69.5, 1.8, 2.0))) }, propsOnly = true)
        val b = sharp(gameLines, props)
        assertEquals("ParlayAPI props", b.quotes(SharpBooks.Bet("NFL", "Pittsburgh Steelers @ Cleveland Browns", start, "Player Receiving Yards", "Justin Jefferson Under 69.5"), rules).quotes.single().via)
        assertEquals(0, gameLines.calls)
        assertEquals("ParlayAPI", b.quotes(bet(), rules).quotes.single().via)
        assertEquals(1, props.calls)
    }

    @Test
    fun `the scan's own family choices don't limit it, and the feeds named for Settings come in the order they're asked`() = runBlocking {
        var seen: ScanSettings? = null
        val feed = object : ReferenceSource {
            override val id = "pinnacle"
            override val displayName = "PinnWire"
            override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot { seen = settings; return snapshot(game(ml())) }
        }
        SharpBooks(sources = { listOf(feed) }, settings = { ScanSettings(families = setOf(MarketFamily.MONEYLINE)) }, clock = { now }).quotes(bet(), rules)
        assertEquals(MarketFamily.entries.toSet(), seen!!.families)
        val propline = Feed("propline", "PropLine"); val parlay = Feed("parlay", "ParlayAPI"); val odds = Feed("oddsapi", "The Odds API")
        val kalshi = Feed("kalshi", "Kalshi"); val pw = Feed("pinnacle", "PinnWire")
        assertEquals(listOf("PinnWire", "PropLine", "ParlayAPI", "The Odds API"), SharpBooks.feedsAmong(listOf(odds, kalshi, parlay, propline, pw)))
        assertFalse(SharpBooks.feedsAmong(listOf(kalshi)).isNotEmpty())
    }
}
