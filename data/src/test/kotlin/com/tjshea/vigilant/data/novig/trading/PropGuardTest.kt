package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.AtBet
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-07: "right now most of the auto bet feature is betting nhl player shots on goal ... First see if it is wise to have a lot of player shots on goal nhl bets and if not,
 * set up some type of guard for obscure auto betting on small props like this" (RESEARCH.md §109): the evidence said unproven, with concentration the one clear fault, so the guard
 * caps how much one kind of player prop may take of the last 24 hours' auto-bets and how many it may have on one game. Nothing is banned, no bar is raised.
 */
class PropGuardTest {

    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L
    private val rules = PropGuard.Rules(share = 0.25, minSample = 8, perGame = 3)
    private val sog = "NHL|SHOTS_ON_GOAL"
    private val yards = "NFL|RECEIVING_YARDS"

    private fun placed(key: String?, game: String = "g", agoH: Double = 1.0) = PropGuard.Placed(key, game, now - (agoH * hour).toLong())

    /** [n] bets of [key], each on its own game (the share rule alone), and [others] bets each of a kind of its own (so no other kind holds a share). */
    private fun history(key: String, n: Int, others: Int) =
        List(n) { placed(key, "g$it") } + List(others) { placed("NFL|STAT$it", "o$it") }

    // ---- the kind of prop a bet is ------------------------------------------------------------------------------------------------

    @Test
    fun `a player prop's kind is its league and stat, however the market is worded`() {
        assertEquals(sog, PropGuard.key("NHL", "Player Shots On Goal", "Leon Draisaitl Over 3.5"))
        assertEquals(sog, PropGuard.key("nhl ", "Player Shots On Goal", "Vasily Podkolzin Over 2.5"))
        assertEquals(yards, PropGuard.key("NFL", "Player Receiving Yards", "Juwan Johnson Under 39.5"))
        assertEquals("SHOTS_ON_GOAL", PropGuard.statOf("Connor McDavid 3.5 SHOTS_ON_GOAL"))
        assertEquals("SHOTS_ON_GOAL", PropGuard.statOf("Shots On Goal"))
        assertEquals("POINTS", PropGuard.statOf("Connor McDavid 1.5 Points"))
        assertEquals("RECEIVING_YARDS", PropGuard.statOf("Player Receiving Yards"))
        assertEquals("NHL shots on goal", PropGuard.labelOf(sog))
    }

    @Test
    fun `a spread, a total, a moneyline and a team total are not player props and are never held back`() {
        assertNull(PropGuard.key("NFL", "Spread", "Kansas City Chiefs -3.5"))
        assertNull(PropGuard.key("NFL", "Total Points", "Over 47.5"))
        assertNull(PropGuard.key("NHL", "Moneyline", "Edmonton Oilers"))
        assertNull(PropGuard.key("NHL", "Team Total Goals", "Edmonton Oilers Over 3.5"))
        assertNull(PropGuard.judge(rules, history(sog, 20, 0), null, "g", now))
    }

    // ---- the share cap ---------------------------------------------------------------------------------------------------------

    @Test
    fun `once the window holds 8 auto-bets a kind that would pass 25 percent of them waits, and under 8 nothing is held`() {
        // 8 bets, 2 of them SOG: one more would be 3 of 9 = 33%.
        val h = history(sog, 2, 6)
        val why = PropGuard.judge(rules, h, sog, "new", now)
        assertNotNull(why)
        assertTrue(why!!, why.startsWith("NHL shots on goal already holds its share of the last 24 hours' auto-bets") && why.contains("25%"))
        // Another kind with 1 of 8 would be 2 of 9 = 22%: goes.
        assertNull(PropGuard.judge(rules, h, yards, "new", now))
        // Under the sample (7 bets, 5 of them SOG) nothing is held: there is no share to speak of yet.
        assertNull(PropGuard.judge(rules, history(sog, 5, 2), sog, "new", now))
        // 25% exactly, then one more would be 3 of 9 = 33%: waits; 1 of 8 plus one = 2 of 9 = 22%: goes.
        assertNotNull(PropGuard.judge(rules, history(sog, 2, 6), sog, "new", now))
        assertNull(PropGuard.judge(rules, history(sog, 1, 7), sog, "new", now))
    }

