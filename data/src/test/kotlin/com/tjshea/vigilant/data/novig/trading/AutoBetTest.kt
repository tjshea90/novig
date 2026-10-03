package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The auto-bet's rules (Tj, 2026-10-01: "automatically bet each bet without me doing anything at all"): the settings he picks (off by default),
 * which bets pass them, and the stake with worked Kelly numbers: it changes with each bet's odds, and is held to his per-bet maximum, to what
 * Novig has for sale and to what's left in the wallet.
 */
class AutoBetTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun row(odds: Int, fair: Double, available: Double? = null) = CnoRow(
        ev = 0.03, startsAtMs = 2_000_000L, league = "NFL", event = "A @ B", market = "Moneyline", bet = "A", odds = odds, available = available,
        book = "Novig", fairProbability = fair, books = 6,
    )

    private fun rules(
        stake: AutoBetStake = AutoBetStake.QUARTER_KELLY, custom: Double = 5.0, max: Double = 100.0, books: Int = 3, ev: Double = 0.03, twoSided: Int = 2,
        maxOdds: Int = 0, allAgree: Boolean = false,
    ) = AutoBet.rules(
        ScanSettings(
            autoBetStake = stake, autoBetCustomStake = custom, autoBetMaxStake = max, autoBetBooks = books, autoBetMinEv = ev, autoBetTwoSided = twoSided, autoBetMaxOdds = maxOdds,
            autoBetAllAgree = allAgree,
        ),
    )

    /** [AutoBet.judge] at -110 (a price no limit on longest odds touches) unless the test says otherwise. */
    private fun judge(r: AutoBet.Rules, ev: Double, check: CnoBooks.Check, american: Int = -110) = AutoBet.judge(r, ev, check, american)

    private fun amount(s: AutoBet.Stake) = (s as AutoBet.Stake.Amount).dollars
    private fun skip(s: AutoBet.Stake) = (s as AutoBet.Stake.Skip).reason

    // ---- the settings -------------------------------------------------------------------------------------------

    @Test
    fun `auto-bet is off by default with careful criteria, and its choices are the ones Tj listed`() {
        val s = ScanSettings()
        assertFalse(s.autoBet)
        assertNull(s.autoBetHalted)
        assertEquals(3, s.autoBetBooks)
        assertEquals(0.03, s.autoBetMinEv, 0.0)
        assertEquals(2, s.autoBetTwoSided)
        assertEquals(AutoBetStake.ONE_DOLLAR, s.autoBetStake)
        assertEquals(5.0, s.autoBetCustomStake, 0.0)
        assertEquals(10.0, s.autoBetMaxStake, 0.0)
        assertEquals("no limit on odds until Tj sets one: what ran before doesn't change", 0, s.autoBetMaxOdds)
        assertEquals(listOf(100, 110, 120, 130, 150, 200, 300, 0), ScanSettings.AUTO_BET_MAX_ODDS_CHOICES)
        assertFalse("every book must agree is off until Tj turns it on: what ran before doesn't change", s.autoBetAllAgree)
        assertEquals(listOf(2, 3, 4, 5), ScanSettings.AUTO_BET_BOOKS_CHOICES)
        assertEquals(listOf(0.02, 0.025, 0.03, 0.0325, 0.035, 0.0375, 0.04), ScanSettings.AUTO_BET_MIN_EV_CHOICES)
        assertEquals(listOf(1, 2, 3), ScanSettings.AUTO_BET_TWO_SIDED_CHOICES)
        assertEquals(listOf("⅛ Kelly", "¼ Kelly", "½ Kelly", "$1", "My amount"), AutoBetStake.entries.map { it.label })
        assertEquals(listOf(0.125, 0.25, 0.5, null, null), AutoBetStake.entries.map { it.kelly })
        // A file saved before auto-bet existed reads with all of it off, and nothing else moves.
        val old = json.decodeFromString(ScanSettings.serializer(), """{"autoScan":"CNO","autoScanSeconds":30,"schema":12}""")
        assertFalse(old.autoBet)
        assertEquals(0, old.autoBetMaxOdds)
        assertFalse(old.autoBetAllAgree)
        assertEquals(30, old.autoScanSeconds)
        assertEquals(AutoScanMode.CNO, old.autoScan)
        // What Tj picks survives a save and a load.
        val picked = ScanSettings(autoBet = true, autoBetBooks = 5, autoBetMinEv = 0.0325, autoBetTwoSided = 3, autoBetStake = AutoBetStake.CUSTOM, autoBetCustomStake = 7.25, autoBetMaxStake = 12.0, autoBetMaxOdds = 130, autoBetAllAgree = true)
        assertEquals(picked, json.decodeFromString(ScanSettings.serializer(), json.encodeToString(ScanSettings.serializer(), picked)))
    }

    @Test
    fun `auto-bet runs only while the CNO scanner runs in the background, not paused and not halted`() {
        val on = ScanSettings(autoBet = true, autoScan = AutoScanMode.CNO, scanner = ScannerMode.BOTH)
        assertTrue(on.autoBetsNow)
        assertTrue(on.copy(autoScan = AutoScanMode.BOTH).autoBetsNow)
        assertFalse("the background scan is off", on.copy(autoScan = AutoScanMode.OFF).autoBetsNow)
        assertFalse("the scanner is Vigilant only: CNO is asleep", on.copy(scanner = ScannerMode.VIGILANT).autoBetsNow)
        assertFalse("paused", on.copy(paused = true).autoBetsNow)
        assertFalse("halted until Tj resumes it", on.copy(autoBetHalted = "an order's answer was lost").autoBetsNow)
        assertFalse("switched off", on.copy(autoBet = false).autoBetsNow)
    }

    @Test
    fun `the rules clamp what was typed, books 2-5, both sides 1-3, an edge never under 0_5 percent`() {
        val r = AutoBet.rules(ScanSettings(autoBetBooks = 9, autoBetTwoSided = 0, autoBetMinEv = 0.0001, autoBetCustomStake = -3.0, autoBetMaxStake = -1.0))
        assertEquals(5, r.minBooks)
        assertEquals(1, r.twoSided)
        assertEquals(AutoBet.MIN_EV_FLOOR, r.minEv, 0.0)
        assertEquals(0.0, r.customStake, 0.0)
        assertEquals(0.0, r.maxStake, 0.0)
        assertEquals(2, AutoBet.rules(ScanSettings(autoBetBooks = 0)).minBooks)
    }

    // ---- which bets pass ----------------------------------------------------------------------------------------

    private fun check(twoSided: Int = 4, agreeing: Int = 3, ev: Double? = 0.02) =
        CnoBooks.Check(twoSided, 0, 0.51, agreeing, 100, ev, CnoBooks.Verdict.CONFIRMED)

    @Test
    fun `a bet passes when its edge, its agreeing books and its two-sided books all meet the criteria`() {
        val r = rules(books = 3, ev = 0.03, twoSided = 2)
        assertNull(judge(r, 0.035, check(twoSided = 4, agreeing = 3)))
        // Exactly at each limit passes.
        assertNull(judge(r, 0.03, check(twoSided = 2, agreeing = 3)))
        // One short of each fails, and says which.
        assertTrue(judge(r, 0.0299, check())!!.contains("under your +3.00% minimum"))
        assertTrue(judge(r, 0.04, check(twoSided = 1, agreeing = 1))!!.contains("1 book prices both sides (you need 2)"))
        assertTrue(judge(r, 0.04, check(twoSided = 4, agreeing = 2))!!.contains("2 books say +EV on their own (you need 3)"))
        // A bet that good is a stale or mismatched price: never bet unattended (15% is the ceiling, and it is allowed).
        assertNull(judge(r, AutoBet.MAX_SANE_EV, check()))
        assertTrue(judge(r, 0.1501, check())!!.contains("usually a stale or mismatched price"))
        assertTrue(judge(r, 0.40, check())!!.contains("over +15.00%"))
        assertTrue(judge(r, 0.04, check(ev = -0.01))!!.contains("not +EV"))
        assertTrue(judge(r, 0.04, check(ev = null))!!.contains("not +EV"))
    }

    /** Tj, 2026-10-01: "require that every sports book scanned agrees the bet is positive EV (for example, 5 of 5 books agree positive EV)". */
    @Test
    fun `with every book must agree on, a bet passes only when all the books that price both sides say +EV, and not otherwise`() {
        val all = rules(books = 2, allAgree = true)
        assertTrue(all.allAgree)
        // 5 of 5, 2 of 2, 3 of 3: every book agrees.
        assertNull(judge(all, 0.04, check(twoSided = 5, agreeing = 5)))
        assertNull(judge(all, 0.04, check(twoSided = 2, agreeing = 2)))
        assertNull(judge(all, 0.04, check(twoSided = 3, agreeing = 3)))
        // 4 of 5, 3 of 4, 2 of 3: one disagrees, and the reason says how many.
        assertEquals("only 4 of 5 books say +EV on their own (you need every one)", judge(all, 0.04, check(twoSided = 5, agreeing = 4)))
        assertTrue(judge(all, 0.04, check(twoSided = 4, agreeing = 3))!!.contains("only 3 of 4 books"))
        assertNotNull(judge(all, 0.04, check(twoSided = 3, agreeing = 2)))
        // Off (the default): the same 4 of 5 passes a minimum of 3, as it did before.
        assertNull(judge(rules(books = 3), 0.04, check(twoSided = 5, agreeing = 4)))
        assertFalse(rules().allAgree)
        // On top of the others, never instead of them: a 2 of 2 under a minimum of 5, the edge and the odds limit all still apply.
        assertTrue(judge(rules(books = 5, allAgree = true), 0.04, check(twoSided = 2, agreeing = 2))!!.contains("(you need 5)"))
        assertTrue(judge(rules(books = 2, ev = 0.03, allAgree = true), 0.02, check(twoSided = 4, agreeing = 4))!!.contains("under your"))
        assertNotNull(judge(rules(books = 2, maxOdds = 120, allAgree = true), 0.04, check(twoSided = 4, agreeing = 4), 130))
        assertTrue(judge(rules(books = 2, twoSided = 3, allAgree = true), 0.04, check(twoSided = 2, agreeing = 2))!!.contains("price both sides (you need 3)"))
        // Every choice of the minimum, with "5 of 5" (Tj's example) for the top one.
        for (need in ScanSettings.AUTO_BET_BOOKS_CHOICES) for (n in 1..7) {
            val ok = judge(rules(books = need, allAgree = true), 0.05, check(twoSided = n, agreeing = n)) == null
            assertEquals("need $need and all, with $n of $n", n >= need, ok)
        }
    }

    @Test
    fun `a longest-odds limit skips a longer price, never a favourite, and counts by reason`() {
        val r = rules(maxOdds = 130)
        assertEquals(130, r.maxOdds)
        // At the limit and shorter pass, favourites always pass.
        assertNull(judge(r, 0.04, check(), 130))
        assertNull(judge(r, 0.04, check(), 100))
        assertNull(judge(r, 0.04, check(), -110))
        assertNull(judge(r, 0.04, check(), -1000))
        // One point longer fails, and the words don't carry the price (the report counts bets by reason).
        val long = judge(r, 0.04, check(), 131)
        assertEquals("its odds are longer than your +130 limit", long)
        assertEquals(long, judge(r, 0.04, check(), 900))
        // No limit (the default): nothing is too long.
        assertNull(judge(rules(), 0.04, check(), 5000))
        assertEquals(0, rules().maxOdds)
        // Every choice is honoured at its own edge.
        for (limit in ScanSettings.AUTO_BET_MAX_ODDS_CHOICES.filter { it > 0 }) {
            assertNull("+$limit at +$limit", judge(rules(maxOdds = limit), 0.04, check(), limit))
            assertNotNull("+${limit + 1} over +$limit", judge(rules(maxOdds = limit), 0.04, check(), limit + 1))
        }
        // A typed limit under +100 is read as +100 (even money), and a negative one as none.
        assertEquals(100, rules(maxOdds = 50).maxOdds)
        assertEquals(0, rules(maxOdds = -5).maxOdds)
        assertTrue(AutoBet.tooLong(100, 101))
        assertFalse(AutoBet.tooLong(100, 100))
        assertFalse(AutoBet.tooLong(0, 100_000))
    }

    @Test
    fun `every choice of books agreeing and books offering both sides is honoured`() {
        for (need in ScanSettings.AUTO_BET_BOOKS_CHOICES) for (agreeing in 0..6) {
            val ok = judge(rules(books = need), 0.05, check(twoSided = 6, agreeing = agreeing)) == null
            assertEquals("need $need, have $agreeing", agreeing >= need, ok)
        }
        for (need in ScanSettings.AUTO_BET_TWO_SIDED_CHOICES) for (two in 0..5) {
            val ok = judge(rules(twoSided = need, books = 2), 0.05, check(twoSided = two, agreeing = 2)) == null
            assertEquals("need $need both sides, have $two", two >= need, ok)
        }
        for (ev in ScanSettings.AUTO_BET_MIN_EV_CHOICES) {
            assertNull(judge(rules(ev = ev), ev, check()))
            assertNotNull(judge(rules(ev = ev), ev - 0.0005, check()))
        }
    }

    // ---- the stake ----------------------------------------------------------------------------------------------

    @Test
    fun `Kelly stakes are worked from each bet's own odds and edge`() {
        // Full Kelly = (fair - price) / (1 - price). +100 (price 0.5), fair 0.515: 0.03. On $1,000: 1/8 = $3.75, 1/4 = $7.50, 1/2 = $15.00.
        val even = row(100, 0.515)
        assertEquals(3.75, amount(AutoBet.stake(rules(AutoBetStake.EIGHTH_KELLY), even, 1000.0, 500.0)), 1e-9)
        assertEquals(7.50, amount(AutoBet.stake(rules(AutoBetStake.QUARTER_KELLY), even, 1000.0, 500.0)), 1e-9)
        assertEquals(15.00, amount(AutoBet.stake(rules(AutoBetStake.HALF_KELLY), even, 1000.0, 500.0)), 1e-9)
        // +150 (price 0.4), fair 0.42: (0.42 - 0.4) / 0.6 = 0.03333; 1/4 of $1,000 = $8.333, floored to the cent.
        assertEquals(8.33, amount(AutoBet.stake(rules(AutoBetStake.QUARTER_KELLY), row(150, 0.42), 1000.0, 500.0)), 1e-9)
        // -300 (price 0.75), fair 0.77: 0.02 / 0.25 = 0.08; 1/8 of $1,000 = $10.00. The same edge on a favourite stakes more than on an underdog.
        assertEquals(10.00, amount(AutoBet.stake(rules(AutoBetStake.EIGHTH_KELLY), row(-300, 0.77), 1000.0, 500.0)), 1e-9)
        // A bigger bankroll scales it; no edge (fair under the price) or no bankroll is no stake at all.
        assertEquals(37.5, amount(AutoBet.stake(rules(AutoBetStake.EIGHTH_KELLY), even, 10_000.0, 500.0)), 1e-9)
        assertTrue(skip(AutoBet.stake(rules(), row(100, 0.49), 1000.0, 500.0)).contains("no Kelly stake"))
        assertTrue(skip(AutoBet.stake(rules(), even, 0.0, 500.0)).contains("no Kelly stake"))
        assertTrue(skip(AutoBet.stake(rules(), row(100, 0.5).copy(fairProbability = null), 1000.0, 500.0)).contains("no Kelly stake"))
    }

    /** Tj, 2026-10-01: "unless ¼ Kelly betting automatically puts a much lower stake on longshots. Does Kelly do this?" */
    @Test
    fun `Kelly stakes less on longer odds at the same edge, and a dollar or a typed amount doesn't`() {
        // The same +4% edge at four prices (fair = price x 1.04), a $185 bankroll, 1/4 Kelly: stake = bankroll / 4 x edge x price / (1 - price).
        fun at(american: Int, stake: AutoBetStake = AutoBetStake.QUARTER_KELLY): AutoBet.Stake {
            val price = AutoBet.priceOf(row(american, 0.5))
            return AutoBet.stake(rules(stake), row(american, price * 1.04), 185.0, 500.0)
        }
        assertEquals("even money: 4% of the bankroll x 1/4", 1.85, amount(at(100)), 1e-9)
        assertEquals("+130: three quarters of that", 1.42, amount(at(130)), 1e-9)
        // Under a dollar is a stake like any other (Tj, 2026-10-01: "often under $1"): the same Kelly number, floored to the cent.
        assertEquals("+200 is 92 cents", 0.92, amount(at(200)), 1e-9)
        assertEquals("+300 is 61 cents", 0.61, amount(at(300)), 1e-9)
        assertEquals("+500 is 37 cents", 0.37, amount(at(500)), 1e-9)
        assertEquals("+900 is 20 cents", 0.20, amount(at(900)), 1e-9)
        // A favourite stakes more at the same edge: -200 is twice the even-money stake.
        assertEquals(3.70, amount(at(-200)), 1e-9)
        // A dollar and a typed amount ignore the odds entirely.
        for (american in listOf(100, 130, 300, 900)) {
            assertEquals(1.0, amount(at(american, AutoBetStake.ONE_DOLLAR)), 0.0)
            assertEquals(5.0, amount(at(american, AutoBetStake.CUSTOM)), 0.0)
        }
    }

    @Test
    fun `a stake is held to what Novig has for sale, to the per-bet maximum, and to the wallet`() {
        val even = row(100, 0.515)
        // Half Kelly of $1,000 is $15: a $10 maximum holds it; $4 available at that price holds it; so does a wallet of $2.50.
        assertEquals(10.0, amount(AutoBet.stake(rules(AutoBetStake.HALF_KELLY, max = 10.0), even, 1000.0, 500.0)), 1e-9)
        assertEquals(4.0, amount(AutoBet.stake(rules(AutoBetStake.HALF_KELLY), row(100, 0.515, available = 4.0), 1000.0, 500.0)), 1e-9)
        assertEquals(2.5, amount(AutoBet.stake(rules(AutoBetStake.HALF_KELLY), even, 1000.0, 2.5)), 1e-9)
        // The wallet's remainder is floored to the cent, never rounded up past what's there.
        assertEquals(2.49, amount(AutoBet.stake(rules(AutoBetStake.HALF_KELLY), even, 1000.0, 2.499)), 1e-9)
    }

    @Test
    fun `a dollar, or the amount Tj typed, is the stake, under the same caps`() {
        val even = row(100, 0.515)
        assertEquals(1.0, amount(AutoBet.stake(rules(AutoBetStake.ONE_DOLLAR), even, 1000.0, 50.0)), 0.0)
        assertEquals(7.25, amount(AutoBet.stake(rules(AutoBetStake.CUSTOM, custom = 7.25), even, 1000.0, 50.0)), 0.0)
        assertEquals("held to the maximum", 5.0, amount(AutoBet.stake(rules(AutoBetStake.CUSTOM, custom = 7.25, max = 5.0), even, 1000.0, 50.0)), 0.0)
        assertEquals("held to the wallet", 3.0, amount(AutoBet.stake(rules(AutoBetStake.CUSTOM, custom = 7.25), even, 1000.0, 3.0)), 0.0)
        // Kelly isn't asked for: a $1 or typed stake doesn't need a bankroll or an edge figure.
        assertEquals(1.0, amount(AutoBet.stake(rules(AutoBetStake.ONE_DOLLAR), row(100, 0.515).copy(fairProbability = null), 0.0, 50.0)), 0.0)
    }

    /** Tj, 2026-10-01: "I don't want a $1 minimum bet for the auto bet feature. It can bet as low as 1 cent, whatever the number is that I have in options." */
    @Test
    fun `the least a stake can be is one cent, so a Kelly stake under a dollar is placed as it is`() {
        val even = row(100, 0.515)
        assertEquals(0.01, AutoBet.MIN_STAKE, 0.0)
        // 1/8 Kelly of a $100 bankroll at this edge is 37.5 cents: bet 37 (floored to the cent), not skipped and not rounded up to a dollar.
        assertEquals(0.37, amount(AutoBet.stake(rules(AutoBetStake.EIGHTH_KELLY), even, 100.0, 500.0)), 1e-9)
        // A typed amount and a maximum per bet can be cents, too.
        assertEquals(0.25, amount(AutoBet.stake(rules(AutoBetStake.CUSTOM, custom = 0.25), even, 1000.0, 50.0)), 1e-9)
        assertEquals(0.05, amount(AutoBet.stake(rules(AutoBetStake.CUSTOM, custom = 5.0, max = 0.05), even, 1000.0, 50.0)), 1e-9)
        assertEquals(0.01, amount(AutoBet.stake(rules(AutoBetStake.CUSTOM, custom = 0.01), even, 1000.0, 50.0)), 1e-9)
        // The fixed dollar is still a dollar.
        assertEquals(1.0, amount(AutoBet.stake(rules(AutoBetStake.ONE_DOLLAR), even, 1000.0, 50.0)), 0.0)
        // The wallet's remainder caps it, down to the last cent.
        assertEquals(0.99, amount(AutoBet.stake(rules(AutoBetStake.ONE_DOLLAR), even, 1000.0, 0.99)), 1e-9)
        assertEquals(0.01, amount(AutoBet.stake(rules(AutoBetStake.ONE_DOLLAR), even, 1000.0, 0.0199)), 1e-9)
    }

    @Test
    fun `a wallet under a cent stops everything, and a stake under a cent is skipped, never rounded up`() {
        val even = row(100, 0.515)
        assertEquals(AutoBet.Stake.WalletEmpty, AutoBet.stake(rules(), even, 1000.0, 0.009))
        assertEquals(AutoBet.Stake.WalletEmpty, AutoBet.stake(rules(), even, 1000.0, 0.0))
        // 1/8 Kelly of a $2 bankroll at this edge is three quarters of a cent.
        assertEquals("its ⅛ Kelly stake is under a cent", skip(AutoBet.stake(rules(AutoBetStake.EIGHTH_KELLY), even, 2.0, 500.0)))
        assertEquals("your maximum per bet is under a cent", skip(AutoBet.stake(rules(AutoBetStake.ONE_DOLLAR, max = 0.005), even, 1000.0, 500.0)))
        assertTrue(skip(AutoBet.stake(rules(AutoBetStake.CUSTOM, custom = 0.0), even, 1000.0, 500.0)).contains("$0"))
        assertTrue(skip(AutoBet.stake(rules(AutoBetStake.CUSTOM, custom = 0.004), even, 1000.0, 500.0)).contains("under a cent"))
    }

    @Test
    fun `the price the order book shows must be the price the bet was judged at`() {
        assertTrue(AutoBet.priceMatches(0.46, 0.46))
        assertTrue(AutoBet.priceMatches(0.46, 0.49))
        assertFalse(AutoBet.priceMatches(0.46, 0.4901))
        assertFalse("the other side of the market", AutoBet.priceMatches(0.40, 0.60))
        assertEquals(0.5, AutoBet.priceOf(row(100, 0.5)), 1e-12)
        assertEquals(0.4, AutoBet.priceOf(row(150, 0.5)), 1e-12)
    }

    /** RESEARCH.md §72: when not every bet can be placed, the money goes to the edges most likely to hold by the close. */
    @Test
    fun `the credible edge is the sharpest book's own edge when it priced the bet, else 70% of the shown edge`() {
        assertEquals(0.7, AutoBet.NO_SHARP_KEEPS, 0.0)
        // A 5% shown edge the sharp book gives only 1.2%, against a 3% one with no sharp book: 1.2% vs 2.1%, the second first.
        assertEquals(0.012, AutoBet.credibleEv(0.05, 0.012), 1e-12)
        assertEquals(0.021, AutoBet.credibleEv(0.03, null), 1e-12)
        // A sharp book that gives more than the shown edge is believed too (sharp 4%+ kept +5.7% at the close in the soccer study).
        assertEquals(0.06, AutoBet.credibleEv(0.03, 0.06), 1e-12)
        val order = listOf(0.05 to 0.012, 0.03 to null, 0.04 to 0.035).sortedByDescending { (shown, sharp) -> AutoBet.credibleEv(shown, sharp) }
        assertEquals(listOf(0.04 to 0.035, 0.03 to null, 0.05 to 0.012), order)
    }
}
