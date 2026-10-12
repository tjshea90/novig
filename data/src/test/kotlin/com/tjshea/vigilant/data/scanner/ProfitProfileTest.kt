package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.novig.trading.maker.MakerRules
import com.tjshea.vigilant.data.scanner.ProfitProfile.Group
import com.tjshea.vigilant.data.scanner.ProfitProfile.Instrument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tj, 2026-10-12: "Profit-proven only": block the types of bet and bid that lost money in his history, favour the winners, treat a small sample as neutral, and off changes nothing. */
class ProfitProfileTest {

    private fun profile(minN: Int = 40, vararg bet: Pair<Pair<String, String>, Group>): ProfitProfile {
        val byDim = HashMap<String, MutableMap<String, Group>>()
        for ((dk, g) in bet) byDim.getOrPut(dk.first) { HashMap() }[dk.second] = g
        return ProfitProfile(mapOf("bet" to byDim, "bid" to emptyMap()), minN, 1.5, 1000, "2026-10-11")
    }

    private val loser = Group(224, -0.158, -2.61, 0.39)
    private val winner = Group(64, 0.33, 1.75, 0.58)

    @Test
    fun `a proven losing type is blocked with the numbers in the words`() {
        val p = profile(40, ("league|kind|side" to "NFL|Player props|over") to loser)
        val j = p.judge(Instrument.BET, "NFL", "Player props", "Josh Allen Over 1.5", 0.03, 600)
        assertNotNull(j.blocked)
        assertTrue(j.blocked!!, j.blocked!!.contains("NFL Player props over bets lose money") && j.blocked!!.contains("224 settled") && j.blocked!!.contains("-15.8%"))
        // the same type's other side is not touched
        assertNull(p.judge(Instrument.BET, "NFL", "Player props", "Josh Allen Under 1.5", 0.03, 600).blocked)
    }

    @Test
    fun `a small sample is neutral, never blocked`() {
        val p = profile(40, ("league|kind|side" to "WNBA|Player props|over") to Group(29, -0.25, -2.46, 0.28))
        val j = p.judge(Instrument.BET, "WNBA", "Player props", "A B Over 5.5", 0.03, 600)
        assertNull(j.blocked)
        assertEquals(0.0, j.score, 0.0)
    }

    @Test
    fun `a loser that is not statistically clear is left alone`() {
        val p = profile(40, ("league|kind|side" to "NHL|Player props|over") to Group(101, -0.15, -0.95, 0.43))
        assertNull(p.judge(Instrument.BET, "NHL", "Player props", "X Over 2.5", 0.03, 600).blocked)
    }

    @Test
    fun `a more specific winner outranks a general loser, and a winner ranks above neutral`() {
        val p = profile(
            40,
            ("kind" to "Player props") to Group(711, -0.106, -2.69, 0.43),
            ("league|kind|side" to "NHL|Player props|under") to Group(60, 0.2, 1.8, 0.6),
        )
        val ok = p.judge(Instrument.BET, "NHL", "Player props", "X Under 2.5", 0.03, 600)
        assertNull("the specific winner overrides the broad loser", ok.blocked)
        assertTrue(ok.score > 0.0)
        assertNotNull(ok.favoured)
        // another league's prop only has the broad loser behind it
        assertNotNull(p.judge(Instrument.BET, "MLB", "Player props", "X Over 2.5", 0.03, 600).blocked)
    }

    @Test
    fun `a marginal dimension needs twice the sample`() {
        val thin = profile(40, ("ev" to "4-6%") to Group(60, -0.14, -2.0, 0.4))
        assertNull(thin.judge(Instrument.BET, "NFL", "Spreads", "A -3.5", 0.05, 600).blocked)
        val enough = profile(40, ("ev" to "4-6%") to Group(218, -0.143, -2.08, 0.4))
        assertNotNull(enough.judge(Instrument.BET, "NFL", "Spreads", "A -3.5", 0.05, 600).blocked)
    }

    @Test
    fun `scores rank winners above neutral above losers, shrunk by sample size`() {
        val p = profile(40, ("kind|side" to "Game totals|over") to winner, ("kind|side" to "Player props|over") to loser)
        val w = p.judge(Instrument.BET, "NCAAF", "Game totals", "Over 50.5", 0.03, 600).score
        val n = p.judge(Instrument.BET, "NCAAF", "Moneylines", "Team A", 0.03, 600).score
        val l = p.judge(Instrument.BET, "NCAAF", "Player props", "A Over 5", 0.03, 600).score
        assertTrue("$w $n $l", w > n && n > l)
        assertTrue("a thin group moves the score little", w < 0.33)
    }

    @Test
    fun `the profile that ships parses and blocks the NFL player-prop Over its history says loses`() {
        val p = ProfitProfile.shipped
        assertNotNull(p)
        assertTrue(p!!.records > 1000)
        val j = p.judge(Instrument.BET, "NFL", "Player props", "Somebody Over 59.5", 0.03, 600)
        assertNotNull(j.blocked)
        assertTrue(p.summary().contains("bets blocked"))
    }

    @Test
    fun `sides are read the way the generator reads them`() {
        assertEquals("over", ProfitProfile.sideOf("Josh Allen Over 1.5"))
        assertEquals("under", ProfitProfile.sideOf("Under 47.5"))
        assertEquals("other", ProfitProfile.sideOf("Kansas City Chiefs -11.5"))
    }

    @Test
    fun `off changes nothing in the bid rules, on carries the profile`() {
        assertNull(MakerRules.of(ScanSettings()).profit)
        val on = ScanSettings().let { it.copy(gates = it.gates.copy(profitProven = true)) }
        assertFalse(ScanSettings().gates.profitProven)
        assertNotNull(MakerRules.of(on).profit)
    }
}
