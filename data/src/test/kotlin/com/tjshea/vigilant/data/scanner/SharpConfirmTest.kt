package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.cno.CnoBookPrice
import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.scanner.SharpConfirm.Quote
import com.tjshea.vigilant.data.scanner.SharpConfirm.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-02: "require bets to be proven positive EV by a current, devigged sharp book such as Pinnacle … fresh (within the last few minutes) odds from the
 * sharp book(s) devigged and compared to the current novig odds for the same exact bet." The rule, with no network: a sharp quote's own devigged price against
 * Novig's, its age, its edge, and the veto.
 */
class SharpConfirmTest {

    private val now = 1_800_000_000_000L
    private val on = ScanSettings(sharpConfirmAutoBet = true, sharpConfirmAlerts = true)
    private val rules = SharpConfirm.rules(on, autoBet = true)!!

    /** Jefferson Under 69.5 at Pinnacle: +100 the Under, −122 the Over. Devigged worst case its fair chance is about 47.6%: Novig's +117 is +3.3% EV. */
    private fun pinnacle(odds: Int = 100, other: Int = -122, ageMs: Long? = 40_000L, via: String = "PinnWire", code: String = "PN") =
        Quote(code, odds, other, ageMs?.let { now - it }, via)

    @Test
    fun `the switches are off by default, and each makes its own rules`() {
        val off = ScanSettings()
        assertFalse(off.sharpConfirmAutoBet)
        assertFalse(off.sharpConfirmAlerts)
        assertNull(SharpConfirm.rules(off, autoBet = true))
        assertNull(SharpConfirm.rules(off, autoBet = false))
        val onlyBet = ScanSettings(sharpConfirmAutoBet = true)
        assertTrue(SharpConfirm.rules(onlyBet, autoBet = true) != null)
        assertNull(SharpConfirm.rules(onlyBet, autoBet = false))
        val onlyAlerts = ScanSettings(sharpConfirmAlerts = true)
        assertNull(SharpConfirm.rules(onlyAlerts, autoBet = true))
        assertTrue(SharpConfirm.rules(onlyAlerts, autoBet = false) != null)
        // The defaults: Pinnacle, 3 minutes, any +EV, no CNO page as a confirmer.
        assertEquals(setOf("PN"), rules.codes)
        assertEquals("Pinnacle", rules.label)
        assertEquals(180_000L, rules.maxAgeMs)
        assertEquals(0.0, rules.minEv, 0.0)
        assertFalse(rules.viaCno)
    }

    @Test
    fun `no setting can raise the quote age past the app's 5 minutes, or lower it under 30 seconds`() {
        assertEquals(Freshness.MAX_QUOTE_AGE_MS, SharpConfirm.rules(on.copy(sharpConfirmMaxAgeSeconds = 3_600), true)!!.maxAgeMs)
        assertEquals(30_000L, SharpConfirm.rules(on.copy(sharpConfirmMaxAgeSeconds = 1), true)!!.maxAgeMs)
        assertEquals(listOf(60, 120, 180, 300), ScanSettings.SHARP_MAX_AGE_CHOICES)
        assertTrue(ScanSettings.SHARP_MAX_AGE_CHOICES.all { it * 1_000L <= Freshness.MAX_QUOTE_AGE_MS })
        assertEquals(setOf("PN", "CS"), SharpConfirm.rules(on.copy(sharpConfirmBooks = SharpBookChoice.PINNACLE_CIRCA), true)!!.codes)
    }

    @Test
    fun `a fresh Pinnacle price that makes Novig's price +EV after devigging confirms it`() {
        val r = SharpConfirm.judge(listOf(pinnacle()), 117, live = false, rules, now)
        assertEquals(Verdict.CONFIRMED, r.verdict)
        assertTrue(r.confirmed)
        assertNull(r.reason)
        val j = r.judged.single()
        // The same math as the books check: worst-case devig, then fair ÷ price − 1.
        val fair = CnoBooks.fairFor(100, -122)!!
        assertEquals(fair, j.fair, 1e-12)
        assertEquals(CnoBooks.evAt(fair, 117, false), j.ev, 1e-12)
        assertTrue(j.ev in 0.02..0.05)
        assertEquals(40_000L, j.ageMs)
        assertTrue(r.detail, r.detail.startsWith("Pinnacle +3.") && r.detail.contains("devigged, 40 sec old, via PinnWire"))
    }

