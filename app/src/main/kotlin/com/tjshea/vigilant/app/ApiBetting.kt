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
import kotlinx.coroutines.delay
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
 * the setting to add money to the wallet"): [amount] is typed in for Tj ([needed], what the bet is short by, rounded up to whole dollars, at
 * least $1); [cost] is what the bet costs; "Back to the bet" re-opens [bet].
 */
data class TopUp(val amount: Double, val needed: Double, val cost: Double, val bet: BetSheetUi)

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
    /** Novig has the order and is finishing it (about 5 s): the sheet can be closed or the next bet started, and the result lands in the Tracker and a notification (Tj, 2026-10-12). */
    val sent: Boolean = false,
    /** How placing came out (the sheet shows it and offers Done). */
    val result: PlaceResult? = null,
    val allowRepeat: Boolean = false,
    val balance: Double? = null,
    val maxStake: Double = 10.0,
    /** Tj picked or typed the amount: the wallet's balance, read after the sheet opened, never changes it. */
    val stakeChosen: Boolean = false,
    /** The amount the sheet chose before the wallet had its say (Settings' amount, or the bet's Kelly stake): [stake] when the wallet holds enough. */
    val baseStake: Double = stake,
    /** Where the amount came from, under the Amount field ("¼ Kelly of your $1,000 bankroll at these odds"); null = Settings' amount. */
    val stakeNote: String? = null,
    /** Money is being moved into the wallet from this sheet's "Add money" ([ApiBetting.fundFromSheet]). */
    val funding: Boolean = false,
    /** What Novig said of the last "Add money" from this sheet (applied), or why it wasn't. */
    val fundMessage: String? = null,
    val fundError: String? = null,
)

/**
 * The Bet sheet's amount (Tj, 2026-09-30: "anywhere in the app where I use the vigilant wallet to place bets in the app, I can type in a custom
 * account for any bet manually. And if I have less than one dollar in the wallet, it automatically enters whatever is left in the wallet as the
 * bet amount"): any amount in dollars and cents up to the per-bet limit, typed or picked; a sheet opens at Settings' bet amount, or at what's
 * left in the wallet when that's less (a wallet under $1 bets all it holds).
 */
object BetAmount {
    /** The dollars in [text], when it's an amount this bet can take (more than $0, at most [max]); else null. */
    fun parse(text: String, max: Double): Double? = WalletAmount.parse(text)?.takeIf { it <= max + 1e-9 }

    /** Why [text] can't be bet, in words; null when it can (or the field is still empty). */
    fun problem(text: String, max: Double): String? {
        if (text.isBlank()) return null
        WalletAmount.parse(text)?.let { v ->
            return if (v > max + 1e-9) "Over your ${String.format(Locale.US, "$%.2f", max)} limit per bet (Settings › Betting & Novig account)" else null
        }
        return WalletAmount.problem(text)
    }

    /**
     * The amount a bet's sheet starts from before the wallet has its say, and why (Tj, 2026-10-01: "Make sure it enters the Kelley value if I
     * select it"): with Kelly chosen for bet slips, the bet's own Kelly stake ([kelly], worked out for its odds and edge) to the cent within
     * [ScanSettings.apiMaxStake]; with "My amount", that amount; with $1, $1; otherwise (no amount for the slip) Settings' "My amount" ([ScanSettings.slipCustomStake]), the one amount.
     */
    fun base(settings: com.tjshea.vigilant.data.scanner.ScanSettings, kelly: Double?): Pair<Double, String?> {
        val max = settings.apiMaxStake
        fun money(v: Double) = String.format(Locale.US, "$%,.2f", v)
        return when (settings.slipStake) {
            com.tjshea.vigilant.data.novig.SlipStake.KELLY -> {
                val k = kelly?.takeIf { it > 0 }?.let { kotlin.math.round(it * 100.0) / 100.0 }?.coerceAtLeast(0.01)
                when {
                    k == null -> settings.slipCustomStake.coerceIn(0.01, max) to "No Kelly stake for this bet (no edge at this price): Settings' amount instead"
                    k > max + 1e-9 -> max to "Kelly says ${money(k)}: held to your ${money(max)} limit per bet"
                    else -> k to "${com.tjshea.vigilant.app.ui.Format.kellyLabel(settings.kellyMultiplier)} of your ${money(settings.bankroll)} bankroll at this bet's odds"
                }
            }
            com.tjshea.vigilant.data.novig.SlipStake.CUSTOM -> settings.slipCustomStake.coerceIn(0.01, max) to "Your bet slip amount (Settings)"
            // $1 for the bet slip is $1 here too (2026-10-02 ~18:10Z: the sheet used to start at its own amount while the slip said $1).
            com.tjshea.vigilant.data.novig.SlipStake.ONE_DOLLAR -> 1.0.coerceIn(0.01, max) to null
            else -> settings.slipCustomStake.coerceIn(0.01, max) to null
        }
    }

