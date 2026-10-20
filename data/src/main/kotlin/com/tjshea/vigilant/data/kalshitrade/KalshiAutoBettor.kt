package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.novig.trading.AutoBet
import com.tjshea.vigilant.data.novig.trading.BetLimits
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.PlaceResult
import com.tjshea.vigilant.data.reference.PlayerOut
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ProfitProfile
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SharpMode
import com.tjshea.vigilant.data.scanner.TrapGuard
import com.tjshea.vigilant.data.tracker.AtBet
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.GameExposure
import com.tjshea.vigilant.data.tracker.GameRef
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.data.tracker.FairBasis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.Locale

/** A scan's bet as an order target at Kalshi (the Novig app's `ApiBetTargets.of`, with Kalshi as the book). */
object KalshiBetTargets {
    fun of(o: Opportunity): BetTarget? {
        val fair = o.fairProbability ?: return null
        return BetTarget(
            market = o.market, outcomeId = o.outcome.outcomeId, league = o.league.displayName, eventName = o.event.description, startsTs = o.event.startsTs,
            marketLabel = o.marketLabel, selection = o.selection, fair = fair, fairAsOfMs = o.fairAsOfMs, source = BetTracker.SOURCE_VIGILANT, placedKey = o.key,
            book = KalshiBetPlacer.BOOK, basis = FairBasis.of(o), live = o.isLive, listedPrice = o.quote?.price, sharpAgainst = o.agreement.sharpAgainst,
        )
    }
}

/** What one auto-bet pass did. */
data class KalshiAutoReport(
    val looked: Int = 0,
    val passed: Int = 0,
    val placed: List<TrackedBet> = emptyList(),
    val skipped: Map<String, Int> = emptyMap(),
    val stopped: String? = null,
    val walletEmpty: Boolean = false,
    /** Orders that would have gone out, in paper mode. */
    val paper: List<TrackedBet> = emptyList(),
)

/**
 * The auto-bet on Kalshi (KV3b): the Novig app's rules ([AutoBet.rules], [AutoBet.judgeVigilant], [AutoBet.scanStake], the trap guard, the injury report, the star minimum, the sharp rule,
 * the per-bet, per-day and per-game limits) run over the Kalshi scan's result and place each bet that passes through [KalshiBetPlacer]. Pregame only. `paper` records the bets that would be
 * placed (at the price now) in [paperTracker] and sends nothing. Everything outside the pass (the wallet read, the injury and first-listed maps, the record of what was decided, a halt) is
 * handed in, so the pass is the same code for the phone and the GitHub engine.
 */
