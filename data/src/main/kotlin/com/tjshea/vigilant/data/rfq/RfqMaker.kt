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
) {
    private class Pending(val created: RfqProtocol.Created, val decision: RfqDecision, val seenMs: Long, val journaled: Boolean, val classes: List<String>) {
        var quotePrice: Double? = null
        var quoteMax: Double? = null
        var quoteId: String? = null
        var reserved: Double = 0.0
        var reservedUntilMs: Long = 0L
    }

    private val nonce = AtomicLong(0)
    private val pending = LinkedHashMap<String, Pending>()

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
    @Volatile var lastError: String? = null; private set

    fun inFlight(): Double = synchronized(pending) { val now = clock(); pending.values.filter { it.reservedUntilMs > now }.sumOf { it.reserved } }

    private fun row(kind: String, rfqId: String?, build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        runCatching { journal(RfqRow(clock(), kind, rfqId, buildJsonObject(build).toString())) }
    }

    private fun halt(why: String, forMs: Long?) {
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

    fun onEvent(e: RfqProtocol.Event) {
        val cfg = config()
        synchronized(this) { val t = clock(); if (window.startMs == 0L) window = Window(t) else if (t - window.startMs >= TALLY_MS) flushTally(t) }
        if (halted != null && clock() >= haltedUntilMs) { halted = null; log("RFQ maker resumes") }
        when (e) {
            is RfqProtocol.Created -> created(e, cfg)
            is RfqProtocol.Closed -> if (synchronized(pending) { pending[e.rfqId]?.journaled } == true) row("CLOSED", e.rfqId) { }
            is RfqProtocol.Executed -> executed(e)
            is RfqProtocol.QuoteAccepted -> accepted(e, cfg)
            is RfqProtocol.QuoteCreated -> synchronized(pending) { pending[e.rfqId]?.quoteId = e.quoteId }
            is RfqProtocol.QuoteExecuted -> {
                filled++
                release(e.rfqId)
                row("QUOTE_EXECUTED", e.rfqId) { put("wager", e.wager); put("price", e.price); put("quoteId", e.quoteId) }
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
        seen++
        val now = clock()
        val d = RfqPricer.decide(e, now, cfg, fairs, openCollateral() + inFlight())
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
                    put("legs", buildJsonArray { d.legs.forEach { l -> add(buildJsonObject { put("label", l.label); put("p", l.probability); put("books", l.books); put("startsMs", l.startsMs) }) } })
                }
                is RfqDecision.Decline -> {
                    put("decision", "decline"); put("reason", d.reason); put("fair", d.fair?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("legs", buildJsonArray { d.legs.forEach { l -> add(buildJsonObject { put("label", l.label); put("p", l.probability); put("books", l.books); put("startsMs", l.startsMs) }) } })
                }
            }
        }
        if (cfg.mode == RfqConfig.Mode.LIVE && halted == null && d is RfqDecision.Quote && now < e.expiresTs - 400L) {
            val perStake = (1.0 - d.price) / d.price
            if (send(RfqProtocol.quote(nonce.incrementAndGet(), e.rfqId, d.price, d.maxWager, e.rfqId))) {
                quotesSent++
                p.quotePrice = d.price; p.quoteMax = d.maxWager
                p.reserved = d.maxWager * perStake; p.reservedUntilMs = e.expiresTs + RESERVE_AFTER_CLOSE_MS
                row("QUOTE_SENT", e.rfqId) { put("price", d.price); put("maxWager", d.maxWager); put("fair", d.fair); put("collateral", p.reserved) }
            }
        }
    }

    private fun accepted(e: RfqProtocol.QuoteAccepted, cfg: RfqConfig) {
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
        val sound = kotlin.math.abs(e.price - p!!.quotePrice!!) < PRICE_ECHO_TOLERANCE && e.wager <= (p.quoteMax ?: 0.0) + 0.005
        val ok = send(RfqProtocol.confirm(nonce.incrementAndGet(), e.rfqId, e.quoteId, sound))
        if (sound && ok) confirmed++
        row("CONFIRM", e.rfqId) { put("confirmed", sound); put("sent", ok); put("wager", e.wager); put("price", e.price); put("quoteId", e.quoteId); put("ours", true) }
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
            (p?.decision as? RfqDecision.Quote)?.let { q -> put("ourPrice", q.price); put("wouldWin", q.price <= e.price + 1e-9) }
        }
        // The evidence counts only trades of RFQs the rules would QUOTE: a trade the rules decline (a same-game combination whose fair is an independent product, a leg in play...) says nothing about what a
        // quote of ours would meet. The journal row above keeps every fair for study.
        (p?.decision as? RfqDecision.Quote)?.let { q ->
            synchronized(margins) { margins.addLast((e.price - q.fair) * 100.0); while (margins.size > KEEP) margins.removeFirst() }
            if (q.price <= e.price + 1e-9) { wouldWin++; wouldEdge += (q.price - q.fair) * 100.0 }
        }
    }

    private fun refused(e: RfqProtocol.Refused) {
        lastError = "${e.code}${e.message?.let { ": ${it.take(80)}" }.orEmpty()}"
        row("REFUSED", null) { put("code", e.code); put("message", e.message ?: ""); put("nonce", e.nonce?.let { JsonPrimitive(it) } ?: JsonNull) }
        when (e.code) {
            "MAKER_LOCKED" -> halt("Novig locked the maker out of quoting for 5 minutes (confirm rate below 99%)", 5 * 60_000L + 5_000L)
            "KYC_REQUIRED" -> halt("Novig says the account's identity check is not verified (KYC_REQUIRED)", null)
            "SELF_EXCLUDED" -> halt("The account is self-excluded (SELF_EXCLUDED)", null)
        }
    }

    private fun release(rfqId: String) { synchronized(pending) { pending[rfqId]?.let { it.reserved = 0.0; it.reservedUntilMs = 0L } } }

    @Volatile private var ledger: RfqLedger.Summary? = null

    /**
     * The result of the maker's real fills (read by the engine every couple of minutes while LIVE). **The loss stop:** when the last 24 hours of settled parlays have lost [RfqConfig.dayLossStop] or more, quoting stops
     * for six hours and Tj is told; the stop is re-judged when it lapses, over a window that has moved on.
     */
    fun onLedger(s: RfqLedger.Summary, cfg: RfqConfig = config()) {
        ledger = s
        row("LEDGER", null) { put("settled", s.settled); put("makerWon", s.makerWon); put("makerLost", s.makerLost); put("open", s.open); put("pnl", s.pnlTotal); put("pnl24h", s.pnl24h) }
        if (cfg.mode == RfqConfig.Mode.LIVE && cfg.dayLossStop > 0.0 && s.pnl24h <= -cfg.dayLossStop) {
            halt("the maker's settled parlays lost ${"%.2f".format(-s.pnl24h)} dollars in 24 hours (the stop is ${"%.2f".format(cfg.dayLossStop)})", 6 * 3_600_000L)
        }
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
        /** A quote's collateral stays reserved this long after its auction closes (the user may accept a while later). */
        const val RESERVE_AFTER_CLOSE_MS = 120_000L
    }
}
