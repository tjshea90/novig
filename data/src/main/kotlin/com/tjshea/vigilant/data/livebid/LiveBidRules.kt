package com.tjshea.vigilant.data.livebid

import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.DevigMethod
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * How a live bid is priced, kept fresh and pulled (Tj, 2026-10-10: "Build a live bid feature for live betting on novig. The feature must have strong safeguards in place to make sure the live bids
 * don't get stale and that the live bids are truly EV. Give me a good slate of options for this setting to fine tune it, including presets I can save and manual fields to type my own numbers";
 * RESEARCH.md §123-§124). A live bid is a post-only order on the side of a Novig line that Pinnacle's live price (the Pinnodds socket) says is worth [margin] more than the bid, resting a few
 * seconds at a time, and pulled the moment anything that justified it changes. This is every rule that decides WHAT is bid and WHEN it comes down; how much money is in play is
 * [LiveBidLimits]. A preset sets these and leaves the limits alone, as the auto-bet's presets leave Tj's money alone.
 *
 * Every number has a default that is the Balanced preset, and every one is a margin against being wrong: Pinnacle's devigged price is an ESTIMATE of the true chance, so the bid sits [margin]
 * under it; the price is only believed while it is fresh, settled and from a market Pinnacle itself stands behind; and a bid never outlives the reasons it was posted.
 */
