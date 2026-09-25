package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.keys.KeyUsage
import com.tjshea.vigilant.data.keys.ProviderUsage
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.Pricing
import com.tjshea.vigilant.data.scanner.ScanProgress
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SourceReport
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.engine.MarketFee

/**
 * A realistic board built through the REAL Planner and Pricing code (not hand-set EV numbers),
 * so screenshots and UI tests show exactly what the math produces.
 */
object SampleScan {
    const val NOW = 1_790_400_000_000L // Fri 2026-09-25 midday
    private const val HOUR = 3_600_000L

    private val events = ArrayList<NovigEvent>()
    private val markets = ArrayList<NovigMarket>()
    private val books = HashMap<String, NovigBook>()
    private val refs = HashMap<String, MutableList<RefEvent>>()

    /** Pinnacle and both exchanges (the free sources), plus a few Odds API sportsbooks. */
    private val books10 = listOf(
        "pinnacle" to "Pinnacle", "polymarket" to "Polymarket", "kalshi" to "Kalshi", "draftkings" to "DraftKings",
        "fanduel" to "FanDuel", "betmgm" to "BetMGM", "williamhill_us" to "Caesars",
    )

    private fun market(id: String, event: String, type: String, start: Long, vararg o: Pair<String, String>) =
        NovigMarket(id, event, type, "OPEN", "", start, MarketFee.GAME, o.map { NovigOutcome(it.first, it.second, "TBD") }).also { markets += it }

    private fun book(id: String, a: String, aBid: Int, b: String, bBid: Int, depth: Long = 40_000) {
        books[id] = NovigBook(id, 1, mapOf(
            a to listOf(BidLevel(aBid, depth), BidLevel(aBid - 5, depth * 3)),
            b to listOf(BidLevel(bBid, depth), BidLevel(bBid - 5, depth * 3)),
        ), NOW - 4_000)
    }

    /** Reference quotes: [shift] nudges each successive book's price a little, like a real market. */
    private fun ref(kind: LineKind, homeOrOver: Double, awayOrUnder: Double, point: Double?, shift: Double = 0.02): List<RefBookMarket> =
        books10.mapIndexed { i, (key, title) ->
            val wobble = (i % 3 - 1) * shift
            val (s1, s2) = if (kind == LineKind.TOTAL) Side.OVER to Side.UNDER else Side.HOME to Side.AWAY
            RefBookMarket(key, title, kind, listOf(
                RefQuote(s1, homeOrOver + wobble, point),
                RefQuote(s2, awayOrUnder - wobble, point?.let { if (kind == LineKind.SPREAD) -it else it }),
            ), NOW - 3 * 60_000)
        }

    private fun game(
        league: String, sport: String, id: String, away: String, home: String, awayAb: String, homeAb: String, start: Long,
        mlBids: Pair<Int, Int>, refMl: Pair<Double, Double>,
        spread: Triple<Double, Pair<Int, Int>, Pair<Double, Double>>? = null,
        total: Triple<Double, Pair<Int, Int>, Pair<Double, Double>>? = null,
    ) {
        events += NovigEvent(id, sport, league, "OPEN_PREGAME", "$away @ $home", start)
        val refMarkets = ArrayList<RefBookMarket>()
        market("$id-ml", id, "MONEY", start, "$id-ml-h" to homeAb, "$id-ml-a" to awayAb)
        book("$id-ml", "$id-ml-h", mlBids.first, "$id-ml-a", mlBids.second)
        refMarkets += ref(LineKind.MONEYLINE, refMl.first, refMl.second, null)
        spread?.let { (homePoint, bids, odds) ->
            market("$id-sp", id, "SPREAD", start, "$id-sp-h" to "$homeAb ${Planner.signed(homePoint)}", "$id-sp-a" to "$awayAb ${Planner.signed(-homePoint)}")
            book("$id-sp", "$id-sp-h", bids.first, "$id-sp-a", bids.second)
            refMarkets += ref(LineKind.SPREAD, odds.first, odds.second, homePoint, 0.01)
        }
        total?.let { (pt, bids, odds) ->
            market("$id-to", id, "TOTAL", start, "$id-to-o" to "Over ${Planner.fmt(pt)}", "$id-to-u" to "Under ${Planner.fmt(pt)}")
            book("$id-to", "$id-to-o", bids.first, "$id-to-u", bids.second)
            refMarkets += ref(LineKind.TOTAL, odds.first, odds.second, pt, 0.01)
        }
        refs.getOrPut(sportKey(league)) { ArrayList() } += RefEvent("r-$id", sportKey(league), start, home, away, refMarkets)
    }

