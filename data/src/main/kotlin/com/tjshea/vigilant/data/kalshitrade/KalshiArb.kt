package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.LineKey
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.OutcomeTarget
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.engine.EvQuote
import java.util.Locale

/** What an arbitrage leg is made of: one side bought at one venue. [price] and [fee] are per $1 of payout. */
data class ArbLeg(
    val venue: String,
    val selection: String,
    val price: Double,
    val fee: Double,
    /** Dollars of payout offered at that price (a Kalshi contract pays $1; Novig's are 1c each). */
    val payoutAvailable: Double,
    val readAtMs: Long?,
) {
    val cost: Double get() = price + fee
}

/**
 * A sure profit between Novig and Kalshi (KV5, Tj 2026-10-20: "find no risk bets between novig and kalshi which, due to the differences in odds, if I bet on both sides of a bet
 * on novig and kalshi, I'm guaranteed to profit"): the same line, opposite sides, one bought at each venue, the two together costing under $1 of payout after both fees.
 * Exactly one side wins, so $1 comes back whichever it is.
 */
data class ArbOpportunity(
    val event: NovigEvent,
    val market: NovigMarket,
    val label: String,
    val novig: ArbLeg,
    val kalshi: ArbLeg,
    /** The Novig outcome bought, and the Kalshi ticker and side (`yes`/`no`) bought. */
    val novigOutcomeId: String,
    val kalshiTicker: String,
    val kalshiSide: String,
    val kind: LineKind,
    /** Whether the two venues are certain to settle this line the same way (game lines); props and others can differ in the rules, said on the card. */
    val safe: Boolean,
    /** The Novig row this was found from, for the bet sheet's pieces (the market, outcome ids, the event). */
    val novigRow: Opportunity? = null,
) {
    /** What $1 of payout costs across both legs, fees included. */
    val cost: Double get() = novig.cost + kalshi.cost

    /** Profit per $1 of payout, and as a share of the money put up. */
    val profitPerDollar: Double get() = 1.0 - cost
    val roi: Double get() = if (cost > 0.0) profitPerDollar / cost else 0.0

    /** The most payout both legs can take at these prices. */
    val maxPayout: Double get() = minOf(novig.payoutAvailable, kalshi.payoutAvailable)

    val key: String get() = "${market.marketId}|${novigOutcomeId}|$kalshiTicker|$kalshiSide"
}

/** The arbitrage finder's knobs (all stored in the Kalshi settings). */
data class ArbSettings(
    /** The least profit per $1 of payout after fees; the finder lists nothing under it (0.5% by default: fees are in it, so this is real money). */
    val minProfit: Double = 0.005,
    /** Props, first halves and other lines whose rules can differ between the venues. Off: game lines only. */
    val includeRisky: Boolean = false,
    /** Both prices must have been read within this long. */
    val maxAgeMs: Long = 90_000L,
    /** The least payout (dollars) both legs can fill, so a one-contract crumb is not listed. */
    val minPayout: Double = 2.0,
)

