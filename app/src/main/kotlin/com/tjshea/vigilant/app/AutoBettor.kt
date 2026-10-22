package com.tjshea.vigilant.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.NovigBetFinder
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.trading.ApiBetPlacer
import com.tjshea.vigilant.data.novig.trading.AutoBet
import com.tjshea.vigilant.data.novig.trading.PropGuard
import com.tjshea.vigilant.data.novig.trading.BetLimits
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.PlaceResult
import com.tjshea.vigilant.data.reference.SharpBooks
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SharpConfirm
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.SharpMode
import com.tjshea.vigilant.data.scanner.SharpVeto
import com.tjshea.vigilant.data.scanner.TrapGuard
import com.tjshea.vigilant.data.tracker.AtBet
import com.tjshea.vigilant.data.tracker.AtBets
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.GameExposure
import com.tjshea.vigilant.data.tracker.GameRef
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The auto-bet (Tj, 2026-10-01: "automatically bet each bet without me doing anything at all, including automatic bets in the background as
 * the cno scanner is on in the background"): inside a background auto-scan cycle, after CrazyNinjaOdds' list, Novig's price now and each best
 * bet's books were read, every CNO bet that passes Tj's criteria ([AutoBet.judge]) is placed through Novig's API from the Vigilant wallet, best
 * edge first, one order at a time, at most [AutoBet.MAX_PER_CYCLE] a cycle.
 *
 * It is the Bet sheet's own path with nobody to confirm ([ApiBetPlacer.placeAuto]: pregame only, fair odds fresh, the edge still there at the
 * real book price, the per-bet and per-day limits, an `IOC` at a ceiling that is never chased), plus what an unattended bet needs:
 *  - **The wallet** is read before the cycle's bets and caps each stake; with less than a dollar left it places nothing and says so once.
 *  - **The bet found on Novig must be the bet shown**: the book's price for the outcome matched must be the price the bet was judged at
 *    ([AutoBet.PRICE_TOLERANCE]); one bet per Novig market, so the other side of a line is never bet after the first.
 *  - **A lost answer stops it.** An order whose answer never came back (nothing says whether it filled) HALTS auto-bet ([ScanSettings.autoBetHalted])
 *    until Tj resumes it: it is never re-sent, and the next cycle can't bet the same thing again.
 *  - **Everything is tracked** exactly as a Bet-sheet bet ([BetTracker.logApi] from the fills, hidden from the lists like a ✓), flagged
 *    [TrackedBet.auto]; a notification for each bet placed and one for each stop.
 * Nothing here runs on its own: [AutoScanner.cycle] calls [run].
 */