    private fun sportKey(league: String) = if (league == "NFL") "americanfootball_nfl" else "baseball_mlb"

    /** An over/under on one team or player for game [id], quoted by Kalshi and Pinnacle. */
    private fun overUnder(id: String, type: String, description: String, line: Double, bids: Pair<Int, Int>, kalshi: Pair<Double, Double>, kind: LineKind, subject: String, stat: String? = null) {
        val mid = "$id-$type-$line"
        market(mid, id, type, events.first { it.eventId == id }.startsTs, "$mid-o" to "Over ${Planner.fmt(line)}", "$mid-u" to "Under ${Planner.fmt(line)}")
            .let { markets[markets.indexOf(it)] = it.copy(description = description) }
        book(mid, "$mid-o", bids.first, "$mid-u", bids.second)
        val list = refs.getValue("americanfootball_nfl")
        val i = list.indexOfFirst { it.id == "r-$id" }
        val extra = listOf("kalshi" to "Kalshi", "pinnacle" to "Pinnacle").mapIndexed { n, (key, title) ->
            RefBookMarket(key, title, kind, listOf(RefQuote(Side.OVER, kalshi.first + n * 0.02, line), RefQuote(Side.UNDER, kalshi.second - n * 0.02, line)), NOW - 3 * 60_000, 0, subject, stat)
        }
        list[i] = list[i].copy(markets = list[i].markets + extra)
    }

    init {
        game("NFL", "FOOTBALL", "g1", "Baltimore Ravens", "Dallas Cowboys", "BAL", "DAL", NOW + 50 * HOUR,
            mlBids = 380 to 615, refMl = 2.45 to 1.62,
            spread = Triple(3.5, 470 to 500, 1.93 to 1.95), total = Triple(47.5, 490 to 490, 1.91 to 1.97))
        game("NFL", "FOOTBALL", "g2", "Kansas City Chiefs", "Las Vegas Raiders", "KC", "LV", NOW + 54 * HOUR,
            mlBids = 300 to 690, refMl = 3.10 to 1.40, spread = Triple(7.5, 505 to 480, 1.87 to 2.01))
        game("NFL", "FOOTBALL", "g3", "Green Bay Packers", "Tampa Bay Buccaneers", "GB", "TB", NOW + 50 * HOUR,
            mlBids = 440 to 545, refMl = 2.20 to 1.72, total = Triple(44.5, 480 to 505, 1.95 to 1.92))
        game("MLB", "BASEBALL", "g4", "Chicago Cubs", "Boston Red Sox", "CHC", "BOS", NOW + 7 * HOUR,
            mlBids = 490 to 495, refMl = 1.98 to 1.90, total = Triple(8.5, 500 to 475, 2.02 to 1.84))
        overUnder("g1", "PASSING_YARDS", "Lamar Jackson 224.5 PASSING_YARDS", 224.5, 455 to 500, 1.87 to 2.02, LineKind.PLAYER_PROP, "Lamar Jackson", "PASSING_YARDS")
        overUnder("g1", "TEAM_TOTAL", "Dallas Cowboys 20.5 TEAM_TOTAL", 20.5, 520 to 470, 2.02 to 1.84, LineKind.TEAM_TOTAL, RefBookMarket.HOME)
        game("MLB", "BASEBALL", "g5", "Baltimore Orioles", "New York Yankees", "BAL", "NYY", NOW + 6 * HOUR,
            mlBids = 560 to 430, refMl = 1.70 to 2.25)
    }

    val settings = ScanSettings(leagues = setOf("NFL", "MLB"), minEvPercent = 0.01)

    fun result(s: ScanSettings = settings): ScanResult {
        val snaps = refs.mapValues { (k, v) -> RefSnapshot(k, v, NOW - 3 * 60_000, 488) }
        val plan = Planner.plan(events, markets, snaps, s, NOW)
        return Pricing.price(plan, books, s, NOW)
    }

