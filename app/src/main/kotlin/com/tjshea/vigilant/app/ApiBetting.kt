package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoChecks
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.NovigBetFinder
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.signing.ManagementKey
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.trading.ApiBetPlacer
import com.tjshea.vigilant.data.novig.trading.BetLimits
import com.tjshea.vigilant.data.novig.trading.BetPlan
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.PlaceResult
import com.tjshea.vigilant.data.novig.trading.PlanResult
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.PlacedBet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Betting through Novig's API in Settings (Tj, 2026-09-29): set up or not, the subaccount's balance, and what a setup or transfer says. */
data class BettingUi(
    /** This phone holds the Vigilant subaccount's trading key: the Bet buttons show. */
    val enabled: Boolean = false,
    val balance: Double? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    /** Set while Tj came from a Bet sheet whose bet the wallet couldn't cover: Settings opens on the wallet with [TopUp.amount] typed in. */
    val topUp: TopUp? = null,
)

/**
 * "Add money" from the Bet sheet (Tj, 2026-09-29: "If my wallet is too low when I go to place a bet in the app, add a button to go directly to
 * the setting to add money to the wallet"): [amount] is what the bet is short by (whole dollars, at least $1); "Back to the bet" re-opens [bet].
 */
data class TopUp(val amount: Double, val needed: Double, val bet: BetSheetUi)

/** The Bet sheet: one bet being looked at, then placed. Nothing is sent until [confirm] and it re-checks the price then. */
data class BetSheetUi(
    val title: String,
    val subtitle: String,
    val target: BetTarget? = null,
    val stake: Double,
    /** Finding the bet's Novig outcome (a CNO bet). */
    val resolving: Boolean = true,
    val plan: BetPlan? = null,
    /** Why the bet can't be placed now, in words (a refused plan). */
    val refusal: String? = null,
    val placing: Boolean = false,
    /** How placing came out (the sheet shows it and offers Done). */
    val result: PlaceResult? = null,
    val allowRepeat: Boolean = false,
    val balance: Double? = null,
    val maxStake: Double = 10.0,
)

/** What [BetTarget] is made of, from a +EV card or a CNO card. */
object ApiBetTargets {
    fun of(o: Opportunity): BetTarget? {
        val fair = o.fairProbability ?: return null
        return BetTarget(
            market = o.market, outcomeId = o.outcome.outcomeId, league = o.league.displayName, eventName = o.event.description, startsTs = o.event.startsTs,
            marketLabel = o.marketLabel, selection = o.selection, fair = fair, fairAsOfMs = o.fairAsOfMs, source = BetTracker.SOURCE_VIGILANT, placedKey = o.key,
        )
    }

    /** A CNO bet at Novig, once its Novig outcome is found; [fairAsOfMs] is when CNO's list was current. */
    fun of(row: CnoRow, found: NovigBetFinder.Found.Bet, market: NovigMarket, fairAsOfMs: Long?): BetTarget? {
        val fair = CnoChecks.fairProbability(row) ?: return null
        return BetTarget(
            // The earlier of CrazyNinjaOdds' start and Novig's own: a game Novig has started must never be bet as pregame.
            market = market, outcomeId = found.outcomeId, league = row.league, eventName = row.event, startsTs = minOf(row.startsAtMs ?: market.startsTs, market.startsTs),
            marketLabel = row.market, selection = row.bet, fair = fair, fairAsOfMs = fairAsOfMs, source = BetTracker.SOURCE_CNO,
            placedKey = MiniWindow.cnoKey(row), book = row.book.ifBlank { "Novig" }, gameUrl = row.gameUrl, betUrl = row.betUrl,
        )
    }

    /** Only a bet priced at Novig can be placed through Novig's API. */
    fun atNovig(row: CnoRow): Boolean = row.book.isBlank() || CnoBooks.codeFor(row.book) == CnoBooks.NOVIG
}

/**
 * The state and actions of betting through Novig's API, kept out of [MainViewModel] (which owns the [UiState] it edits): set up (the management
 * key, in memory only), fund and withdraw, the Bet sheet, and syncing the Tracker with what Novig holds.
 */
