package com.tjshea.vigilant.data.rfq

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicLong

/** One line of the maker's journal (`rfq-<day>`, archived with the engine's other journals): what happened to one RFQ, as small JSON in [data]. */
@Serializable
data class RfqRow(val atMs: Long, val kind: String, val rfqId: String?, val data: String)

/**
 * The parlay maker's brain (MK, Tj 2026-11-18), independent of the socket: it is fed [RfqProtocol.Event]s and sends frames through [send]. In [RfqConfig.Mode.SHADOW] it sends NOTHING: it prices
 * every RFQ, journals what it WOULD have quoted, and when the RFQ trades (the `executed` channel shows every parlay trade on Novig and its price) it compares: what the market paid over our fair price,
 * and whether our quote would have won. In [RfqConfig.Mode.LIVE] it quotes what [RfqPricer] allows, reserves the collateral until the RFQ is settled or lapses, confirms the last look of its own
 * quotes (a rejection counts against Novig's 99% confirm rate, so a quote it makes is a quote it keeps) and stops itself on the faults in [halt].
 */
class RfqMaker(
    private val config: () -> RfqConfig,
    private val fairs: LegFairs,
    /** The maker's open collateral on Novig (`GET /rfq/v3/collateral`), dollars. */
    private val openCollateral: () -> Double,
    private val send: (String) -> Boolean,
    private val journal: (RfqRow) -> Unit,
    private val tell: (String, String) -> Unit = { _, _ -> },
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
    /** The scan's market label for an outcome id, or null when the scan does not hold that outcome at all (told apart from "held, but no fair line" in the tally). */
    private val legInfo: (String) -> String? = { null },
    /** One RFQ in this many that the maker cannot price is journaled whole (the rest only in the tally): ~60 RFQs a second would otherwise be gigabytes a day. */
    private val sampleEvery: Int = 25,
    /** What the other makers charge, per class of parlay, learned by [probe] (MK12); null = no market model. */
    private val market: RfqMarketModel? = null,
    private val probe: RfqProbe? = null,
    /** The maker's subaccount balance, dollars (read every minute by the engine), or null when unknown. Used only in LIVE: SHADOW judges prices as if the wallet were free. */
    private val cash: () -> Double? = { null },
    /** The collateral each outcome carries across the maker's open parlays (`GET /rfq/v3/executions`, [RfqLedger.exposure]); empty when not read. */
    private val exposure: () -> Map<String, Double> = { emptyMap() },
    /** The outcome ids of each open parlay (MK56), for the guard against identical parlays and one outcome in many. */
    private val openSets: () -> List<List<String>> = { emptyList() },
    /** Called once for each quote of ours that is filled, with what the parlay hub needs to show its legs later (MK15). */
    private val onFill: (FillRecord) -> Unit = {},
) {
    private class Pending(val created: RfqProtocol.Created, val decision: RfqDecision, val seenMs: Long, val journaled: Boolean, val classes: List<String>) {
        var quotePrice: Double? = null
        var quoteMax: Double? = null
        var quoteId: String? = null
        var reserved: Double = 0.0
        var reservedUntilMs: Long = 0L
        /** The user accepted this quote (a win): its collateral is certain, held in full (CS1). */
        var certain: Boolean = false
    }

    private val nonce = AtomicLong(0)
    private val pending = LinkedHashMap<String, Pending>()
    private val filledIds = LinkedHashSet<String>()

    // ---- what the status says ----
    @Volatile var seen = 0L; private set
    @Volatile var priceable = 0L; private set
    @Volatile var executedSeen = 0L; private set
    @Volatile var quotesSent = 0L; private set
    @Volatile var accepted = 0L; private set
    @Volatile var confirmed = 0L; private set
    @Volatile var filled = 0L; private set
    @Volatile var gaps = 0L; private set
    @Volatile var halted: String? = null; private set
    private var haltedUntilMs = 0L
    private var lastOffset = -1L
    private val declines = HashMap<String, Long>()
    /** For every executed RFQ we could price: the price Novig's market paid minus our fair, in probability points (x 100), the last [KEEP] of them. */
    private val margins = ArrayDeque<Double>()
    private var wouldWin = 0L
    private var wouldEdge = 0.0
    private var modelWouldWin = 0L
    private var liveWouldWin = 0L
    private var liveWouldEdge = 0.0
    private var modelWouldEdge = 0.0
    @Volatile var lastError: String? = null; private set
    /** MK13: stop quoting so the quotes already out can finish their last look before the job ends (a closed socket rejects the trade and counts against the 99% confirm rate). */
    @Volatile var draining = false; private set
    @Volatile private var lastQuoteAtMs = 0L
    /** Novig's own view of the maker (`GET /rfq/v3/maker`), read every minute by the engine. */
    @Volatile var standing: RfqStanding? = null; private set
    private val refusals = HashMap<String, Long>()
    /** RFQs this maker already took up: two sockets deliver every RFQ (the pricing socket and a second one), and an RFQ must be priced, probed and quoted once. */
    private val claimed = LinkedHashSet<String>()
    /** Quotes whose last look was answered, so a `quote_accepted` that arrives on two sockets is answered once. */
    private val answeredLooks = LinkedHashSet<String>()

    /** No new quote from now on; the ones out stay answerable. */
    fun beginDrain() { if (!draining) { draining = true; log("RFQ maker draining: no new quotes, the last looks of the ones out are still answered") } }

    /** How long until the last quote sent has had time to be accepted and confirmed (users accepted 4-29 s after a quote on 2026-11-19), or 0. */
    fun drainRemainingMs(): Long = if (lastQuoteAtMs == 0L) 0L else maxOf(0L, lastQuoteAtMs + DRAIN_AFTER_QUOTE_MS - clock())

    /** What Novig says of the maker. A lock it reports stops quoting until its stated end (not the 5 minutes the maker used to assume). */
    fun onStanding(s: RfqStanding) {
        standing = s
        val until = s.lockUntilMs
        if (s.locked && until != null && until > clock()) halt("Novig reports the maker locked (${s.lockSource}) until ${java.time.Instant.ofEpochMilli(until)}", until - clock() + 1_000L)
        else if (s.locked && until == null && s.lockSource == "admin") halt("Novig locked the maker by hand (admin): contact Novig", null)
        row("STANDING", null) { put("tier", s.tier ?: ""); put("counted", s.counted); put("confirmed", s.confirmed); put("threshold", s.threshold ?: 0.0); put("minSample", s.minSample); put("lock", s.lockSource ?: "") }
    }

    fun inFlight(): Double = synchronized(pending) { val now = clock(); pending.values.filter { it.reservedUntilMs > now }.sumOf { it.reserved } }

    /** Send times of the quotes and accept times of the wins, newest last, for the recent win rate (CS1); guarded by the `pending` lock. */
    private val quoteLog = ArrayDeque<Long>()
    private val winLog = ArrayDeque<Long>()

    /** The share of recent quotes that were accepted: wins in the last 2 hours over quotes, smoothed toward the floor with 100 quotes of prior, never under [RfqConfig.quoteWinFloor]. Call under the `pending` lock. */
    private fun winRate(cfg: RfqConfig, now: Long): Double {
        val from = now - WIN_WINDOW_MS
        while (quoteLog.isNotEmpty() && quoteLog.first() < from) quoteLog.removeFirst()
        while (winLog.isNotEmpty() && winLog.first() < from) winLog.removeFirst()
        val floor = cfg.quoteWinFloor.coerceIn(0.001, 0.9)
        return maxOf(floor, minOf(1.0, (winLog.size + floor * PRIOR_QUOTES) / (quoteLog.size + PRIOR_QUOTES)))
    }

    /**
     * The cash the quotes out have a claim on (CS1): a won quote in full, the rest (with [RfqConfig.reserveByWinRate]) only for the number of wins that is almost certain to be enough, using the largest
     * collaterals first. With it off, or when every quote could win, the same as [inFlight].
     */
    fun cashHeld(cfg: RfqConfig = config()): Double = synchronized(pending) {
        val now = clock()
        val live = pending.values.filter { it.reservedUntilMs > now && it.reserved > 0.0 }
        val certain = live.filter { it.certain }.sumOf { it.reserved }
        val open = live.filter { !it.certain }.map { it.reserved }
        if (!cfg.reserveByWinRate || open.isEmpty()) return@synchronized certain + open.sum()
        val k = poissonQuantile(open.size * winRate(cfg, now), cfg.reserveConfidence.coerceIn(0.5, 0.99999))
        certain + (if (k >= open.size) open.sum() else open.sortedDescending().take(k).sum())
    }

    private fun row(kind: String, rfqId: String?, build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        runCatching { journal(RfqRow(clock(), kind, rfqId, buildJsonObject(build).toString())) }
    }

    private var haltIsLossStop = false

    private fun halt(why: String, forMs: Long?) {
        haltIsLossStop = false
        if (halted == null) { log("RFQ maker halted: $why"); tell("The parlay maker stopped quoting", why) }
        halted = why
        haltedUntilMs = if (forMs == null) Long.MAX_VALUE else clock() + forMs
        row("HALT", null) { put("why", why) }
    }

    // ---- the tally: what the flow looks like, kept small (a row every 5 minutes) instead of a row per RFQ ----
    private class Window(val startMs: Long) {
        var created = 0L; var executed = 0L; var executedWager = 0.0; var executedSeenHere = 0L; var executedPriced = 0L
        val legCounts = HashMap<String, Long>(); val reasons = HashMap<String, Long>(); val legClasses = HashMap<String, Long>()
        val kinds = HashMap<String, Long>()
        val tradeLegs = HashMap<String, Long>()
    }
    private var window = Window(0L)

    private fun bump(m: HashMap<String, Long>, k: String) { m.merge(k, 1L, Long::plus) }

    private fun legClass(id: String): String = when {
        fairs.leg(id) != null -> "priced"
        legInfo(id) != null -> "scanned"
        else -> "unscanned"
    }

    private fun kindOf(id: String): String = (legInfo(id) ?: "?").replace(Regex("[-+]?[0-9]+(\\.[0-9]+)?"), "#").trim().take(40)

    private fun sampled(rfqId: String): Boolean = sampleEvery <= 1 || (rfqId.hashCode().toLong() and 0x7fffffffL) % sampleEvery == 0L

    private fun flushTally(now: Long) {
        val w = window
        if (w.created == 0L && w.executed == 0L) { window = Window(now); return }
        fun top(m: Map<String, Long>, n: Int) = buildJsonObject { m.entries.sortedByDescending { it.value }.take(n).forEach { put(it.key, it.value) } }
        row("TALLY", null) {
            put("fromMs", w.startMs); put("created", w.created); put("legs", top(w.legCounts, 12)); put("reasons", top(w.reasons, 12)); put("legClasses", top(w.legClasses, 4)); put("scannedKinds", top(w.kinds, 20))
            put("executed", w.executed); put("executedWager", w.executedWager); put("executedSeenHere", w.executedSeenHere); put("executedPriced", w.executedPriced); put("tradeLegs", top(w.tradeLegs, 12))
        }
        window = Window(now)
    }

    /**
     * One frame from Novig. [reply] sends a frame back on the socket this event arrived on (a last look is answered on the line it came in on, so the quiet last-look socket answers its own `quote_accepted`);
     * null = the maker's own [send].
     */
    fun onEvent(e: RfqProtocol.Event, reply: ((String) -> Boolean)? = null) {
        val cfg = config()
        synchronized(this) { val t = clock(); if (window.startMs == 0L) window = Window(t) else if (t - window.startMs >= TALLY_MS) flushTally(t) }
        if (halted != null && clock() >= haltedUntilMs) { halted = null; log("RFQ maker resumes") }
        when (e) {
            is RfqProtocol.Created -> created(e, cfg)
            is RfqProtocol.Closed -> if (synchronized(pending) { pending[e.rfqId]?.journaled } == true) row("CLOSED", e.rfqId) { }
            is RfqProtocol.Executed -> executed(e)
            is RfqProtocol.QuoteAccepted -> accepted(e, cfg, reply ?: send)
            is RfqProtocol.QuoteCreated -> synchronized(pending) { pending[e.rfqId]?.quoteId = e.quoteId }
            is RfqProtocol.QuoteExecuted -> {
                // A fill is delivered to every socket of the maker that listens to `quotes`: it counts and is recorded once.
                val first = synchronized(filledIds) { filledIds.add(e.rfqId).also { if (filledIds.size > 5_000) filledIds.remove(filledIds.first()) } }
                if (first) filled++
                val p = synchronized(pending) { pending[e.rfqId] }
                holdUntilBalanceCatchesUp(e.rfqId)
                row("QUOTE_EXECUTED", e.rfqId) { put("wager", e.wager); put("price", e.price); put("quoteId", e.quoteId) }
                if (first) (p?.decision as? RfqDecision.Quote)?.let { q ->
                    runCatching {
                        onFill(FillRecord(e.rfqId, clock(), e.price, q.fair, e.wager, q.legs.map { l ->
                            FillLeg(l.outcomeId, l.marketId, l.label, l.eventDesc, l.league, l.marketType, l.startsMs, l.probability)
                        }))
                    }
                }
            }
            is RfqProtocol.QuoteUnfilled -> {
                release(e.rfqId)
                row("QUOTE_UNFILLED", e.rfqId) { put("reason", e.reason) }
                if (e.reason.contains("maker")) halt("Novig could not take the collateral from the balance (${e.reason})", 30 * 60_000L)
            }
            is RfqProtocol.QuoteDropped -> {
                release(e.rfqId)
                row("QUOTE_DROPPED", e.rfqId) { put("reason", e.reason); e.minWager?.let { put("minWager", it) }; e.requiredCollateral?.let { put("requiredCollateral", it) } }
            }
            is RfqProtocol.Refused -> refused(e)
            is RfqProtocol.Ack, is RfqProtocol.Other -> Unit
        }
    }

    private fun created(e: RfqProtocol.Created, cfg: RfqConfig) {
        if (cfg.mode == RfqConfig.Mode.OFF) return
        synchronized(claimed) { if (!claimed.add(e.rfqId)) return; while (claimed.size > MAX_PENDING) claimed.remove(claimed.first()) }
        seen++
        val now = clock()
        // SHADOW sends nothing, so the capital the live parlays hold is no limit on what it prices: it judges every RFQ as if the wallet were free (the per-parlay cap still binds), and the evidence (what the market
        // paid over our fair, what our rule would have won) is about PRICES. With the wallet nearly all in open collateral (2026-11-19: $7 cash, $73 held) 14% of RFQs declined for "the caps allow a $# stake".
        val capital = if (cfg.mode == RfqConfig.Mode.SHADOW) 0.0 else openCollateral() + inFlight()
        // LIVE sizes a quote to the balance that is there: cash less the collateral of the quotes still out and the reserve (docs: an unbacked quote is passed over, an unfundable fill stops the maker).
        // A balance not read yet is no balance to quote against: LIVE waits for the first reading (within a minute).
        // MK17: with the wallet as the collateral limit the paper run reads the balance too and judges as the live one would (the cash there is, the share of the wallet one parlay may take).
        val cashNow = cash()
        val cashRoom = when {
            cfg.mode == RfqConfig.Mode.LIVE -> cashNow?.let { it - cashHeld(cfg) - cfg.cashReserve } ?: -1.0
            cfg.walletCollateral && cashNow != null -> cashNow - cfg.cashReserve
            else -> null
        }
        val walletTotal = if (cfg.walletCollateral) cashNow?.let { it + openCollateral() } else null
        // PW1: the share of the wallet held in OPEN parlays (not the quotes still out, which almost never fill): how scarce the cash is.
        val utilization = if (cfg.adaptiveCash && walletTotal != null && walletTotal > 0.0) (openCollateral() / walletTotal).coerceIn(0.0, 1.0) else null
        val priced = RfqPricer.decide(e, now, cfg, fairs, capital, market, cashRoom, walletTotal, utilization)
        val d = if (cfg.mode == RfqConfig.Mode.LIVE && priced is RfqDecision.Quote) concentrated(e, priced, cfg, now, walletTotal) ?: priced else priced
        offerProbe(e, d, cfg)
        when (d) {
            is RfqDecision.Quote -> priceable++
            is RfqDecision.Decline -> synchronized(declines) { declines.merge(d.reason.replace(Regex("[0-9]+(\\.[0-9]+)?"), "#"), 1L, Long::plus) }
        }
        val classes = e.outcomeIds.map { legClass(it) }
        synchronized(this) {
            val w = window
            w.created++; bump(w.legCounts, if (e.outcomeIds.size >= 10) "10+" else e.outcomeIds.size.toString())
            when (d) { is RfqDecision.Decline -> bump(w.reasons, d.reason.replace(Regex("[0-9]+(\\.[0-9]+)?"), "#")); is RfqDecision.Quote -> bump(w.reasons, "QUOTE") }
            classes.forEachIndexed { i, c -> bump(w.legClasses, c); if (c == "scanned") bump(w.kinds, kindOf(e.outcomeIds[i])) }
        }
        // Whole rows for what the maker can price (or only a rule stopped), and a sample of the rest.
        val keep = d is RfqDecision.Quote || (d is RfqDecision.Decline && d.fair != null) || sampled(e.rfqId)
        val p = Pending(e, d, now, keep, classes)
        synchronized(pending) {
            pending[e.rfqId] = p
            while (pending.size > MAX_PENDING) pending.remove(pending.keys.first())
            val old = now - PENDING_MS
            while (pending.isNotEmpty() && pending.values.first().seenMs < old && pending.values.first().reservedUntilMs < now) pending.remove(pending.keys.first())
        }
        if (keep) row("CREATED", e.rfqId) {
            put("outcomeIds", buildJsonArray { e.outcomeIds.forEach { add(JsonPrimitive(it)) } })
            put("classes", classes.joinToString(","))
            put("expiresTs", e.expiresTs); put("minWager", e.minWager); put("mode", cfg.mode.name)
            when (d) {
                is RfqDecision.Quote -> {
                    put("decision", "quote"); put("price", d.price); put("maxWager", d.maxWager); put("fair", d.fair); put("edge", d.edge)
                    put("rulePrice", d.rulePrice); d.modelPrice?.let { put("modelPrice", it) }; d.livePrice?.let { put("livePrice", it) }; if (d.via.isNotEmpty()) put("via", d.via)
                    put("legs", buildJsonArray { d.legs.forEach { l -> add(legJson(l)) } })
                }
                is RfqDecision.Decline -> {
                    put("decision", "decline"); put("reason", d.reason); put("fair", d.fair?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("legs", buildJsonArray { d.legs.forEach { l -> add(legJson(l)) } })
                }
            }
        }
        if (cfg.mode == RfqConfig.Mode.LIVE && halted == null && !draining && d is RfqDecision.Quote && now < e.expiresTs - 400L) {
            val perStake = (1.0 - d.price) / d.price
            if (send(RfqProtocol.quote(nonce.incrementAndGet(), e.rfqId, d.price, d.maxWager, e.rfqId))) {
                quotesSent++; lastQuoteAtMs = now
                synchronized(pending) { quoteLog.addLast(now); while (quoteLog.size > MAX_RATE_LOG) quoteLog.removeFirst() }
                p.quotePrice = d.price; p.quoteMax = d.maxWager
                p.reserved = d.maxWager * perStake; p.reservedUntilMs = e.expiresTs + config().reserveAfterCloseMs
                row("QUOTE_SENT", e.rfqId) { put("price", d.price); put("maxWager", d.maxWager); put("fair", d.fair); put("collateral", p.reserved); put("held", cashHeld(cfg)); put("full", inFlight()) }
            }
        }
    }

    /**
     * A decline when a quote would take any of its legs over [RfqConfig.maxLegCollateral]: the collateral that outcome already carries in open parlays and in quotes still out, plus this quote's. Null = fine.
     * Parlays on the same favourite (or the same game) lose together, which the caps on one parlay and on the total do not see.
     */
    private fun concentrated(e: RfqProtocol.Created, q: RfqDecision.Quote, cfg: RfqConfig, now: Long, walletTotal: Double? = null): RfqDecision.Decline? {
        // With the wallet as the limit (MK17) one outcome may carry [RfqConfig.walletLegShare] of the wallet, not a fixed $30.
        // MK56 (Tj 2026-11-20: "the parlay system is losing money miserably and taking identical bets"): 19 of the 20 settled parlays were buyer wins, but they were not 20 bets: the Houston Texans moneyline alone was in
        // 6 of them and the same Magic + Thunder parlay was filled twice in 7 minutes. A parlay that repeats the legs of one already held (or quoted) is the same bet again, and one outcome in many parlays makes them
        // one position that wins or loses together: neither the cap on one parlay nor the collateral cap saw it.
        if (cfg.maxSameParlay > 0 || cfg.maxParlaysPerOutcome > 0) {
            val sets = ArrayList<Set<String>>(); for (l in openSets()) sets += l.toSet()
            synchronized(pending) { for (p in pending.values) if (p.reservedUntilMs > now && p.reserved > 0.0) sets += p.created.outcomeIds.toSet() }
            val mineSet = e.outcomeIds.toSet()
            if (cfg.maxSameParlay > 0) {
                val same = sets.count { it == mineSet }
                if (same >= cfg.maxSameParlay) return RfqDecision.Decline("the same parlay is already held or quoted ($same time${if (same == 1) "" else "s"}: an identical bet is not a new one)", q.fair, q.legs)
            }
            if (cfg.maxParlaysPerOutcome > 0) {
                val worstCount = e.outcomeIds.maxOfOrNull { id -> sets.count { id in it } } ?: 0
                val limit = cfg.maxParlaysPerOutcome + (if (cfg.outcomeCountWalletStep > 0.0 && walletTotal != null) (walletTotal / cfg.outcomeCountWalletStep).toInt() else 0)      // PW1: one more per step of wallet
                if (worstCount >= limit) return RfqDecision.Decline("a leg is already in $worstCount open parlays or quotes (the limit is $limit: they would win or lose together)", q.fair, q.legs)
            }
        }
        val cap = if (cfg.walletCollateral && walletTotal != null) cfg.walletLegShare * walletTotal else cfg.maxLegCollateral
        if (cap <= 0.0) return null
        val held = HashMap(exposure())
        synchronized(pending) { for (p in pending.values) if (p.reservedUntilMs > now && p.reserved > 0.0) for (id in p.created.outcomeIds) held.merge(id, p.reserved, Double::plus) }
        val mine = q.maxWager * (1.0 - q.price) / q.price
        val worst = e.outcomeIds.maxByOrNull { (held[it] ?: 0.0) + mine } ?: return null
        val total = (held[worst] ?: 0.0) + mine
        if (total <= cap + 1e-9) return null
        return RfqDecision.Decline("a leg already carries $${"%.2f".format(held[worst] ?: 0.0)} of collateral (this quote adds $${"%.2f".format(mine)}, the cap is $${"%.2f".format(cap)})", q.fair, q.legs)
    }

    /** A priced RFQ is a candidate for a market probe: SHADOW probes every priced RFQ; LIVE only those it declines (a probe of one it would quote meets its own quote). */
    private fun offerProbe(e: RfqProtocol.Created, d: RfqDecision, cfg: RfqConfig) {
        val pr = probe ?: return
        if (!cfg.probe || cfg.mode == RfqConfig.Mode.OFF) return
        val fair: Double; val legs: List<LegFair>; val ours: Double?
        when (d) {
            is RfqDecision.Quote -> { if (cfg.mode == RfqConfig.Mode.LIVE) return; fair = d.fair; legs = d.legs; ours = d.price }
            is RfqDecision.Decline -> { fair = d.fair ?: return; legs = d.legs.takeIf { it.isNotEmpty() } ?: return; ours = null }
        }
        pr.offer(RfqProbe.Item(e.rfqId, e.outcomeIds, RfqMarketModel.classOf(legs, fair), fair, ours, e.outcomeIds.size, clock()))
    }

    private fun accepted(e: RfqProtocol.QuoteAccepted, cfg: RfqConfig, reply: (String) -> Boolean) {
        // Two sockets can deliver the same last look: it is answered once (a second answer would only get CONFIRM_ALREADY_DECIDED), unless the first answer could not be sent.
        if (synchronized(answeredLooks) { e.quoteId in answeredLooks }) { row("CONFIRM", e.rfqId) { put("dup", true); put("quoteId", e.quoteId) }; return }
        accepted++
        val p = synchronized(pending) { pending[e.rfqId] }
        val ours = p?.quotePrice != null && cfg.mode == RfqConfig.Mode.LIVE
        if (!ours) {
            // Not a quote THIS connection made (a second job of the same maker overlaps a handover, or the mode changed): it is not ours to answer. "Your first answer counts": a rejection from here would
            // beat the confirmation of the connection that did quote, and would count against Novig's 99% confirm rate.
            row("CONFIRM", e.rfqId) { put("confirmed", false); put("sent", false); put("ours", false); put("quoteId", e.quoteId) }
            return
        }
        // A quote we made is a quote we keep: the last look is rejected only when the win is not the one we quoted (a different price or a bigger stake than our maximum), never because the market moved.
        // Novig echoes our four-decimal price rounded to a THOUSANDTH, so the echo is up to 0.0005 away from what we sent: 0.4495 came back as 0.450 on 2026-11-19, a gap of 5.0000000000000044e-4 in floating point,
        // which the old 5e-4 bound refused: our own win was rejected, counted against the 99% confirm rate (1 reject in 12 last looks = 92%) and locked the maker for 5 minutes (MAKER_LOCKED, run 44474874762).
        // The bound is 0.0006 (half a thousandth plus slack for the arithmetic): a rounding difference is never a reason to reject our own quote; it fills at OUR price.
        synchronized(pending) {
            p!!.certain = true; p.reservedUntilMs = maxOf(p.reservedUntilMs, clock() + BALANCE_CATCHUP_MS)
            winLog.addLast(clock()); while (winLog.size > MAX_RATE_LOG) winLog.removeFirst()
        }
        val sound = kotlin.math.abs(e.price - p!!.quotePrice!!) < PRICE_ECHO_TOLERANCE && e.wager <= (p.quoteMax ?: 0.0) + 0.005
        val ok = reply(RfqProtocol.confirm(nonce.incrementAndGet(), e.rfqId, e.quoteId, sound))
        if (ok) synchronized(answeredLooks) { answeredLooks.add(e.quoteId); while (answeredLooks.size > 2_000) answeredLooks.remove(answeredLooks.first()) }
        if (sound && ok) confirmed++
        row("CONFIRM", e.rfqId) { put("confirmed", sound); put("sent", ok); put("wager", e.wager); put("price", e.price); put("quoteId", e.quoteId); put("ours", true) }
    }

    /** One leg as journaled: the fair and what is needed to grade the leg later from the final score (the outcome's name, its event's "Away @ Home", the market type and league): MK14. */
    private fun legJson(l: LegFair) = buildJsonObject {
        put("label", l.label); put("p", l.probability); put("books", l.books); put("startsMs", l.startsMs)
        put("type", l.marketType); put("league", l.league); put("name", l.outcomeName); put("desc", l.eventDesc); put("event", l.eventId); put("outcome", l.outcomeId); put("market", l.marketId)
    }

    private fun executed(e: RfqProtocol.Executed) {
        executedSeen++
        if (lastOffset >= 0 && e.offset > lastOffset + 1) gaps++
        if (e.offset >= 0) lastOffset = maxOf(lastOffset, e.offset)
        val p = synchronized(pending) { pending[e.rfqId] }
        val fair = when (val d = p?.decision) { is RfqDecision.Quote -> d.fair; is RfqDecision.Decline -> d.fair; null -> null }
        synchronized(this) {
            val w = window
            w.executed++; w.executedWager += e.wager
            if (p != null) { w.executedSeenHere++; bump(w.tradeLegs, p.created.outcomeIds.size.toString()) }
            if (fair != null) w.executedPriced++
        }
        row("EXECUTED", e.rfqId) {
            put("offset", e.offset); put("wager", e.wager); put("price", e.price)
            put("fair", fair?.let { JsonPrimitive(it) } ?: JsonNull)
            put("seenHere", p != null)
            if (p != null) {
                put("nLegs", p.created.outcomeIds.size); put("classes", p.classes.joinToString(","))
                // A trade we could not price is the one to study later: its legs are named (Novig's public catalog resolves them), the rest is in the tally.
                if (fair == null) put("outcomeIds", buildJsonArray { p.created.outcomeIds.forEach { add(JsonPrimitive(it)) } })
            }
            (p?.decision as? RfqDecision.Quote)?.let { q ->
                put("ourPrice", q.price); put("wouldWin", q.price <= e.price + 1e-9)
                put("rulePrice", q.rulePrice); put("ruleWouldWin", q.rulePrice <= e.price + 1e-9)
                q.livePrice?.let { lp -> put("livePrice", lp); put("liveWouldWin", lp <= e.price + 1e-9) }
                q.modelPrice?.let { m -> put("modelPrice", m); put("modelWouldWin", m <= e.price + 1e-9) }
            }
        }
        // The evidence counts only trades of RFQs the rules would QUOTE: a trade the rules decline (a same-game combination whose fair is an independent product, a leg in play...) says nothing about what a
        // quote of ours would meet. The journal row above keeps every fair for study.
        // A quote priced on the market's own ratio (props of one game: `via`) is judged against a fair built from the market, so it is no evidence of what the market pays over OUR fair: left out of the counters.
        (p?.decision as? RfqDecision.Quote)?.takeIf { it.via.isEmpty() }?.let { q ->
            synchronized(margins) { margins.addLast((e.price - q.fair) * 100.0); while (margins.size > KEEP) margins.removeFirst() }
            if (q.price <= e.price + 1e-9) { wouldWin++; wouldEdge += (q.price - q.fair) * 100.0 }
            q.modelPrice?.let { m -> if (m <= e.price + 1e-9) { modelWouldWin++; modelWouldEdge += (m - q.fair) * 100.0 } }
            q.livePrice?.let { lp -> if (lp <= e.price + 1e-9) { liveWouldWin++; liveWouldEdge += (lp - q.fair) * 100.0 } }
        }
    }

    private fun refused(e: RfqProtocol.Refused) {
        lastError = "${e.code}${e.message?.let { ": ${it.take(80)}" }.orEmpty()}"
        synchronized(refusals) { refusals.merge(e.code, 1L, Long::plus) }
        row("REFUSED", null) { put("code", e.code); put("message", e.message ?: ""); put("nonce", e.nonce?.let { JsonPrimitive(it) } ?: JsonNull) }
        when (e.code) {
            "MAKER_LOCKED" -> halt("Novig locked the maker out of quoting for 5 minutes (confirm rate below 99%)", 5 * 60_000L + 5_000L)
            // 2026-11-21: Novig answered MAKER_BARRED at 06:37 and then paused the whole exchange for maintenance (SYSTEM_LOCKED): "can't quote RIGHT NOW" was held until the next job (up to 6 h). One refused frame is harmless, so try again in 30 minutes.
            "MAKER_BARRED" -> halt("Novig says the maker cannot quote right now (MAKER_BARRED): contact Novig; trying again in 30 minutes", 30 * 60_000L)
            "READ_ONLY_KEY" -> halt("the key the RFQ socket uses is read-only (READ_ONLY_KEY): quoting needs the trading key", null)
            "KYC_REQUIRED" -> halt("Novig says the account's identity check is not verified (KYC_REQUIRED)", null)
            "SELF_EXCLUDED" -> halt("The account is self-excluded (SELF_EXCLUDED)", null)
        }
    }

    /** A fill locked the collateral at Novig, but the balance we read does not show it until the next reading: keep counting it for [BALANCE_CATCHUP_MS] instead of freeing it at once (which let a second quote spend the same cash). */
    private fun holdUntilBalanceCatchesUp(rfqId: String) { synchronized(pending) { pending[rfqId]?.let { if (it.reserved > 0.0) it.reservedUntilMs = clock() + BALANCE_CATCHUP_MS } } }

    private fun release(rfqId: String) { synchronized(pending) { pending[rfqId]?.let { it.reserved = 0.0; it.reservedUntilMs = 0L } } }

    @Volatile private var ledger: RfqLedger.Summary? = null

    /**
     * The result of the maker's real fills (read by the engine every couple of minutes while LIVE). **The loss stop:** when the last 24 hours of settled parlays have lost [RfqConfig.dayLossStop] or more, quoting stops
     * for six hours and Tj is told; the stop is re-judged when it lapses, over a window that has moved on.
     */
    fun onLedger(s: RfqLedger.Summary, cfg: RfqConfig = config()) {
        ledger = s
        row("LEDGER", null) { put("settled", s.settled); put("makerWon", s.makerWon); put("makerLost", s.makerLost); put("open", s.open); put("pnl", s.pnlTotal); put("pnl24h", s.pnl24h) }
        val stop = lossStop(cfg)
        // ON2: the stop judges the parlays filled under the current profile (lossStopFromMs); with 0 that is every fill, as before.
        val judged = s.pnlStop24h
        if (cfg.mode == RfqConfig.Mode.LIVE && stop > 0.0 && judged <= -stop) {
            halt("the maker's settled parlays lost ${"%.2f".format(-judged)} dollars in 24 hours${if (cfg.lossStopFromMs > 0L) " (fills since the profile began)" else ""} (the stop is ${"%.2f".format(stop)})", 6 * 3_600_000L)
            haltIsLossStop = true
        } else if (halted != null && haltIsLossStop && (stop <= 0.0 || judged > -stop)) {
            // MK17d (Tj: "turn the stop loss off"): a halt that the loss stop made ends when the stop is switched off (or raised above the loss), not six hours later.
            halted = null; haltedUntilMs = 0L; haltIsLossStop = false
            log("RFQ maker resumes: the loss stop is ${if (stop <= 0.0) "off" else "no longer reached"}")
        }
    }

    /** The loss over 24 hours that stops LIVE quoting: the fixed dollars, or with the wallet as the limit (MK17) the larger of that and [RfqConfig.dayLossStopPct] of the wallet. */
    fun lossStop(cfg: RfqConfig): Double {
        if (!cfg.walletCollateral) return cfg.dayLossStop
        val wallet = cash()?.let { it + openCollateral() } ?: return cfg.dayLossStop
        return maxOf(cfg.dayLossStop, cfg.dayLossStopPct * wallet)
    }

    /** The whole state as data for the phone ([RfqStatus]). */
    fun snapshot(connected: Boolean, cfg: RfqConfig, makerId: String?, collateral: Double, note: String, now: Long = clock()): RfqStatus {
        val ms = synchronized(margins) { margins.toList() }
        val med = ms.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        val top = synchronized(declines) { declines.entries.sortedByDescending { it.value }.take(4).map { "${it.key} x${it.value}" } }
        return RfqStatus(
            atMs = now, config = cfg, connected = connected, makerId = makerId, halted = halted, seen = seen, priceable = priceable, tradesSeen = executedSeen, pricedTrades = ms.size,
            medianGapPoints = med, wouldWin = wouldWin, wouldEarnPoints = if (wouldWin > 0) wouldEdge / wouldWin else null, quotesSent = quotesSent, won = accepted, confirmed = confirmed,
            filled = filled, openCollateral = collateral, declines = top, note = if (connected) "" else note.take(120),
            settled = ledger?.settled ?: 0, makerWon = ledger?.makerWon ?: 0, makerLost = ledger?.makerLost ?: 0, openParlays = ledger?.open ?: 0, realizedPnl = ledger?.pnlTotal, realizedPnl24h = ledger?.pnl24h,
            probe = probe?.line().orEmpty(),
            market = market?.summary()?.take(12)?.map { c -> "${c.cls} n=${c.n} nobody=${(c.noQuote * 100).toInt()}%" + (c.median?.let { " median ${"%+.2f".format(it)}pts (p25 ${"%+.2f".format(c.p25)}, p75 ${"%+.2f".format(c.p75)})" } ?: "") }.orEmpty(),
            modelWouldWin = modelWouldWin, modelWouldEarnPoints = if (modelWouldWin > 0) modelWouldEdge / modelWouldWin else null, liveHurdleWouldWin = liveWouldWin, liveHurdleEarnPoints = if (liveWouldWin > 0) liveWouldEdge / liveWouldWin else null,
            walletCash = cash(), walletTotal = cash()?.let { it + collateral }, walletParlayCap = cash()?.let { cfg.walletShare * (it + collateral) }, walletLegCap = cash()?.let { cfg.walletLegShare * (it + collateral) }, lossStopNow = lossStop(cfg),
            makerTier = standing?.tier, confirmCounted = standing?.counted, confirmConfirmed = standing?.confirmed, confirmThreshold = standing?.threshold, confirmMinSample = standing?.minSample,
            lockSource = standing?.lockSource, lockUntilMs = standing?.lockUntilMs,
            refusals = synchronized(refusals) { refusals.entries.sortedByDescending { it.value }.take(5).map { "${it.key} x${it.value}" } },
        )
    }

    /** The whole state in words for the status file and the engine log. */
    fun status(connected: Boolean, cfg: RfqConfig = config()): String {
        if (cfg.mode == RfqConfig.Mode.OFF) return "RFQ maker: off"
        val top = synchronized(declines) { declines.entries.sortedByDescending { it.value }.take(3).joinToString(", ") { "${it.key} ${it.value}x" } }
        val ms = synchronized(margins) { margins.toList() }
        val med = ms.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        return "RFQ maker ${cfg.mode.name}: socket ${if (connected) "live" else "not connected"}${halted?.let { " · STOPPED: $it" }.orEmpty()} · $seen RFQs seen, $priceable priceable" +
            (if (top.isNotBlank()) " (declined: $top)" else "") +
            " · $executedSeen trades seen" + (med?.let { " (priced ${ms.size}: the market paid a median ${"%+.2f".format(it)} points over our fair; our quote at margin ${(cfg.margin * 100).toInt()}% would have won $wouldWin${if (wouldWin > 0) ", earning ${"%+.2f".format(wouldEdge / wouldWin)} points of pot each" else ""})" } ?: "") +
            (if (cfg.mode == RfqConfig.Mode.LIVE) " · quotes sent $quotesSent, won $accepted, confirmed $confirmed, filled $filled" else "") +
            (if (gaps > 0) " · $gaps execution gaps" else "") + (lastError?.let { " · last error $it" }.orEmpty())
    }

    companion object {
        /** An RFQ trades within seconds of its creation: two minutes is far more than needed, and the cap keeps a burst from filling memory. */
        const val MAX_PENDING = 20_000
        const val PENDING_MS = 120_000L
        const val TALLY_MS = 5 * 60_000L
        const val KEEP = 500
        /** How far Novig's echo of our quote's price may be from the price we sent (it rounds to a thousandth): half a thousandth plus slack for floating point. */
        const val PRICE_ECHO_TOLERANCE = 6e-4
        /** After the last quote sent, the maker keeps answering for this long before a job ends: users accepted 4 to 29 seconds after a quote (14 fills, 2026-11-19). */
        const val DRAIN_AFTER_QUOTE_MS = 45_000L
        /**
         * A quote's collateral stays reserved this long after its auction closes: the user accepts AFTER the 3-second auction (all 16 live fills came 6 to 29 seconds after the quote was sent, the RFQ having closed at 3 s),
         * so the auction's close frees nothing. 45 s is 1.5 times the slowest fill seen (it was 120 s, which on a $90 wallet held two $35 longshot quotes' collateral for two minutes each and declined ~1,200
         * affordable RFQs in 11 minutes as "cash is short": MK17c).
         */
        const val RESERVE_AFTER_CLOSE_MS = 45_000L
        /** After a fill the collateral stays counted this long: the balance is read once a minute, so until the next reading the cash still shows what the fill has already locked. */
        const val BALANCE_CATCHUP_MS = 75_000L
        /** CS1: the window the recent win rate is read over, the quotes' prior weight and the log's size. */
        const val WIN_WINDOW_MS = 2 * 3_600_000L
        const val PRIOR_QUOTES = 100.0
        const val MAX_RATE_LOG = 20_000

        /** The smallest k with P(X <= k) >= [confidence] for X ~ Poisson([lambda]): the wins that are almost certainly enough. */
        fun poissonQuantile(lambda: Double, confidence: Double): Int {
            if (lambda <= 0.0) return 0
            var k = 0; var term = Math.exp(-lambda); var cum = term
            while (cum < confidence && k < 100_000) { k++; term *= lambda / k; cum += term }
            return k
        }
    }
}