    @Test
    fun `Pinnacle saying the bet isn't +EV at Novig's price is not a confirmation, and says so without numbers`() {
        // Novig +100 against Pinnacle's own +100/−122: fair under 50%: negative.
        val r = SharpConfirm.judge(listOf(pinnacle()), 100, false, rules, now)
        assertEquals(Verdict.NOT_CONFIRMED, r.verdict)
        assertEquals("Pinnacle's own devigged price doesn't show it +EV at Novig's price", r.reason)
        // The same sentence at another price (the report counts bets by reason: one line, not one per price).
        assertEquals(r.reason, SharpConfirm.judge(listOf(pinnacle()), 102, false, rules, now).reason)
        assertTrue(r.detail, r.detail.startsWith("Pinnacle -"))
    }

    @Test
    fun `a quote over the age limit, or with no time at all, proves nothing`() {
        val old = SharpConfirm.judge(listOf(pinnacle(ageMs = 181_000L)), 117, false, rules, now)
        assertEquals(Verdict.STALE, old.verdict)
        assertEquals("Pinnacle's price for it is older than 3 min (or has no time)", old.reason)
        val untimed = SharpConfirm.judge(listOf(pinnacle(ageMs = null)), 117, false, rules, now)
        assertEquals(Verdict.STALE, untimed.verdict)
        // The edge of the limit: exactly 3 minutes is fresh.
        assertEquals(Verdict.CONFIRMED, SharpConfirm.judge(listOf(pinnacle(ageMs = 180_000L)), 117, false, rules, now).verdict)
        // A quote a little in the future (the feed's clock): fresh; a minute and more ahead is not trusted.
        assertEquals(Verdict.CONFIRMED, SharpConfirm.judge(listOf(pinnacle(ageMs = -30_000L)), 117, false, rules, now).verdict)
        assertEquals(Verdict.STALE, SharpConfirm.judge(listOf(pinnacle(ageMs = -120_000L)), 117, false, rules, now).verdict)
        // A tighter limit is honoured.
        val oneMinute = SharpConfirm.rules(on.copy(sharpConfirmMaxAgeSeconds = 60), true)!!
        assertEquals(Verdict.STALE, SharpConfirm.judge(listOf(pinnacle(ageMs = 70_000L)), 117, false, oneMinute, now).verdict)
        assertEquals(Verdict.CONFIRMED, SharpConfirm.judge(listOf(pinnacle(ageMs = 50_000L)), 117, false, oneMinute, now).verdict)
    }

    @Test
    fun `the minimum edge is the sharp book's own, and any +EV means more than zero`() {
        val ev = SharpConfirm.judge(listOf(pinnacle()), 117, false, rules, now).judged.single().ev
        assertTrue(ev in 0.02..0.05)
        val twoPercent = SharpConfirm.rules(on.copy(sharpConfirmMinEv = 0.02), true)!!
        assertEquals(Verdict.CONFIRMED, SharpConfirm.judge(listOf(pinnacle()), 117, false, twoPercent, now).verdict)
        val fourPercent = SharpConfirm.rules(on.copy(sharpConfirmMinEv = 0.04), true)!!
        val short = SharpConfirm.judge(listOf(pinnacle()), 117, false, fourPercent, now)
        assertEquals(Verdict.NOT_CONFIRMED, short.verdict)
        assertEquals("Pinnacle's own devigged price shows less than your 4.0% minimum edge", short.reason)
        // A price with no vig at all and no edge is not "any +EV".
        assertEquals(Verdict.NOT_CONFIRMED, SharpConfirm.judge(listOf(pinnacle(100, -100)), 100, false, rules, now).verdict)
    }

    @Test
    fun `no sharp book with both sides is NO_QUOTE, and a source that couldn't be asked is UNAVAILABLE`() {
        val none = SharpConfirm.judge(emptyList(), 117, false, rules, now)
        assertEquals(Verdict.NO_QUOTE, none.verdict)
        assertEquals("no Pinnacle price for both sides of this exact bet", none.reason)
        // A quote of a book that isn't a sharp one here is not counted.
        assertEquals(Verdict.NO_QUOTE, SharpConfirm.judge(listOf(pinnacle(code = "DK")), 117, false, rules, now).verdict)
        val down = SharpConfirm.judge(emptyList(), 117, false, rules, now, unavailable = "ParlayAPI's credits are held back for today")
        assertEquals(Verdict.UNAVAILABLE, down.verdict)
        assertEquals("couldn't get a fresh Pinnacle price (ParlayAPI's credits are held back for today)", down.reason)
    }