    @Test
    fun `the cap bites on a day one market fills, and costs little on a spread-out one`() {
        // Oct 6's day: half the candidates were SOG (16 of 34 placed), the rest spread over five other kinds. The guard holds SOG to about a quarter of what is placed.
        val others = listOf(yards, "NHL|ASSISTS", "NHL|GOALS", "NHL|POINTS", "NHL|SAVES")
        val hist = ArrayList<PropGuard.Placed>()
        var sogPlaced = 0
        var sogHeld = 0
        repeat(60) { i ->
            val key = if (i % 2 == 0) sog else others[(i / 2) % others.size]
            val game = "g${i / 3}"
            if (PropGuard.judge(rules, hist, key, game, now) == null) { hist += placed(key, game); if (key == sog) sogPlaced++ } else if (key == sog) sogHeld++
        }
        assertTrue("SOG held to about a quarter of the day's auto-bets: $sogPlaced of ${hist.size}", sogPlaced.toDouble() / hist.size <= 0.30)
        assertTrue("some SOG bets were held: $sogHeld", sogHeld > 5)
        assertTrue("every other kind went through: ${hist.size - sogPlaced} of 30", hist.size - sogPlaced == 30)
        // Spread across six kinds the cap never touches anything.
        val keys = listOf(sog, yards, "NFL|RECEPTIONS", "NBA|POINTS", "MLB|PITCHER_STRIKEOUTS", "NHL|ASSISTS")
        val spread = ArrayList<PropGuard.Placed>()
        var held = 0
        repeat(36) { i -> val k = keys[i % keys.size]; if (PropGuard.judge(rules, spread, k, "g$i", now) == null) spread += placed(k, "g$i") else held++ }
        assertEquals(0, held)
    }

    /** A cap of 25% cannot be met with three kinds in play: held to it, the auto-bet would stop. It is never stricter than an even split plus one bet. */
    @Test
    fun `with few kinds in play the cap is an even split, never a stop`() {
        // Three kinds, 3 each (n = 9): a fourth of any would be 40%, over 25%, but within one bet of the even third: goes.
        val even = listOf(sog, yards, "NHL|ASSISTS").flatMap { k -> List(3) { placed(k, "g${k}$it") } }
        listOf(sog, yards, "NHL|ASSISTS").forEach { assertNull(it, PropGuard.judge(rules, even, it, "new", now)) }
        // One kind far ahead is held: 6 SOG against 2 and 1 (n = 9).
        val lopsided = List(6) { placed(sog, "s$it") } + List(2) { placed(yards, "y$it") } + placed("NHL|ASSISTS", "a")
        assertNotNull(PropGuard.judge(rules, lopsided, sog, "new", now))
        assertNull(PropGuard.judge(rules, lopsided, yards, "new", now))
        // Two kinds only: an even split is half each.
        val two = List(5) { placed(sog, "s$it") } + List(5) { placed(yards, "y$it") }
        assertNull(PropGuard.judge(rules, two, sog, "new", now))
        assertNotNull(PropGuard.judge(rules, List(8) { placed(sog, "s$it") } + List(2) { placed(yards, "y$it") }, sog, "new", now))
        // A game-line bet is one more "kind": 6 props of two kinds and 4 spreads are three groups.
        val withLines = List(3) { placed(sog, "s$it") } + List(3) { placed(yards, "y$it") } + List(4) { placed(null, "l$it") }
        assertNull(PropGuard.judge(rules, withLines, sog, "new", now))
    }

    @Test
    fun `only the last 24 hours count`() {
        val old = List(10) { placed(sog, "g$it", agoH = 30.0) }
        assertNull("30 hours ago is not in the window: no sample, no share", PropGuard.judge(rules, old, sog, "new", now))
        val mixed = List(6) { placed(yards, "o$it") } + List(2) { placed(sog, "s$it", agoH = 23.0) }
        assertNotNull("23 hours ago is", PropGuard.judge(rules, mixed, sog, "new", now))
    }

    @Test
    fun `a share of 0 turns the cap off, and the typed sample is Tj's`() {
        assertNull(PropGuard.judge(rules.copy(share = 0.0), history(sog, 20, 0), sog, "new", now))
        // A bigger sample: 12 needed, 8 bets are not enough.
        assertNull(PropGuard.judge(rules.copy(minSample = 12), history(sog, 4, 4), sog, "new", now))
        assertNotNull(PropGuard.judge(rules.copy(minSample = 12), history(sog, 4, 8), sog, "new", now))
        // 50%: 4 of 8 plus one = 5 of 9 = 56% waits; 3 of 8 plus one = 4 of 9 = 44% goes.
        assertNotNull(PropGuard.judge(rules.copy(share = 0.5), history(sog, 4, 4), sog, "new", now))
        assertNull(PropGuard.judge(rules.copy(share = 0.5), history(sog, 3, 5), sog, "new", now))
    }

    // ---- the per-game limit ----------------------------------------------------------------------------------------------------