    /** What a sheet opens with: [setting] (within [max]), or [balance] rounded down to the cent when the wallet holds less but not nothing. */
    fun starting(setting: Double, max: Double, balance: Double?): Double {
        val base = setting.coerceIn(0.01, max)
        val left = balance?.let { kotlin.math.floor(it * 100.0 + 1e-6) / 100.0 } ?: return base
        return if (left >= 0.01 && left < base - 1e-9) left else base
    }
}

/**
 * The amount Tj types for the Vigilant wallet (Tj, 2026-09-29: "allow me to add custom amounts to the vigilant wallet in the app settings by
 * typing in an amount"): dollars and cents, "$" and thousands commas allowed, more than $0 and at most [MAX] (a typo guard: an extra zero
 * shouldn't move a month's bankroll).
 */
object WalletAmount {
    const val MAX = 10_000.0

    /** The dollars in [text], or null when it isn't an amount that can be sent. */
    fun parse(text: String): Double? {
        val t = text.trim().removePrefix("$").replace(",", "").trim()
        if (!Regex("""\d{1,7}(\.\d{0,2})?|\.\d{1,2}""").matches(t)) return null
        val v = t.toDoubleOrNull() ?: return null
        return v.takeIf { it >= 0.01 && it <= MAX }
    }

    /** Why [text] can't be sent, in words; null when it can (or the field is still empty). */
    fun problem(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        if (parse(t) != null) return null
        val v = t.removePrefix("$").replace(",", "").trim().toDoubleOrNull()
        return when {
            v == null -> "Type an amount in dollars, like 25 or 12.50"
            v > MAX -> "At most ${String.format(Locale.US, "$%,.0f", MAX)} at a time"
            v < 0.01 -> "More than \$0, please"
            else -> "Dollars and cents only (two decimal places)"
        }
    }

    /** What a bet short by [needed] dollars suggests adding: whole dollars, rounded up, at least $1. */
    fun suggest(needed: Double): Double = kotlin.math.ceil(needed - 1e-9).coerceAtLeast(1.0).coerceAtMost(MAX)

    /** [v] as the field shows it: "25" for whole dollars, "12.50" otherwise. */
    fun text(v: Double): String = if (v == kotlin.math.floor(v)) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.2f", v)
}

/** What [BetTarget] is made of, from a +EV card or a CNO card. */
object ApiBetTargets {
    fun of(o: Opportunity): BetTarget? {
        val fair = o.fairProbability ?: return null
        return BetTarget(
            market = o.market, outcomeId = o.outcome.outcomeId, league = o.league.displayName, eventName = o.event.description, startsTs = o.event.startsTs,
            marketLabel = o.marketLabel, selection = o.selection, fair = fair, fairAsOfMs = o.fairAsOfMs, source = BetTracker.SOURCE_VIGILANT, placedKey = o.key,
            basis = com.tjshea.vigilant.data.tracker.FairBasis.of(o),
            live = o.isLive,
            liveLane = o.refEvent?.id?.startsWith("pinnacle-live-") == true,
            listedPrice = o.quote?.price,
            sharpAgainst = o.agreement.sharpAgainst,
        )
    }