    @Test
    fun `a second sharp book that disagrees vetoes, and one that agrees adds nothing needed`() {
        val both = SharpConfirm.rules(on.copy(sharpConfirmBooks = SharpBookChoice.PINNACLE_CIRCA), true)!!
        // Pinnacle says +EV at +117; Circa's own price (Under +110, Over −140: fair about 45%) says it isn't.
        val circaNo = pinnacle(code = "CS", odds = 110, other = -140, via = "CNO's page")
        val split = SharpConfirm.judge(listOf(pinnacle(), circaNo), 117, false, both, now)
        assertEquals(Verdict.NOT_CONFIRMED, split.verdict)
        assertEquals("the sharp books disagree about it (one says +EV, another doesn't)", split.reason)
        // Both agree: confirmed. Circa alone (Pinnacle doesn't list it) also confirms.
        assertEquals(Verdict.CONFIRMED, SharpConfirm.judge(listOf(pinnacle(), pinnacle(code = "CS")), 117, false, both, now).verdict)
        assertEquals(Verdict.CONFIRMED, SharpConfirm.judge(listOf(pinnacle(code = "CS")), 117, false, both, now).verdict)
        // With Pinnacle only chosen, Circa's quote isn't looked at.
        assertEquals(Verdict.CONFIRMED, SharpConfirm.judge(listOf(pinnacle(), circaNo), 117, false, rules, now).verdict)
        // A stale second book can't veto: only fresh quotes speak.
        assertEquals(Verdict.CONFIRMED, SharpConfirm.judge(listOf(pinnacle(), circaNo.copy(atMs = now - 600_000L)), 117, false, both, now).verdict)
    }

    @Test
    fun `a live game's Novig taker fee comes off the edge`() {
        val pregame = SharpConfirm.judge(listOf(pinnacle()), 117, live = false, rules, now).judged.single().ev
        val live = SharpConfirm.judge(listOf(pinnacle()), 117, live = true, rules, now).judged.single().ev
        assertTrue("$live < $pregame", live < pregame)
    }

    @Test
    fun `CNO's game page gives Pinnacle's column, dated by the page's own update`() {
        val view = CnoBooksView(
            "Justin Jefferson Under 69.5", prices = listOf(CnoBookPrice("PN", 100, null, -122, null), CnoBookPrice("DK", 110, null, null, null), CnoBookPrice("CS", -105, null, -115, null)),
            fetchedAtMs = now - 10_000L, cnoAgeSeconds = 25,
        )
        assertEquals(now - 35_000L, view.dataAtMs)
        val quotes = SharpConfirm.fromCno(view, rules)
        assertEquals(listOf("PN"), quotes.map { it.code })
        assertEquals(now - 35_000L, quotes.single().atMs)
        assertEquals("CNO's page", quotes.single().via)
        // A page that didn't date itself is dated by when it was read.
        assertEquals(now - 10_000L, SharpConfirm.fromCno(view.copy(cnoAgeSeconds = null), rules).single().atMs)
        assertTrue(SharpConfirm.fromCno(null, rules).isEmpty())
        // A one-sided Pinnacle column isn't a quote.
        assertTrue(SharpConfirm.fromCno(view.copy(prices = listOf(CnoBookPrice("PN", 100, null, null, null))), rules).isEmpty())
    }

    @Test
    fun `CNO's page can veto without a feed call, never confirm`() {
        val no = listOf(pinnacle(via = "CNO's page"))
        // At Novig +100 Pinnacle's own price says no: a veto.
        val veto = SharpConfirm.preVeto(no, 100, false, rules, now)
        assertEquals(Verdict.NOT_CONFIRMED, veto!!.verdict)
        // At +117 it says yes: no veto (a veto is never a confirmation either).
        assertNull(SharpConfirm.preVeto(no, 117, false, rules, now))
        // A stale page doesn't veto: only a fresh quote speaks against a bet.
        assertNull(SharpConfirm.preVeto(listOf(pinnacle(ageMs = 400_000L, via = "CNO's page")), 100, false, rules, now))
        // Under the minimum edge but still +EV: not a veto (the feed is asked).
        assertNull(SharpConfirm.preVeto(no, 117, false, SharpConfirm.rules(on.copy(sharpConfirmMinEv = 0.04), true)!!, now))
        // Nothing on the page: nothing to say.
        assertNull(SharpConfirm.preVeto(emptyList(), 100, false, rules, now))
    }
}
