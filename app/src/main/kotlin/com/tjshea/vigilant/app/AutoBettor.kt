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
import com.tjshea.vigilant.data.novig.trading.BetLimits
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.PlaceResult
import com.tjshea.vigilant.data.reference.SharpBooks
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SharpConfirm
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.SharpMode
import com.tjshea.vigilant.data.scanner.SharpVeto
import com.tjshea.vigilant.data.scanner.TrapGuard
import com.tjshea.vigilant.data.tracker.AtBet
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
        // The kill switch (Tj, 2026-10-05): whoever calls, and whatever the settings passed in say, a stopped app places nothing.
        if (settings.killed || c.settingsStore.flow.value?.killed == true) return finish(now, Report(), blocker = "Stopped by the STOP button: tap RESUME on the red bar to run again")
        settings.autoBetHalted?.let { return finish(now, Report(halted = true), blocker = "stopped: $it (the Auto-bet tab › Resume auto-bet)") }
        failure?.takeIf { now < failedUntilMs }?.let { return finish(now, Report(stopped = it), blocker = it) }
        val placer = placer()
        if (!AppBook.isNovig || placer == null) return finish(now, Report(), blocker = "Betting through Novig's API isn't set up (Settings › Betting & Novig account › Enable betting)")
        cooldown.entries.removeAll { it.value <= now }

        val skipped = LinkedHashMap<String, Int>()
        fun skip(reason: String) { skipped.merge(reason, 1, Int::plus) }

        // Pregame bets at Novig whose books were read, best edge first. Each is judged on Novig's price read in the last minute: a bet with no
        // such price isn't placed (its edge is whatever it was a while ago).
        val all = AlertPicks.cnoChecked(state, rules.minEv, now)
        // The trap guard's first rule: games too far off were left out of the candidates (their books weren't even read); counted here.
        AlertPicks.tooEarly(state, rules.minEv, now).takeIf { it > 0 }?.let { n -> skipped[TrapGuard.earlyReason(settings.trapEarlyHours)] = n }
        val passing = ArrayList<AlertPicks.CnoChecked>()
        for (item in all.distinctBy { it.pick.row.key }.sortedByDescending { it.shown.ev }) {
            val row = item.pick.row
            val startsAt = row.startsAtMs
            when {
                !ApiBetTargets.atNovig(row) -> skip("not priced at Novig")
                item.pick.live || startsAt == null || startsAt - now < MIN_LEAD_MS -> skip("not pregame (live betting isn't available)")
                item.live == null -> skip("no Novig price read in the last minute")
                (cooldown[row.key] ?: 0L) > now -> skip("tried a moment ago")
                else -> AutoBet.judge(rules, item.shown.ev, item.check, item.shown.row.odds, BetKind.of(row.market, row.bet))?.let(::skip)
                    ?: sharpReason(item, settings, state, now)?.let(::skip)
                    ?: passing.add(item)
            }
        }
        if (passing.isEmpty()) {
            // Nothing to bet this cycle, but the wallet this cycle read may already be empty (Tj, 2026-10-02 ~22:10Z): asleep until he adds money.
            c.wallet.last?.takeIf { now - it.atMs < WalletBalance.FRESH_MS }?.let { w ->
                if (w.dollars < AutoBet.MIN_STAKE) { if (!walletEmptyNoted) walletRanOut(w.dollars) } else walletEmptyNoted = false
            }
            return finish(now, Report(looked = all.size, skipped = skipped))
        }

        val balance0 = try {
            wallet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: NovigApiException) {
            return stopFor(now, Report(looked = all.size, passed = passing.size, skipped = skipped), "Couldn't read the wallet: ${e.advice}")
        } catch (e: Exception) {
            return stopFor(now, Report(looked = all.size, passed = passing.size, skipped = skipped), "Couldn't read the wallet: ${e.message ?: e.javaClass.simpleName}")
        } ?: return finish(now, Report(looked = all.size, passed = passing.size, skipped = skipped), blocker = "Betting through Novig's API isn't set up (Settings › Betting & Novig account › Enable betting)")
        var balance = balance0
        if (balance >= AutoBet.MIN_STAKE) walletEmptyNoted = false

        // One bet per Novig market (either side) while any is open: the other side of a line is never bet after the first.
        val openMarkets = c.tracker.all().filter { it.status == BetStatus.PENDING && it.marketId.isNotBlank() }.mapTo(HashSet()) { it.marketId }
        val limits = BetLimits(
            maxStake = rules.maxStake, maxPerDay = settings.apiMaxPerDay, minEv = rules.minEv, maxOdds = rules.maxOdds, maxPerGame = settings.apiMaxPerGame,
            minEvWhere = "Auto-bet tab › Smallest edge (EV) at Novig's price now",
        )
        // One game is one event (Tj, 2026-10-04: "auto bet placed bets on a team at +5, then the same team at +6, then the same team at +10"): what is at risk
        // on each game across all its markets, open bets and resting bids, as the cycle began. The placer checks it again on its own read of the Tracker
        // (so a bet placed earlier this cycle, or by hand meanwhile, counts there); this pass is what keeps a full game from costing a book read per bet.
        val exposure = if (settings.apiMaxPerGame > 0.0) GameExposure.items(c.tracker.all()) + GameExposure.bidItems(c.makerStore.all()) else emptyList()
        val placed = ArrayList<TrackedBet>()
        var stopped: String? = null
        var walletEmpty = false
        var halted = false

        // When the wallet or the cycle's cap can't take every bet, the money goes first to the edges most likely to hold (RESEARCH.md §72): the sharpest
        // book's own edge where its veto priced the bet, else 70% of the shown edge ([AutoBet.credibleEv]). Ties keep the shown edge's order.
        val vetoOn = settings.sharpAutoBet == SharpMode.VETO
        val ordered = passing.sortedByDescending { AutoBet.credibleEv(it.shown.ev, vetoSaid[it.pick.row.key]?.takeIf { v -> vetoOn && v.verdict == SharpVeto.Verdict.PASSED }?.ev) }
        for (item in ordered) {
            currentCoroutineContext().ensureActive()
            // STOP pressed since this cycle began: no further order, whatever the settings this pass started with said (Tj, 2026-10-05). (A Pause is held at each
            // order by the placer's own check: ApiBetPlacer's `paused`.)
            if (c.settingsStore.flow.value?.killed == true) { stopped = "the STOP button was pressed while this pass ran"; break }
            if (placed.size >= AutoBet.MAX_PER_CYCLE) { stopped = "placed ${AutoBet.MAX_PER_CYCLE} this cycle (the best edges first); the rest wait for the next"; break }
            val row = item.pick.row
            // A Kelly stake is never sized on more edge than the sharpest book backs (its own fair, where its veto priced the bet: RESEARCH.md §72).
            val sharpFair = vetoSaid[row.key]?.takeIf { v -> vetoOn && v.verdict == SharpVeto.Verdict.PASSED }?.fair
            val stake = when (val s = AutoBet.stake(rules, item.shown.row, settings.bankroll, balance, sharpFair)) {
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
            val game = GameRef(target.market.eventId, target.eventName, target.startsTs, target.league)
            if (settings.apiMaxPerGame > 0.0) {
                val check = GameExposure.check(game, exposure, target.market.marketId, target.outcomeId, stake, settings.apiMaxPerGame)
                if (check.blocked) { noteGameLimit(check); skip(AutoBet.GAME_LIMIT_SKIP); continue }
            }
            // The trap guard's second rule (RESEARCH.md §71): on a game line, Novig's own trades say whether the price just moved to make it look cheap.
            val moveReason = novigMove(item, target, settings)
            if (moveReason != null) { cooldown[row.key] = clock() + AutoBet.COOLDOWN_MS; skip(moveReason); continue }

            // The order is marked in flight BEFORE it can be sent (saved, and auto-bet stays stopped until it's cleared): if the process dies after
            // Novig takes the order and before the Tracker has it (Tj's v0.38.0 report: the app crashed, out of memory), the next cycle would find
            // the same bet again with nothing on record and place it twice. The marker survives that; a restart finds auto-bet stopped, with why.
            val marker = inFlightNote(target, stake)
            withContext(NonCancellable) { runCatching { c.settingsStore.update { it.copy(autoBetHalted = marker) } } }
            // Once an order may be on its way it is followed to its end and recorded, whatever happens to this coroutine.
            val result = try {
                withContext(Dispatchers.IO + NonCancellable) { placer.placeAuto(target.copy(auto = true, atBet = recordOf(item, state, settings, stake, now)), stake, limits, AutoBet.priceOf(item.shown.row)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlaceResult.Unconfirmed("Something went wrong while placing it (${e.message ?: e.javaClass.simpleName}).")
            }
            // A definitive answer (placed, nothing filled, refused, Novig said no) clears the marker; a lost answer replaces it below.
            if (result !is PlaceResult.Unconfirmed) {
                withContext(NonCancellable) { runCatching { c.settingsStore.update { if (it.autoBetHalted == marker) it.copy(autoBetHalted = null) else it } } }
            }
            when (result) {
                is PlaceResult.Placed -> {
                    val bet = result.bet
                    placed += bet
                    openMarkets += target.market.marketId
                    balance -= bet.stake
                    withContext(NonCancellable) { markPlaced(c, target, result, clock()) }
                    notes.placed(app, target, bet, item.check, walletLeft = balance, sharp = sharpSaid[row.key]?.takeIf { it.confirmed }?.detail)
                }
                is PlaceResult.NotFilled -> { cooldown[row.key] = now + AutoBet.COOLDOWN_MS; skip("nobody was selling at that price") }
                is PlaceResult.Refused -> {
                    cooldown[row.key] = now + AutoBet.COOLDOWN_MS
                    if (result.tooSmall) tooSmallBelow = maxOf(tooSmallBelow, stake)
                    if (result.reason.contains("daily limit")) {
                        stopped = "your daily limit of ${money(settings.apiMaxPerDay)} for API bets is reached"
                        failure = "waiting: $stopped (checked again every ${failBackoffMs / 60_000L} minutes)"
                        failedUntilMs = now + failBackoffMs
                        break
                    }
                    if (result.gameLimit) { c.eventLog.info("AUTOBET", "held back: ${result.reason}"); skip(AutoBet.GAME_LIMIT_SKIP) } else skip(result.reason.take(REASON_CHARS))
                }
                is PlaceResult.Failed -> {
                    stopped = "Novig refused: ${result.message}"
                    failure = "waiting after Novig refused an order: ${result.message}"
                    failedUntilMs = now + failBackoffMs
                    break
                }
                is PlaceResult.Unconfirmed -> {
                    // Nothing says whether it filled: never again until Tj has looked (Novig, then the Auto-bet tab › Resume).
                    halted = true
                    stopped = "an order's answer was lost, so auto-bet is stopped: ${result.message}"
                    withContext(NonCancellable) { runCatching { c.settingsStore.update { it.copy(autoBetHalted = result.message) } } }
                    break
                }
            }
        }
        if (walletEmpty && !walletEmptyNoted) walletRanOut(balance)
        val report = Report(looked = all.size, passed = passing.size, placed = placed, skipped = skipped, stopped = stopped, walletEmpty = walletEmpty, halted = halted)
        if (stopped != null && !walletEmpty) postStop(now, stopped, halted)
        return finish(now, report, balance = balance)
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
        runCatching { c.eventLog.info("AUTOBET", "wallet empty (${money(balance)}): scanning paused") }
        notes.stopped(
            app, "Wallet empty: Vigilant is asleep",
            "The Vigilant wallet has ${money(balance)}, so auto-bet can't place anything: scanning is paused (nothing is read, nothing is bet). " +
                "Add money in Settings › Betting & Novig account, then tap Resume.",
        )
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
     * ([TrapGuard.MOVE_KINDS]) with [ScanSettings.trapNovigMove] on are read; a read that fails stops nothing (the guard adds a check, it never
     * blocks betting on a hiccup) and is recorded as UNREAD.
     */
    private suspend fun novigMove(item: AlertPicks.CnoChecked, target: BetTarget, settings: ScanSettings): String? {
        val row = item.shown.row
        val kind = BetKind.of(row.market, row.bet)
        if (!settings.trapNovigMove || kind !in TrapGuard.MOVE_KINDS) return null
        val move = try {
            TrapGuard.move(recentTrades(target.market.marketId), target.outcomeId, AutoBet.priceOf(row), clock())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            moveSaid[item.pick.row.key] = "UNREAD · ${(e.message ?: e.javaClass.simpleName).take(REASON_CHARS)}"
            c.eventLog.count("trap.move.UNREAD")
            return null
        }
        val verdict = when {
            move.level == null -> "NO LEVEL"
            move.trap -> "TRAP"
            else -> "CLEAR"
        }
        moveSaid[item.pick.row.key] = "$verdict · ${TrapGuard.describe(move)}"
        c.eventLog.count("trap.move.${verdict.replace(' ', '_')}")
        val why = TrapGuard.moveReason(kind, move) ?: return null
        c.eventLog.info("AUTOBET", "trap guard: ${row.bet}: $why")
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
        /** Auto-bet bets the sharp veto stopped only for its bar (the sharpest book gave a positive edge under [ScanSettings.sharpVetoMinEv]). */
        const val SHARP_BAR_COUNTER = "sharpbar.autobet.under"

        /** The same for CNO's alerts. */
        const val SHARP_BAR_ALERT_COUNTER = "sharpbar.alerts.under"

        /** The report's reason for a game line the trap guard's move rule stopped (one wording, so the report counts them together). */
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
            val found = withContext(Dispatchers.IO) { runCatching { c.betFinder.find(row) }.getOrNull() } as? NovigBetFinder.Found.Bet ?: return null
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
    fun text(bet: TrackedBet, target: BetTarget, check: CnoBooks.Check, walletLeft: Double? = null): String {
        val odds = bet.american?.let { if (it > 0) "+$it" else "$it" } ?: "?"
        val left = walletLeft?.let { " · wallet ${String.format(Locale.US, "$%.2f", it.coerceAtLeast(0.0))} left" }.orEmpty()
        return "$odds · ${check.agreeing} of ${check.twoSided} books agree · ${target.marketLabel} · ${target.eventName}$left"
    }

    /** Why a bet's notification wouldn't show on this phone (Android's permission or settings), or null when it will. */
    fun blocked(context: android.content.Context): String? {
        if (!ScanService.canNotify(context)) return "Notifications are not allowed for Vigilant (Android Settings › Apps › Vigilant › Notifications)"
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return "Notifications are switched off for Vigilant (Android Settings › Apps › Vigilant › Notifications)"
        val importance = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL_BET)?.importance
        if (importance == NotificationManager.IMPORTANCE_NONE) return "The \"Auto-bet bets placed\" notification channel is switched off (Android Settings › Apps › Vigilant › Notifications)"
        return null
    }

    fun placed(app: Application, target: BetTarget, bet: TrackedBet, check: CnoBooks.Check, walletLeft: Double? = null, sharp: String? = null) {
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
        if (!ScanService.canNotify(app)) return
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