@Serializable
data class LiveBidQuality(
    // ---- the price ------------------------------------------------------------------------------------------------------------------------------------------------------------
    /** How far under Pinnacle's fair a bid sits: the EV it has at the moment it is posted (maker pays no fee). Tapes (RESEARCH.md §124): 3% kept about +0.6% a fill, 5% about +4%. */
    val margin: Double = 0.05,
    /** How Pinnacle's margin is taken out of its price. [DevigMethod.WORST_CASE] gives the lowest fair of the methods, so the bid is never priced off a generous one. */
    val devig: DevigMethod = DevigMethod.WORST_CASE,
    /** Bid prices outside this window are left alone (a long shot or a near certainty is where the devig is least reliable and a fill means least). */
    val minPrice: Double = 0.10,
    val maxPrice: Double = 0.90,
    /** Pinnacle fair prices outside this are left alone: the tails are where its devig is least reliable and a nearly decided game has nothing to find. */
    val minFair: Double = 0.12,
    val maxFair: Double = 0.88,
    /** Never post a bid that would be the best bid on its side (above every bid already resting): on the tapes, bids that led the book were picked off most (RESEARCH.md §124). */
    val neverLead: Boolean = false,
    /** Allow a bid on both outcomes of one market at once. Off: one side at a time (the other is left alone while one is up). */
    val bothSides: Boolean = false,
    // ---- which games and lines ------------------------------------------------------------------------------------------------------------------------------------------------
    val moneyline: Boolean = true,
    val spread: Boolean = true,
    val total: Boolean = true,
    /** Tennis moves on every point and its feed carries no clock; off by default (Tj's 37 real live orders were 33 tennis, 0 filled). */
    val tennis: Boolean = false,
    /** Only these leagues (Novig's names, "NCAAF", "NBA"); empty = every league Pinnacle and Novig both carry. */
    val onlyLeagues: Set<String> = emptySet(),
    // ---- how fresh the fair must be -------------------------------------------------------------------------------------------------------------------------------------------
    /** Pinnacle has said nothing about this matchup for this long: its price is not known to be current (no bid, and a resting one is pulled). 0 = off. */
    val maxQuietSec: Int = 20,
    /** The line's price last changed longer ago than this: too old to bid from (0 = no limit; a calm line can sit unchanged for minutes, so this is a strict choice). */
    val maxFairAgeSec: Int = 0,
    /** Pinnacle's price must have sat unchanged this long before a bid goes up (a spike that reverts inside it is not a price). */
    val settleSec: Int = 3,
    /** Pinnacle's own margin on the line: a wider one means a thin or uncertain market where the devig guesses more. Live median is 6.2%, 90% of lines are under 7.7%. */
    val maxOverround: Double = 0.08,
    /** Pinnacle's own limit on the line, dollars: a market it caps low is one it is unsure of (basketball preseason limits were $100-500, hockey and football $3,000). 0 = off. */
    val minPinnLimit: Double = 500.0,
    /** Pinnacle's fair and Novig's middle for the same side must agree within this (probability, 0.10 = 10 points): a bigger gap is a wrong match or a stale price, not an edge. 0 = off. */
    val maxBookGap: Double = 0.10,
    // ---- how long a bid lives -------------------------------------------------------------------------------------------------------------------------------------------------
    /** Seconds a bid may rest before Novig removes it by itself: the dead-man switch if the phone, the feed or a cancel fails. */
    val ttlSec: Int = 30,
    /** A bid with this many seconds left is replaced by a fresh one (if still wanted) so a good bid is always up. */
    val refreshBeforeSec: Int = 8,
    /** The replacement goes up before the old bid ends (a live order takes seconds to land, so waiting leaves a gap); for a moment both can fill. Off: wait for the old one to end. */
    val overlapRepost: Boolean = true,
    // ---- when a bid comes down ------------------------------------------------------------------------------------------------------------------------------------------------
    /** Pull a resting bid when Pinnacle's fair no longer leaves it at least this much EV. */
    val pullBelowEv: Double = 0.01,
    /** Pull when the game's score changes (Pinnacle's score frame arrives a median 2 s before its reprice). */
    val pullOnScore: Boolean = true,
    /** After a score, no bid goes up for this long (Novig re-quotes in a median 3.5-4.3 s; its 90th percentile is longer). 0 = off. */
    val scoreHoldSec: Int = 30,
    /** After a Pinnacle danger-zone frame (a goal threat, a break point, an imminent suspension), no bid for this long and a resting one is pulled. 0 = off. */
    val dangerHoldSec: Int = 10,
    /** Pull when Novig's own middle for the bid's side falls this far (probability) from where it was when the bid went up: the market is moving against it. 0 = off. */
    val novigMovePull: Double = 0.04,
    /** After a bid fills, no new bid on that side for this long (the fill may be the first of a move). */
    val coolOffSec: Int = 30,
    // ---- self-checks that stop the whole feature ------------------------------------------------------------------------------------------------------------------------------
    /** Stop everything when [pickOffLimit] of the last [pickOffWindow] judged fills were picked off (Pinnacle's fair 30 s after the fill was under the price paid). */
    val pickOffWindow: Int = 6,
    val pickOffLimit: Int = 4,
    /** Stop everything when pulling a bid is measured to take longer than this at the 90th percentile of the last ten (0 = off): a bid that cannot be pulled fast is not safe to rest. */
    val maxCancelSec: Int = 12,
    /** The same for a bid reaching Novig's book. 0 = off. */
    val maxPlaceSec: Int = 15,
    /** Count Novig's maker credit (50% of the taker's fee, paid in cash within 7 days, in play only) in the EV. Off: the gate and the numbers shown ignore it; it is a bonus. */
    val countCredit: Boolean = false,
) {
    /** These rules in one line, for Settings and the bid's record. */
    fun summary(): String = buildList {
        add("${pct(margin)} under Pinnacle's fair")
        add("fair ${pct(minFair)}-${pct(maxFair)}")
        add(buildList { if (moneyline) add("moneyline"); if (spread) add("spread"); if (total) add("total") }.joinToString("/").ifEmpty { "no line kinds" } + if (tennis) " incl. tennis" else "")
        if (onlyLeagues.isNotEmpty()) add(onlyLeagues.sorted().joinToString(", "))
        add("bid rests ${ttlSec}s")
        add("pulled if EV < ${pct(pullBelowEv)}")
        if (scoreHoldSec > 0) add("${scoreHoldSec}s hold after a score")
        if (dangerHoldSec > 0) add("${dangerHoldSec}s after a danger frame")
        if (maxQuietSec > 0) add("Pinnacle silent > ${maxQuietSec}s = pull")
        add("overround ≤ ${pct(maxOverround)}")
        if (minPinnLimit > 0) add("Pinnacle limit ≥ $${minPinnLimit.toLong()}")
        if (neverLead) add("never leads the book")
    }.joinToString(" · ")

    companion object {
        /** "5%", "2.5%": a fraction as the percent Settings shows. */
        fun pct(v: Double): String = Math.round(v * 1000).let { t -> if (t % 10 == 0L) "${t / 10}%" else String.format(Locale.US, "%.1f%%", t / 10.0) }
    }
}

/**
 * The money behind live bids: Tj's own limits, which a preset never changes. [stakeMode] sizes one bid (⅛ Kelly of [ScanSettings.bankroll] by default, on the bid's own price and edge:
 * `fraction × (fair − price) / (1 − price) × bankroll`, makers pay no fee), never under [minStake] (when Kelly asks for less the bid is raised to it) and never over [maxStake]. The rest cap what
 * can be at risk at once and in a day, and stop the feature after a bad day.
 */