class AutoBettor(
    private val app: Application,
    private val c: AppContainer,
    private val clock: () -> Long = System::currentTimeMillis,
    /** The placer, or null when betting through the API isn't set up. */
    private val placer: () -> ApiBetPlacer? = { c.autoBetPlacer() },
    /** The Vigilant wallet's balance now; null when betting isn't set up. May throw Novig's refusal. */
    private val wallet: suspend () -> Double? = {
        val t = c.trading
        val address = c.subaccountKeyId
        if (t == null || address == null) null else withContext(Dispatchers.IO) { t.balance(address) }.also { c.wallet.record(it) }
    },
    /** [row]'s exact Novig bet as a target (market read, outcome found), or null when it can't be found for certain. */
    private val resolve: suspend (CnoRow) -> BetTarget? = { row -> resolveOnNovig(c, row) },
    private val notes: AutoBetNotes = AutoBetNotes,
    /**
     * The sharp-book check for one candidate (Tj, 2026-10-02): rules, the bet, CNO's game page for it, Novig's price now, live?, now. Only asked when
     * Settings switch it on and the bet passed everything else.
     */
    private val sharp: suspend (SharpConfirm.Rules, SharpBooks.Bet, CnoBooksView?, Int, Boolean, Long) -> SharpConfirm.Result =
        { rules, bet, view, odds, live, at -> SharpGate.check(c.sharp, rules, bet, view, odds, live, at) },
    /** How long nothing is sent after Novig refuses an order ([FAIL_BACKOFF_MS]). */
    private val failBackoffMs: Long = FAIL_BACKOFF_MS,
    /** A Novig market's newest trades (public, one request): what the trap guard's move rule reads before a game line is bet ([TrapGuard.move]). */
    private val recentTrades: suspend (String) -> List<TrapGuard.Trade> = { id -> withContext(Dispatchers.IO) { c.novig.trades(id) } },
    /** The injury reports as they are now: a bet on a player they say is out is never placed ([com.tjshea.vigilant.data.reference.PlayerOut]; Tj, 2026-10-07). */
    private val injuries: () -> com.tjshea.vigilant.data.reference.InjuryBook = { c.injuries.book.value },
) {

    /** What the last [run] did, for Settings, Diagnostics and the tests. */
    data class Report(
        /** CNO bets looked at (candidates with their books read). */
        val looked: Int = 0,
        /** Of those, how many passed Tj's criteria. */
        val passed: Int = 0,
        val placed: List<TrackedBet> = emptyList(),
        /** Why bets weren't placed, reason → how many (a few distinct ones). */
        val skipped: Map<String, Int> = emptyMap(),
        /** Why the run stopped early (wallet, daily limit, Novig refused, halted), or null. */
        val stopped: String? = null,
        val walletEmpty: Boolean = false,
        val halted: Boolean = false,
        /** Why each CNO bet that was looked at was not placed, by CNO row key (Tj, 2026-10-11: "Why isn't it betting the 4 Star Bet in the screenshot?"): shown on the bet's card. */
        val why: Map<String, String> = emptyMap(),
    ) {
        val staked: Double get() = placed.sumOf { it.stake }
    }

    data class Status(
        val lastRunMs: Long? = null,
        val last: Report = Report(),
        /** Why it can't place anything at all right now (not set up, wallet unreadable): shown in Settings. */
        val blocker: String? = null,
        /** The wallet's balance when last read. */
        val balance: Double? = null,
        /** Bets placed and dollars staked since the app process started. */
        val placedSinceStart: Int = 0,
        val stakedSinceStart: Double = 0.0,
        /** The sharp check since the app process started: each bet's latest verdict ("veto.VETOED", "confirm.CONFIRMED"), counted ([sharpLine]). */
        val sharp: Map<String, Int> = emptyMap(),
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Bets refused or not found recently, by CNO row key → when they may be tried again. */
    private val cooldown = HashMap<String, Long>()

    /**
     * The most Novig refused as too small since the app process started (`ORDER_TOO_SMALL`; its threshold isn't published), 0 = none yet: a stake at
     * or under it is skipped, not sent again, on every later cycle too (AutoBettorTest "once Novig has refused a size…"). A restart asks again, in case
     * Novig changed it.
     */
    private var tooSmallBelow = 0.0

    /** An order of ours is being sent and followed right now ([AutoBetRecovery] leaves its halt alone until it is over). */
    @Volatile var sending = false

        private set

    /** Bets (by selection text) held back after an order whose answer was never confirmed ([AutoBetRecovery.forceResume]): never placed twice. */
    private val quarantined = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun quarantine(selection: String, untilMs: Long) { quarantined[selection.lowercase()] = untilMs }

    /** Hours from now to a start ([EvMath.leadScale] sizes a Kelly stake by it); null when the start is not known. */
    private fun hoursToStart(startsAtMs: Long?): Double? = startsAtMs?.takeIf { it > 0L }?.let { (it - clock()) / 3_600_000.0 }

    private fun isQuarantined(text: String, now: Long): Boolean = quarantined.entries.any { (sel, until) -> until > now && text.lowercase().contains(sel) }

    /** The wallet-empty note went out and the wallet hasn't refilled since. */
    private var walletEmptyNoted = false

    /** The last stop note posted and when: the same one isn't posted again for [NOTE_REPEAT_MS]. */
    private var lastStopNote: Pair<String, Long>? = null

    /** Novig refused an order (the location check, KYC, a wallet it won't take) or the day's limit was reached: nothing is sent again until [failedUntilMs], whatever the interval. */
    private var failedUntilMs = 0L
    private var failure: String? = null

    /**
     * One pass for [settings] over [state] (the CNO list, live prices and books as the cycle just read them). Never throws for a bet's own
     * trouble: it ends up in the [Report]. Cancelled cleanly: a bet's order, once sent, is always followed to its end and recorded.
     */
    suspend fun run(settings: ScanSettings, state: UiState): Report {
        val now = clock()
        val rules = AutoBet.rules(settings)
        // Whoever calls, a halted or switched-off auto-bet places nothing.
        if (!settings.autoBet) return finish(now, Report(), blocker = "Auto-bet is off")
        // The GitHub engine places the bets of Vigilant's own scan (Tj, 2026-10-15, TCL20d), so that lane ([runTennis]) stands down while it trades. THIS lane is CrazyNinjaOdds' list, which the engine never
        // reads (Tj, 2026-10-16: "I like it. the auto bet system should still use it as a source"): it keeps betting, and never takes a market the engine's own ledger holds open (below).
        // The kill switch (Tj, 2026-10-05): whoever calls, and whatever the settings passed in say, a stopped app places nothing.
        if (settings.killed || c.settingsStore.flow.value?.killed == true) return finish(now, Report(), blocker = "Stopped by the STOP button: tap RESUME on the red bar to run again")
        settings.autoBetHalted?.let { return finish(now, Report(halted = true), blocker = "stopped: $it (the Auto-bet tab › Resume auto-bet)") }
        failure?.takeIf { now < failedUntilMs }?.let { return finish(now, Report(stopped = it), blocker = it) }
        val placer = placer()
        if (!AppBook.isNovig || placer == null) return finish(now, Report(), blocker = "Betting through Novig's API isn't set up (Settings › Betting & Novig account › Enable betting)")
        cooldown.entries.removeAll { it.value <= now }

        val skipped = LinkedHashMap<String, Int>()
        // The bet being judged (CNO row key): what stops it is kept against it, for its card.
        val why = HashMap<String, String>()
        var judging: String? = null
        fun skip(reason: String) { skipped.merge(reason, 1, Int::plus); judging?.let { why[it] = reason } }

        // Pregame bets at Novig whose books were read, best edge first. Each is judged on Novig's price read in the last minute: a bet with no
        // such price isn't placed (its edge is whatever it was a while ago).
        // While a minimum star rating is on (Tj, 2026-10-11) the rating decides: the edge, odds, kinds, books, sharp, trap-guard and small-prop filters are not applied ([AutoBet.Rules.byStars]).
        val byStars = rules.byStars
        val all = AlertPicks.cnoChecked(state, rules.readEv, now, trapGuard = !byStars)
        // The trap guard's first rule: games too far off were left out of the candidates (their books weren't even read); counted here.
        if (!byStars) {
            AlertPicks.tooEarly(state, rules.minEv, now).takeIf { it > 0 }?.let { n -> skipped[TrapGuard.earlyReason(settings.trapEarlyHours)] = n }
            AlertPicks.listedEarly(state, rules.minEv, now).takeIf { it > 0 }?.let { n -> skipped[TrapGuard.listedEarlyReason(settings.trapEarlyHours)] = n }
        }
        val passing = ArrayList<AlertPicks.CnoChecked>()
        for (item in all.distinctBy { it.pick.row.key }.sortedByDescending { it.shown.ev }) {
            val row = item.pick.row
            judging = row.key
            val startsAt = row.startsAtMs
            when {
                !ApiBetTargets.atNovig(row) -> skip("not priced at Novig")
                item.pick.live || startsAt == null || startsAt - now < MIN_LEAD_MS -> skip("not pregame (live betting isn't available)")
                item.live == null -> skip("no Novig price read in the last minute")
                (cooldown[row.key] ?: 0L) > now -> skip("tried a moment ago")
                isQuarantined(row.bet, now) -> skip("an earlier order for it was never confirmed")
                else -> com.tjshea.vigilant.data.reference.PlayerOut.forCnoRow(injuries(), row, now, state.teams[row.key])?.let(::skip)
                    ?: AutoBet.judge(rules, item.shown.ev, item.check, item.shown.row.odds, BetKind.of(row.market, row.bet), AutoBet.isOverProp(row.market, row.bet))?.let(::skip)
                    ?: AutoBet.starsReason(rules, starsFor(settings, item.check.rating?.stars, row.league, BetKind.of(row.market, row.bet).label, row.bet, item.shown.ev, startsAt, now), item.check.rating?.stars)?.let(::skip)
                    ?: (if (byStars) null else sharpReason(item, settings, state, now))?.let(::skip)
                    ?: (if (item.check.sharpAgainst) "a sharp book prices both sides and says it isn't +EV at Novig's price" else null)?.let(::skip)
                    ?: passing.add(item)
            }
        }
        judging = null
        // Bets that passed every rule but were not reached or not paid for this pass say why too ([Report.why]).
        val passWhy = { msg: String -> HashMap(why).also { m -> passing.forEach { m.putIfAbsent(it.pick.row.key, msg) } } }
        if (passing.isEmpty()) {
            // Nothing to bet this cycle, but the wallet this cycle read may already be empty (Tj, 2026-10-02 ~22:10Z): asleep until he adds money.
            c.wallet.last?.takeIf { now - it.atMs < WalletBalance.FRESH_MS }?.let { w ->
                if (w.dollars < AutoBet.SLEEP_BELOW) { if (!walletEmptyNoted) walletRanOut(w.dollars) } else walletEmptyNoted = false
            }
            return finish(now, Report(looked = all.size, skipped = skipped, why = why))
        }

        val balance0 = try {
            wallet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: NovigApiException) {
            return stopFor(now, Report(looked = all.size, passed = passing.size, skipped = skipped, why = passWhy("the wallet couldn't be read")), "Couldn't read the wallet: ${e.advice}")
        } catch (e: Exception) {
            return stopFor(now, Report(looked = all.size, passed = passing.size, skipped = skipped, why = passWhy("the wallet couldn't be read")), "Couldn't read the wallet: ${e.message ?: e.javaClass.simpleName}")
        } ?: return finish(now, Report(looked = all.size, passed = passing.size, skipped = skipped, why = passWhy("betting through Novig's API isn't set up")), blocker = "Betting through Novig's API isn't set up (Settings › Betting & Novig account › Enable betting)")
        var balance = balance0
        if (balance >= AutoBet.SLEEP_BELOW) walletEmptyNoted = false
        else {
            if (!walletEmptyNoted) walletRanOut(balance)
            return finish(now, Report(looked = all.size, passed = passing.size, skipped = skipped, walletEmpty = true, stopped = "the wallet has ${money(balance)}, under ${money(AutoBet.SLEEP_BELOW)}", why = passWhy("the wallet has ${money(balance)}, under ${money(AutoBet.SLEEP_BELOW)}")), balance = balance)
        }

        // One bet per Novig market (either side) while any is open: the other side of a line is never bet after the first.
        val openMarkets = c.tracker.all().filter { it.status == BetStatus.PENDING && it.marketId.isNotBlank() }.mapTo(HashSet()) { it.marketId }
        // What the GitHub engine holds open (its ledger, as the phone last read it): one bet a market across both systems.
        state.cloudUi.trades?.entries.orEmpty().filter { it.status == "OPEN" || it.status == "UNCONFIRMED" }.mapTo(openMarkets) { it.marketId }
        val limits = limitsOf(rules, settings)
        // One game is one event (Tj, 2026-10-04: "auto bet placed bets on a team at +5, then the same team at +6, then the same team at +10"): what is at risk
        // on each game across all its markets, open bets and resting bids, as the cycle began. The placer checks it again on its own read of the Tracker
        // (so a bet placed earlier this cycle, or by hand meanwhile, counts there); this pass is what keeps a full game from costing a book read per bet.
        val exposure = if (settings.apiMaxPerGame > 0.0) GameExposure.items(c.tracker.all()) + GameExposure.bidItems(c.makerStore.all()) else emptyList()
        val placed = ArrayList<TrackedBet>()
        var stopped: String? = null
        var walletEmpty = false
        var halted = false
        // The small-prop guard (Tj, 2026-10-07; RESEARCH.md §109): how much of the last 24 hours' auto-bets each kind of player prop already holds, as this pass began; the bets this pass
        // places are added as they go, so a pass cannot fill a day's share in one go.
        val guardRules = if (byStars) PropGuard.Rules(share = 0.0, minSample = 0, perGame = 0) else PropGuard.rules(settings)
        val guardHistory = ArrayList(if (guardRules.on) PropGuard.history(c.tracker.all(), now) else emptyList())

        // When the wallet or the cycle's cap can't take every bet, the money goes first to the edges most likely to hold (RESEARCH.md §72): the sharpest
        // book's own edge where its veto priced the bet, else 70% of the shown edge ([AutoBet.credibleEv]). Ties keep the shown edge's order.
        val vetoOn = settings.sharpAutoBet == SharpMode.VETO
        val credible = { it: AlertPicks.CnoChecked -> AutoBet.credibleEv(it.shown.ev, vetoSaid[it.pick.row.key]?.takeIf { v -> vetoOn && v.verdict == SharpVeto.Verdict.PASSED }?.ev) }
        // Rated by stars: the best-rated first (then the likeliest edge), since the rating is what chose them.
        val ordered = if (byStars) passing.sortedWith(compareByDescending<AlertPicks.CnoChecked> { starsFor(settings, it.check.rating?.stars, it.pick.row.league, BetKind.of(it.pick.row.market, it.pick.row.bet).label, it.pick.row.bet, it.shown.ev, it.pick.row.startsAtMs, now) ?: 0.0 }.thenByDescending(credible)) else passing.sortedByDescending(credible)
        for (item in ordered) {
            currentCoroutineContext().ensureActive()
            // STOP pressed since this cycle began: no further order, whatever the settings this pass started with said (Tj, 2026-10-05). (A Pause is held at each
            // order by the placer's own check: ApiBetPlacer's `paused`.)
            if (c.settingsStore.flow.value?.killed == true) { stopped = "the STOP button was pressed while this pass ran"; break }
            if (placed.size >= AutoBet.MAX_PER_CYCLE) { stopped = "placed ${AutoBet.MAX_PER_CYCLE} this cycle (the best edges first); the rest wait for the next"; break }
            val row = item.pick.row
            judging = row.key
            // A Kelly stake is never sized on more edge than the sharpest book backs (its own fair, where its veto priced the bet: RESEARCH.md §72).
            val sharpFair = vetoSaid[row.key]?.takeIf { v -> vetoOn && v.verdict == SharpVeto.Verdict.PASSED }?.fair
            val stake = when (val s = AutoBet.stake(rules, item.shown.row, settings.bankroll, balance, sharpFair, item.check.fairProbability, hoursToStart(row.startsAtMs))) {
                is AutoBet.Stake.WalletEmpty -> { walletEmpty = true; stopped = "the wallet has ${money(balance)}, under a cent"; break }
                is AutoBet.Stake.Skip -> { skip(s.reason); continue }
                is AutoBet.Stake.Amount -> s.dollars
            }
            if (stake <= tooSmallBelow + 1e-9) { skip("Novig refused an order of ${money(tooSmallBelow)} as too small, and this one is no bigger"); continue }
            val target = try {
                resolve(row)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (target == null) {
                cooldown[row.key] = now + NOT_FOUND_COOLDOWN_MS
                skip("its exact bet couldn't be found on Novig")
                continue
            }
            // Novig's price for this bet was read from one outcome's book, and its bet slip was found by the catalog: they must be the same outcome.
            val priced = item.live?.outcomeId
            if (priced != null && priced != target.outcomeId) { cooldown[row.key] = now + NOT_FOUND_COOLDOWN_MS; skip("Novig's price and its bet slip name different outcomes"); continue }
            if (target.market.marketId in openMarkets) { skip("a bet in this Novig market is already open"); continue }
            // Negative EV on ANY scanner stops it (Tj, 2026-10-11): Vigilant's own latest scan prices this exact Novig outcome too.
            state.result?.opportunities?.firstOrNull { it.market.marketId == target.market.marketId && it.outcome.outcomeId == target.outcomeId && !it.fairIsOld(now) && !it.isLive }
                ?.let { o -> if ((o.evPercent ?: 0.0) < 0.0) { skip("Vigilant's own scan prices it negative EV (${com.tjshea.vigilant.app.ui.Format.evPercentShort(o.evPercent ?: 0.0)})"); continue } }
            val game = GameRef(target.market.eventId, target.eventName, target.startsTs, target.league)
            val guardKey = PropGuard.key(row.league, row.market, row.bet)
            val guardGame = PropGuard.gameKey(target.market.eventId, target.eventName, target.startsTs)
            if (guardKey != null) PropGuard.judge(guardRules, guardHistory, guardKey, guardGame, clock())?.let { skip(it); continue }
            if (settings.apiMaxPerGame > 0.0) {
                val check = GameExposure.check(game, exposure, target.market.marketId, target.outcomeId, stake, settings.apiMaxPerGame)
                if (check.blocked) { noteGameLimit(check); skip(AutoBet.GAME_LIMIT_SKIP); continue }
            }
            // The trap guard's second rule (RESEARCH.md §71): on a game line, Novig's own trades say whether the price just moved to make it look cheap.
            val moveReason = if (byStars) null else novigMove(item, target, settings)
            if (moveReason != null) { cooldown[row.key] = clock() + AutoBet.COOLDOWN_MS; skip(moveReason); continue }

            // The order is sent and followed to its end ([sendOrder]), then what came of it is applied to this pass.
            when (val sent = sendOrder(placer, target, stake, limits, AutoBet.priceOf(item.shown.row), recordOf(item, state, settings, stake, now), row.key, now)) {
                is Sent.Placed -> {
                    val bet = sent.bet
                    placed += bet
                    guardHistory += PropGuard.Placed(guardKey, guardGame, clock())
                    openMarkets += target.market.marketId
                    balance -= bet.stake
                    withContext(NonCancellable) { markPlaced(c, target, sent.result, clock()) }
                    notes.placed(app, target, bet, item.check, walletLeft = balance, sharp = sharpSaid[row.key]?.takeIf { it.confirmed }?.detail)
                }
                is Sent.Skipped -> skip(sent.reason)
                is Sent.Stop -> { stopped = sent.why; halted = sent.halted; break }
            }
        }
        if (walletEmpty && !walletEmptyNoted) walletRanOut(balance)
        judging = null
        val placedKeys = placed.mapTo(HashSet()) { it.placedKey }
        val finalWhy = passWhy(stopped?.let { "the pass stopped: $it" } ?: "its turn did not come this pass").also { m -> ordered.forEach { if (it.pick.row.key in placedKeys) m.remove(it.pick.row.key) } }
        val report = Report(looked = all.size, passed = passing.size, placed = placed, skipped = skipped, stopped = stopped, walletEmpty = walletEmpty, halted = halted, why = finalWhy)
        if (stopped != null && !walletEmpty) postStop(now, stopped, halted)
        return finish(now, report, balance = balance)
    }

    /**
     * Tennis (Tj, 2026-10-10: "I want the auto bid feature and auto bet features both to include all tennis. If it's has to use vigilant scanner that is fine"). CrazyNinjaOdds
     * carries no tennis (RESEARCH.md §85), so [run] can never see it; this takes it from Vigilant's own finished scan [result] (Pinnacle's price as the fair), judges it by the same
     * rules ([AutoBet.judgeScan]: edge, favourite bar, odds limits, kinds, stake, wallet) and places it through the same placer, whose own book read, price match and limits
     * ([ApiBetPlacer.placeAuto]) are the last word. Pregame only. Switched by [ScanSettings.autoBetTennis] with ATP/WTA among the leagues.
     */
    suspend fun runTennis(settings: ScanSettings, state: UiState, result: ScanResult): Report {
        val now = clock()
        val rules = AutoBet.rules(settings)
        if (!settings.autoBet || !(settings.autoBetTennis || settings.autoBetVigilant)) return Report()
        if (settings.gates.cloudTrading != com.tjshea.vigilant.data.scanner.CloudTradeMode.OFF) return Report()
        // Tj, 2026-10-22, CH66: with the GitHub engine driving, Vigilant's bets are the engine's (Engine trading), never this phone's.
        if (settings.cloudDrives) return Report()
        if (settings.killed || c.settingsStore.flow.value?.killed == true) return finish(now, Report(), blocker = "Stopped by the STOP button: tap RESUME on the red bar to run again")
        settings.autoBetHalted?.let { return finish(now, Report(halted = true), blocker = "stopped: $it (the Auto-bet tab › Resume auto-bet)") }
        failure?.takeIf { now < failedUntilMs }?.let { return finish(now, Report(stopped = it), blocker = it) }
        val placer = placer()
        if (!AppBook.isNovig || placer == null) return finish(now, Report(), blocker = "Betting through Novig's API isn't set up (Settings › Betting & Novig account › Enable betting)")
        if (result.partial) return Report()
        cooldown.entries.removeAll { it.value <= now }

        val skipped = LinkedHashMap<String, Int>()
        fun skip(reason: String) { skipped.merge(reason, 1, Int::plus) }
        // Tennis (no CNO page) and, with [ScanSettings.autoBetVigilant], every other league's bets from this scan.
        val tennis = result.opportunities.filter {
            (if (it.league.tennis) settings.autoBetTennis else settings.autoBetVigilant) &&
                it.league.novigName in settings.leagues && it.quote != null && it.fairProbability != null
        }
        val passing = ArrayList<Opportunity>()
        // A minimum star rating decides which bets go (Tj, 2026-10-11): the best-rated first, then the biggest edge.
        val profile = if (settings.gates.profitProven) com.tjshea.vigilant.data.scanner.ProfitProfile.shipped else null
        // Profit-proven only: the types of bet with the best history first (a neutral type scores 0), then the usual order.
        val profitScore = { o: Opportunity -> profile?.judge(com.tjshea.vigilant.data.scanner.ProfitProfile.Instrument.BET, o.league.novigName, com.tjshea.vigilant.data.scanner.BetKind.of(o.marketLabel, o.selection).label, o.selection, o.evPercent, (o.event.startsTs - now) / 60_000L)?.score ?: 0.0 }
        val first = (if (rules.byStars) tennis.sortedWith(compareByDescending<Opportunity> { starsFor(settings, it.rating?.stars, it.league.novigName, BetKind.of(it.marketLabel, it.selection).label, it.selection, it.evPercent, it.event.startsTs, now) ?: 0.0 }.thenByDescending { it.evPercent ?: 0.0 }) else tennis.sortedByDescending { it.evPercent ?: 0.0 })
            .let { list -> if (profile != null) list.sortedByDescending(profitScore) else list }
        for (o in first) {
            val q = o.quote ?: continue
            when {
                o.isLive || o.event.startsTs - now < MIN_LEAD_MS -> skip("not pregame (live betting isn't available)")
                (cooldown[o.key] ?: 0L) > now -> skip("tried a moment ago")
                isQuarantined(o.selection, now) -> skip("an earlier order for it was never confirmed")
                o.league.tennis -> AutoBet.judgeScan(rules, q.evPercent, q.priceAmerican, BetKind.of(o.marketLabel, o.selection), AutoBet.isOverProp(o.marketLabel, o.selection))?.let(::skip) ?: AutoBet.starsReason(rules, starsFor(settings, o.rating?.stars, o.league.novigName, BetKind.of(o.marketLabel, o.selection).label, o.selection, o.evPercent, o.event.startsTs, now), o.rating?.stars)?.let(::skip) ?: passing.add(o)
                else -> vigilantReason(o, rules, settings, state, now)?.let(::skip) ?: passing.add(o)
            }
        }
        if (tennis.isEmpty()) return Report()
        if (passing.isEmpty()) return finish(now, Report(looked = tennis.size, skipped = skipped))

        val balance0 = try {
            wallet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return stopFor(now, Report(looked = tennis.size, passed = passing.size, skipped = skipped), "Couldn't read the wallet: ${(e as? NovigApiException)?.advice ?: e.message ?: e.javaClass.simpleName}")
        } ?: return finish(now, Report(looked = tennis.size, passed = passing.size, skipped = skipped), blocker = "Betting through Novig's API isn't set up (Settings › Betting & Novig account › Enable betting)")
        var balance = balance0
        if (balance < AutoBet.SLEEP_BELOW) {
            if (!walletEmptyNoted) walletRanOut(balance)
            return finish(now, Report(looked = tennis.size, passed = passing.size, skipped = skipped, walletEmpty = true, stopped = "the wallet has ${money(balance)}, under ${money(AutoBet.SLEEP_BELOW)}"), balance = balance)
        }
        val openMarkets = c.tracker.all().filter { it.status == BetStatus.PENDING && it.marketId.isNotBlank() }.mapTo(HashSet()) { it.marketId }
        val limits = limitsOf(rules, settings)
        val exposure = if (settings.apiMaxPerGame > 0.0) GameExposure.items(c.tracker.all()) + GameExposure.bidItems(c.makerStore.all()) else emptyList()
        val placed = ArrayList<TrackedBet>()
        var stopped: String? = null
        var walletEmpty = false
        var halted = false
        for (o in passing) {
            currentCoroutineContext().ensureActive()
            if (c.settingsStore.flow.value?.killed == true) { stopped = "the STOP button was pressed while this pass ran"; break }
            if (placed.size >= AutoBet.MAX_PER_CYCLE) { stopped = "placed ${AutoBet.MAX_PER_CYCLE} this cycle (the best edges first); the rest wait for the next"; break }
            val q = o.quote ?: continue
            val stake = when (val s = AutoBet.scanStake(rules, q, settings.bankroll, balance, hoursToStart(o.event.startsTs))) {
                is AutoBet.Stake.WalletEmpty -> { walletEmpty = true; stopped = "the wallet has ${money(balance)}, under a cent"; break }
                is AutoBet.Stake.Skip -> { skip(s.reason); continue }
                is AutoBet.Stake.Amount -> s.dollars
            }
            if (stake <= tooSmallBelow + 1e-9) { skip("Novig refused an order of ${money(tooSmallBelow)} as too small, and this one is no bigger"); continue }
            val target = ApiBetTargets.of(o)
            if (target == null) { skip("its fair price is missing"); continue }
            if (target.market.marketId in openMarkets) { skip("a bet in this Novig market is already open"); continue }
            if (settings.apiMaxPerGame > 0.0) {
                val check = GameExposure.check(GameRef(target.market.eventId, target.eventName, target.startsTs, target.league), exposure, target.market.marketId, target.outcomeId, stake, settings.apiMaxPerGame)
                if (check.blocked) { noteGameLimit(check); skip(AutoBet.GAME_LIMIT_SKIP); continue }
            }
            val atBet = runCatching { BetRecord.opportunity(state.copy(settings = settings), o, AtBet.HOW_AUTO, now, stake = stake) }.getOrNull()
            when (val sent = sendOrder(placer, target, stake, limits, q.cost, atBet, o.key, now)) {
                is Sent.Placed -> {
                    val bet = sent.bet
                    placed += bet
                    openMarkets += target.market.marketId
                    balance -= bet.stake
                    withContext(NonCancellable) { markPlaced(c, target, sent.result, clock()) }
                    notes.placed(app, target, bet, null, walletLeft = balance)
                }
                is Sent.Skipped -> skip(sent.reason)
                is Sent.Stop -> { stopped = sent.why; halted = sent.halted; break }
            }
        }
        if (walletEmpty && !walletEmptyNoted) walletRanOut(balance)
        val report = Report(looked = tennis.size, passed = passing.size, placed = placed, skipped = skipped, stopped = stopped, walletEmpty = walletEmpty, halted = halted)
        if (stopped != null && !walletEmpty) postStop(now, stopped, halted)
        return finish(now, report, balance = balance)
    }

    /**
     * Why Vigilant's own bet [o] (not tennis) is not placed, or null: the freshness the alerts ask of a bet, the injury report, the trap guard, the Tracker's duplicates, then
     * [AutoBet.judgeVigilant] on the books behind its fair line and the sharp rule ([ScanSettings.sharpAutoBet]) on the sharp books among them.
     */
    private fun vigilantReason(o: Opportunity, rules: AutoBet.Rules, settings: ScanSettings, state: UiState, now: Long): String? {
        val q = o.quote ?: return "no Novig price"
        if (o.fairIsOld(now)) return "the other books' prices behind it are too old"
        if (o.priceIsOld(now) || now - (o.bookFetchedAtMs ?: 0L) > AlertPicks.FRESH_PRICE_MS) return "no Novig price read in the last minute"
        com.tjshea.vigilant.data.reference.PlayerOut.forTag(state.injuries[o.key])?.let { return it }
        if (!rules.byStars && TrapGuard.tooEarly(o.event.startsTs, now, settings.trapEarlyHours, state.firstListed[o.key], settings.trapFirstListed) != null) return TrapGuard.earlyReason(settings.trapEarlyHours)
        val a = o.agreement
        AutoBet.judgeVigilant(rules, q.evPercent, q.priceAmerican, BetKind.of(o.marketLabel, o.selection), a.twoSided, a.agreeing, AutoBet.isOverProp(o.marketLabel, o.selection))?.let { return it }
        AutoBet.starsReason(rules, starsFor(settings, o.rating?.stars, o.league.novigName, BetKind.of(o.marketLabel, o.selection).label, o.selection, o.evPercent, o.event.startsTs, now))?.let { return it }
        // A sharp book that disagrees vetoes the bet, whatever the sharp rule or a minimum star rating says (Tj, 2026-10-11: "If any sharp book disagrees that a bet is positive EV, it should be vetoed and not bet").
        if (a.sharpAgainst) return "a sharp book prices both sides and says it isn't +EV at Novig's price"
        // The rating already weighs the sharp book (a sharp book against a bet caps it at 2½ stars): while it decides, the sharp rule is not asked again.
        if (rules.byStars) return null
        return when (settings.sharpAutoBet) {
            SharpMode.OFF -> null
            SharpMode.VETO -> if (a.sharpAgainst) "a sharp book prices both sides and says it isn't +EV at Novig's price" else null
            SharpMode.CONFIRM -> when {
                a.sharpAgainst -> "a sharp book prices both sides and says it isn't +EV at Novig's price"
                !a.sharpAgrees -> "no sharp book confirms it is +EV"
                else -> null
            }
        }
    }

    /**
     * The limits the placer holds an auto-bet to: Tj's dollar limits always; the edge floor and the odds limits only while no minimum star rating decides ([AutoBet.Rules.byStars]: the floor is then the
     * noise floor, so a bet still has to be +EV at the real price, and the odds limits are off).
     */
    /**
     * The stars the minimum is held against. With Settings › Profit-proven only ON, a type of bet Tj's history says loses money has its stars lowered by [ProfitProfile.Judgement.starPenalty]
     * (a type that is not proven either way, and every type with the switch off, is unchanged). Only a minimum star rating reads this: it overrides the profile's block (Tj, 2026-10-12).
     */
    private fun starsFor(settings: ScanSettings, stars: Double?, league: String, kind: String, selection: String, ev: Double?, startsAtMs: Long?, now: Long): Double? =
        com.tjshea.vigilant.data.scanner.ProfitProfile.adjustedStars(settings.gates.profitProven, stars, league, kind, selection, ev, startsAtMs, now)

    private fun limitsOf(rules: AutoBet.Rules, settings: ScanSettings) = BetLimits(
        maxStake = rules.maxStake, maxPerDay = settings.apiMaxPerDay, minEv = rules.readEv,
        maxOdds = if (rules.byStars) 0 else rules.maxOdds, minOdds = if (rules.byStars) 0 else rules.minOdds, maxPerGame = settings.apiMaxPerGame, starsDecide = rules.byStars,
        minEvWhere = if (rules.byStars) "the noise floor ${AutoBet.MIN_EV_FLOOR * 100}% that applies while a minimum star rating is on" else "Auto-bet tab › Smallest edge (EV) at Novig's price now",
    )

    /** What one order came to, for the pass that sent it. */
    private sealed interface Sent {
        data class Placed(val bet: TrackedBet, val result: PlaceResult.Placed) : Sent

        /** Not placed, and the pass carries on with the next bet. */
        data class Skipped(val reason: String) : Sent

        /** Not placed, and the pass ends ([halted]: an order's answer was lost, so auto-bet stays stopped until Tj looks). */
        data class Stop(val why: String, val halted: Boolean = false) : Sent
    }

    /**
     * Sends one order for [target] and follows it to its end, for every kind of auto-bet ([run]: a CrazyNinjaOdds bet; [runPinnacle]: a bet on Pinnacle's price). [key] is the
     * bet's cooldown key. The order is marked in flight BEFORE it can be sent (saved, and auto-bet stays stopped until it's cleared): if the process dies after Novig takes the
     * order and before the Tracker has it (Tj's v0.38.0 report: the app crashed, out of memory), the next cycle would find the same bet again with nothing on record and
     * place it twice. The marker survives that; a restart finds auto-bet stopped, with why. Once an order may be on its way it is followed to its end and recorded, whatever
     * happens to the coroutine that sent it.
     */
    private suspend fun sendOrder(
        placer: ApiBetPlacer,
        target: BetTarget,
        stake: Double,
        limits: BetLimits,
        expectedPrice: Double,
        atBet: AtBet?,
        key: String,
        now: Long,
    ): Sent {
        val marker = inFlightNote(target, stake)
        withContext(NonCancellable) { runCatching { c.settingsStore.update { it.copy(autoBetHalted = marker) } } }
        sending = true
        val result = try {
            withContext(Dispatchers.IO + NonCancellable) { placer.placeAuto(target.copy(auto = true, atBet = atBet), stake, limits, expectedPrice) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PlaceResult.Unconfirmed("Something went wrong while placing it (${e.message ?: e.javaClass.simpleName}).")
        } finally {
            sending = false
        }
        // A definitive answer (placed, nothing filled, refused, Novig said no) clears the marker; a lost answer replaces it below.
        if (result !is PlaceResult.Unconfirmed) {
            withContext(NonCancellable) { runCatching { c.settingsStore.update { if (it.autoBetHalted == marker) it.copy(autoBetHalted = null) else it } } }
        }
        return when (result) {
            is PlaceResult.Placed -> Sent.Placed(result.bet, result)
            is PlaceResult.NotFilled -> {
                cooldown[key] = now + AutoBet.COOLDOWN_MS
                Sent.Skipped("nobody was selling at that price")
            }
            is PlaceResult.Refused -> {
                cooldown[key] = now + AutoBet.COOLDOWN_MS
                if (result.tooSmall) tooSmallBelow = maxOf(tooSmallBelow, stake)
                if (result.reason.contains("daily limit")) {
                    val why = "your daily limit of ${money(limits.maxPerDay)} for API bets is reached"
                    failure = "waiting: $why (checked again every ${failBackoffMs / 60_000L} minutes)"
                    failedUntilMs = now + failBackoffMs
                    return Sent.Stop(why)
                }
                if (result.gameLimit) {
                    c.eventLog.info("AUTOBET", "held back: ${result.reason}")
                    Sent.Skipped(AutoBet.GAME_LIMIT_SKIP)
                } else {
                    Sent.Skipped(result.reason.take(REASON_CHARS))
                }
            }
            is PlaceResult.Failed -> {
                failure = "waiting after Novig refused an order: ${result.message}"
                failedUntilMs = now + failBackoffMs
                Sent.Stop("Novig refused: ${result.message}")
            }
            is PlaceResult.Unconfirmed -> {
                // Nothing says whether it filled: never again until Tj has looked (Novig, then the Auto-bet tab › Resume).
                // The words start so that [AutoBetRecovery] knows this halt is one it can check on its own.
                withContext(NonCancellable) { runCatching { c.settingsStore.update { it.copy(autoBetHalted = "$LOST_ORDER_PREFIX for ${target.selection}: ${result.message}") } } }
                Sent.Stop("an order's answer was lost, so auto-bet is stopped: ${result.message}", halted = true)
            }
        }
    }

    /**
     * The wallet ran out (Tj, 2026-10-02 ~22:10Z: "make it also stop scanning and put the app to sleep once the wallet runs out of money"): every scan
     * is paused, as the Pause button pauses it ([ScanSettings.paused]: a scan under way stops, CNO's list is held, background auto-scan and its service
     * stop), and the wallet-empty note says so. Once per emptying: Tj resuming with the wallet still empty isn't paused again until it has refilled
     * and run out again ([walletEmptyNoted]).
     */
    private suspend fun walletRanOut(balance: Double) {
        walletEmptyNoted = true
        withContext(NonCancellable) { runCatching { c.settingsStore.update { it.copy(pausedByHand = true) } } }
        if (c.runner.running) runCatching { c.runner.stop() }
        runCatching { ScanService.cancelDone(app) }
        runCatching { c.eventLog.info("AUTOBET", "wallet low (${money(balance)}, under ${money(AutoBet.SLEEP_BELOW)}): everything paused") }
        notes.stopped(
            app, "Wallet low: Vigilant is asleep",
            "The Vigilant wallet has ${money(balance)}, under ${money(AutoBet.SLEEP_BELOW)}, so scanning, betting and bidding are paused (nothing is read, nothing is bet, no bid goes up). " +
                "Add money in Settings › Betting & Novig account, then tap Resume.",
        )
    }

    /**
     * Every fresh wallet reading, wherever it came from (the strip, a cycle, a bet's answer): under [AutoBet.SLEEP_BELOW] the app goes to sleep once ([walletRanOut]), and a reading at or above
     * it re-arms that. A reading older than [FRESH_READING_MS] is ignored, so the figure saved at the last run can't put the app to sleep after Tj has added money.
     */
    suspend fun onWalletReading(dollars: Double, atMs: Long) {
        if (clock() - atMs > FRESH_READING_MS || !dollars.isFinite()) return
        if (dollars >= AutoBet.SLEEP_BELOW) { walletEmptyNoted = false; return }
        if (walletEmptyNoted) return
        walletRanOut(dollars)
    }

    /** What the sharp book said about each bet that passed (CNO row key → the numbers), for the bet's pop-up and Diagnostics. */
    private val sharpSaid = HashMap<String, SharpConfirm.Result>()

    /** Each bet's latest sharp verdict since the app started (CNO row key → "veto.VETOED" or "confirm.NO_QUOTE"), the oldest dropped past [SHARP_TALLY_KEEP]. */
    private val sharpVerdicts = object : LinkedHashMap<String, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > SHARP_TALLY_KEEP
    }

    /**
     * Why the sharp books don't let [item] through, or null. [SharpMode.VETO] (the default, Tj 2026-10-02 17:01Z: "Only skip a bet if the sharpest book for that
     * market says it is not +ev"): the sharpest book for the bet's kind on its book page says it isn't +EV at Novig's price ([SharpVeto]; free, no feed call).
     * [SharpMode.CONFIRM]: a fresh sharp book's own devigged price must make it +EV ([SharpConfirm]; asked last, only a bet about to be placed costs a feed call).
     */
    private suspend fun sharpReason(item: AlertPicks.CnoChecked, settings: ScanSettings, state: UiState, now: Long): String? {
        val row = item.shown.row
        when (settings.sharpAutoBet) {
            SharpMode.OFF -> return null
            SharpMode.VETO -> {
                val veto = SharpVeto.judge(state.booksAt(item.pick.row.key, now)?.view, row.league, row.market, row.bet, row.odds, item.pick.live, settings.sharpVetoMinEv)
                vetoSaid[item.pick.row.key] = veto
                tally(item.pick.row.key, "veto.${veto.verdict}")
                c.eventLog.count("sharp.veto.${veto.verdict}")
                // The bar's own share (v0.56.0, RESEARCH.md §72): vetoed though the sharp book gave a small edge, so Diagnostics can say what the bar costs.
                if (veto.vetoed && (veto.ev ?: 0.0) > 0.0) c.eventLog.count(SHARP_BAR_COUNTER)
                return veto.reason
            }
            SharpMode.CONFIRM -> Unit
        }
        val rules = SharpConfirm.rules(settings, autoBet = true) ?: return null
        val bet = SharpBooks.Bet(row.league, row.event, row.startsAtMs, row.market, row.bet)
        val result = try {
            sharp(rules, bet, state.booksAt(item.pick.row.key, now)?.view, row.odds, item.pick.live, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SharpConfirm.Result(SharpConfirm.Verdict.UNAVAILABLE, reason = "couldn't get a fresh ${rules.label} price (${e.message ?: e.javaClass.simpleName})".take(REASON_CHARS))
        }
        if (sharpSaid.size > SHARP_SAID_KEEP) sharpSaid.clear()
        sharpSaid[item.pick.row.key] = result
        tally(item.pick.row.key, "confirm.${result.verdict}")
        c.eventLog.count("sharp.autobet.${result.verdict}")
        if (result.verdict == SharpConfirm.Verdict.UNAVAILABLE) c.eventLog.warn("SHARP", result.reason ?: "the sharp check couldn't ask")
        return result.reason
    }

    /** What the trap guard's move rule read for each game line it checked (CNO row key → the bet record's words, [AtBet.novigMove]). */
    private val moveSaid = object : LinkedHashMap<String, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > SHARP_SAID_KEEP
    }

    /**
     * Why the trap guard's move rule stops [item] (a stable reason, counted by the report), or null. Only full-game moneylines, spreads and totals
     * ([TrapGuard.MOVE_KINDS]) with [ScanSettings.trapNovigMove] on are read. A read that fails (429, 451, no route) skips the bet ([MOVE_UNREAD_SKIP]; Tj, 2026-10-07,
     * proposal 10: before, it went through unchecked) and is recorded as UNREAD; the next cycle reads again.
     */
    private suspend fun novigMove(item: AlertPicks.CnoChecked, target: BetTarget, settings: ScanSettings): String? {
        val row = item.shown.row
        return novigMove(item.pick.row.key, BetKind.of(row.market, row.bet), AutoBet.priceOf(row), row.bet, target, settings)
    }

    /** [novigMove] for any bet: its [key] (where the record is kept), [kind], the [price] it was judged at, and its [words]. */
    private suspend fun novigMove(key: String, kind: BetKind, price: Double, words: String, target: BetTarget, settings: ScanSettings): String? {
        if (!settings.trapNovigMove || kind !in TrapGuard.MOVE_KINDS) return null
        val move = try {
            TrapGuard.move(recentTrades(target.market.marketId), target.outcomeId, price, clock())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            moveSaid[key] = "UNREAD · ${(e.message ?: e.javaClass.simpleName).take(REASON_CHARS)}"
            c.eventLog.count("trap.move.UNREAD")
            return MOVE_UNREAD_SKIP
        }
        val verdict = when {
            move.level == null -> "NO LEVEL"
            move.trap -> "TRAP"
            else -> "CLEAR"
        }
        moveSaid[key] = "$verdict · ${TrapGuard.describe(move)}"
        c.eventLog.count("trap.move.${verdict.replace(' ', '_')}")
        val why = TrapGuard.moveReason(kind, move) ?: return null
        c.eventLog.info("AUTOBET", "trap guard: $words: $why")
        return MOVE_SKIP
    }

    /** What the sharp veto said about each bet it judged (CNO row key → verdict), for the bet's record ([AtBet]). */
    private val vetoSaid = object : LinkedHashMap<String, SharpVeto.Result>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SharpVeto.Result>?) = size > SHARP_SAID_KEEP
    }

    /** The bet as the auto-bet decided it ([AtBet]): the check, the veto or confirmation it passed, the stake and the wallet. Never stops a bet. */
    private fun recordOf(item: AlertPicks.CnoChecked, state: UiState, settings: ScanSettings, stake: Double, now: Long): AtBet? = runCatching {
        BetRecord.cno(
            state.copy(settings = settings), item.shown.row, item.pick.live, AtBet.HOW_AUTO, com.tjshea.vigilant.data.tracker.BetTracker.SOURCE_CNO, now,
            stake = stake, check = item.check, veto = vetoSaid[item.pick.row.key], sharpConfirm = sharpSaid[item.pick.row.key]?.detail,
        ).copy(novigMove = moveSaid[item.pick.row.key])
    }.getOrNull()

    /** One bet's newest sharp verdict ("veto.PASSED", "confirm.NO_QUOTE") into Settings' tally. */
    private fun tally(key: String, verdict: String) {
        sharpVerdicts[key] = verdict
        _status.update { it.copy(sharp = sharpVerdicts.values.groupingBy { v -> v }.eachCount()) }
    }

    /** The games whose per-game hold was already logged at that exposure: the same hold isn't logged again every cycle. */
    private val gameLimitNoted = HashSet<String>()

    /** One log line (which game, what's at risk, what this would add) the first time a bet is held back at an exposure; the skip itself is counted every cycle. */
    private fun noteGameLimit(check: GameExposure.Check) {
        if (gameLimitNoted.size > GAME_LIMIT_NOTES) gameLimitNoted.clear()
        if (gameLimitNoted.add("${check.game.eventId}|${check.game.eventName}|${check.now}")) c.eventLog.info("AUTOBET", "held back: ${check.words()}")
    }

    private fun postStop(now: Long, why: String, halted: Boolean) {
        val last = lastStopNote
        if (last != null && last.first == why && now - last.second < NOTE_REPEAT_MS) return
        // A stop that's only "placed 5 this cycle" isn't news.
        if (why.startsWith("placed ")) return
        lastStopNote = why to now
        notes.stopped(app, if (halted) "Auto-bet stopped" else "Auto-bet paused", why.replaceFirstChar { it.uppercase() })
    }

    private fun stopFor(now: Long, report: Report, why: String): Report {
        postStop(now, why, halted = false)
        return finish(now, report.copy(stopped = why))
    }

    /** The flight recorder's view of a run (Tj, 2026-10-02): the funnel in counters (looked → passed → placed, and why not), each bet placed and each stop as an event. */
    internal fun record(report: Report, blocker: String?) {
        val log = c.eventLog
        if (report.looked > 0) {
            log.count("autobet.runs")
            log.count("autobet.looked", report.looked.toLong())
            log.count("autobet.passed", report.passed.toLong())
        }
        report.skipped.forEach { (reason, n) -> log.count("autobet.skip.$reason", n.toLong()) }
        report.placed.forEach { b ->
            log.count("autobet.placed")
            log.info("AUTOBET", "placed ${b.selection} ${money(b.stake)}${b.american?.let { " at ${if (it > 0) "+$it" else "$it"}" } ?: ""}" + (b.evPercentAtBet?.let { String.format(Locale.US, " (%+.1f%% EV)", it * 100) } ?: ""))
        }
        report.stopped?.takeIf { !it.startsWith("placed ") }?.let { log.warn("AUTOBET", "stopped: $it") }
        blocker?.takeIf { it != "Auto-bet is off" }?.let { log.warn("AUTOBET", "can't place bets: $it") }
    }

    private fun finish(now: Long, report: Report, blocker: String? = null, balance: Double? = null): Report {
        runCatching { record(report, blocker) }
        _status.update {
            it.copy(
                lastRunMs = now, last = report, blocker = blocker, balance = balance ?: it.balance,
                placedSinceStart = it.placedSinceStart + report.placed.size, stakedSinceStart = it.stakedSinceStart + report.staked,
            )
        }
        return report
    }

    private fun money(v: Double) = String.format(Locale.US, "$%.2f", v)

    companion object {
        /** A wallet reading older than this is not acted on by [onWalletReading]. */
        const val FRESH_READING_MS = 5 * 60_000L


        /** Auto-bet bets the sharp veto stopped only for its bar (the sharpest book gave a positive edge under [ScanSettings.sharpVetoMinEv]). */
        const val SHARP_BAR_COUNTER = "sharpbar.autobet.under"

        /** The same for CNO's alerts. */
        const val SHARP_BAR_ALERT_COUNTER = "sharpbar.alerts.under"

        /** The report's reason for a game line the trap guard's move rule stopped (one wording, so the report counts them together). */
        /** The words of a game line left alone because Novig's recent trades couldn't be read (the trap guard's move rule has nothing to judge it on). */
        const val MOVE_UNREAD_SKIP = "Novig's recent trades couldn't be read, so this game line isn't bet (trap guard)"

        const val MOVE_SKIP = "Novig just moved: its price fell 2¢+ under this hour's level as the other side was bought (trap guard)"

        /** How many sharp-check answers are kept for the pop-ups before they're dropped (a run's bets are a handful). */
        private const val SHARP_SAID_KEEP = 200

        /** How many bets' sharp verdicts Settings' tally counts at most (the newest). */
        private const val SHARP_TALLY_KEEP = 2_000

        /**
         * The sharp check's tally for the Auto-bet tab (Tj, 2026-10-02 16:05Z: "I'm getting no volume so far on auto bet with the option for each bet
         * to be verified positive EV by a sharp book. Is this working correctly? Is it getting sharp book pricing?"): how many bets it was asked about and
         * what it said, so the reason nothing is placed is on screen. [label]: the sharp books ("Pinnacle"). Null before it asked about any bet.
         */
        fun sharpLine(s: Status, label: String): String? {
            val veto = s.sharp.filterKeys { it.startsWith("veto.") }.mapKeys { it.key.removePrefix("veto.") }
            val confirm = s.sharp.filterKeys { it.startsWith("confirm.") }.mapKeys { it.key.removePrefix("confirm.") }
            val lines = listOfNotNull(vetoLine(veto), confirmLine(confirm, label))
            return lines.joinToString(" ").takeIf { it.isNotEmpty() }
        }

        private fun vetoLine(n: Map<String, Int>): String? {
            val asked = n.values.sum().takeIf { it > 0 } ?: return null
            fun c(v: SharpVeto.Verdict) = n[v.name] ?: 0
            val parts = listOfNotNull(
                "${c(SharpVeto.Verdict.PASSED)} the sharpest book agreed",
                c(SharpVeto.Verdict.VETOED).takeIf { it > 0 }?.let { "$it vetoed (the sharpest book said not +EV, or under the bar)" },
                c(SharpVeto.Verdict.NO_SHARP).takeIf { it > 0 }?.let { "$it with no sharp book on the page (not vetoed)" },
            )
            return "Sharp veto since Vigilant started: $asked bet${if (asked == 1) "" else "s"} judged, " + parts.joinToString(", ") + "."
        }

        private fun confirmLine(n: Map<String, Int>, label: String): String? {
            val asked = n.values.sum().takeIf { it > 0 } ?: return null
            fun c(v: SharpConfirm.Verdict) = n[v.name] ?: 0
            val parts = listOfNotNull(
                "${c(SharpConfirm.Verdict.CONFIRMED)} confirmed",
                c(SharpConfirm.Verdict.NO_QUOTE).takeIf { it > 0 }?.let { "$it with no $label price for that exact line" },
                c(SharpConfirm.Verdict.NOT_CONFIRMED).takeIf { it > 0 }?.let { "$it that $label's own price doesn't show +EV (enough)" },
                c(SharpConfirm.Verdict.STALE).takeIf { it > 0 }?.let { "$it with $label's price too old" },
                c(SharpConfirm.Verdict.UNAVAILABLE).takeIf { it > 0 }?.let { "$it when no feed could be asked" },
            )
            return "Sharp check since Vigilant started: $asked bet${if (asked == 1) "" else "s"} asked about, " + parts.joinToString(", ") + "."
        }

        /** A game starting sooner than this isn't bet: Novig may be moving it to live, and the price is about to jump. */
        const val MIN_LEAD_MS = 60_000L

        /** How a halt after an order whose answer never came back begins ([isLostOrderHalt]). */
        const val LOST_ORDER_PREFIX = "An order's answer was lost"

        /** Whether [halt] is the kind [AutoBetRecovery] checks and clears by itself: an order in flight when the app stopped, or one whose answer was lost. */
        fun isLostOrderHalt(halt: String): Boolean = halt.startsWith("Vigilant stopped while an order") || halt.startsWith(LOST_ORDER_PREFIX)

        /** The bet a halt is about, from its words (null when it names none). */
        fun haltSelection(halt: String): String? =
            Regex("""^Vigilant stopped while an order for (.+?) \(\$""").find(halt)?.groupValues?.get(1)
                ?: Regex("""^$LOST_ORDER_PREFIX for (.+?): """).find(halt)?.groupValues?.get(1)

        /** What the saved halt says while an order may be on its way ([run]): a restart that finds it knows what to check. */
        fun inFlightNote(target: BetTarget, stake: Double): String =
            "Vigilant stopped while an order for ${target.selection} (${String.format(Locale.US, "$%.2f", stake)}) was being placed, so nothing says whether Novig filled it. " +
                "Check Novig and the Tracker's Sync with Novig's fills before resuming."

        /** A bet whose Novig outcome wasn't found isn't searched for again at once. */
        const val NOT_FOUND_COOLDOWN_MS = 5 * 60_000L

        /** After Novig refuses an order, nothing is tried for this long. */
        const val FAIL_BACKOFF_MS = 5 * 60_000L

        /** The same stop isn't announced again for this long. */
        const val NOTE_REPEAT_MS = 60 * 60_000L

        private const val REASON_CHARS = 90

        /** The per-game hold notes kept so the same one isn't logged every cycle. */
        private const val GAME_LIMIT_NOTES = 200

        /** [row]'s exact bet on Novig, as the Bet sheet finds it ([NovigBetFinder]), with its market; null unless the outcome is in that market. */
        suspend fun resolveOnNovig(c: AppContainer, row: CnoRow): BetTarget? {
            val attempt = withContext(Dispatchers.IO) { runCatching { c.betFinder.attempt(row) }.getOrNull() }
            val found = attempt?.found as? NovigBetFinder.Found.Bet
            if (found == null) {
                // Why, by reason, in Diagnostics' counters (Tj, 2026-10-10: "many times it says it can't find a bet"): the next file says which step failed.
                c.eventLog.count("autobet.notfound." + (attempt?.why ?: "the lookup itself failed").take(90))
                return null
            }
            val market = found.market ?: found.marketId?.let { id -> withContext(Dispatchers.IO) { runCatching { c.novig.market(id) }.getOrNull() } } ?: return null
            if (market.outcomes.none { it.outcomeId == found.outcomeId }) return null
            return ApiBetTargets.of(row, found, market, c.cno.state.value.snapshot?.dataAtMs)
        }

        /** A line for Settings and Diagnostics: what the last run did. */
        fun line(s: Status, now: Long): String {
            val r = s.last
            if (s.lastRunMs == null) return "No check yet: it runs with the next background CNO scan."
            val at = "Last check ${com.tjshea.vigilant.app.ui.Format.age(s.lastRunMs, now)}"
            s.blocker?.let { return "$at: $it" }
            val parts = listOfNotNull(
                if (r.placed.isNotEmpty()) "placed ${r.placed.size} (${String.format(Locale.US, "$%.2f", r.staked)})" else null,
                "${r.looked} looked at, ${r.passed} met your criteria".takeIf { r.looked > 0 || r.placed.isEmpty() },
                r.stopped?.let { "stopped: $it" },
                r.skipped.entries.sortedByDescending { it.value }.take(2).takeIf { it.isNotEmpty() }?.joinToString("; ", "skipped: ") { "${it.value} ${it.key}" },
            )
            return "$at: ${parts.joinToString(" · ")}"
        }
    }
}

