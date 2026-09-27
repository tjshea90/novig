package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.book.BookBoard
import com.tjshea.vigilant.data.book.Sportsbook
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.Pricing
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SourceReport

/**
 * Vigilant MGM's board: BetMGM's posted odds (with its own ids, as PropLine sends them) priced
 * through the REAL BookBoard and Pricing against Pinnacle and two other books, like [SampleScan].
 */
object SampleMgm {
    const val NOW = SampleScan.NOW
    private const val HOUR = 3_600_000L

    private fun q(side: Side, american: Int, point: Double? = null, id: String? = null) =
        RefQuote(side, if (american > 0) 1 + american / 100.0 else 1 + 100.0 / -american, point, id)

    private fun mkt(book: String, title: String, kind: LineKind, a: RefQuote, b: RefQuote, subject: String? = null, stat: String? = null, fixture: String? = null) =
        RefBookMarket(book, title, kind, listOf(a, b), NOW - 40_000, subject = subject, stat = stat, bookEventId = fixture)

    private val nfl = listOf(
        RefEvent(
            "pl:555", "americanfootball_nfl", NOW + 5 * HOUR, home = "Dallas Cowboys", away = "Baltimore Ravens",
            markets = listOf(
                mkt("pinnacle", "Pinnacle", LineKind.MONEYLINE, q(Side.HOME, -150), q(Side.AWAY, 130)),
                mkt("draftkings", "DraftKings", LineKind.MONEYLINE, q(Side.HOME, -155), q(Side.AWAY, 130)),
                mkt("fanduel", "FanDuel", LineKind.MONEYLINE, q(Side.HOME, -148), q(Side.AWAY, 126)),
                mkt("betmgm", "BetMGM", LineKind.MONEYLINE, q(Side.HOME, -175, id = "888-1001"), q(Side.AWAY, 150, id = "888-1002"), fixture = "17345678"),
                mkt("pinnacle", "Pinnacle", LineKind.SPREAD, q(Side.HOME, -110, -3.5), q(Side.AWAY, -110, 3.5)),
                mkt("betmgm", "BetMGM", LineKind.SPREAD, q(Side.HOME, -105, -3.5, "889-2001"), q(Side.AWAY, -115, 3.5, "889-2002"), fixture = "17345678"),
                mkt("pinnacle", "Pinnacle", LineKind.PLAYER_PROP, q(Side.OVER, -120, 6.5), q(Side.UNDER, 100, 6.5), subject = "CeeDee Lamb", stat = "RECEPTIONS"),
                mkt("betmgm", "BetMGM", LineKind.PLAYER_PROP, q(Side.OVER, 110, 6.5, "990-3001"), q(Side.UNDER, -140, 6.5, "990-3002"), subject = "CeeDee Lamb", stat = "RECEPTIONS", fixture = "17345678"),
            ),
        ),
        RefEvent(
            "pl:556", "americanfootball_nfl", NOW + 8 * HOUR, home = "Chicago Bears", away = "New York Jets",
            markets = listOf(
                mkt("pinnacle", "Pinnacle", LineKind.TOTAL, q(Side.OVER, -105, 41.5), q(Side.UNDER, -115, 41.5)),
                mkt("draftkings", "DraftKings", LineKind.TOTAL, q(Side.OVER, -110, 41.5), q(Side.UNDER, -110, 41.5)),
                mkt("betmgm", "BetMGM", LineKind.TOTAL, q(Side.OVER, 100, 41.5, "770-1"), q(Side.UNDER, -120, 41.5, "770-2"), fixture = "17345999"),
            ),
        ),
    )

    val settings = ScanSettings(leagues = setOf("NFL"), minEvPercent = 0.01, referenceBooks = listOf("pinnacle", "draftkings", "fanduel", "betmgm"))

    fun result(s: ScanSettings = settings): ScanResult {
        val snap = RefSnapshot("americanfootball_nfl", nfl, NOW - 60_000, provider = "propline")
        val board = BookBoard.build(Sportsbook.BETMGM, listOf(snap), NOW - 60_000, NOW)
        val fair = snap.copy(events = snap.events.map { e -> e.copy(markets = e.markets.filter { it.bookKey != "betmgm" }) })
        val p = board.plan(listOf(fair), s, NOW, youngOnly = true)
        return BookBoard.withBookAges(Pricing.price(p.plan, p.books, s, NOW), p.seenAt)
    }

    fun state(s: ScanSettings = settings): UiState {
        val r = result(s)
        return UiState(
            settings = s,
            result = r,
            feed = r.feed(s),
            status = ScanStatus(scannedAtMs = NOW - 60_000, sources = listOf(SourceReport("propline", "PropLine", 1, 0, 2, null)), booksFetched = r.stats.marketsPriced),
            proplineKeys = listOf("propline-sample-0001"),
            loaded = true,
        ).indexed(NOW)
    }
}
