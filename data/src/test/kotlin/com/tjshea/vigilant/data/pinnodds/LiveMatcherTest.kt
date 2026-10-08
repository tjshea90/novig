package com.tjshea.vigilant.data.pinnodds

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.pinnodds.PinnTestFrames.live
import com.tjshea.vigilant.data.pinnodds.PinnTestFrames.parse
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMatcherTest {
    private val start = 1_791_417_900_000L
    private val now = start + 3_600_000L

    private fun market(id: String, type: String, a: String, b: String, strike: Double?, status: String = "OPEN", eventId: String = "ev1") =
        NovigMarket(id, eventId, type, status, "d", start, MarketFee.GAME, listOf(NovigOutcome("$id-a", a, "TBD"), NovigOutcome("$id-b", b, "TBD")), strike)

    private val nba = NovigEvent("ev1", "BASKETBALL", "NBA", NovigEvent.STATUS_LIVE, "Milwaukee Bucks @ Oklahoma City Thunder", start)

    private fun pinn(id: Long, home: String, away: String, live: Boolean = true, startMs: Long = start, lastFrame: Long = now) =
        PinnEvent(id).also { it.home = home; it.away = away; it.live = live; it.startMs = startMs; it.lastFrameAtMs = lastFrame }

    @Test
    fun `a Pinnacle matchup and a Novig game are the same when both teams fit and the start times agree`() {
        val pairs = LiveMatcher.matchEvents(listOf(pinn(1, "Oklahoma City Thunder", "Milwaukee Bucks")), listOf(nba), now)
        assertEquals(1, pairs.size)
        assertEquals(1L, pairs.single().pinnEventId)
        assertEquals(false, pairs.single().swapped)
    }

    @Test
    fun `one team alone, a different day, or a different game is no match`() {
        assertTrue(LiveMatcher.matchEvents(listOf(pinn(1, "Oklahoma City Thunder", "Phoenix Suns")), listOf(nba), now).isEmpty())
        assertTrue(LiveMatcher.matchEvents(listOf(pinn(1, "Oklahoma City Thunder", "Milwaukee Bucks", startMs = start + 24 * 3_600_000L)), listOf(nba), now).isEmpty())
        assertTrue(LiveMatcher.matchEvents(listOf(pinn(1, "Memphis Grizzlies", "Orlando Magic")), listOf(nba), now).isEmpty())
    }

    @Test
    fun `a live matchup beats a stale re-issued one with the same teams`() {
        val stale = pinn(1, "Oklahoma City Thunder", "Milwaukee Bucks", live = false, lastFrame = now - 3_600_000L)
        val liveOne = pinn(2, "Oklahoma City Thunder", "Milwaukee Bucks")
        assertEquals(2L, LiveMatcher.matchEvents(listOf(stale, liveOne), listOf(nba), now).single().pinnEventId)
    }

    @Test
    fun `a matchup whose home and away are the other way round is matched as swapped`() {
        val tennis = NovigEvent("ev2", "TENNIS", "ATP", NovigEvent.STATUS_LIVE, "Carlos Alcaraz @ Jannik Sinner", start)
        val p = pinn(7, home = "Carlos Alcaraz", away = "Jannik Sinner")
        val pair = LiveMatcher.matchEvents(listOf(p), listOf(tennis), now).single()
        assertTrue(pair.swapped)
        val ml = market("m", "MONEY", "Carlos Alcaraz", "Jannik Sinner", 0.0, eventId = "ev2")
        val t = LiveMatcher.targets(pair, listOf(ml)).single()
        // Novig's home (Sinner) is Pinnacle's away, so Pinnacle's HOME (Alcaraz) is the outcome named Alcaraz.
        assertEquals("m-a", t.outcomeBySide.getValue(PinnSide.HOME))
        assertEquals("m-b", t.outcomeBySide.getValue(PinnSide.AWAY))
    }

    @Test
    fun `a doubleheader - the live game goes with the live matchup, whichever order Novig lists them in`() {
        val g1 = NovigEvent("g1", "BASEBALL", "MLB", NovigEvent.STATUS_LIVE, "Milwaukee Bucks @ Oklahoma City Thunder", start)
        val g2 = NovigEvent("g2", "BASEBALL", "MLB", NovigEvent.STATUS_PREGAME, "Milwaukee Bucks @ Oklahoma City Thunder", start + 3 * 3_600_000L)
        val p = pinn(1, "Oklahoma City Thunder", "Milwaukee Bucks")
        val sameStage = { pe: PinnEvent, ne: NovigEvent -> if (pe.live) ne.status != NovigEvent.STATUS_PREGAME else ne.status == NovigEvent.STATUS_PREGAME }
        for (order in listOf(listOf(g1, g2), listOf(g2, g1))) {
            val pairs = LiveMatcher.matchEvents(listOf(p), order, now, sameStage)
            assertEquals(listOf("g1"), pairs.map { it.event.eventId })
        }
    }

    private val pair = LiveMatcher.Pair(1L, nba, false)

    @Test
    fun `a moneyline maps its abbreviations to home and away`() {
        val t = LiveMatcher.targets(pair, listOf(market("ml", "MONEY", "OKC", "MIL", 0.0))).single()
        assertEquals("ml-a", t.outcomeBySide.getValue(PinnSide.HOME))
        assertEquals("ml-b", t.outcomeBySide.getValue(PinnSide.AWAY))
        val flipped = LiveMatcher.targets(pair, listOf(market("ml2", "MONEY", "MIL", "OKC", 0.0))).single()
        assertEquals("ml2-b", flipped.outcomeBySide.getValue(PinnSide.HOME))
    }

    @Test
    fun `a spread's home outcome is the one whose handicap is the strike, and a whole number is left alone`() {
        val t = LiveMatcher.targets(pair, listOf(market("sp", "SPREAD", "MIL +31.5", "OKC -31.5", -31.5))).single()
        assertEquals("sp-b", t.outcomeBySide.getValue(PinnSide.HOME))
        assertEquals(-31.5, t.strike!!, 0.0)
        assertTrue(LiveMatcher.targets(pair, listOf(market("sp0", "SPREAD", "OKC -3", "MIL +3", -3.0))).isEmpty())
        assertTrue("names that disagree with the strike are not guessed", LiveMatcher.targets(pair, listOf(market("spx", "SPREAD", "OKC -3.5", "MIL +4.5", -3.5))).isEmpty())
    }

    @Test
    fun `a total needs Over and Under and a half-point line`() {
        val t = LiveMatcher.targets(pair, listOf(market("tt", "TOTAL", "Over 257.5", "Under 257.5", 257.5))).single()
        assertEquals("tt-a", t.outcomeBySide.getValue(PinnSide.OVER))
        assertTrue(LiveMatcher.targets(pair, listOf(market("t0", "TOTAL", "Over 257", "Under 257", 257.0))).isEmpty())
        assertTrue(LiveMatcher.targets(pair, listOf(market("t1", "TOTAL", "Yes", "No", 257.5))).isEmpty())
    }

    @Test
    fun `a closed market, another game's market and a prop are not targets`() {
        val ms = listOf(
            market("c", "MONEY", "OKC", "MIL", 0.0, status = "CLOSED"),
            market("o", "MONEY", "OKC", "MIL", 0.0, eventId = "other"),
            market("p", "POINTS", "Over 20.5", "Under 20.5", 20.5),
        )
        assertTrue(LiveMatcher.targets(pair, ms).isEmpty())
    }

    @Test
    fun `the Pinnacle line for a target is the main line at the strike, with the sign flipped when swapped`() {
        val b = PinnBook(DevigMethod.MULTIPLICATIVE)
        b.apply(parse(live(markets = arrayOf(PinnTestFrames.spread(1, -3.5, -110, -110), PinnTestFrames.spread(1, -4.5, 120, -140, alt = true), PinnTestFrames.total(1, 220.5, -110, -110)))), 0)
        val e = b.events.getValue(1)
        val spread = LiveMatcher.targets(pair, listOf(market("sp", "SPREAD", "OKC -3.5", "MIL +3.5", -3.5))).single()
        assertEquals("s;0;s;-3.5", spread.line(e)!!.key)
        val altOnly = LiveMatcher.targets(pair, listOf(market("sp2", "SPREAD", "OKC -4.5", "MIL +4.5", -4.5))).single()
        assertNull("an alternate Pinnacle line is not used", altOnly.line(e))
        val total = LiveMatcher.targets(pair, listOf(market("tt", "TOTAL", "Over 220.5", "Under 220.5", 220.5))).single()
        assertNotNull(total.line(e))
        val other = LiveMatcher.targets(pair, listOf(market("tt2", "TOTAL", "Over 219.5", "Under 219.5", 219.5))).single()
        assertNull(other.line(e))
        val swapped = spread.copy(swapped = true, strike = 3.5)
        assertEquals("s;0;s;-3.5", swapped.line(e)!!.key)
    }

    // Tennis (2026-10-08 tape): Pinnacle books a match as a "Sets" child (the winner) and a "Games" child (games spread and total, names suffixed " (Games)") under one parent.
    private fun tennisChild(id: Long, units: String, group: Long = 500, live: Boolean = true) =
        PinnEvent(id).also {
            it.home = "Carolina Alves"; it.away = "Luisina Giovannini"; it.live = live; it.startMs = start; it.lastFrameAtMs = now
            it.sportId = PinnBook.TENNIS_SPORT_ID; it.units = units; it.regular = true; it.parentId = group
        }

    private val atp = NovigEvent("ev3", "TENNIS", "WTA", NovigEvent.STATUS_LIVE, "Luisina Giovannini @ Carolina Alves", start)

    @Test
    fun `a tennis match takes its winner from the Sets child and its spread and total from the Games child`() {
        val sets = tennisChild(1, "Sets")
        val games = tennisChild(2, "Games")
        val pair = LiveMatcher.matchEvents(listOf(games, sets), listOf(atp), now).single()
        assertEquals(1L, pair.pinnEventId)
        assertEquals(2L, pair.linesEventId)
        val ts = LiveMatcher.targets(
            pair,
            listOf(
                market("ml", "MONEY", "Carolina Alves", "Luisina Giovannini", 0.0, eventId = "ev3"),
                market("tot", "TOTAL", "Over 21.5", "Under 21.5", 21.5, eventId = "ev3"),
            ),
        ).associateBy { it.type }
        assertEquals(1L, ts.getValue(PinnLineType.MONEYLINE).pinnEventId)
        assertEquals(2L, ts.getValue(PinnLineType.TOTAL).pinnEventId)
    }

    @Test
    fun `a tennis sets spread or total is never priced from games lines`() {
        val pair = LiveMatcher.matchEvents(listOf(tennisChild(1, "Sets"), tennisChild(2, "Games")), listOf(atp), now).single()
        val setMarkets = listOf(
            market("tot", "TOTAL", "Over 2.5", "Under 2.5", 2.5, eventId = "ev3"),
            market("sp", "SPREAD", "Carolina Alves -1.5", "Luisina Giovannini +1.5", -1.5, eventId = "ev3"),
        )
        assertTrue(LiveMatcher.targets(pair, setMarkets).isEmpty())
    }

    @Test
    fun `a tennis match with no Games child gets a winner target and no spread or total`() {
        val pair = LiveMatcher.matchEvents(listOf(tennisChild(1, "Sets")), listOf(atp), now).single()
        val ts = LiveMatcher.targets(
            pair,
            listOf(market("ml", "MONEY", "Carolina Alves", "Luisina Giovannini", 0.0, eventId = "ev3"), market("tot", "TOTAL", "Over 21.5", "Under 21.5", 21.5, eventId = "ev3")),
        )
        assertEquals(listOf(PinnLineType.MONEYLINE), ts.map { it.type })
    }

    @Test
    fun `the book keeps tennis Sets and Games children and strips the Games suffix from the names`() {
        val book = PinnBook()
        val frame = live(id = 7, home = "Carolina Alves (Games)", away = "Luisina Giovannini (Games)", units = "Games", markets = arrayOf(PinnTestFrames.total(1, 21.5, -110, -110)))
            .replace("\"sport_id\":3", "\"sport_id\":2")
        book.apply(parse(frame), now)
        val e = book.events.getValue(7L)
        assertTrue(e.regular)
        assertEquals("Carolina Alves", e.home)
        assertEquals("Games", e.units)
        // Other sports' non-Regular books stay special.
        book.apply(parse(live(id = 8, units = "Corners", markets = arrayOf(PinnTestFrames.total(1, 9.5, -110, -110)))), now)
        assertTrue(!book.events.getValue(8L).regular)
    }
}
