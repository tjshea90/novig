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
) {
    private class Pending(val created: RfqProtocol.Created, val decision: RfqDecision, val seenMs: Long) {
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

    fun onEvent(e: RfqProtocol.Event) {
        val cfg = config()
        if (halted != null && clock() >= haltedUntilMs) { halted = null; log("RFQ maker resumes") }
        when (e) {
            is RfqProtocol.Created -> created(e, cfg)
            is RfqProtocol.Closed -> row("CLOSED", e.rfqId) { }
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
        val p = Pending(e, d, now)
        synchronized(pending) {
            pending[e.rfqId] = p
            while (pending.size > MAX_PENDING) pending.remove(pending.keys.first())
        }
        row("CREATED", e.rfqId) {
            put("outcomeIds", buildJsonArray { e.outcomeIds.forEach { add(JsonPrimitive(it)) } })
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
        // A quote we made is a quote we keep: the last look is rejected only when the win is not the one we quoted (a different price or a bigger stake than our maximum), never because the market moved.
        val sound = ours && kotlin.math.abs(e.price - p!!.quotePrice!!) < 1e-6 && e.wager <= (p.quoteMax ?: 0.0) + 0.005
        val ok = send(RfqProtocol.confirm(nonce.incrementAndGet(), e.rfqId, e.quoteId, sound))
        if (sound && ok) confirmed++
        row("CONFIRM", e.rfqId) { put("confirmed", sound); put("sent", ok); put("wager", e.wager); put("price", e.price); put("quoteId", e.quoteId); put("ours", ours) }
    }

    private fun executed(e: RfqProtocol.Executed) {
        executedSeen++
        if (lastOffset >= 0 && e.offset > lastOffset + 1) gaps++
        if (e.offset >= 0) lastOffset = maxOf(lastOffset, e.offset)
        val p = synchronized(pending) { pending[e.rfqId] }
        val fair = when (val d = p?.decision) { is RfqDecision.Quote -> d.fair; is RfqDecision.Decline -> d.fair; null -> null }
        row("EXECUTED", e.rfqId) {
            put("offset", e.offset); put("wager", e.wager); put("price", e.price)
            put("fair", fair?.let { JsonPrimitive(it) } ?: JsonNull)
            put("seenHere", p != null)
            (p?.decision as? RfqDecision.Quote)?.let { q -> put("ourPrice", q.price); put("wouldWin", q.price <= e.price + 1e-9) }
        }
        if (fair != null) {
            synchronized(margins) { margins.addLast((e.price - fair) * 100.0); while (margins.size > KEEP) margins.removeFirst() }
            (p?.decision as? RfqDecision.Quote)?.let { q -> if (q.price <= e.price + 1e-9) { wouldWin++; wouldEdge += (q.price - q.fair) * 100.0 } }
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
        const val MAX_PENDING = 600
        const val KEEP = 500
        /** A quote's collateral stays reserved this long after its auction closes (the user may accept a while later). */
        const val RESERVE_AFTER_CLOSE_MS = 120_000L
    }
}
