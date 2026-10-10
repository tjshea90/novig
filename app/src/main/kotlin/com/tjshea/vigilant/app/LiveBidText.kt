package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.livebid.LiveBid
import com.tjshea.vigilant.data.livebid.LiveBidDeskStatus
import com.tjshea.vigilant.data.livebid.LiveBidPresets
import com.tjshea.vigilant.data.livebid.LiveBidReport
import com.tjshea.vigilant.data.livebid.SavedLiveBidPreset
import com.tjshea.vigilant.data.pinnodds.LiveRunnerStatus
import com.tjshea.vigilant.data.scanner.ScanSettings
import java.util.Locale

/** The words of Settings › Live bids and its Diagnostics block (Tj, 2026-10-10; RESEARCH.md §123-§124). */
object LiveBidText {
    const val HINT = "A live bid is a post-only order on a side of one of Novig's live lines, priced under what Pinnacle's live price says the side is worth, resting for seconds at a time. If a taker buys " +
        "it, you have bought at a price better than Pinnacle's, so the bid is positive expected value; the bid pays no fee, and in play Novig credits the maker half of the taker's fee. " +
        "What makes this safe or not is how stale the bid can get: every bid is re-checked twice a second and on every Pinnacle frame, and comes down at once when the score changes, Pinnacle " +
        "flags a danger zone or goes quiet, its price falls toward the bid, the line closes, a feed drops, or anything stops vouching for it. Each order also carries an expiry Novig enforces " +
        "by itself, so a phone that dies cannot leave a bid up. It uses the same Pinnodds socket as Pinnodds live (one connection per account): the Pinnodds key goes in Settings › Pinnodds live."
    const val EVIDENCE = "Evidence so far: replaying 21 games (about 50 minutes) of stored Pinnacle and Novig tapes with Novig's measured 5.3 s in-play order delay, a bid 5% under the fair kept about +4% " +
        "per fill two minutes later (3% kept under +1%). That is a small sample, queue position is not in it, and no live post-only bid has been sent to Novig yet: the app measures how long a bid takes " +
        "to land and to be pulled on your phone, shows it in Diagnostics, and stops itself if a pull is too slow. Watch paper first."
    const val FEED_TITLE = "Live bids"
    const val FEED_SUB = "Judges every live game Pinnacle and Novig both carry and writes down the bids it WOULD post (paper), pulls them the way a real one would, and follows each paper fill. Nothing is sent."
    const val REAL_TITLE = "Bid with real money"
    const val REAL_SUB = "Posts the bids on Novig, sized by the stake rule below and held to the limits below. Starts off."
    const val RESUME = "Resume"
    const val CONFIRM_TITLE = "Bid with real money on live games?"

    fun money(d: Double): String = if (d == Math.floor(d)) "$" + d.toInt() else String.format(Locale.US, "$%.2f", d)
    fun pct(p: Double): String = com.tjshea.vigilant.data.livebid.LiveBidQuality.pct(p)

    /** What one bid costs at full: the Kelly stake for a typical bid (fair 50%, margin as set), shown in the confirmation and under the stake rule. */
    fun typicalStake(s: ScanSettings): Double? {
        val lim = s.liveBidLimits
        val fair = 0.50
        val price = fair / (1.0 + s.liveBidQuality.margin)
        return com.tjshea.vigilant.data.livebid.LiveBidStake.dollars(lim, fair, price, s.bankroll, s.apiMaxStake)
    }

    fun confirm(s: ScanSettings): String {
        val l = s.liveBidLimits
        val typical = typicalStake(s)
        return "With this on, the app posts real bids on Novig's live lines by itself: ${l.stakeMode.label}" + (if (l.stakeMode.kelly != null) " of your ${money(s.bankroll)} bankroll" else "") +
            " a bid" + (typical?.let { " (about ${money(it)} at a ${pct(s.liveBidQuality.margin)} edge)" } ?: "") + ", never under ${money(l.minStake)} or over ${money(l.maxStake)}, at most ${l.maxBids} up at once, " +
            "${money(l.maxPerGame)} a game and ${money(l.maxPerDay)} a day, and it stops for the day if live bids lose ${money(l.haltLoss)}. Each bid is ${pct(s.liveBidQuality.margin)} under Pinnacle's price " +
            "and rests at most ${s.liveBidQuality.ttlSec} s. Pinnacle's price is an estimate of the true chance, so a bid that fills can still lose: it is positive expected value, not a sure thing. " +
            "No post-only bid has been sent to Novig in play yet: how long one takes to land and to be pulled is measured as it goes, and it stops itself if a pull takes longer than " +
            "${s.liveBidQuality.maxCancelSec} s. Start small."
    }

