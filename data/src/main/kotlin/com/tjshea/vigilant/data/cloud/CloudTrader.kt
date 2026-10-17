package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.trading.AutoBet
import com.tjshea.vigilant.data.reference.PlayerOut
import com.tjshea.vigilant.data.scanner.Agreement
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.CloudTradeMode
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ProfitProfile
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SharpMode
import com.tjshea.vigilant.data.scanner.TrapGuard
import com.tjshea.vigilant.data.tracker.RepeatGuard
import com.tjshea.vigilant.engine.EvMath
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId

/** One order the engine decided on: a paper one is what it WOULD have sent, a live one is what Novig took. */
@Serializable
data class TradeEntry(
    /** The order's client id: made from (market, outcome, rule version, minute), so a restart or hand-over can never send the same decision twice. */
    val id: String,
    val atMs: Long,
    val mode: String,
    val marketId: String,
    val outcomeId: String,
    val eventId: String,
    val event: String,
    val league: String,
    val kind: String,
    val marketLabel: String,
    val selection: String,
    val startsTs: Long,
    val contracts: Long,
    val avgPrice: Double,
    /** Dollars staked, fee included. */
    val cost: Double,
    val fee: Double,
    val fair: Double,
    val evPercent: Double,
    val stars: Double?,
    val fairAgeS: Long?,
    val books: Int,
    val note: String,
    /** OPEN until a grader (TCL20e) settles it: WON, LOST, PUSH, VOID. */
    val status: String = "OPEN",
    val pnl: Double? = null,
    val orderId: String? = null,
) {
    val payout: Double get() = contracts * EvMath.CONTRACT_PAYOUT_DOLLARS
}

/** The engine's order book of its own: every decision, the day it started on and why it stopped, if it did. Carried from job to job on `cloud-feed` (`trades.json`). */
@Serializable
data class TradeLedger(
    val entries: List<TradeEntry> = emptyList(),
    /** The Eastern calendar day [dayStartEquity] belongs to. */
    val dayKey: String = "",
    val dayStartEquity: Double = 0.0,
    /** Why ordering has stopped (a loss stop, a location refusal, a bad key): shown on the phone; empty = trading. */
    val stopped: String = "",
    val stoppedAtMs: Long = 0L,
) {
    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
        fun parse(text: String): TradeLedger? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
        fun text(l: TradeLedger): String = json.encodeToString(serializer(), l)

        /** How long the ledger keeps entries (a grader has settled them long before). */
        const val KEEP_MS = 14L * 24 * 3_600_000L
    }
}

/**
 * Where an order goes. Paper trading has no port (the trader records the fill it would have had); live trading ([CloudTradeMode.LIVE]) is a port over the trading key: it reads the cash, sends one
 * immediate-or-cancel order per decision under the decision's client id and says what filled, and cancels what rests when the window ends. A port NEVER retries an order whose answer was lost: it looks it up by client id.
 */
interface CloudOrderPort {
    /** The subaccount's cash in dollars, or null when it cannot be read (nothing is ordered then). */
    suspend fun cash(): Double?

    /** Set when the last call was refused in a way that must stop ordering (a location check, a bad or revoked key, a locked account); null otherwise. Said in words, never a key. */
    val blocked: String?

    suspend fun place(entry: TradeEntry, limitPrice: Double): Placed
    suspend fun cancelResting()

    /** Every outcome the subaccount holds, whoever placed it (the engine, the phone's desks, Tj by hand); null = could not be read. */
    suspend fun positions(): List<com.tjshea.vigilant.data.novig.trading.NovigPosition>? = null

    sealed interface Placed {
        /** [contracts] filled at [avgPrice]; [feeDollars] paid. */
        data class Filled(val contracts: Long, val avgPrice: Double, val feeDollars: Double, val orderId: String) : Placed
        /** Nothing was bought (the price moved, no depth): the decision is dropped, cooled down. */
        data class Missed(val why: String) : Placed
        /** Ordering must stop: location refusal (GEOLOCATION_*), 401/403/423. Nothing was bought. */
        data class Stop(val why: String) : Placed