    /**
     * A CNO bet at Novig, once its Novig outcome is found; [fairAsOfMs] is when CNO's list was current. A ParlayAPI pick (TASKS.md P1) is the
     * same CNO-shaped row with ParlayAPI's fair odds: [source] [BetTracker.SOURCE_PARLAY], its own [placedKey], [fairAsOfMs] when its board was read.
     */
    fun of(
        row: CnoRow,
        found: NovigBetFinder.Found.Bet,
        market: NovigMarket,
        fairAsOfMs: Long?,
        source: String = BetTracker.SOURCE_CNO,
        placedKey: String = MiniWindow.cnoKey(row),
    ): BetTarget? {
        val fair = CnoChecks.fairProbability(row) ?: return null
        return BetTarget(
            // The earlier of CrazyNinjaOdds' start and Novig's own: a game Novig has started must never be bet as pregame.
            market = market, outcomeId = found.outcomeId, league = row.league, eventName = row.event, startsTs = minOf(row.startsAtMs ?: market.startsTs, market.startsTs),
            marketLabel = row.market, selection = row.bet, fair = fair, fairAsOfMs = fairAsOfMs, source = source,
            placedKey = placedKey, book = row.book.ifBlank { "Novig" }, gameUrl = row.gameUrl, betUrl = row.betUrl,
            basis = com.tjshea.vigilant.data.tracker.FairBasis(
                if (source == BetTracker.SOURCE_PARLAY) com.tjshea.vigilant.data.tracker.FairBasis.SOURCE_PARLAY else com.tjshea.vigilant.data.tracker.FairBasis.SOURCE_CNO,
                books = row.books ?: 0,
            ),
        )
    }

    /** A ParlayAPI pick's target (TASKS.md P1): its Novig row at ParlayAPI's fair odds, read at [boardAtMs]. */
    fun of(p: com.tjshea.vigilant.data.reference.ParlayPick, found: NovigBetFinder.Found.Bet, market: NovigMarket, boardAtMs: Long?): BetTarget? =
        of(p.row, found, market, boardAtMs, BetTracker.SOURCE_PARLAY, p.key)

    /** Only a bet priced at Novig can be placed through Novig's API. */
    fun atNovig(row: CnoRow): Boolean = row.book.isBlank() || CnoBooks.codeFor(row.book) == CnoBooks.NOVIG
}

/**
 * The state and actions of betting through Novig's API, kept out of [MainViewModel] (which owns the [UiState] it edits): set up (the management
 * key, entered once and then saved sealed on this phone: [ManagementKeyStore]), fund and withdraw any amount Tj types, the Bet sheet and its
 * way to the wallet ("Add money"), and syncing the Tracker with what Novig holds.
 */
private class LiveRowGone : RuntimeException()