    /** What a built-in preset is for; null for Tj's own. */
    fun why(p: SavedLiveBidPreset): String? = when (p.name) {
        LiveBidPresets.CAREFUL.name ->
            "The safest, and the one to start real money with. A 6% margin, never the best bid on its side, only lines Pinnacle backs with a limit of \$1,000 or more and a margin under 7.5%. Pulled when " +
                "Pinnacle is silent 15 s or its price has sat 30 s, held off 30 s after a score and 15 s after a danger frame, pulled when 2% of edge is left. Stops itself on 3 picked-off fills out of 5, " +
                "or when a pull takes over 10 s. Fewest bids."
        LiveBidPresets.BALANCED.name ->
            "More bids, still positive on the tapes: a 5% margin (the middle of the range where fills times edge were flat on the grid), may be the best bid on a thin line, Pinnacle's limit \$500 or more and margin under 8%, " +
                "a 30 s hold after a score, pulled when 1% is left. Stops itself on 4 picked-off fills out of 6, or when a pull takes over 12 s."
        LiveBidPresets.PAPER_WIDE.name ->
            "For watching, not for money: a 4% margin and bids that rest 60 s kept only about +1% a fill on the tapes, which is inside the noise of a 20-fill sample. Applying it turns real bids off."
        else -> null
    }

    /** The preset in force, as the home list says it. */
    fun preset(s: ScanSettings): String = LiveBidPresets.active(s)?.name ?: (s.liveBidPresetName?.let { "$it (changed)" } ?: "your own rules")

    fun inForce(s: ScanSettings): String {
        val active = LiveBidPresets.active(s)
        return when {
            active != null -> "In force: ${active.name}."
            s.liveBidPresetName != null -> "Last applied: ${s.liveBidPresetName}, changed since (your own settings now)."
            else -> "No preset applied: your own settings."
        }
    }

    /** The line under the switches: where the desk is and what it has done. */
    fun statusLine(d: LiveBidDeskStatus, s: ScanSettings): String {
        if (!s.liveBid) return "Off."
        if (s.liveBidHalted != null) return "Stopped: ${s.liveBidHalted}"
        val mode = if (s.liveBidReal) "REAL" else "paper"
        return "$mode: ${d.active} up (${money(d.restingDollars)} if all filled), today ${d.posted} posted, ${d.fills} filled for ${money(d.paid)}" + (if (d.timing.isNotEmpty()) " · ${d.timing}" else "") +
            (d.standDown?.let { " · stood down: $it" } ?: "") + (d.problem?.let { " · $it" } ?: "")
    }

    /**
     * Why nothing is up, in words (Tj, 2026-10-10: "It isn't posting any live bids at all"): the first thing in the chain that is not there (key, feed, a matched game), else what the judge said
     * no to most. Null when a bid is up or nothing needs saying. [r] is the runner's status, [hasKey] whether a Pinnodds key is saved.
     */
    fun whyNone(s: ScanSettings, d: LiveBidDeskStatus, r: LiveRunnerStatus, hasKey: Boolean): String? {
        if (!s.liveBid || s.liveBidHalted != null || d.active > 0) return null
        if (s.paused) return "Nothing runs while STOP ALL or the pause is on."
        if (!hasKey) return "No Pinnodds key is saved, so there is no Pinnacle price to bid from (Settings › Pinnodds live)."
        if (!r.running) return "The live feed is not running" + (r.problem?.let { ": $it" } ?: " yet (it starts a moment after the switch goes on).")
        if (r.socket != "live") return "The Pinnodds feed is ${r.socket}" + (r.problem?.let { " ($it)" } ?: "") + "."
        if (r.pinnLive == 0) return "Connected, but Pinnacle has no live game on right now."
        if (r.matched == 0) return "Connected: ${r.pinnLive} live Pinnacle games, none of them is also a live game on Novig right now."
        r.bidError?.let { return "The bid judge hit an error and carried on: $it" }
        r.bidGate?.let { return "Games are matched but the bid judge is not running: $it." }
        if (r.bidTargets == 0) return "${r.matched} live games are matched to Novig, but none of their lines can be priced from Pinnacle's main lines yet (${r.watched} Novig markets watched)."
        val q = s.liveBidQuality
        val top = d.skips.entries.sortedByDescending { it.value }.take(3)
        val head = "${r.matched} live games matched to Novig, ${r.bidTargets} lines watched, ${r.bidJudged} judgments made. "
        if (top.isEmpty()) return head + "No look has been judged yet."
        val tennisHint = if (!q.tennis && top.any { it.key == com.tjshea.vigilant.data.livebid.LiveBidSkip.TENNIS_OFF }) " Tennis is off (Tennis switch below); most live games at some hours are tennis." else ""
        return head + "None passed. Most common reasons: " + top.joinToString(" · ") { "${it.key} ×${it.value}" } + "." + tennisHint
    }