/** The bet leaves the +EV and CNO lists the way a ✓ makes it ([PlacedBets]): it's placed. The Bet sheet and the auto-bet both do this after an API bet. */
internal suspend fun markPlaced(c: AppContainer, target: BetTarget, placed: PlaceResult.Placed, now: Long) {
    val key = target.placedKey ?: return
    runCatching {
        c.placed.mark(
            com.tjshea.vigilant.data.tracker.PlacedBet(
                key = key, title = target.selection, detail = "${target.marketLabel} · ${target.eventName}", odds = MiniWindow.american(placed.bet.american ?: 0),
                placedAtMs = now, startsAtMs = target.startsTs, event = target.eventName, market = target.marketLabel,
                outcomeId = target.outcomeId, league = target.league,
            ),
        )
    }
}

/** How long a bet whose slip closed stays off the lists before it may come back, when the order did not go through (Tj, 2026-10-13: "after about 15 seconds"). */
internal const val MISS_RESTORE_MS = 15_000L

/**
 * The bet leaves the lists the moment its slip closes ([ApiBetting.confirm]'s order-sent step, Tj 2026-10-13): marked like a ✓ before Novig has answered. [markPlaced] replaces this
 * mark when the bet is Placed; [restoreAfterMiss] takes it off again when it was not.
 */