class ApiBettingController(
    private val c: AppContainer,
    private val state: MutableStateFlow<UiState>,
    private val scope: CoroutineScope,
    private val toasts: MutableSharedFlow<String>,
    /** A market's book read from Novig now; null = the app's own reader ([AppContainer.novig]). Tests hand in their own. */
    private val readBook: (suspend (String) -> NovigBook?)? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    /** A bet that missed came back to the lists: its market is priced again now, so a bet no longer +EV does not show (Tj, 2026-10-13). */
    private val onRestored: (Collection<String>) -> Unit = {},
    /** [MISS_RESTORE_MS]; tests shorten it. */
    private val missRestoreMs: Long = MISS_RESTORE_MS,
) : com.tjshea.vigilant.app.ui.ApiBetTarget {
    private var betJob: Job? = null

    /** The one placer for the trading client in use (its lock keeps orders one at a time), made under [placerLock]. */
    private var placerCache: Pair<Any, ApiBetPlacer>? = null
    private val placerLock = Any()

    private fun settings() = state.value.settings
    /** A Bet sheet's bet is placed by hand: only Tj's own dollar limits, no minimum edge ([BetLimits.manual]). */
    private fun limits() = settings().let { BetLimits.manual(it.apiMaxStake, it.apiMaxPerDay, it.apiMaxPerGame) }

    /**
     * Plans run on the default dispatcher's threads, and two can start together (a typed amount right after the sheet opened): made and
     * read under one lock, so neither sees a placer half set up (one did, and planned nothing) and there's never a second placer with a
     * second order lock.
     */
    private fun placer(): ApiBetPlacer? = synchronized(placerLock) {
        val t = c.trading ?: return null
        placerCache?.takeIf { it.first === t }?.second
            ?: ApiBetPlacer(
                t, c.tracker, books = readBook ?: ::freshBook, limits = ::limits, paused = { settings().paused }, clock = clock, lock = c.betLock,
                restingBids = { com.tjshea.vigilant.data.tracker.GameExposure.bidItems(c.makerStore.all()) },
                events = c::orderEvents, warm = { c.orderSocket?.warm() }, timing = { c.eventLog.info("BET", it) },
                profit = { if (settings().gates.profitProven) com.tjshea.vigilant.data.scanner.ProfitProfile.shipped else null },
                repeatRules = { settings().gates.repeatRules() },
                restingPositions = { com.tjshea.vigilant.data.tracker.RepeatGuard.fromBids(c.makerStore.all()) },
            ).also { placerCache = t to it }
    }

    /** The market's book read from Novig just now (never one shown from the last scan). */
    private suspend fun freshBook(marketId: String): NovigBook? = c.freshBook(marketId)

    /** Called when the connection changes or loads: whether the Bet buttons show. */
    fun refreshEnabled() {
        state.update { it.copy(betting = it.betting.copy(enabled = c.trading != null)) }
        if (c.trading != null) refreshBalance(quiet = true)
        refreshSavedKey()
    }

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
    private suspend fun keep(typed: ManagementKey?) {
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
                keep(typed)
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

    // ---- setup, money ---------------------------------------------------------------------------------------------

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
                keep(typed)
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

    /** Re-reads the saved connection after something else changed it (the trading key shared with GitHub's engine, TCL20c). */
    suspend fun reloadConnection() {
        val conn = runCatching { c.novigConnection.load() }.getOrNull() ?: return
        state.update { it.copy(novig = it.novig.copy(connection = conn), betting = it.betting.copy(enabled = c.trading != null)) }
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
        if (c.trading == null || state.value.novig.connection?.subaccountKeyId == null) return
        scope.launch {
            val balance = readBalance(quiet) ?: return@launch
            state.update { it.copy(betting = it.betting.copy(balance = balance)) }
        }
    }

    /** The subaccount's balance now, or null (said unless [quiet]) when it can't be read. */
    private suspend fun readBalance(quiet: Boolean = true): Double? {
        val trading = c.trading ?: return null
        val address = state.value.novig.connection?.subaccountKeyId ?: return null
        return try {
            withContext(Dispatchers.IO) { trading.balance(address) }.also { c.wallet.record(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NovigApiException) {
            if (!quiet) fail(e.advice)
            null
        } catch (e: Exception) {
            if (!quiet) fail("Couldn't read the balance: ${e.message ?: e.javaClass.simpleName}")
            null
        }
    }

    /**
     * Moves [amount] dollars (any amount Tj types, Tj 2026-09-29) between the cash wallet and the subaccount ([direction] "fund" or "defund").
     * [typed]: the management key Tj just entered, saved once Novig accepts it; null = the saved one.
     */
    fun transfer(direction: String, amount: Double, typed: ManagementKey? = null) {
        if (state.value.novig.connection == null) return
        if (state.value.betting.busy || !(amount > 0.0)) return
        scope.launch { transferNow(direction, amount, typed) }
    }

    /** The transfer itself, to its end: true when Novig applied it (its words are in [BettingUi.message]), false when it didn't (in [BettingUi.error]). */
    private suspend fun transferNow(direction: String, amount: Double, typed: ManagementKey?): Boolean {
        val conn = state.value.novig.connection ?: return false
        state.update { it.copy(betting = it.betting.copy(busy = true, error = null, message = "Starting…")) }
        val key = keyFor(typed) ?: return false
        try {
            val out = withContext(Dispatchers.IO) {
                c.bettingSetup.transfer(conn, key.keyId, key.pem, direction, amount) { step -> state.update { it.copy(betting = it.betting.copy(message = step)) } }
            }
            // Novig answered the signed transfer (applied or rejected): the key is good.
            keep(typed)
            out.balance?.let { c.wallet.record(it) }
            state.update {
                it.copy(betting = it.betting.copy(busy = false, message = out.message.takeIf { out.applied }, error = out.message.takeUnless { out.applied }, balance = out.balance ?: it.betting.balance))
            }
            if (!out.applied) refreshBalance(quiet = true)
            // Money in or out changes what was put in: the Tracker's all-time figure is read again (PL).
            else scope.launch { runCatching { c.walletMoney.refresh(maxAgeMs = 0L) } }
            return out.applied
        } catch (e: CancellationException) {
            throw e
        } catch (e: NovigApiException) {
            fail(keyAdvice(e, usedSaved = typed == null))
        } catch (e: IllegalArgumentException) {
            fail(e.message ?: "That key file couldn't be read.")
        } catch (e: Exception) {
            fail("The transfer failed: ${e.message ?: e.javaClass.simpleName}")
        }
        return false
    }

    /**
     * "Add money" inside a Bet sheet (Tj, 2026-10-01: "a button in all the bet slips … to add money to the vigilant wallet in amounts of $1, 2, 5, 10, 15,
     * 20, or an amount I type in"): moves [amount] from the cash wallet to the Vigilant wallet with the management key saved on this phone, without leaving
     * the sheet, then reads the wallet again so the bet's amount and plan follow it. The sheet says what Novig answered.
     */
    fun fundFromSheet(amount: Double) {
        val sheet = state.value.betSheet ?: return
        if (sheet.placing || sheet.funding || state.value.betting.busy || !(amount > 0.0)) return
        state.update { it.copy(betSheet = sheet.copy(funding = true, fundMessage = null, fundError = null)) }
        scope.launch {
            val applied = transferNow("fund", amount, null)
            val b = state.value.betting
            state.update { it.copy(betSheet = it.betSheet?.copy(funding = false, fundMessage = b.message.takeIf { applied }, fundError = b.error.takeUnless { applied })) }
            if (applied) checkWallet()
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
            // A fill the sync added may be one of Vigilant's own bids: it reads as a bid in the Tracker from now on ([com.tjshea.vigilant.data.tracker.TrackedBet.isBid]).
            if (report.added > 0) withContext(Dispatchers.IO) { runCatching { c.tracker.tagBids(c.makerStore.all().mapNotNullTo(HashSet()) { it.orderId }) } }
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

    /** A new sheet for a bet whose Kelly stake is [kelly]: its amount (the wallet's remainder when that's less) and where it came from. */
    private fun newSheet(title: String, subtitle: String, target: BetTarget?, kelly: Double?, resolving: Boolean): BetSheetUi {
        val (base, note) = BetAmount.base(settings(), kelly)
        val balance = state.value.betting.balance
        return BetSheetUi(
            title, subtitle, target, BetAmount.starting(base, settings().apiMaxStake, balance), resolving = resolving,
            maxStake = settings().apiMaxStake, balance = balance, baseStake = base, stakeNote = note,
        )
    }

    /**
     * Reads the wallet as a sheet opens (the balance on hand may be old), and when Tj hasn't picked an amount yet and the wallet holds less
     * than the sheet opened with, the amount becomes what's left in it.
     */
    private fun checkWallet() {
        if (c.trading == null) return
        scope.launch {
            val balance = readBalance() ?: return@launch
            var planAgain = false
            state.update { s ->
                planAgain = false
                val cur = s.betSheet
                val next = if (cur == null || cur.placing || cur.result != null) cur else {
                    val stake = if (cur.stakeChosen) cur.stake else BetAmount.starting(cur.baseStake, settings().apiMaxStake, balance)
                    if (kotlin.math.abs(stake - cur.stake) < 1e-9) cur.copy(balance = balance)
                    else {
                        // A sheet still finding its bet plans with the new amount once it's found; one that has it plans again now.
                        planAgain = cur.target != null && !cur.resolving
                        cur.copy(stake = stake, balance = balance, plan = null, refusal = null)
                    }
                }
                s.copy(betting = s.betting.copy(balance = balance), betSheet = next)
            }
            if (planAgain) {
                betJob?.cancel()
                betJob = scope.launch { replan() }
            }
        }
    }

    override fun betPick(p: com.tjshea.vigilant.data.reference.ParlayPick) {
        bet(p)
    }

    override fun bet(o: Opportunity) {
        if (c.trading == null) return
        val target = ApiBetTargets.of(o)
        if (target == null) {
            toasts.tryEmit("This bet has no fair odds to check it against")
            return
        }
        val record = runCatching { BetRecord.opportunity(state.value, o, com.tjshea.vigilant.data.tracker.AtBet.HOW_SHEET, clock()) }.getOrNull()
        open(target.copy(atBet = record), o.selection, "${o.marketLabel} · ${o.event.description}", kelly = o.suggestedStake)
    }

    /** A CNO card's bet: its Novig outcome is found first (the same match the Open button uses), then the sheet opens. */
    override fun bet(row: CnoRow) = betRow(row, "CrazyNinjaOdds") { found, market -> ApiBetTargets.of(row, found, market, c.cno.state.value.snapshot?.dataAtMs)?.let { it.copy(sharpAgainst = cnoSharpAgainst(row)) } }

    /** A sharp book among the books read for [row] says it is not +EV at Novig's price ([CnoBooks.Check.sharpAgainst]); false when the books were not read. */
    private fun cnoSharpAgainst(row: CnoRow): Boolean =
        state.value.books[row.key]?.view?.let { v -> CnoBooks.check(v, row, live = (row.startsAtMs ?: Long.MAX_VALUE) <= clock())?.sharpAgainst } == true

    /** A CNO-shaped row's Kelly stake (CNO's or ParlayAPI's fair odds against Novig's price, capped at what's available). */
    private fun kellyOf(row: CnoRow): Double? =
        com.tjshea.vigilant.app.ui.cnoStake(com.tjshea.vigilant.data.cno.CnoPick(row, row.ev, live = (row.startsAtMs ?: Long.MAX_VALUE) <= clock()), settings())

    /**
     * A ParlayAPI pick's bet (TASKS.md P1, Tj 2026-09-30: "a button where I can bet each bet inside the app using the same logic as … the cno
     * scanner"): the same as a CNO card's, with ParlayAPI's fair odds as read at [boardAtMs] (the sheet refuses them once too old) and logged
     * to the Tracker as ParlayAPI's.
     */
    fun bet(p: com.tjshea.vigilant.data.reference.ParlayPick, boardAtMs: Long? = state.value.parlayPicks.readAtMs) =
        betRow(p.row, "ParlayAPI") { found, market -> ApiBetTargets.of(p, found, market, boardAtMs) }

    private fun betRow(row: CnoRow, lister: String, target: (NovigBetFinder.Found.Bet, NovigMarket) -> BetTarget?) {
        if (c.trading == null) return
        if (!ApiBetTargets.atNovig(row)) {
            toasts.tryEmit("Only bets priced at Novig can be placed through Novig's API")
            return
        }
        state.update { it.copy(betSheet = newSheet(row.bet, "${row.market} · ${row.event}", null, kellyOf(row), resolving = true)) }
        checkWallet()
        betJob?.cancel()
        betJob = scope.launch {
            val attempt = withContext(Dispatchers.IO) { runCatching { c.betFinder.attempt(row) }.getOrNull() }
            val found = attempt?.found as? NovigBetFinder.Found.Bet
            if (found == null) c.eventLog.count("betsheet.notfound." + (attempt?.why ?: "the lookup itself failed").take(90))
            val market = found?.let { f -> f.market ?: f.marketId?.let { id -> withContext(Dispatchers.IO) { runCatching { c.novig.market(id) }.getOrNull() } } }
            val t = (if (found != null && market != null) target(found, market) else null)?.let { t ->
                // The bet as found (its book page, Novig's price, CNO's numbers): kept on the bet once it fills ([BetRecord]).
                val live = (row.startsAtMs ?: Long.MAX_VALUE) <= clock()
                t.copy(atBet = runCatching { BetRecord.cno(state.value, row, live, com.tjshea.vigilant.data.tracker.AtBet.HOW_SHEET, t.source, clock()) }.getOrNull())
            }
            if (t == null) {
                state.update { it.copy(betSheet = it.betSheet?.copy(resolving = false, refusal = "Novig's exact bet couldn't be found for this one (${attempt?.why ?: "Novig didn't answer"}; its market or line may not be listed the way $lister names it): use Open in Novig instead.")) }
                return@launch
            }
            state.update { it.copy(betSheet = it.betSheet?.copy(target = t, resolving = false)) }
            replan()
        }
    }

    private fun open(target: BetTarget, title: String, subtitle: String, kelly: Double?) {
        state.update { it.copy(betSheet = newSheet(title, subtitle, target, kelly, resolving = false)) }
        betJob?.cancel()
        betJob = scope.launch { replan() }
        checkWallet()
    }

    /** An amount picked from the chips. */
    fun setStake(stake: Double) = chooseStake(stake, waitMs = 0L)

    /** An amount typed in the sheet: priced once typing pauses, so each keystroke isn't a read of Novig's book. */
    fun typeStake(stake: Double) = chooseStake(stake, waitMs = TYPING_PAUSE_MS)

    private fun chooseStake(stake: Double, waitMs: Long) {
        val sheet = state.value.betSheet ?: return
        if (sheet.placing || sheet.result != null) return
        val amount = stake.coerceIn(0.0, settings().apiMaxStake)
        if (sheet.stakeChosen && kotlin.math.abs(amount - sheet.stake) < 1e-9 && (sheet.plan != null || sheet.refusal != null)) return
        state.update { it.copy(betSheet = sheet.copy(stake = amount, stakeChosen = true, plan = null, refusal = null)) }
        if (sheet.target == null || sheet.resolving) return // planned once the bet is found
        betJob?.cancel()
        betJob = scope.launch {
            if (waitMs > 0) delay(waitMs)
            replan()
        }
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

    /**
     * A live-lane bet (Tj, 2026-10-11) is priced from the lane's fair AS IT IS NOW, not the one on the card when it was tapped: a line that moved in the seconds the sheet was open changes the EV the plan
     * is judged by. Null once the row has left the list (its prices are over 5 s old): nothing is bet on them. Any other bet is returned as it is.
     */
    private fun withLiveFair(target: BetTarget): BetTarget? {
        val st = state.value
        var t = target
        if (target.liveLane) {
            val row = st.liveRows.firstOrNull { it.key == target.placedKey } ?: return null
            val fair = row.fairProbability ?: return null
            t = t.copy(fair = fair, fairAsOfMs = row.fairAsOfMs)
        }
        // The LOWEST fair any scanner has for this exact bet right now (Tj, 2026-10-11: "If a bet ever becomes negative EV anywhere in the app on any scanner, prevent me from betting it"): Vigilant's
        // own latest scan of the same Novig outcome can price a CNO or ParlayAPI bet lower than the list it came from, and then that is the fair its EV is judged by.
        val now = clock()
        val others = st.result?.opportunities?.firstOrNull { it.market.marketId == t.market.marketId && it.outcome.outcomeId == t.outcomeId && !it.fairIsOld(now) && !it.isLive }?.fairProbability
        if (others != null && others < t.fair) t = t.copy(fair = others)
        return t
    }

    private suspend fun replan() {
        val sheet = state.value.betSheet ?: return
        val target = sheet.target ?: return
        val placer = placer() ?: return
        val result = try {
            val priced = withLiveFair(target) ?: throw LiveRowGone()
            placer.plan(priced, sheet.stake, sheet.allowRepeat)
        } catch (e: LiveRowGone) {
            PlanResult.Refused(LIVE_GONE)
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
            }).let { u ->
                // The bet is not +EV at Novig's price: it leaves the lists now, not at the next price read.
                if (result is PlanResult.Refused && result.negativeEv) {
                    val t = clock()
                    u.copy(negativeSeen = (u.negativeSeen.filterValues { t - it < 60_000L }) + listOfNotNull(target.placedKey?.let { it to t }))
                } else u
            }
        }
    }

    /** Places the bet the sheet shows (the button is the confirm). Re-checks the price; a price that moved against Tj is refused. */
    fun confirm() {
        val sheet = state.value.betSheet ?: return
        val target = sheet.target ?: return
        val plan = sheet.plan ?: return
        if (sheet.placing || sheet.result != null) return
        val placer = placer() ?: return
        val priced = withLiveFair(target)
        if (priced == null) {
            state.update { it.copy(betSheet = it.betSheet?.copy(plan = null, refusal = LIVE_GONE)) }
            return
        }
        state.update { it.copy(betSheet = sheet.copy(placing = true)) }
        betJob?.cancel()
        // Not betJob: opening the next bet cancels that, and this one must be followed to its end whatever the sheet does.
        scope.launch {
            // The bet leaves the lists the moment the sheet closes (Tj, 2026-10-13); it comes back only if the order did not go through ([restoreAfterMiss]).
            var sentAt = 0L
            var provisional: kotlinx.coroutines.Job? = null
            // Once the order may be on its way it is always followed to its end (and recorded) even if the sheet is closed: a cancelled call
            // would drop the answer to an order Novig has already taken.
            val result = try {
                // The record's time and stake are the order's: the sheet may have been open a while.
                val now = clock()
                val placing = priced.copy(atBet = target.atBet?.copy(atMs = now, minutesToStart = (target.startsTs - now) / 60_000L, stake = sheet.stake))
                withContext(Dispatchers.IO + NonCancellable) {
                    placer.placeByHand(placing, sheet.stake, plan.limitPrice, sheet.allowRepeat) {
                        // Novig has the order: the sheet says so and may be closed; the end is still being waited for below.
                        // Tj, 2026-10-12: "Close the bet slip immediately automatically after I place the bet. Then send a notification of successful bet or any error."
                        state.update { st -> st.copy(betSheet = if (st.betSheet?.target === target) null else st.betSheet) }
                        sentAt = clock()
                        provisional = scope.launch(Dispatchers.IO + NonCancellable) { markProvisional(c.placed, target, sentAt) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlaceResult.Unconfirmed("Something went wrong while placing it (${e.message ?: e.javaClass.simpleName}). Nothing is assumed: tap Sync with Novig in the Tracker in a minute.")
            }
            // The sheet on screen may be another bet by now (the next one was started while this ended): only its own sheet gets the result.
            state.update { st -> st.copy(betSheet = st.betSheet?.let { if (it.target === target) it.copy(placing = false, sent = false, result = result) else it }) }
            // The sheet is gone by now: the answer comes as a notification (success or error), and a toast for whoever is looking.
            withContext(NonCancellable) {
                runCatching {
                    if (result is PlaceResult.Placed) AutoBetNotes.handResult(c.app, "Bet placed ${money(result.bet.stake)} · ${target.selection}", "${result.bet.american?.let { com.tjshea.vigilant.engine.Odds.formatAmerican(it) } ?: ""} · ${target.marketLabel} · ${target.eventName}".trim(' ', '·'))
                    else AutoBetNotes.handResult(c.app, "Bet NOT placed · ${target.selection}", resultNote(result, target))
                }
            }
            if (result !is PlaceResult.Placed) toasts.tryEmit(resultNote(result, target))
            if (result is PlaceResult.Placed) {
                withContext(NonCancellable) {
                    provisional?.join()
                    hideFromLists(target, result)
                    refreshBalance(quiet = true)
                }
                toasts.tryEmit("Bet placed: ${money(result.bet.stake)} on ${target.selection}")
            } else if (sentAt > 0L && (result is PlaceResult.NotFilled || result is PlaceResult.Refused || result is PlaceResult.Failed)) {
                // Not unconfirmed: that one may have been placed, so it stays off the list until the Tracker settles it.
                withContext(NonCancellable) {
                    provisional?.join()
                    restoreAfterMiss(c.placed, target, sentAt, clock, missRestoreMs)
                    onRestored(listOf(target.market.marketId))
                }
            }
        }
    }

    /** A bet that did not end as Placed, in a line for the notification (the sheet may be closed by then). */
    private fun resultNote(r: PlaceResult, target: BetTarget): String = when (r) {
        is PlaceResult.NotFilled -> "Not filled on ${target.selection}: nobody was selling at that price (no money moved)"
        is PlaceResult.Refused -> "Not placed on ${target.selection}: ${r.reason}"
        is PlaceResult.Unconfirmed -> "Check ${target.selection}: ${r.message}"
        is PlaceResult.Failed -> "Bet on ${target.selection} failed: ${r.message}"
        else -> "Bet on ${target.selection} didn't go through"
    }

    /** The bet leaves the +EV and CNO lists the way a ✓ makes it ([PlacedBets]): it's placed (shared with the auto-bet: [markPlaced]). */
    private suspend fun hideFromLists(target: BetTarget, placed: PlaceResult.Placed) = markPlaced(c, target, placed, clock())

    // ---- "Add money" from the sheet -------------------------------------------------------------------------------

    /**
     * The sheet's "Add money to the wallet": closes the sheet and asks Settings to open on the wallet with what the bet is short by typed in
     * (the caller switches to Settings). The bet is kept for "Back to the bet".
     */
    fun requestTopUp(amount: Double? = null) {
        val sheet = state.value.betSheet ?: return
        // Also from a result Novig refused for the balance; never from a bet that's placed.
        if (sheet.placing || sheet.result is PlaceResult.Placed) return
        val cost = sheet.plan?.expectedCost ?: sheet.stake
        val needed = (cost - (state.value.betting.balance ?: sheet.balance ?: 0.0)).coerceAtLeast(0.0)
        betJob?.cancel()
        state.update {
            it.copy(
                betSheet = null,
                betting = it.betting.copy(topUp = TopUp(amount ?: WalletAmount.suggest(needed), needed, cost, sheet.copy(plan = null, refusal = null, result = null)), message = null, error = null),
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
            it.copy(betSheet = bet.copy(stake = bet.stake.coerceIn(0.0, settings().apiMaxStake), stakeChosen = true, resolving = false, placing = false, result = null, maxStake = settings().apiMaxStake, balance = it.betting.balance))
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

    companion object {
        /** A typed amount is priced once typing has paused this long. */
        const val TYPING_PAUSE_MS = 400L
        const val LIVE_GONE = "This live price has left the list: Pinnacle's and Novig's prices behind it are more than 5 seconds old, so nothing is bet on them. Tap the bet again when it is back on the list."
    }
}