        /** The order was sent and its end was never seen: it may have filled. It is kept as money at risk (UNCONFIRMED), never sent again, and ordering stops for a while. */
        data class Unconfirmed(val why: String) : Placed
    }
}

/**
 * The engine's auto-bet (Tj, 2026-10-15: the autonomous bet and bid app on GitHub; TCL20d). It judges the bets the scan lists with the SAME rules as the phone's auto-bet ([AutoBet.rules], [AutoBet.judgeVigilant], stars,
 * sharp rule, injuries, trap guard, one bet a market, the repeat guard), sizes each with [CloudKelly], and either records the order it would have sent (PAPER) or sends it through [port] (LIVE). Pregame only,
 * like the phone's auto-bet. Nothing here can place an order in any mode but LIVE, and LIVE needs a port, which exists only when the trading key was sent from the phone.
 */
class CloudTrader(
    private val unavailable: (Opportunity, Long) -> String? = { _, _ -> null },
    private val port: CloudOrderPort? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile var ledger: TradeLedger = TradeLedger()
        private set

    /** The cooldown after a miss, per decision key. */
    private val cooldown = HashMap<String, Long>()

    /** One trading pass as the volume journal keeps it (`tradepass-<day>.jsonl`, archived with the engine's other journals): how many listed bets, how many passed the rules, how many orders, and why the rest were held back. */
    @kotlinx.serialization.Serializable
    data class PassRow(val atMs: Long, val mode: String, val looked: Int, val passed: Int, val ordered: Int, val spent: Double, val cash: Double, val skipped: Map<String, Int> = emptyMap(), val stopped: String = "")

    data class Report(
        val mode: CloudTradeMode,
        val looked: Int = 0,
        val passed: Int = 0,
        val placed: List<TradeEntry> = emptyList(),
        val skipped: Map<String, Int> = emptyMap(),
        val stopped: String = "",
        val cash: Double = 0.0,
        val equity: Double = 0.0,
    ) {
        fun row(atMs: Long) = PassRow(atMs, mode.name.lowercase(), looked, passed, placed.size, placed.sumOf { it.cost }, cash, skipped, stopped)

        /** One line for the status and the log. */
        fun line(): String = when (mode) {
            CloudTradeMode.OFF -> "trading off"
            else -> "${mode.name.lowercase()}: looked ${looked}, passed ${passed}, ${if (mode == CloudTradeMode.LIVE) "ordered" else "paper-ordered"} ${placed.size} (${money(placed.sumOf { it.cost })})" +
                (if (stopped.isNotEmpty()) ", stopped: $stopped" else "") + (skipped.entries.sortedByDescending { it.value }.take(3).takeIf { it.isNotEmpty() }?.joinToString(prefix = "; held back: ") { "${it.key} x${it.value}" } ?: "")
        }
    }

    private data class AcctPos(val marketId: String, val outcomeId: String, val dollars: Double, val payout: Double, val eventId: String?, val family: String)

    /** What one Novig outcome is (its event, start, market and selection), so a position the account holds can be put in its game and family; set by the engine from the scan. */
    data class OutcomeInfo(val eventId: String, val eventName: String, val startsTs: Long, val marketLabel: String, val selection: String)
    @Volatile var describeOutcome: ((String) -> OutcomeInfo?)? = null

    /** Seeds the ledger from the previous job's file. Entries older than [TradeLedger.KEEP_MS] are dropped. */
    fun load(l: TradeLedger) {
        val cut = clock() - TradeLedger.KEEP_MS
        ledger = l.copy(entries = l.entries.filter { it.atMs >= cut })
    }

    /** A settled entry's result written by the grader (TCL20e), or by a test. */
    fun settle(id: String, status: String, pnl: Double) {
        ledger = ledger.copy(entries = ledger.entries.map { if (it.id == id) it.copy(status = status, pnl = pnl) else it })
    }

    /** What is at risk now: open entries of games that have not been over long (a game is over for exposure 3 h after its start; the grader settles it for good). */
    fun atRisk(now: Long): Double = ledger.entries.filter { open(it, now) }.sumOf { it.cost }

    private fun open(e: TradeEntry, now: Long) = (e.status == "OPEN" || e.status == "UNCONFIRMED") && now - e.startsTs < EXPOSURE_AFTER_START_MS

    /** Stops ordering (a location refusal, a bad key): kept in the ledger so the phone says why and the next job knows. */
    fun stop(why: String) { ledger = ledger.copy(stopped = why, stoppedAtMs = clock()) }

    /** Called at the end of the engine's hours: whatever rests on Novig under the engine's key is cancelled. */
    suspend fun windowEnded() { runCatching { port?.cancelResting() } }

    /**
     * The engine's bids' fills are positions of the same book (TCL31): an entry whose id is already there is replaced (a bid that filled more), a new one is added. The trader's risk caps, the one-bet-a-market rule
     * and the grader all see them. Nothing is ordered by this.
     */
    fun record(entry: TradeEntry) {
        val i = ledger.entries.indexOfFirst { it.id == entry.id }
        ledger = ledger.copy(entries = if (i >= 0) ledger.entries.toMutableList().also { it[i] = entry.copy(status = it[i].status, pnl = it[i].pnl) } else ledger.entries + entry)
    }

    /** The markets this book holds an open position in (the bid desk puts no bid there). */
    fun openMarkets(now: Long = clock()): Set<String> = ledger.entries.filter { open(it, now) && !it.note.startsWith("bid") }.mapTo(HashSet()) { it.marketId }

    /**
     * [extraHeld]: the bid desk's resting and filled bids as positions, so the repeat guard counts them with the bets (resting bids count as filled: they may fill any moment).
     */
    suspend fun pass(rows: List<Opportunity>, s: ScanSettings, now: Long = clock(), extraHeld: List<RepeatGuard.Pos> = emptyList()): Report {
        val mode = s.gates.cloudTrading
        if (mode == CloudTradeMode.OFF) return Report(mode)
        val live = mode == CloudTradeMode.LIVE
        // The phone's STOP / Pause outranks everything: the engine does not run then, and a pass that still arrives orders nothing.
        if (s.killed || s.paused) return Report(mode, stopped = "the app is stopped or paused on the phone")
        if (live && port == null) return Report(mode, stopped = "live trading needs the trading key sent from the phone (Settings › Cloud scan)")
        // A stop (a location refusal, a lost answer) holds ordering for [STOP_HOLD_MS]; then one pass tries again, and the cash read is the first thing it asks.
        if (ledger.stopped.isNotEmpty() && live) {
            if (now - ledger.stoppedAtMs < STOP_HOLD_MS) return Report(mode, stopped = ledger.stopped)
            ledger = ledger.copy(stopped = "", stoppedAtMs = 0L)
        }
        val rules = AutoBet.rules(s)
        val skipped = LinkedHashMap<String, Int>()
        fun skip(r: String) { skipped.merge(r, 1, Int::plus) }

        val candidates = ArrayList<Opportunity>()
        for (o in rows) {
            val why = reason(o, rules, s, now)
            if (why != null) skip(why) else candidates += o
        }
        // Nothing passed the rules (most passes, most of the time): no wallet read, no request to Novig.
        if (candidates.isEmpty()) return Report(mode, looked = rows.size, skipped = skipped)

        // the money: a paper wallet is the bankroll setting plus what graded entries made; a live one is the subaccount's cash
        val settled = ledger.entries.filter { it.status != "OPEN" }.sumOf { it.pnl ?: 0.0 }
        var risk = atRisk(now)
        var cash: Double
        if (live) {
            val p = port!!
            val c = p.cash()
            if (c == null) { p.blocked?.let { stop(it) }; return Report(mode, stopped = p.blocked ?: "the subaccount's cash could not be read") }
            cash = c
        } else {
            cash = (s.bankroll + settled - risk).coerceAtLeast(0.0)
        }
        val equity = cash + risk
        // The whole account, not only this engine's ledger (Tj, 2026-10-17, TCL91: -18.5 and -17.5 on one game beside the engine's -19.5): what the subaccount holds in each game counts against the game cap and
        // the same-team-lines rule, whoever placed it. A position the scan cannot describe is still counted by its market.
        val acct = if (live) port?.positions().orEmpty().mapNotNull { p ->
            val info = describeOutcome?.invoke(p.outcomeId)
            if (p.qty <= 0L) null else AcctPos(p.marketId, p.outcomeId, p.cost, p.qty.toDouble(), info?.eventId, info?.let { RepeatGuard.family(it.eventName, it.startsTs, it.marketLabel, it.selection) }.orEmpty())
        } else emptyList()
        val acctKeys = acct.mapTo(HashSet()) { it.outcomeId + "|" + it.marketId }
        val day = Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate().toString()
        if (ledger.dayKey != day) ledger = ledger.copy(dayKey = day, dayStartEquity = equity, stopped = if (live) ledger.stopped else "")
        if (cash < AutoBet.SLEEP_BELOW) return Report(mode, cash = cash, equity = equity, stopped = "the wallet has ${money(cash)}, under ${money(AutoBet.SLEEP_BELOW)}")

        // The best-rated, then the likeliest edge first (the edge a sharp book would not back counts for 70%).
        val ordered = candidates.sortedWith(compareByDescending<Opportunity> { it.rating?.stars ?: 0.0 }.thenByDescending { AutoBet.credibleEv(it.evPercent ?: 0.0, null) })
        val repeat = s.gates.repeatRules()
        val placed = ArrayList<TradeEntry>()
        var stopped = ""
        for (o in ordered) {
            if (placed.size >= MAX_PER_PASS) { skip("pass limit ($MAX_PER_PASS) reached: the rest wait"); break }
            val q = o.quote ?: run { skip("no Novig price at decision time"); null } ?: continue
            val fair = o.fairProbability ?: run { skip("no fair odds at decision time"); null } ?: continue
            val fee = o.market.fee ?: run { skip("no fee known for the market"); null } ?: continue
            val id = clientId(o, now)
            if (ledger.entries.any { it.id == id }) { skip("already decided"); continue }
            if ((cooldown[o.key] ?: 0L) > now) { skip("tried a moment ago"); continue }
            // one bet per Novig market while one is open: the other side of a line is never bet after the first
            if (ledger.entries.any { open(it, now) && it.marketId == o.market.marketId }) { skip("a bet in this market is already open"); continue }
            val a = o.agreement
            val gameRisk = maxOf(ledger.entries.filter { open(it, now) && it.eventId == o.market.eventId }.sumOf { it.cost }, acct.filter { it.eventId == o.market.eventId }.sumOf { it.dollars })
            val sized = CloudKelly.stake(
                CloudKelly.Inputs(
                    fair = fair, cost = q.cost, cash = cash, atRisk = risk, gameAtRisk = gameRisk, dayStartEquity = ledger.dayStartEquity,
                    fairAgeMs = o.fairAsOfMs?.let { (now - it).coerceAtLeast(0L) }, agreeing = a.agreeing, sharpAgrees = a.sharpAgrees, stars = o.rating?.stars,
                    kelly = s.gates.cloudKelly, betCap = s.gates.cloudBetCap, gameCap = s.gates.cloudGameCap, walletUse = s.gates.cloudWalletUse, dayStop = s.gates.cloudDayStop,
                ),
            )
            val stake = when (sized) {
                is CloudKelly.Stake.Skip -> { skip(sized.reason); if (sized.reason.contains("loss stop") || sized.reason.contains("wallet-use")) break else continue }
                is CloudKelly.Stake.Amount -> sized
            }
            val fill = walk(o, fair, fee, stake.dollars, rules.readEv)
            if (fill == null) { skip("less than one contract at a positive edge"); continue }
            if (repeat != null) {
                val bidIds = extraHeld.mapTo(HashSet()) { it.outcomeId + "|" + it.marketId }
                val held = acct.map { RepeatGuard.Pos(it.marketId, it.outcomeId, it.dollars, it.payout, family = it.family) } + ledger.entries.filter { open(it, now) && it.outcomeId + "|" + it.marketId !in acctKeys && !(it.note.startsWith("bid") && it.outcomeId + "|" + it.marketId in bidIds) }.map { RepeatGuard.Pos(it.marketId, it.outcomeId, it.cost, it.payout, family = RepeatGuard.family(it.event, it.startsTs, it.marketLabel, it.selection)) } + extraHeld
                RepeatGuard.check(repeat, held, o.market.marketId, o.outcome.outcomeId, fill.cost, fill.contracts * EvMath.CONTRACT_PAYOUT_DOLLARS, o.market.outcomes.map { it.outcomeId }, RepeatGuard.family(o.eventName, o.event.startsTs, o.marketLabel, o.selection))
                    ?.let { skip("repeat guard"); continue }
            }
            var entry = TradeEntry(
                id = id, atMs = now, mode = mode.name, marketId = o.market.marketId, outcomeId = o.outcome.outcomeId, eventId = o.market.eventId, event = o.eventName, league = o.league.novigName,
                kind = BetKind.of(o.marketLabel, o.selection).label, marketLabel = o.marketLabel, selection = o.selection, startsTs = o.event.startsTs, contracts = fill.contracts, avgPrice = fill.avgPrice,
                cost = fill.cost, fee = fill.fee, fair = fair, evPercent = fill.evPercent, stars = o.rating?.stars, fairAgeS = o.fairAsOfMs?.let { (now - it) / 1000L }, books = a.agreeing, note = stake.note,
            )
            if (live) {
                when (val r = port!!.place(entry, fill.limitPrice)) {
                    is CloudOrderPort.Placed.Filled -> entry = entry.copy(contracts = r.contracts, avgPrice = r.avgPrice, fee = r.feeDollars, cost = r.contracts * r.avgPrice * EvMath.CONTRACT_PAYOUT_DOLLARS + r.feeDollars, orderId = r.orderId)
                    is CloudOrderPort.Placed.Missed -> { cooldown[o.key] = now + AutoBet.COOLDOWN_MS; skip("not filled: ${r.why.take(60)}"); continue }
                    is CloudOrderPort.Placed.Stop -> { stopped = r.why; stop(r.why); break }
                    is CloudOrderPort.Placed.Unconfirmed -> {
                        // possibly filled: counted as at risk, never sent again, ordering held while Tj / the grader reconcile
                        val u = entry.copy(status = "UNCONFIRMED", note = entry.note + " | " + r.why.take(160))
                        ledger = ledger.copy(entries = ledger.entries + u); placed += u; cash -= u.cost; risk += u.cost
                        stopped = r.why; stop(r.why); break
                    }
                }
            }
            ledger = ledger.copy(entries = ledger.entries + entry)
            placed += entry
            cash -= entry.cost; risk += entry.cost
        }
        return Report(mode, looked = rows.size, passed = candidates.size, placed = placed, skipped = skipped, stopped = stopped, cash = cash, equity = equity)
    }

    /** Why a listed bet is not ordered, or null. The phone's auto-bet rules for Vigilant's own scanned bets, in the same order. */
    internal fun reason(o: Opportunity, rules: AutoBet.Rules, s: ScanSettings, now: Long): String? {
        val q = o.quote ?: return "no Novig price"
        o.fairProbability ?: return "no fair odds"
        if (o.isLive || o.event.startsTs - now < MIN_LEAD_MS) return "not pregame"
        if (o.market.status != "OPEN") return "market closed"
        if (o.fairIsOld(now)) return "the other books' prices behind it are too old"
        if (o.priceIsOld(now) || now - (o.bookFetchedAtMs ?: 0L) > FRESH_PRICE_MS) return "no Novig price read in the last 3 minutes"
        unavailable(o, now)?.let { return it }
        if (!rules.byStars && TrapGuard.tooEarly(o.event.startsTs, now, s.trapEarlyHours, null, false) != null) return TrapGuard.earlyReason(s.trapEarlyHours)
        profileBlock(o, s, now)?.let { return it }
        val a: Agreement.Count = o.agreement
        AutoBet.judgeVigilant(rules, q.evPercent, q.priceAmerican, BetKind.of(o.marketLabel, o.selection), a.twoSided, a.agreeing, AutoBet.isOverProp(o.marketLabel, o.selection))?.let { return it }
        val stars = ProfitProfile.adjustedStars(s.gates.profitProven, o.rating?.stars, o.league.novigName, BetKind.of(o.marketLabel, o.selection).label, o.selection, o.evPercent, o.event.startsTs, now)
        AutoBet.starsReason(rules, stars, o.rating?.stars)?.let { return it }
        if (a.sharpAgainst) return "a sharp book prices both sides and says it isn't +EV at Novig's price"
        if (rules.byStars) return null
        return when (s.sharpAutoBet) {
            SharpMode.OFF, SharpMode.VETO -> null
            SharpMode.CONFIRM -> if (!a.sharpAgrees) "no sharp book confirms it is +EV" else null
        }
    }

    /**
     * The types of bet Tj's own history says lose (Settings › Profit-proven only, on): never ordered, whatever the star minimum says (Tj, 2026-10-17, chat t1792212927614: "for any losing bets and bid types with
     * big enough samples, make the engine never auto bet or auto bid these types"). The same [ProfitProfile] the phone's auto-bet and the bid desk judge by; regenerate it with tools/profit-profile.py.
     */
    internal fun profileBlock(o: Opportunity, s: ScanSettings, now: Long): String? {
        if (!s.gates.profitProven) return null
        val pf = ProfitProfile.shipped ?: return null
        val q = o.quote ?: return null
        return pf.judge(ProfitProfile.Instrument.BET, o.league.novigName, BetKind.of(o.marketLabel, o.selection).label, o.selection, q.evPercent, (o.event.startsTs - now) / 60_000L, typeOnly = true).blocked?.let { "Not bet: $it" }
    }

    /** What taking [dollars] cheapest-first from the visible ladder would buy while each level stays at or above [minEv] after the fee. Null = under one contract. */
    internal fun walk(o: Opportunity, fair: Double, fee: com.tjshea.vigilant.engine.MarketFee, dollars: Double, minEv: Double): Fill? {
        var left = dollars
        var contracts = 0L
        var cost = 0.0
        var feeSum = 0.0
        var paid = 0.0
        var limit = 0.0
        for (level in o.ladder.sortedBy { it.price }) {
            val q = EvMath.quote(fair, level.price, fee, eventLive = false)
            if (q.evPercent < minEv - 1e-9) break
            val per = q.cost * EvMath.CONTRACT_PAYOUT_DOLLARS
            val take = minOf(level.contracts, Math.floor(left / per + 1e-9).toLong())
            if (take <= 0L) break
            contracts += take; cost += take * per; feeSum += take * q.fee * EvMath.CONTRACT_PAYOUT_DOLLARS; paid += take * level.price; left -= take * per; limit = level.price
            if (left < per) break
        }
        if (contracts <= 0L) return null
        val avg = paid / contracts
        return Fill(contracts, avg, cost, feeSum, limit, EvMath.quote(fair, avg, fee, eventLive = false).evPercent)
    }

    data class Fill(val contracts: Long, val avgPrice: Double, val cost: Double, val fee: Double, val limitPrice: Double, val evPercent: Double)

    companion object {
        /** The phone's own bars: a game that starts within a minute is not bet, a Novig price older than three minutes is not the price. */
        const val MIN_LEAD_MS = 60_000L
        const val FRESH_PRICE_MS = 3 * 60_000L
        const val MAX_PER_PASS = 10
        /** Open entries stop counting as money at risk this long after the game started (a grader settles them for good). */
        const val EXPOSURE_AFTER_START_MS = 3L * 3_600_000L
        /** Bumped when the decision rules change, so a new rule's decision has a new client id. */
        const val RULE_VERSION = 1
        val ZONE: ZoneId = EngineHours.ZONE

        /** The decision's client id: (market, outcome, rule version, minute). A restart or hand-over inside the same minute makes the same id. */
        fun clientId(o: Opportunity, now: Long): String =
            java.util.UUID.nameUUIDFromBytes("vc$RULE_VERSION|${o.market.marketId}|${o.outcome.outcomeId}|${now / 60_000L}".toByteArray(Charsets.UTF_8)).toString()

        /** How long a stop holds live ordering before one pass tries again. */
        const val STOP_HOLD_MS = 30 * 60_000L

        private fun money(v: Double) = String.format(java.util.Locale.US, "$%.2f", v)
    }
}