    fun diagnostics(s: ScanSettings, d: LiveBidDeskStatus, r: LiveRunnerStatus, bids: List<LiveBid>, running: Boolean): String {
        val o = StringBuilder()
        o.appendLine("LIVE BIDS (RESEARCH.md §123-§124)")
        val l = s.liveBidLimits
        o.appendLine(
            "Switch: ${if (s.liveBid) "ON" else "off"} · ${if (running) "feed running" else "feed not running"} · ${if (s.liveBidReal) "REAL money" else "paper"}" + (s.liveBidHalted?.let { " · HALTED: $it" } ?: "") +
                " · preset ${LiveBidPresets.active(s)?.name ?: (s.liveBidPresetName?.let { "$it (changed)" } ?: "none")}",
        )
        o.appendLine("Rules: ${s.liveBidQuality.summary()}")
        val q = s.liveBidQuality
        o.appendLine(
            "Detail: devig ${q.devig.displayName} · price ${pct(q.minPrice)}-${pct(q.maxPrice)} · fair ${pct(q.minFair)}-${pct(q.maxFair)} · quiet ${q.maxQuietSec}s · fair age ${q.maxFairAgeSec}s · settle ${q.settleSec}s · " +
                "book gap ${pct(q.maxBookGap)} · ttl ${q.ttlSec}s refresh ${q.refreshBeforeSec}s overlap ${q.overlapRepost} · pull below ${pct(q.pullBelowEv)}, on score ${q.pullOnScore}, Novig move ${pct(q.novigMovePull)} · " +
                "cool-off ${q.coolOffSec}s · pick-off stop ${q.pickOffLimit}/${q.pickOffWindow} · max pull ${q.maxCancelSec}s, max place ${q.maxPlaceSec}s · credit counted ${q.countCredit} · both sides ${q.bothSides} · tennis ${q.tennis} · " +
                "leagues ${if (q.onlyLeagues.isEmpty()) "all" else q.onlyLeagues.sorted().joinToString("/")}",
        )
        o.appendLine(
            "Limits: ${l.stakeMode.label}${if (l.stakeMode == com.tjshea.vigilant.data.scanner.AutoBetStake.CUSTOM) " ${money(l.customStake)}" else ""} on a ${money(s.bankroll)} bankroll · stake ${money(l.minStake)}-${money(l.maxStake)} · " +
                "${l.maxBids} bids, ${l.maxBidsPerGame} a game · ${money(l.maxPerGame)} a game · ${money(l.maxPerDay)} a day · halt at ${money(l.haltLoss)} lost · wallet reserve ${money(l.walletReserve)}",
        )
        o.appendLine("Engine: running ${r.running} · lines watched for bids ${r.bidTargets} · judgments made ${r.bidJudged} · socket ${r.socket} · Pinnacle live ${r.pinnLive} · matched ${r.matched} · Novig markets watched ${r.watched}" +
            (r.bidGate?.let { " · JUDGE NOT RUNNING: $it" } ?: "") + (r.bidError?.let { " · JUDGE ERROR: $it" } ?: "") + (r.problem?.let { " · feed problem: $it" } ?: ""))
        LiveBidReport.lines(bids, d).forEach { o.appendLine(it) }
        return o.toString()
    }
}