internal suspend fun markProvisional(placed: com.tjshea.vigilant.data.tracker.PlacedBets, target: BetTarget, now: Long) {
    val key = target.placedKey ?: return
    runCatching {
        placed.mark(
            com.tjshea.vigilant.data.tracker.PlacedBet(
                key = key, title = target.selection, detail = "${target.marketLabel} · ${target.eventName}", odds = "",
                placedAtMs = now, startsAtMs = target.startsTs, event = target.eventName, market = target.marketLabel,
                outcomeId = target.outcomeId, league = target.league,
            ),
        )
    }
}

/**
 * After the order was sent at [sentAt] and did not go through: waits until [MISS_RESTORE_MS] after the slip closed, then takes the provisional mark off, unless the bet was marked placed
 * since (only the provisional mark, which has no odds and the time [sentAt], is ever removed). The lists drop it again at once if it is no longer +EV (CNO's live EV, the feed's re-price).
 */
internal suspend fun restoreAfterMiss(placed: com.tjshea.vigilant.data.tracker.PlacedBets, target: BetTarget, sentAt: Long, clock: () -> Long, restoreMs: Long = MISS_RESTORE_MS) {
    val key = target.placedKey ?: return
    val wait = sentAt + restoreMs - clock()
    if (wait > 0) kotlinx.coroutines.delay(wait)
    val mine = placed.flow.value?.bets?.firstOrNull { b -> key in b.keys && b.odds.isEmpty() && b.placedAtMs == sentAt } ?: return
    runCatching { placed.unmark(mine.key) }
}