object KalshiArb {
    /**
     * Every sure profit between the Novig prices in [novig] and Kalshi's lines in [kalshi] (the venue read's snapshots), best first. The Novig market is read against Kalshi's own
     * event (the scan's planner: teams, start, home or away, the line and the side of each outcome), so a line is only paired when the planner says it is the same line.
     */
    fun find(novig: ScanResult, kalshi: Collection<RefSnapshot>, settings: ArbSettings, now: Long): List<ArbOpportunity> {
        if (kalshi.isEmpty()) return emptyList()
        val rows = (novig.opportunities + novig.games.flatMap { it.outcomes }).distinctBy { it.key }.filter { it.quote != null && it.ladder.isNotEmpty() && !it.isLive }
        if (rows.isEmpty()) return emptyList()
        val events = rows.map { it.event }.distinctBy { it.eventId }
        val matches = Planner.matchEvents(events, kalshi).filter { it.refEvent != null }.associateBy { it.event.eventId }
        val byMarket = rows.groupBy { it.market.marketId }
        val out = ArrayList<ArbOpportunity>()
        for ((_, group) in byMarket) {
            val first = group.first()
            val match = matches[first.event.eventId] ?: continue
            val ref = match.refEvent ?: continue
            val planned = Planner.planOne(match, first.market) ?: continue
            val key = planned.lineKey ?: continue
            val kalshiMarket = ref.markets.firstOrNull { key.matches(it) } ?: continue
            if (!settings.includeRisky && !safe(key, first.market)) continue
            val kalshiAge = kalshiMarket.lastUpdateMs
            if (kalshiAge == null || now - kalshiAge > settings.maxAgeMs) continue
            for (pa in planned.outcomes) {
                val row = group.firstOrNull { it.outcome.outcomeId == pa.outcome.outcomeId } ?: continue
                val q = row.quote ?: continue
                val side = (pa.target as? OutcomeTarget.Is)?.side ?: continue
                val opposite = opposite(side) ?: continue
                val kq = kalshiMarket.quotes.firstOrNull { it.side == opposite } ?: continue
                val id = kq.bookOutcomeId ?: continue
                val ticker = id.substringBeforeLast('|', "")
                val kSide = id.substringAfterLast('|', "")
                if (ticker.isEmpty() || (kSide != "yes" && kSide != "no")) continue
                val kPrice = 1.0 / kq.decimalOdds
                if (kPrice <= 0.0 || kPrice >= 1.0) continue
                val kSize = kq.size ?: continue
                val novigAge = row.bookFetchedAtMs
                if (novigAge == null || now - novigAge > settings.maxAgeMs) continue
                val level = row.ladder.first()
                val leg1 = ArbLeg("Novig", row.selection, q.price, q.fee, level.contracts * 0.01, novigAge)
                val leg2 = ArbLeg("Kalshi", kalshiSelection(planned, ref.home, ref.away, opposite, kalshiMarket.line, kalshiMarket.kind, kalshiMarket.subject, kalshiMarket.stat, row), kPrice, KalshiFees.takerPerContract(kPrice), kSize, kalshiAge)
                val arb = ArbOpportunity(row.event, row.market, row.marketLabel, leg1, leg2, row.outcome.outcomeId, ticker, kSide, row.kind, safe(key, first.market), row)
                if (arb.profitPerDollar >= settings.minProfit - 1e-12 && arb.maxPayout >= settings.minPayout - 1e-9) out += arb
            }
        }
        return out.sortedByDescending { it.profitPerDollar }
    }

    /** Game lines settle the same on both venues (the winner, the full-game spread and total, a team's total); anything else carries different rules (a player who does not play, a half, a set). */
    fun safe(key: LineKey, market: NovigMarket): Boolean = when (key.kind) {
        LineKind.MONEYLINE, LineKind.SPREAD, LineKind.TOTAL, LineKind.TEAM_TOTAL -> key.period == 0 && market.marketType in SAFE_TYPES
        LineKind.PLAYER_PROP -> false
    }

    private val SAFE_TYPES = setOf("MONEY", "SPREAD", "TOTAL", "TEAM_TOTAL")

    private fun opposite(side: Side): Side? = when (side) {
        Side.HOME -> Side.AWAY
        Side.AWAY -> Side.HOME
        Side.OVER -> Side.UNDER
        Side.UNDER -> Side.OVER
        else -> null
    }

    /** The Kalshi side in the same words as the Novig row's: "Under 51.5", "Cowboys +3.5" (the other team). */
    private fun kalshiSelection(planned: com.tjshea.vigilant.data.scanner.PlannedMarket, home: String, away: String, side: Side, line: Double?, kind: LineKind, subject: String?, stat: String?, row: Opportunity): String {
        // The planner's selection text for the opposite outcome of the same market is exactly what the other leg is called.
        val other = planned.outcomes.firstOrNull { (it.target as? OutcomeTarget.Is)?.side == side }
        return other?.selection ?: "${side.name.lowercase().replaceFirstChar { it.uppercase() }}${line?.let { " " + String.format(Locale.US, "%s", it) } ?: ""}"
    }
}
