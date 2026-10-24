package com.tjshea.vigilant.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SX (Tj, 2026-10-24: "1 star means the bet is still positive EV but low confidence/support from history 5 Star means the bet is a type that is proven profitable from data and is not
 * held back by safeguards against bad bets"): the stars a type's record at Novig earns, read from star_evidence.json (research/star_rating_sx_2026-10-24.md).
 */
class StarEvidenceTest {
    private fun table(vararg cells: Pair<String, String>) = StarEvidence.parse(
        """{"asOf":"2026-10-24","cells":{${cells.joinToString(",") { (k, v) -> "\"$k\":$v" }}}}""",
    )!!

    private val now = 1_000_000_000_000L
    private fun type(league: String = "NBA", kind: BetKind = BetKind.PROP, selection: String = "Joe Under 20.5", cost: Double = 0.45) =
        BetType.of(league, kind, selection, cost, now + 3 * 3_600_000L, now)

    private fun inputs(t: BetType?, ev: Double = 0.03, books: Int = 6) = BetRating.Inputs(books, books, ev, sharpAgrees = true, sharpAgainst = false, spread = 0.01, type = t)
    private fun sx(i: BetRating.Inputs, tab: StarEvidence.Table) = BetRating.rate(i, scale = BetRating.Scale.SX, evidence = tab)

    @Test
    fun `a bet's type has its sport, kind, side and price band`() {
        val t = type()
        assertEquals(BetType("NBA", "prop", "under", 180, 0.45), t)
        assertEquals("over", type(selection = "Over 220.5", kind = BetKind.TOTAL).side)
        assertEquals("fav", type(selection = "Lakers -3.5", kind = BetKind.SPREAD, cost = 0.55).side)
        assertEquals("dog", type(selection = "Lakers +3.5", kind = BetKind.SPREAD, cost = 0.45).side)
        assertEquals("TENNIS", BetType.sportOf("ATP Paris")); assertEquals("*", BetType.sportOf("MLS (USA)")); assertEquals("MLB", BetType.sportOf("mlb"))
        assertEquals("+150 longer", StarEvidence.priceBand(0.39)); assertEquals("+100..+150", StarEvidence.priceBand(0.45))
        assertEquals("-150..-100", StarEvidence.priceBand(0.5)); assertEquals("-150 shorter", StarEvidence.priceBand(0.6)); assertNull(StarEvidence.priceBand(null))
    }

    @Test
    fun `the most specific cell with 100 games answers`() {
        val tab = table(
            "NBA|prop|under|+100..+150" to """{"games":99,"clv":0.05,"clvSe":0.001}""",
            "*|prop|under|+100..+150" to """{"games":120,"clv":0.01,"clvSe":0.005}""",
            "*|*|*" to """{"games":500,"clv":-0.004,"clvSe":0.002}""",
        )
        assertEquals("*|prop|under|+100..+150", tab.lookup(type())!!.key)
        assertEquals("*|*|*", tab.lookup(type(kind = BetKind.SPREAD, selection = "Bulls +2.5"))!!.key)
        assertNull(table("*|*|*" to """{"games":50,"clv":0.0,"clvSe":0.01}""").lookup(type()))
    }

    @Test
    fun `stars climb a half star at each step of proof, and the top needs a CLV worth having`() {
        fun stars(z: Double, clv: Double) = BetRating.proofStars(StarEvidence.Cell("k", 200, clv, clv / z, null, null))
        assertEquals(1.0, stars(0.4, 0.004), 0.0)
        assertEquals(1.5, stars(0.5, 0.004), 0.0)
        assertEquals(2.0, stars(1.0, 0.004), 0.0)
        assertEquals(3.0, stars(1.64, 0.004), 0.0)
        assertEquals("4 stars need +0.5% CLV", 3.5, stars(2.0, 0.004), 0.0)
        assertEquals(4.0, stars(2.0, 0.006), 0.0)
        assertEquals("5 stars need +1% CLV", 4.5, stars(3.0, 0.009), 0.0)
        assertEquals(5.0, stars(2.58, 0.012), 0.0)
        assertEquals(1.0, BetRating.proofStars(StarEvidence.Cell("k", 200, -0.01, 0.004, null, null)), 0.0)
    }

    @Test
    fun `a type proven to lose on both sources has no trusted edge, on one source it is only held back`() {
        val losing = """{"games":200,"clv":-0.015,"clvSe":0.004,"btGames":900,"btClv":-0.012,"btZ":-8.8}"""
        val r = sx(inputs(type()), table("*|*|*" to losing))
        assertEquals(BetRating.NONE, r.stars, 0.0)
        assertTrue(r.doubt!!.contains("lost to Novig's close"))
        // The real lines lose but the backtest says the type is fine: unproven, 1 star (two sources must agree to call it losing).
        assertEquals(1.0, sx(inputs(type()), table("*|*|*" to """{"games":200,"clv":-0.015,"clvSe":0.004,"btGames":900,"btClv":0.004,"btZ":2.0}""")).stars, 0.0)
        // A strong real record the backtest contradicts is held to 2 stars.
        assertEquals(2.0, sx(inputs(type()), table("*|*|*" to """{"games":300,"clv":0.02,"clvSe":0.005,"btGames":900,"btClv":-0.01,"btZ":-5.0}""")).stars, 0.0)
        // Losing within half a point (the upper bound above -0.5%) is not losing.
        assertEquals(1.0, sx(inputs(type()), table("*|*|*" to """{"games":300,"clv":-0.004,"clvSe":0.002,"btZ":-9.0}""")).stars, 0.0)
    }

    @Test
    fun `a proven type earns its stars unless a safeguard holds it back`() {
        val proven = table("*|prop|under|+100..+150" to """{"games":400,"clv":0.015,"clvSe":0.005,"roi":0.03,"roiSe":0.02,"btGames":900,"btClv":0.007,"btZ":4.5}""")
        val r = sx(inputs(type()), proven)
        assertEquals(5.0, r.stars, 0.0)
        assertEquals("*|prop|under|+100..+150", r.evidence!!.key)
        assertEquals("one company: at most 2", 2.0, sx(inputs(type(), books = 1), proven).stars, 0.0)
        assertEquals("three companies: at most 4", 4.0, sx(inputs(type(), books = 3), proven).stars, 0.0)
        assertEquals("an edge over 12%: at most 4", 4.0, sx(inputs(type(), ev = 0.15), proven).stars, 0.0)
        assertEquals("the floors still apply", BetRating.NONE, sx(inputs(type(), ev = 0.004), proven).stars, 0.0)
        assertEquals("a sharp book against", BetRating.NONE, sx(inputs(type()).copy(sharpAgrees = false, sharpAgainst = true), proven).stars, 0.0)
        assertEquals("no type or no record: +EV, unproven", 1.0, sx(inputs(null), proven).stars, 0.0)
        assertEquals(1.0, sx(inputs(type(kind = BetKind.MONEYLINE, selection = "Bulls", cost = 0.45)), proven).stars, 0.0)
        assertTrue(BetRating.evidenceText(r)!!.startsWith("proven at Novig: 400 games"))
        assertTrue(BetRating.evidenceText(sx(inputs(null), proven))!!.contains("no record"))
        // The support score is still worked out (the engine's stake sizing reads it).
        assertTrue(r.support in 1.0..5.0 && r.points > 0.0)
    }

    @Test
    fun `the shipped table loads and rates every bet by its record`() {
        val tab = StarEvidence.shipped
        assertNotNull("star_evidence.json ships in the app", tab)
        val all = tab!!.cells["*|*|*"]
        assertNotNull(all); assertTrue(all!!.games >= 100)
        assertEquals(BetRating.Scale.SX, BetRating.CURRENT_SCALE)
        val r = BetRating.rate(inputs(type()))
        assertTrue(r.stars == BetRating.NONE || r.stars in 1.0..5.0)
        assertNotNull(r.evidence)
    }
}