@Serializable
data class LiveBidLimits(
    val stakeMode: AutoBetStake = AutoBetStake.EIGHTH_KELLY,
    /** The amount [AutoBetStake.CUSTOM] bids. */
    val customStake: Double = 2.0,
    /** A Kelly stake under this is raised to it (Novig refuses orders too small to trade; this is also the smallest risk worth taking). */
    val minStake: Double = 1.0,
    /** The most one bid may cost if it fills (and never over Settings' per-bet maximum for the API). */
    val maxStake: Double = 5.0,
    /** Bids up at once, in all games. */
    val maxBids: Int = 8,
    val maxBidsPerGame: Int = 3,
    /** The most at risk in one game: what its bids cost if they fill, plus what its bids already filled today. */
    val maxPerGame: Double = 10.0,
    /** The most live bids may have filled in a day (fills plus the bids up). */
    val maxPerDay: Double = 40.0,
    /** A day's settled loss on live bids that halts the feature until Tj resumes it. */
    val haltLoss: Double = 15.0,
    /** Dollars of the wallet that bids never touch (every resting bid is counted against the wallet: Novig holds nothing back for them, NOVIG_API.md §17). */
    val walletReserve: Double = 5.0,
    /**
     * Fill the wallet (Tj, 2026-10-10: "make sure the auto bid feature fills up with bids up to the wallet balance. I want as many bids up as possible as long as each bid is clearly positive EV"):
     * the counts and the per-game and per-day dollars above stop being the ceiling and the wallet is ([effective]). Every bid still has to pass the quality rules, the stake rule and the wallet
     * (reserve kept, every resting bid counted); [haltLoss] still stops the day. Off: the numbers above are the limits.
     */
    val fillWallet: Boolean = false,
) {
    /**
     * The limits the desk enforces right now. With [fillWallet] on: bids up at once and in a game are lifted to [WALLET_MAX_BIDS] / [WALLET_MAX_PER_GAME_BIDS] (the wallet check, which counts every
     * resting bid, is the real ceiling; the count only guards against a runaway loop), a game may hold [WALLET_GAME_SHARE] of the wallet and a day twice the wallet (fills return money to the
     * wallet only when they settle). Never lower than what Tj set.
     */
    fun effective(wallet: Double?): LiveBidLimits {
        if (!fillWallet || wallet == null || wallet <= 0.0) return this
        return copy(
            maxBids = maxOf(maxBids, WALLET_MAX_BIDS), maxBidsPerGame = maxOf(maxBidsPerGame, WALLET_MAX_PER_GAME_BIDS),
            maxPerGame = maxOf(maxPerGame, wallet * WALLET_GAME_SHARE), maxPerDay = maxOf(maxPerDay, wallet * 2.0),
        )
    }

    companion object {
        const val WALLET_MAX_BIDS = 60
        const val WALLET_MAX_PER_GAME_BIDS = 6
        const val WALLET_GAME_SHARE = 0.25
    }
}

/** One of the presets, built in or Tj's own. [paperOnly]: the rules are too thin to risk money on; applying it turns real bets off. */
@Serializable
data class SavedLiveBidPreset(val name: String, val quality: LiveBidQuality, val paperOnly: Boolean = false)

/** The built-in live bid presets and the saving and applying of Tj's own (the same shape as [com.tjshea.vigilant.data.scanner.Presets]). */
object LiveBidPresets {

    /**
     * The safest. A 6% margin (the tapes' per-fill EV rose with the margin and fills hardly fell until 8%: RESEARCH.md §124), never the best bid on its side, fair price 15-85%, Pinnacle's
     * margin at most 7.5% and its limit at least $1,000 (a soft quote is its own noise), Pinnacle silent for 15 s or its price unchanged for 30 s pulls, a 30 s hold after a score and 15 s after
     * a danger frame, pulled at 2% left, a pick-off stop at 3 of 5, and a pull that takes longer than 10 s stops it.
     */
    val CAREFUL = SavedLiveBidPreset(
        "Careful",
        LiveBidQuality(
            margin = 0.06, neverLead = true, minFair = 0.15, maxFair = 0.85, minPrice = 0.12, maxPrice = 0.85,
            maxQuietSec = 15, maxFairAgeSec = 30, settleSec = 3, maxOverround = 0.075, minPinnLimit = 1000.0, maxBookGap = 0.08,
            ttlSec = 30, refreshBeforeSec = 8, pullBelowEv = 0.02, scoreHoldSec = 30, dangerHoldSec = 15, novigMovePull = 0.03, coolOffSec = 60,
            pickOffWindow = 5, pickOffLimit = 3, maxCancelSec = 10, maxPlaceSec = 12,
        ),
    )

    /**
     * More bids, still positive on the tapes: a 5% margin (the flat part of fills-times-EV on the grid runs 3-6%: the middle of it), may lead a thin book, fair price 12-88%, Pinnacle's margin at most 8% and limit at least $500, a
     * 30 s hold after a score, pulled at 1% left.
     */
    val BALANCED = SavedLiveBidPreset("Balanced", LiveBidQuality())