class KalshiAutoBettor(
    private val placer: KalshiBetPlacer,
    private val tracker: BetTracker,
    private val paperTracker: BetTracker?,
    private val wallet: suspend () -> Double?,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Why a bet's player cannot be bet on (an injury report that rules him out), or null. */
    private val unavailable: (Opportunity) -> String? = { null },
    /** When a bet was first listed, by key, for the trap guard's second rule. */
    private val firstListed: (Opportunity) -> Long? = { null },
    /** The bet as decided ([AtBet]), kept on the bet once it fills. */
    private val atBet: (Opportunity, Double, Long) -> AtBet? = { _, _, _ -> null },
    private val restingBids: suspend () -> List<GameExposure.Item> = { emptyList() },
) {
    private val cooldown = HashMap<String, Long>()
    private var tooSmallBelow = 0.0

    /** Runs one pass over [result]. [paper]: record, do not order. */
    suspend fun run(settings: ScanSettings, result: ScanResult, paper: Boolean): KalshiAutoReport {
        val now = clock()
        val rules = AutoBet.rules(settings)
        if (!settings.autoBet || settings.killed) return KalshiAutoReport()
        if (result.partial) return KalshiAutoReport()
        cooldown.entries.removeAll { it.value <= now }
        val skipped = LinkedHashMap<String, Int>()
        fun skip(reason: String) { skipped.merge(reason, 1, Int::plus) }
        val candidates = result.opportunities.filter { it.league.novigName in settings.leagues && it.quote != null && it.fairProbability != null }
        val profile = if (settings.gates.profitProven) ProfitProfile.shipped else null
        val ordered = (if (rules.byStars) candidates.sortedWith(compareByDescending<Opportunity> { it.rating?.stars ?: 0.0 }.thenByDescending { it.evPercent ?: 0.0 }) else candidates.sortedByDescending { it.evPercent ?: 0.0 })
        val passing = ArrayList<Opportunity>()
        for (o in ordered) {
            when {
                o.isLive || o.event.startsTs - now < MIN_LEAD_MS -> skip("not pregame (live betting is by hand only)")
                (cooldown[o.key] ?: 0L) > now -> skip("tried a moment ago")
                else -> reason(o, rules, settings, now)?.let(::skip) ?: passing.add(o)
            }
        }
        if (candidates.isEmpty()) return KalshiAutoReport()
        if (passing.isEmpty()) return KalshiAutoReport(looked = candidates.size, skipped = skipped)

        val balance0 = try {
            wallet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return KalshiAutoReport(looked = candidates.size, passed = passing.size, skipped = skipped, stopped = "Couldn't read the Kalshi balance: ${e.message ?: e.javaClass.simpleName}")
        }
        // Paper mode needs no money; a real pass needs the balance.
        var balance = balance0 ?: if (paper) settings.bankroll else return KalshiAutoReport(looked = candidates.size, passed = passing.size, skipped = skipped, stopped = "Kalshi isn't set up (Settings › Kalshi › key)")
        if (!paper && balance < AutoBet.SLEEP_BELOW) {
            return KalshiAutoReport(looked = candidates.size, passed = passing.size, skipped = skipped, walletEmpty = true, stopped = "the Kalshi balance is ${money(balance)}, under ${money(AutoBet.SLEEP_BELOW)}")
        }
        val all = tracker.all()
        val openMarkets = (all + (if (paper) paperTracker?.all().orEmpty() else emptyList())).filter { it.status == BetStatus.PENDING && it.marketId.isNotBlank() }.mapTo(HashSet()) { it.marketId }
        val limits = limitsOf(rules, settings)
        val exposure = if (settings.apiMaxPerGame > 0.0) GameExposure.items(all) + restingBids() else emptyList()
        val placed = ArrayList<TrackedBet>()
        val papered = ArrayList<TrackedBet>()
        var stopped: String? = null
        var walletEmpty = false
        for (o in passing) {
            currentCoroutineContext().ensureActive()
            if (settings.killed) { stopped = "the STOP button was pressed while this pass ran"; break }
            if (placed.size + papered.size >= AutoBet.MAX_PER_CYCLE) { stopped = "placed ${AutoBet.MAX_PER_CYCLE} this cycle (the best edges first); the rest wait for the next"; break }
            val q = o.quote ?: continue
            val stake = when (val s = AutoBet.scanStake(rules, q, settings.bankroll, balance, hoursToStart(o.event.startsTs, now))) {
                is AutoBet.Stake.WalletEmpty -> { walletEmpty = true; stopped = "the Kalshi balance is ${money(balance)}, under a cent"; break }
                is AutoBet.Stake.Skip -> { skip(s.reason); continue }
                is AutoBet.Stake.Amount -> s.dollars
            }
            if (stake <= tooSmallBelow + 1e-9) { skip("Kalshi refused an order of ${money(tooSmallBelow)} as too small, and this one is no bigger"); continue }
            val target = KalshiBetTargets.of(o)
            if (target == null) { skip("its fair price is missing"); continue }
            if (target.market.marketId in openMarkets) { skip("a bet in this Kalshi market is already open"); continue }
            if (settings.apiMaxPerGame > 0.0) {
                val check = GameExposure.check(GameRef(target.market.eventId, target.eventName, target.startsTs, target.league), exposure, target.market.marketId, target.outcomeId, stake, settings.apiMaxPerGame)
                if (check.blocked) { skip(AutoBet.GAME_LIMIT_SKIP.replace("Betting & Novig account", "Kalshi")); continue }
            }
            val at = atBet(o, stake, now)
            if (paper) {
                // What the real pass would do, judged by the same planner on the same fresh tops, recorded at that price and sent nowhere.
                when (val p = placer.plan(target.copy(auto = true, atBet = at), stake, limitsOverride = limits)) {
                    is com.tjshea.vigilant.data.novig.trading.PlanResult.Ready -> {
                        val bet = paperTracker?.track(o, p.plan.expectedCost, placedKey = o.key, atBet = at)
                        if (bet != null) { papered += bet; openMarkets += target.market.marketId; balance -= p.plan.expectedCost }
                    }
                    is com.tjshea.vigilant.data.novig.trading.PlanResult.Refused -> skip(p.reason.take(REASON_CHARS))
                }
                continue
            }
            val result2 = try {
                placer.placeAuto(target.copy(auto = true, atBet = at), stake, limits, q.cost)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlaceResult.Unconfirmed("Something went wrong while placing it (${e.message ?: e.javaClass.simpleName}).")
            }
            when (result2) {
                is PlaceResult.Placed -> { placed += result2.bet; openMarkets += target.market.marketId; balance -= result2.bet.stake }
                is PlaceResult.NotFilled -> { cooldown[o.key] = now + AutoBet.COOLDOWN_MS; skip("nobody was selling at that price") }
                is PlaceResult.Refused -> {
                    cooldown[o.key] = now + AutoBet.COOLDOWN_MS
                    if (result2.tooSmall) tooSmallBelow = maxOf(tooSmallBelow, stake)
                    if (result2.reason.contains("daily limit")) { stopped = "your daily limit of ${money(limits.maxPerDay)} for API bets is reached"; break }
                    skip(if (result2.gameLimit) AutoBet.GAME_LIMIT_SKIP.replace("Betting & Novig account", "Kalshi") else result2.reason.take(REASON_CHARS))
                }
                is PlaceResult.Failed -> { stopped = "Kalshi refused: ${result2.message}"; break }
                is PlaceResult.Unconfirmed -> { stopped = "an order's answer was lost, so the auto-bet is stopped: ${result2.message}"; lostOrder = result2.message; break }
            }
        }
        return KalshiAutoReport(looked = candidates.size, passed = passing.size, placed = placed, skipped = skipped, stopped = stopped, walletEmpty = walletEmpty, paper = papered)
    }

    /** Set when an order's answer was lost: the caller keeps the auto-bet stopped until Tj has looked (the Novig app's halt). */
    @Volatile var lostOrder: String? = null

    private fun reason(o: Opportunity, rules: AutoBet.Rules, settings: ScanSettings, now: Long): String? {
        val q = o.quote ?: return "no Kalshi price"
        if (o.fairIsOld(now)) return "the other books' prices behind it are too old"
        if (o.priceIsOld(now) || now - (o.bookFetchedAtMs ?: 0L) > FRESH_PRICE_MS) return "no Kalshi price read in the last minute"
        unavailable(o)?.let { return it }
        if (!rules.byStars && TrapGuard.tooEarly(o.event.startsTs, now, settings.trapEarlyHours, firstListed(o), settings.trapFirstListed) != null) return TrapGuard.earlyReason(settings.trapEarlyHours)
        val a = o.agreement
        val kind = BetKind.of(o.marketLabel, o.selection)
        AutoBet.judgeVigilant(rules, q.evPercent, q.priceAmerican, kind, a.twoSided, a.agreeing, AutoBet.isOverProp(o.marketLabel, o.selection))?.let { return it }
        AutoBet.starsReason(rules, ProfitProfile.adjustedStars(settings.gates.profitProven, o.rating?.stars, o.league.novigName, kind.label, o.selection, o.evPercent, o.event.startsTs, now))?.let { return it }
        if (a.sharpAgainst) return "a sharp book prices both sides and says it isn't +EV at Kalshi's price"
        if (rules.byStars) return null
        return when (settings.sharpAutoBet) {
            SharpMode.OFF -> null
            SharpMode.VETO -> null
            SharpMode.CONFIRM -> if (!a.sharpAgrees) "no sharp book confirms it is +EV" else null
        }
    }

    private fun limitsOf(rules: AutoBet.Rules, settings: ScanSettings) = BetLimits(
        maxStake = rules.maxStake, maxPerDay = settings.apiMaxPerDay, minEv = rules.readEv,
        maxOdds = if (rules.byStars) 0 else rules.maxOdds, minOdds = if (rules.byStars) 0 else rules.minOdds, maxPerGame = settings.apiMaxPerGame, starsDecide = rules.byStars,
        minEvWhere = if (rules.byStars) "the noise floor that applies while a minimum star rating is on" else "Kalshi tab › Auto-bet › Smallest edge (EV) at Kalshi's price now",
    )

    private fun hoursToStart(startsAtMs: Long?, now: Long): Double? = startsAtMs?.takeIf { it > 0L }?.let { (it - now) / 3_600_000.0 }
    private fun money(v: Double) = String.format(Locale.US, "$%.2f", v)

    companion object {
        /** A bet starting sooner than this is not auto-bet. */
        const val MIN_LEAD_MS = 2 * 60_000L

        /** The price must have been read within this long (the Novig app's AlertPicks.FRESH_PRICE_MS). */
        const val FRESH_PRICE_MS = 60_000L
        const val REASON_CHARS = 140
    }
}