/**
 * The auto-bet's notifications (Tj, 2026-10-01: "Make a push notification for every automatic bet, so I can see each bet placed and the stake and
 * EV"): one of its own for each bet placed (it spent money while nobody looked), on a HIGH-importance channel so it pops up, with the stake and the
 * EV in the title and the odds, books and what's left in the wallet under it; and one for each stop that needs Tj.
 */
object AutoBetNotes {
    /** Stops (wallet empty, a lost order, Novig refusing): the v0.39.0 channel, left as it is on a phone that already has it. */
    const val CHANNEL = "auto_bet"

    /**
     * Each bet placed. A channel's importance can't be raised by the app once it exists, and v0.39.0's bets were posted on [CHANNEL] at normal
     * importance (no pop-up): the bets get this new one.
     */
    const val CHANNEL_BET = "auto_bet_placed"
    private const val ID_PLACED = 5_000_000
    private const val ID_STOP = 4_999_999
    private const val ID_SAMPLE = 4_999_998

    fun ensureChannel(context: android.content.Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Auto-bet stops", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When auto-bet stops (wallet empty, a lost order, Novig refusing)."
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BET, "Auto-bet bets placed", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A pop-up for every bet auto-bet places with your wallet: the stake, the EV and the odds."
                enableVibration(true)
            },
        )
    }

    /** "Auto-bet $4.20 · +3.4% EV · Over 5.5": the stake and the edge up front, so the collapsed notification already says what was bet. */
    fun title(bet: TrackedBet, target: BetTarget): String {
        val ev = bet.evPercentAtBet?.let { String.format(Locale.US, " · %+.1f%% EV", it * 100) }.orEmpty()
        return "Auto-bet ${String.format(Locale.US, "$%.2f", bet.stake)}$ev · ${target.selection}"
    }

    /** "+120 · 4 of 5 books agree · Moneyline · A @ B · wallet $2.10 left". */
    fun text(bet: TrackedBet, target: BetTarget, check: CnoBooks.Check?, walletLeft: Double? = null): String {
        val odds = bet.american?.let { if (it > 0) "+$it" else "$it" } ?: "?"
        val left = walletLeft?.let { " · wallet ${String.format(Locale.US, "$%.2f", it.coerceAtLeast(0.0))} left" }.orEmpty()
        val books = check?.let { "${it.agreeing} of ${it.twoSided} books agree · " }.orEmpty()
        return "$odds · $books${target.marketLabel} · ${target.eventName}$left"
    }

    /** Why a bet's notification wouldn't show on this phone (Android's permission or settings), or null when it will. */
    fun blocked(context: android.content.Context): String? {
        if (!ScanService.canNotify(context)) return "Notifications are not allowed for Vigilant (Android Settings › Apps › Vigilant › Notifications)"
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return "Notifications are switched off for Vigilant (Android Settings › Apps › Vigilant › Notifications)"
        val importance = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL_BET)?.importance
        if (importance == NotificationManager.IMPORTANCE_NONE) return "The \"Auto-bet bets placed\" notification channel is switched off (Android Settings › Apps › Vigilant › Notifications)"
        return null
    }

    fun placed(app: Application, target: BetTarget, bet: TrackedBet, check: CnoBooks.Check?, walletLeft: Double? = null, sharp: String? = null) {
        if (!ScanService.canNotify(app)) return
        ensureChannel(app)
        // The sharp book that confirmed it (Tj, 2026-10-02), when the check is on: "Pinnacle +2.1% (devigged, 45 sec old, via PinnWire)".
        val text = text(bet, target, check, walletLeft) + (sharp?.let { " · sharp: $it" } ?: "")
        val n = NotificationCompat.Builder(app, CHANNEL_BET)
            .setSmallIcon(R.drawable.ic_scan)
            .withWallet(app)
            .setContentTitle(title(bet, target))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(EvAlerts.openVigilant(app, ID_PLACED))
            .build()
        // Its own notification each (the bet's id is the tag): two bets in one cycle never replace one another.
        runCatching { NotificationManagerCompat.from(app).notify(bet.id, ID_PLACED, n) }
    }

    /** The end of a bet placed by hand from the Bet sheet, which closes as soon as Novig has the order: a placed bet or the error, as a pop-up on the bet channel. */
    fun handResult(app: Application, title: String, text: String) {
        if (!ScanService.canNotify(app)) return
        ensureChannel(app)
        val n = NotificationCompat.Builder(app, CHANNEL_BET)
            .setSmallIcon(R.drawable.ic_scan)
            .withWallet(app)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(EvAlerts.openVigilant(app, ID_PLACED))
            .build()
        runCatching { NotificationManagerCompat.from(app).notify("hand-${System.nanoTime()}", ID_PLACED, n) }
    }

    /** A lock auto-lock placed (RESEARCH.md §67): the profit whichever side wins, the side bought, the game. */
    fun locked(app: Application, bet: TrackedBet, view: LockView, guaranteed: Double) {
        if (!ScanService.canNotify(app)) return
        ensureChannel(app)
        val title = "Auto-lock " + String.format(java.util.Locale.US, "+$%.2f", guaranteed) + " · ${view.heldName}"
        val text = "Bought ${bet.contracts} contracts of ${view.otherName} at ${bet.american?.let { com.tjshea.vigilant.engine.Odds.formatAmerican(it) } ?: "?"}: " +
            "at least ${String.format(java.util.Locale.US, "$%.2f", guaranteed)} profit whichever side wins · ${bet.marketLabel} · ${bet.eventName}"
        val n = NotificationCompat.Builder(app, CHANNEL_BET)
            .setSmallIcon(R.drawable.ic_scan)
            .withWallet(app)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(EvAlerts.openVigilant(app, ID_PLACED))
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(bet.id, ID_PLACED, n) }
    }

    /** A made-up bet on the real channel ("Send a test notification"): what the next real one looks like, and whether it shows at all. */
    fun sample(app: Application): Boolean {
        if (!ScanService.canNotify(app)) return false
        ensureChannel(app)
        val n = NotificationCompat.Builder(app, CHANNEL_BET)
            .setSmallIcon(R.drawable.ic_scan)
            .withWallet(app)
            .setContentTitle("Auto-bet \$0.37 · +4.2% EV · Test bet")
            .setContentText("+117 · 3 of 3 books agree · Moneyline · A @ B · wallet \$2.10 left")
            .setStyle(NotificationCompat.BigTextStyle().bigText("This is a test: it's what a real auto-bet notification looks like. +117 · 3 of 3 books agree · Moneyline · A @ B · wallet \$2.10 left"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(EvAlerts.openVigilant(app, ID_SAMPLE))
            .build()
        return runCatching { NotificationManagerCompat.from(app).notify("auto-bet-test", ID_SAMPLE, n) }.isSuccess
    }

    fun stopped(app: Application, title: String, text: String) {
        if (!ScanService.canNotify(app) || !NotifyGate.allow()) return
        ensureChannel(app)
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_scan)
            .withWallet(app)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(EvAlerts.openVigilant(app, ID_STOP))
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(ID_STOP, n) }
    }
}