    /**
     * For watching, not for money: a 4% margin and 60 s bids, which kept about +1% a fill on the tapes (the mean, credit not counted), within the noise of a 20-fill sample. Applying it
     * turns real bets off.
     */
    val PAPER_WIDE = SavedLiveBidPreset(
        "Paper study (wide)",
        LiveBidQuality(margin = 0.04, ttlSec = 60, refreshBeforeSec = 10, scoreHoldSec = 15, maxOverround = 0.09, minPinnLimit = 250.0, maxBookGap = 0.12, coolOffSec = 15, pickOffWindow = 8, pickOffLimit = 6),
        paperOnly = true,
    )

    /**
     * More fills (Tj, 2026-10-10: "very few of my live bids are actually filled ... I want them to fill"). Every rule that kept a bid from resting is loosened by one step, and the edge is cut to 3%
     * (the grid's fills a bid-hour fell from about 2 at 3% to about 0 at 8%), which Novig's maker credit adds to in play (about 0.4-1.5% by league, counted here). The bid may lead the book and
     * both sides of a market may be up, so a bid sits at the front where the money is, and the settle and hold times are halved so a fresh Pinnacle price is bid on sooner. What does NOT loosen:
     * the price must still be Pinnacle's main line with a limit of $250 or more and a margin under 9%, a bid is pulled the instant the score, a danger frame, Pinnacle's silence, the edge
     * (under 0.5%) or Novig's middle says so, and the pick-off stop stays at 4 of 6. A 3% edge is thin: the stake stays tiny until the Diagnostics show fills that held.
     */
    val FILL = SavedLiveBidPreset(
        "More fills",
        LiveBidQuality(
            margin = 0.03, minPrice = 0.06, maxPrice = 0.94, minFair = 0.08, maxFair = 0.92, neverLead = false, bothSides = true,
            maxQuietSec = 20, settleSec = 2, maxOverround = 0.09, minPinnLimit = 250.0, maxBookGap = 0.12,
            ttlSec = 20, refreshBeforeSec = 6, pullBelowEv = 0.005, scoreHoldSec = 15, dangerHoldSec = 6, novigMovePull = 0.05, coolOffSec = 15,
            pickOffWindow = 6, pickOffLimit = 4, maxCancelSec = 12, maxPlaceSec = 15, countCredit = true,
        ),
    )

    val BUILT_IN: List<SavedLiveBidPreset> = listOf(CAREFUL, BALANCED, FILL, PAPER_WIDE)

    /** Every preset Tj can pick: the built-in ones, then his own. */
    fun all(s: ScanSettings): List<SavedLiveBidPreset> = BUILT_IN + s.liveBidPresets

    fun builtIn(name: String): Boolean = BUILT_IN.any { it.name.equals(name.trim(), ignoreCase = true) }

    /** [s] with [preset] applied: its rules in force, real bets off when it is paper-only. The limits are Tj's and stay. */
    fun apply(s: ScanSettings, preset: SavedLiveBidPreset): ScanSettings =
        s.copy(liveBidQuality = preset.quality, liveBidPresetName = preset.name, liveBidReal = s.liveBidReal && !preset.paperOnly)

    /** Whether [s] still has [preset]'s rules exactly. */
    fun matches(s: ScanSettings, preset: SavedLiveBidPreset): Boolean = s.liveBidQuality == preset.quality

    /** The preset [s] was set from, while its rules are still in force; null when none was, or something changed since. */
    fun active(s: ScanSettings): SavedLiveBidPreset? = s.liveBidPresetName?.let { n -> all(s).firstOrNull { it.name == n } }?.takeIf { matches(s, it) }

    /** [s] with the current rules saved as Tj's own preset [name] (replacing his own of that name). Null when the name is empty or a built-in's. */
    fun save(s: ScanSettings, name: String): ScanSettings? {
        val n = name.trim().take(MAX_NAME)
        if (n.isEmpty() || builtIn(n)) return null
        return s.copy(liveBidPresets = s.liveBidPresets.filterNot { it.name.equals(n, ignoreCase = true) } + SavedLiveBidPreset(n, s.liveBidQuality), liveBidPresetName = n)
    }

    /** [s] without Tj's own preset [name] (a built-in can't be deleted). */
    fun delete(s: ScanSettings, name: String): ScanSettings =
        if (builtIn(name)) s else s.copy(liveBidPresets = s.liveBidPresets.filterNot { it.name.equals(name, ignoreCase = true) }, liveBidPresetName = s.liveBidPresetName?.takeUnless { it.equals(name, ignoreCase = true) })

    const val MAX_NAME = 40
}