    @Test
    fun `at most 3 bets on one kind of prop in one game, other kinds and other games are not counted`() {
        val three = List(3) { placed(sog, "game1") }
        assertNotNull(PropGuard.judge(rules.copy(share = 0.0), three, sog, "game1", now))
        assertTrue(PropGuard.judge(rules.copy(share = 0.0), three, sog, "game1", now)!!.contains("limit of 3 bets on one game"))
        assertNull("another game", PropGuard.judge(rules.copy(share = 0.0), three, sog, "game2", now))
        assertNull("another kind of prop on the same game", PropGuard.judge(rules.copy(share = 0.0), three, "NHL|ASSISTS", "game1", now))
        assertNull("two on the game: a third is fine", PropGuard.judge(rules.copy(share = 0.0), three.take(2), sog, "game1", now))
        assertNull("0 turns it off", PropGuard.judge(rules.copy(share = 0.0, perGame = 0), three, sog, "game1", now))
        assertNotNull("2 is Tj's choice too", PropGuard.judge(rules.copy(share = 0.0, perGame = 2), three.take(2), sog, "game1", now))
    }

    @Test
    fun `both off is no guard at all`() {
        val off = PropGuard.rules(ScanSettings(propGuardShare = 0.0, propGuardPerGame = 0))
        assertFalse(off.on)
        assertNull(PropGuard.judge(off, history(sog, 30, 0), sog, "g0", now))
        assertEquals("off: no cap on how much one kind of player prop may take", PropGuard.summary(off))
    }

    // ---- the history ------------------------------------------------------------------------------------------------------------

    private fun bet(
        id: String, market: String = "Player Shots On Goal", selection: String = "Leon Draisaitl Over 3.5", league: String = "NHL", auto: Boolean = true,
        how: String? = AtBet.HOW_AUTO, agoH: Double = 1.0, lockFor: String? = null, eventId: String = "ev1",
    ) = TrackedBet(
        id = id, createdAtMs = now - (agoH * hour).toLong(), league = league, eventName = "Edmonton Oilers @ Anaheim Ducks", startsTs = now + 5 * hour, marketLabel = market,
        selection = selection, marketId = "m-$id", outcomeId = "o-$id", price = 0.4, cost = 0.4, fairAtBet = 0.43, evPercentAtBet = 0.04, stake = 2.0, auto = auto,
        atBet = how?.let { h -> atBet(h) }, lockFor = lockFor, eventId = eventId,
    )

    private fun atBet(how: String): AtBet = AtBet(
        atMs = now, version = "t", how = how, scanner = "cno", preset = null, rules = "",
    )

    @Test
    fun `the history is the Tracker's taker auto-bets of the window - not a filled bid, a lock, a hand bet or an old one`() {
        val bets = listOf(
            bet("a"), bet("b", market = "Player Receiving Yards", selection = "Juwan Johnson Under 39.5", league = "NFL"),
            bet("c", how = AtBet.HOW_BID), bet("d", auto = false, how = AtBet.HOW_SHEET), bet("e", lockFor = "a"), bet("f", agoH = 30.0),
            bet("g", market = "Spread", selection = "Edmonton Oilers -1.5"),
        )
        val h = PropGuard.history(bets, now)
        assertEquals(3, h.size)
        assertEquals(listOf(sog, yards, null), h.map { it.key })
        assertEquals("the game is Novig's event id", "ev1", h.first().game)
        assertEquals("NFL receiving yards 1 (33%), NHL shots on goal 1 (33%)", PropGuard.sharesLine(h, 3).substringAfter(": "))
        assertEquals("no auto-bets", PropGuard.sharesLine(emptyList()))
    }

    @Test
    fun `the settings give the guard's rules, clamped, with 25 percent, 8 bets and 3 a game by default`() {
        val d = PropGuard.rules(ScanSettings())
        assertEquals(PropGuard.Rules(0.25, 8, 3), d)
        assertEquals(0.95, PropGuard.rules(ScanSettings(propGuardShare = 3.0)).share, 1e-9)
        assertEquals(0.0, PropGuard.rules(ScanSettings(propGuardShare = -1.0)).share, 1e-9)
        assertEquals(1, PropGuard.rules(ScanSettings(propGuardMinSample = 0)).minSample)
        assertEquals(0, PropGuard.rules(ScanSettings(propGuardPerGame = -2)).perGame)
        assertEquals("no kind of player prop over 25% of the last 24 h's auto-bets (once there are 8; never under an even split of the kinds being bet) · at most 3 auto-bets on one kind of prop in one game", PropGuard.summary(d))
    }
}