    private val sources = listOf(
        SourceReport("pinnacle", "Pinnacle", 2, 0, 5, null),
        SourceReport("polymarket", "Polymarket", 2, 0, 5, null),
        SourceReport("kalshi", "Kalshi", 2, 0, 4, null),
    )

    /** After a scan. [withFair] false = no source matched anything (Novig prices only). */
    fun state(s: ScanSettings = settings, withFair: Boolean = true): UiState {
        val r = if (withFair) result(s) else Pricing.price(Planner.plan(events, markets, emptyMap(), s, NOW), books, s, NOW)
        return UiState(
            settings = s,
            result = r,
            feed = r.feed(s),
            status = ScanStatus(scannedAtMs = NOW - 60_000, sources = if (withFair) sources else emptyList(), booksFetched = 14),
            oddsApiKeys = listOf("1234567890abcdef1234", "abcdefabcdefabcd5678"),
            pinnapiKeys = listOf("trial-key-sample-0001"),
            usage = usage(),
            bets = bets,
            loaded = true,
        )
    }

    /** A ledger mid-month: key 1 of The Odds API partly used, key 2 untouched; a busy scan day. */
    fun usage(now: Long = System.currentTimeMillis()): UsageBook {
        val month = QuotaPolicy.ODDS_API.periodStart(now)
        val day = QuotaPolicy.PINNAPI.periodStart(now)
        return UsageBook(
            mapOf(
                "oddsapi" to ProviderUsage(
                    keys = mapOf(
                        "1234567890abcdef1234" to KeyUsage(periodStart = month, used = 312, remaining = 188, limit = 500, calls = 104, lastCallMs = now - 60_000, lastCost = 3),
                    ),
                    dayStart = day, callsToday = 6,
                ),
                "pinnacle" to ProviderUsage(
                    keys = mapOf("trial-key-sample-0001" to KeyUsage(periodStart = day, used = 37, calls = 37, lastCallMs = now - 60_000, lastCost = 1)),
                    dayStart = day, callsToday = 37,
                ),
                "novig" to ProviderUsage(dayStart = day, callsToday = 142),
                "polymarket" to ProviderUsage(dayStart = day, callsToday = 12),
                "kalshi" to ProviderUsage(dayStart = day, callsToday = 9),
            ),
        )
    }

    /** Launch state: nothing fetched, nothing will be until Scan. */
    fun fresh(s: ScanSettings = settings) = UiState(settings = s, loaded = true, bets = bets)

    /** Mid-scan with results streaming in: the feed so far, fair odds still arriving. */
    fun streaming(s: ScanSettings = settings): UiState {
        val r = result(s).copy(freshSinceMs = NOW - 10_000)
        return state(s).copy(
            result = r,
            feed = r.feed(s),
            status = ScanStatus(scanning = true, progress = ScanProgress("Fair odds 3/5 · Novig prices", 40, 120), scannedAtMs = NOW - 20 * 60_000),
        )
    }

    /** Mid-scan, reading Novig books. */
    fun scanning(s: ScanSettings = settings) = fresh(s).copy(status = ScanStatus(scanning = true, progress = ScanProgress("Novig prices", 9, 24)))

    val bets = listOf(
        TrackedBet("b1", NOW - 26 * HOUR, "NFL", "New York Jets @ Chicago Bears", NOW - 20 * HOUR, "Moneyline", "New York Jets", "m", "o",
            0.42, 0.42, 0.445, 0.0595, 25.0, BetStatus.WON, NOW - 16 * HOUR, closingFair = 0.452),
        TrackedBet("b2", NOW - 30 * HOUR, "MLB", "Houston Astros @ Oakland Athletics", NOW - 24 * HOUR, "Total", "Over 8.5", "m2", "o2",
            0.51, 0.51, 0.527, 0.0333, 18.0, BetStatus.LOST, NOW - 20 * HOUR, closingFair = 0.521),
        TrackedBet("b3", NOW - 2 * HOUR, "NFL", "Baltimore Ravens @ Dallas Cowboys", NOW + 50 * HOUR, "Moneyline", "Dallas Cowboys", "g1-ml", "g1-ml-h",
            0.385, 0.385, 0.398, 0.034, 20.0, closingFair = 0.401),
    )
}