class ApiBettingController(
    private val c: AppContainer,
    private val state: MutableStateFlow<UiState>,
    private val scope: CoroutineScope,
    private val toasts: MutableSharedFlow<String>,
    /** A market's book read from Novig now; null = the app's own reader ([AppContainer.novig]). Tests hand in their own. */
    private val readBook: (suspend (String) -> NovigBook?)? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var betJob: Job? = null
    private var placerFor: Any? = null
    private var placerCache: ApiBetPlacer? = null

    private fun settings() = state.value.settings
    private fun limits() = settings().let { BetLimits(it.apiMaxStake, it.apiMaxPerDay, it.apiMinEv) }

    private fun placer(): ApiBetPlacer? {
        val t = c.trading ?: return null
        if (placerFor !== t) {
            placerFor = t
            placerCache = ApiBetPlacer(t, c.tracker, books = readBook ?: ::freshBook, limits = ::limits, paused = { settings().paused }, clock = clock)
        }
        return placerCache
    }

    /** The market's book read from Novig just now (never one shown from the last scan). */
    private suspend fun freshBook(marketId: String): NovigBook? =
        withContext(Dispatchers.IO) { c.novig.books(listOf(marketId)).takeIf { it.failed == 0 && it.fromCache == 0 }?.books?.get(marketId) }

    /** Called when the connection changes or loads: whether the Bet buttons show. */
    fun refreshEnabled() {
        state.update { it.copy(betting = it.betting.copy(enabled = c.trading != null)) }
        if (c.trading != null) refreshBalance(quiet = true)
        refreshSavedKey()
    }

    // ---- setup, money ---------------------------------------------------------------------------------------------

    // ---- the saved management key ------------------------------------------------------------------------------------

    /** Reads what's saved (never the key itself) into [NovigUi.managementKey]: on open, and after a save or forget. */
    fun refreshSavedKey() {
        scope.launch {
            val hint = withContext(Dispatchers.IO) { runCatching { c.managementKeys.hint() }.getOrNull() }
            state.update { it.copy(novig = it.novig.copy(managementKey = hint)) }
        }
    }

    /**
     * The key a setup or transfer uses: the one Tj just typed ([typed]), or the saved one. Null (with the reason shown) when neither is there.
     */
    private suspend fun keyFor(typed: ManagementKey?): ManagementKey? {
        if (typed != null) return typed
        val saved = withContext(Dispatchers.IO) { runCatching { c.managementKeys.load() }.getOrNull() }
        if (saved == null) {
            fail(
                if (state.value.novig.managementKey?.unreadable == true) "The saved management key can't be unlocked on this phone any more: enter it once more below."
                else "Enter your management key ID and its .pem file first.",
            )
        }
        return saved
    }

    /** Novig has just accepted [typed] (a call signed with it went through): it's saved, and never asked for again. */
    private suspend fun remember(typed: ManagementKey?) {
        if (typed == null) return
        val hint = withContext(Dispatchers.IO + NonCancellable) { runCatching { c.managementKeys.save(typed) }.getOrNull() }
        if (hint != null) state.update { it.copy(novig = it.novig.copy(managementKey = hint)) }
    }

    /** Novig refused the call: say why, and when it was the saved key's signature, how to fix that. */
    private fun keyAdvice(e: NovigApiException, usedSaved: Boolean): String =
        e.advice + if (usedSaved && e.status == 401 && e.serverMessage?.contains("timestamp") != true) " Tap Replace to enter the key again." else ""

    /** "Save key": checks Novig accepts it (one signed echo), then saves it for every later setup and transfer. */
    fun saveKey(typed: ManagementKey) {
        if (state.value.betting.busy) return
        scope.launch {
            state.update { it.copy(betting = it.betting.copy(busy = true, error = null, message = "Checking the key with Novig…")) }
            try {
                withContext(Dispatchers.IO) { c.bettingSetup.check(typed) }
                remember(typed)
                state.update { it.copy(betting = it.betting.copy(busy = false, message = "Management key saved on this phone. You won't be asked for it again.", error = null)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NovigApiException) {
                fail(keyAdvice(e, usedSaved = false))
            } catch (e: IllegalArgumentException) {
                fail(e.message ?: "That key file couldn't be read.")
            } catch (e: Exception) {
                fail("Couldn't check the key: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** "Forget": the saved key is deleted from this phone; the next setup or transfer asks for it again. */
    fun forgetKey() {
        scope.launch {
            withContext(Dispatchers.IO + NonCancellable) { runCatching { c.managementKeys.clear() } }
            state.update { it.copy(novig = it.novig.copy(managementKey = null), betting = it.betting.copy(message = "The saved management key was removed from this phone.", error = null)) }
        }
    }

    /** [typed]: the key Tj just entered (saved once Novig accepts it); null = the saved one. */
    fun enable(typed: ManagementKey? = null) {
        val conn = state.value.novig.connection ?: return
        if (state.value.betting.busy) return
        scope.launch {
            state.update { it.copy(betting = it.betting.copy(busy = true, error = null, message = "Starting…")) }
            val key = keyFor(typed) ?: return@launch
            try {
                val updated = withContext(Dispatchers.IO) {
                    c.bettingSetup.enable(conn, key.keyId, key.pem) { step -> state.update { it.copy(betting = it.betting.copy(message = step)) } }
                }
                remember(typed)
                c.novigConnection.save(updated)
                c.useConnection(updated)
                state.update {
                    it.copy(
                        novig = it.novig.copy(connection = updated),
                        betting = it.betting.copy(enabled = c.trading != null, busy = false, message = "Betting is set up. Add money to the subaccount to place bets.", error = null),
                    )
                }
                refreshBalance(quiet = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NovigApiException) {
                fail(keyAdvice(e, usedSaved = typed == null))
            } catch (e: IllegalArgumentException) {
                fail(e.message ?: "That key file couldn't be read.")
            } catch (e: Exception) {
                fail("Setup failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun fail(text: String) = state.update { it.copy(betting = it.betting.copy(busy = false, message = null, error = text)) }

    /** Turns betting off on this phone (the Bet buttons go). The trading key stays in the Keystore, so turning it on again is quick. */
    fun disable() {
        val conn = state.value.novig.connection ?: return
        scope.launch {
            val updated = conn.copy(tradingKeyId = null, tradingAlias = null)
            runCatching { c.novigConnection.save(updated) }
            c.useConnection(updated)
            state.update { it.copy(novig = it.novig.copy(connection = updated), betting = BettingUi(message = "Betting through the API is off. Your scans and the Tracker are unchanged.")) }
        }
    }

    fun refreshBalance(quiet: Boolean = false) {
        val trading = c.trading ?: return
        val address = state.value.novig.connection?.subaccountKeyId ?: return
        scope.launch {
            val balance = try {
                withContext(Dispatchers.IO) { trading.balance(address) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NovigApiException) {
                if (!quiet) fail(e.advice)
                return@launch
            } catch (e: Exception) {
                if (!quiet) fail("Couldn't read the balance: ${e.message ?: e.javaClass.simpleName}")
                return@launch
            }
            state.update { it.copy(betting = it.betting.copy(balance = balance)) }
        }
    }

    /**
     * Moves [amount] dollars (any amount Tj types, Tj 2026-09-29) between the cash wallet and the subaccount ([direction] "fund" or "defund").
     * [typed]: the management key Tj just entered, saved once Novig accepts it; null = the saved one.
     */
    fun transfer(direction: String, amount: Double, typed: ManagementKey? = null) {
        val conn = state.value.novig.connection ?: return
        if (state.value.betting.busy || !(amount > 0.0)) return
        scope.launch {
            state.update { it.copy(betting = it.betting.copy(busy = true, error = null, message = "Starting…")) }
            val key = keyFor(typed) ?: return@launch
            try {
                val out = withContext(Dispatchers.IO) {
                    c.bettingSetup.transfer(conn, key.keyId, key.pem, direction, amount) { step -> state.update { it.copy(betting = it.betting.copy(message = step)) } }
                }
                // Novig answered the signed transfer (applied or rejected): the key is good.
                remember(typed)
                state.update {
                    it.copy(betting = it.betting.copy(busy = false, message = out.message.takeIf { out.applied }, error = out.message.takeUnless { out.applied }, balance = out.balance ?: it.betting.balance))
                }
                if (!out.applied) refreshBalance(quiet = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NovigApiException) {
                fail(keyAdvice(e, usedSaved = typed == null))
            } catch (e: IllegalArgumentException) {
                fail(e.message ?: "That key file couldn't be read.")
            } catch (e: Exception) {
                fail("The transfer failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Adds what Novig holds that the Tracker doesn't (an order whose answer was lost). [all]: every fill, not just the last hour's. */
    fun sync(all: Boolean = true) {
        val sync = c.apiSync ?: return
        scope.launch {
            val report = try {
                withContext(Dispatchers.IO) { if (all) sync.run(sinceMs = null) else sync.run() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toasts.tryEmit("Couldn't read Novig's fills: ${(e as? NovigApiException)?.advice ?: e.message ?: e.javaClass.simpleName}")
                return@launch
            }
            toasts.tryEmit(
                when {
                    report.added == 0 && report.unknown == 0 -> "The Tracker already has every bet Novig filled"
                    else -> listOfNotNull(
                        "Added ${report.added} bet${if (report.added == 1) "" else "s"} from Novig's fills".takeIf { report.added > 0 },
                        "${report.unknown} fill${if (report.unknown == 1) "" else "s"} on markets Novig no longer lists".takeIf { report.unknown > 0 },
                    ).joinToString(" · ")
                },
            )
        }
    }

    // ---- the Bet sheet --------------------------------------------------------------------------------------------

    private fun startingStake(): Double = settings().apiBetStake.coerceIn(0.01, settings().apiMaxStake)

    fun bet(o: Opportunity) {
        if (c.trading == null) return
        val target = ApiBetTargets.of(o)
        if (target == null) {
            toasts.tryEmit("This bet has no fair odds to check it against")
            return
        }
        open(target, o.selection, "${o.marketLabel} · ${o.event.description}")
    }

    /** A CNO card's bet: its Novig outcome is found first (the same match the Open button uses), then the sheet opens. */
    fun bet(row: CnoRow) {
        if (c.trading == null) return
        if (!ApiBetTargets.atNovig(row)) {
            toasts.tryEmit("Only bets priced at Novig can be placed through Novig's API")
            return
        }
        state.update { it.copy(betSheet = BetSheetUi(row.bet, "${row.market} · ${row.event}", stake = startingStake(), maxStake = settings().apiMaxStake, balance = it.betting.balance)) }
        betJob?.cancel()
        betJob = scope.launch {
            val found = withContext(Dispatchers.IO) { runCatching { c.betFinder.find(row) }.getOrNull() } as? NovigBetFinder.Found.Bet
            val market = found?.let { f -> f.market ?: f.marketId?.let { id -> withContext(Dispatchers.IO) { runCatching { c.novig.market(id) }.getOrNull() } } }
            val asOf = c.cno.state.value.snapshot?.dataAtMs
            val target = if (found != null && market != null) ApiBetTargets.of(row, found, market, asOf) else null
            if (target == null) {
                state.update { it.copy(betSheet = it.betSheet?.copy(resolving = false, refusal = "Novig's exact bet couldn't be found for this one (its market or line isn't listed the way CrazyNinjaOdds names it): use Open in Novig instead.")) }
                return@launch
            }
            state.update { it.copy(betSheet = it.betSheet?.copy(target = target, resolving = false)) }
            replan()
        }
    }

    private fun open(target: BetTarget, title: String, subtitle: String) {
        state.update {
            it.copy(betSheet = BetSheetUi(title, subtitle, target, startingStake(), resolving = false, maxStake = settings().apiMaxStake, balance = it.betting.balance))
        }
        betJob?.cancel()
        betJob = scope.launch { replan() }
    }

    fun setStake(stake: Double) {
        val sheet = state.value.betSheet ?: return
        if (sheet.placing || sheet.result != null) return
        state.update { it.copy(betSheet = sheet.copy(stake = stake.coerceIn(0.0, settings().apiMaxStake), plan = null, refusal = null)) }
        betJob?.cancel()
        betJob = scope.launch { replan() }
    }

    /** Looks at the bet again: a fresh book, the plan for the stake now, or why it can't be placed. */
    fun refreshPlan() {
        val sheet = state.value.betSheet ?: return
        if (sheet.placing || sheet.result != null || sheet.target == null) return
        state.update { it.copy(betSheet = sheet.copy(plan = null, refusal = null)) }
        betJob?.cancel()
        betJob = scope.launch { replan() }
    }

    /** "Bet it again": the sheet's plan may repeat an outcome already bet through the API. */
    fun allowRepeat() {
        val sheet = state.value.betSheet ?: return
        if (sheet.placing || sheet.result != null) return
        state.update { it.copy(betSheet = sheet.copy(allowRepeat = true, plan = null, refusal = null)) }
        betJob?.cancel()
        betJob = scope.launch { replan() }
    }

    private suspend fun replan() {
        val sheet = state.value.betSheet ?: return
        val target = sheet.target ?: return
        val placer = placer() ?: return
        val result = try {
            placer.plan(target, sheet.stake, sheet.allowRepeat)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PlanResult.Refused("Couldn't check the price: ${e.message ?: e.javaClass.simpleName}")
        }
        state.update { s ->
            val cur = s.betSheet ?: return@update s
            if (cur.stake != sheet.stake || cur.target !== target) s // The sheet moved on (a new stake, another bet): this answer is old.
            else s.copy(betSheet = when (result) {
                is PlanResult.Ready -> cur.copy(plan = result.plan, refusal = null)
                is PlanResult.Refused -> cur.copy(plan = null, refusal = result.reason)
            })
        }
    }

    /** Places the bet the sheet shows (the button is the confirm). Re-checks the price; a price that moved against Tj is refused. */
    fun confirm() {
        val sheet = state.value.betSheet ?: return
        val target = sheet.target ?: return
        val plan = sheet.plan ?: return
        if (sheet.placing || sheet.result != null) return
        val placer = placer() ?: return
        state.update { it.copy(betSheet = sheet.copy(placing = true)) }
        betJob?.cancel()
        betJob = scope.launch {
            // Once the order may be on its way it is always followed to its end (and recorded) even if the sheet is closed: a cancelled call
            // would drop the answer to an order Novig has already taken.
            val result = try {
                withContext(Dispatchers.IO + NonCancellable) { placer.place(target, sheet.stake, plan.limitPrice, sheet.allowRepeat) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlaceResult.Unconfirmed("Something went wrong while placing it (${e.message ?: e.javaClass.simpleName}). Nothing is assumed: tap Sync with Novig in the Tracker in a minute.")
            }
            state.update { it.copy(betSheet = it.betSheet?.copy(placing = false, result = result)) }
            if (result is PlaceResult.Placed) {
                withContext(NonCancellable) {
                    hideFromLists(target, result)
                    refreshBalance(quiet = true)
                }
                toasts.tryEmit("Bet placed: ${money(result.bet.stake)} on ${target.selection}")
            }
        }
    }

    /** The bet leaves the +EV and CNO lists the way a ✓ makes it ([PlacedBets]): it's placed. */
    private suspend fun hideFromLists(target: BetTarget, placed: PlaceResult.Placed) {
        val key = target.placedKey ?: return
        runCatching {
            c.placed.mark(
                PlacedBet(
                    key = key, title = target.selection, detail = "${target.marketLabel} · ${target.eventName}", odds = MiniWindow.american(placed.bet.american ?: 0),
                    placedAtMs = clock(), startsAtMs = target.startsTs, event = target.eventName, market = target.marketLabel,
                    outcomeId = target.outcomeId, league = target.league,
                ),
            )
        }
    }

    // ---- "Add money" from the sheet -------------------------------------------------------------------------------

    /**
     * The sheet's "Add money to the wallet": closes the sheet and asks Settings to open on the wallet with what the bet is short by typed in
     * (the caller switches to Settings). The bet is kept for "Back to the bet".
     */
    fun requestTopUp() {
        val sheet = state.value.betSheet ?: return
        if (sheet.placing || sheet.result != null) return
        val cost = sheet.plan?.expectedCost ?: sheet.stake
        val needed = (cost - (state.value.betting.balance ?: sheet.balance ?: 0.0)).coerceAtLeast(0.0)
        betJob?.cancel()
        state.update {
            it.copy(
                betSheet = null,
                betting = it.betting.copy(topUp = TopUp(WalletAmount.suggest(needed), needed, sheet.copy(plan = null, refusal = null)), message = null, error = null),
            )
        }
    }

    /** "Back to the bet": the same bet and amount, priced again from a fresh book against the wallet as it is now. */
    fun backToBet() {
        val top = state.value.betting.topUp ?: return
        state.update { it.copy(betting = it.betting.copy(topUp = null)) }
        val bet = top.bet
        val target = bet.target
        if (target == null) {
            // A CNO bet whose Novig outcome wasn't found yet: nothing to re-open.
            toasts.tryEmit("Open the bet again from its card")
            return
        }
        state.update {
            it.copy(betSheet = bet.copy(stake = bet.stake.coerceIn(0.0, settings().apiMaxStake), resolving = false, placing = false, result = null, maxStake = settings().apiMaxStake, balance = it.betting.balance))
        }
        betJob?.cancel()
        betJob = scope.launch { replan() }
    }

    /** The wallet banner's ✕, or leaving Settings: the pending bet is let go. */
    fun dismissTopUp() {
        if (state.value.betting.topUp != null) state.update { it.copy(betting = it.betting.copy(topUp = null)) }
    }

    fun dismiss() {
        // A bet being placed is never cancelled by closing the sheet: it finishes in the background and lands in the Tracker.
        if (state.value.betSheet?.placing != true) betJob?.cancel()
        state.update { it.copy(betSheet = null) }
    }

    private fun money(v: Double) = String.format(Locale.US, "$%.2f", v)
}
