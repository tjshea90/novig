package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.livebid.LiveBidHeld
import com.tjshea.vigilant.data.livebid.LiveBidJudge
import com.tjshea.vigilant.data.livebid.LiveBidVerdict
import com.tjshea.vigilant.data.livebid.LiveBidWant
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.engine.PriceGrid
import java.util.Locale

/** The part of the live bid desk Live light's bids talk to: say a bid is justified, vouch for one that is up, take one down. The desk owns the orders, the limits and the stops. */
interface LightBidDesk {
    fun want(w: LiveBidWant)
    fun keepAlive(outcomeId: String, nowMs: Long)
    fun noWant(outcomeId: String, reason: String)
    fun pull(outcomeId: String, reason: String)
    fun noteFair(outcomeId: String, fair: Double, atMs: Long)
    fun held(outcomeId: String): LiveBidHeld?
}

/**
 * How Live light bids (Tj, 2026-10-14: "bids that are very favorable to me based on the actual score/live odds"). A bid goes up only on a side the score and clock call decided ([minFair]) at a price [margin]
 * under that fair, inside a price window, and under what Novig already offers (a bid that would take is the taker's business, not a bid's). It stays while the model's fair keeps at least [keepEv] over its price,
 * and comes down the moment it does not, or the game is no longer being judged (the desk pulls any bid nobody vouched for in 3.5 s).
 */
data class LiveLightBidRules(
    val margin: Double = 0.07,
    val minFair: Double = 0.85,
    val minPrice: Double = 0.30,
    val maxPrice: Double = 0.95,
    val keepEv: Double = 0.03,
    /** The middle tier (Tj, 2026-10-20, CH42: bids on obvious +EV totals): a side the model rates at least [midFair] but under [minFair] is bid [midMargin] under its fair, a wider cushion for a less certain call. */
    val midFair: Double = 0.65,
    val midMargin: Double = 0.15,
)

object LiveLightBids {
    /** Post a bid at the price that keeps [LiveLightBidRules.margin] under [sf]'s fair, or say why not (fixed words, counted). */
    fun judge(sf: LiveLightScan.SideFair, rules: LiveLightBidRules): LiveBidVerdict {
        if (sf.fair < rules.midFair) return LiveBidVerdict.Skip("not decided enough to bid")
        val margin = if (sf.fair >= rules.minFair) rules.margin else maxOf(rules.margin, rules.midMargin)
        val price = PriceGrid.floor(sf.fair / (1.0 + margin)) ?: return LiveBidVerdict.Skip("price outside the window")
        if (price < rules.minPrice - 1e-9 || price > rules.maxPrice + 1e-9) return LiveBidVerdict.Skip("price outside the window")
        // Post-only: a bid at or over what Novig offers would take and is refused whole.
        if (!sf.ask.isNaN() && price >= sf.ask - com.tjshea.vigilant.data.novig.trading.maker.MakerQuote.step(price) + 1e-9) return LiveBidVerdict.Skip("Novig already offers it near that price (a bet, not a bid)")
        val bestBid = sf.book?.bestBid(sf.outcomeId)?.price
        val leads = bestBid == null || price > bestBid + 1e-9
        val mid = if (bestBid != null && !sf.ask.isNaN()) (bestBid + sf.ask) / 2.0 else null
        return LiveBidVerdict.Post(price, sf.fair, sf.fair / price - 1.0, LiveBidJudge.credit(sf.line.fee, price), bestBid, sf.ask.takeUnless { it.isNaN() }, mid, leads, 0.0, null)
    }

    /** Whether a bid already up at [held]'s price may stay: the model's fair must still beat it by [LiveLightBidRules.keepEv]. */
    fun keep(sf: LiveLightScan.SideFair?, held: LiveBidHeld, rules: LiveLightBidRules): String? {
        if (sf == null) return "the game is no longer judged"
        return if (sf.fair / held.price - 1.0 < rules.keepEv) "the model's fair (${"%.1f".format(Locale.US, sf.fair * 100)}%) no longer clears the bid by ${(rules.keepEv * 100).toInt()}%" else null
    }

    /** The desk's record of a bid: the same names the bets use. */
    fun want(ev: NovigEvent, sf: LiveLightScan.SideFair, post: LiveBidVerdict.Post, score: String?, clock: String?, nowMs: Long): LiveBidWant {
        val label = if (sf.line.kind == com.tjshea.vigilant.data.novig.burst.LadderKind.TOTAL) "Total" else if (sf.line.threshold == 0.0) "Moneyline" else "Spread"
        return LiveBidWant(
            atMs = nowMs, outcomeId = sf.outcomeId, marketId = sf.line.marketId, eventId = ev.eventId, pinnEventId = 0L, league = ev.league, eventName = ev.description, startsTs = ev.startsTs,
            marketLabel = label, selection = "${sf.line.label} ${sf.side.lowercase()}", verdict = post, fee = sf.line.fee, score = score, clock = clock,
        )
    }
}
