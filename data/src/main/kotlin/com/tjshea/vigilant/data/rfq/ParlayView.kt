package com.tjshea.vigilant.data.rfq

import com.tjshea.vigilant.data.cloud.DashboardModel
import com.tjshea.vigilant.data.cloud.DashboardModel.Tone
import java.util.Locale

/**
 * What the Engine tab's Parlays view says about the parlay maker (MKV, Tj 2026-11-18: "I want it in vigilant"): plain words and numbers from the engine's [RfqStatus], pure so a test can read them. The
 * view only monitors and controls; the maker runs on the GitHub engine.
 */
object ParlayView {
    data class View(
        val headline: String,
        val tone: Tone,
        val explain: String,
        val tiles: List<DashboardModel.Tile>,
        val evidence: String,
        val rule: String,
        /** Anything that needs a look: a lockout, a down socket in LIVE. Null = nothing. */
        val warning: String?,
        /** The evidence rule is met: [RfqStatus.EVIDENCE_TRADES] priced trades over which our own rule would have won a fifth or more and earned. Never switches anything by itself. */
        val evidenceMet: Boolean,
    )

    private fun pts(x: Double) = (if (x >= 0) "+" else "-") + String.format(Locale.US, "%.2f", kotlin.math.abs(x))
    private fun usd(x: Double) = "\$" + String.format(Locale.US, "%.0f", x)

    fun of(r: RfqStatus?): View {
        if (r == null) return View("Waiting for the engine", Tone.NEUTRAL, "The GitHub engine publishes the parlay maker's state once a job has started with the maker's file. Pull down to read it again.", emptyList(), "", "", null, false)
        val c = r.config
        val pricedPct = if (r.seen > 0) 100.0 * r.priceable / r.seen else 0.0
        val tiles = ArrayList<DashboardModel.Tile>()
        tiles += DashboardModel.Tile("RFQs seen", "%,d".format(Locale.US, r.seen), "%,d priced by the engine (%.1f%%)".format(Locale.US, r.priceable, pricedPct))
        tiles += DashboardModel.Tile("Trades seen", "%,d".format(Locale.US, r.tradesSeen), "${r.pricedTrades} of them the engine could price")
        tiles += DashboardModel.Tile(
            "Market paid over our fair", r.medianGapPoints?.let { pts(it) + " pts" } ?: "—", "median, points of pot; we ask at least ${"%.1f".format(Locale.US, c.minReturn * 100)}% on the collateral",
            if ((r.medianGapPoints ?: 0.0) > 0.0) Tone.GOOD else Tone.NEUTRAL,
        )
        tiles += DashboardModel.Tile("Our rule would have won", if (r.pricedTrades > 0) "${100 * r.wouldWin / r.pricedTrades}%" else "—", r.wouldEarnPoints?.let { "${r.wouldWin} of ${r.pricedTrades} trades, ${pts(it)} pts each if our fair is true" } ?: "of the trades it could price")
        // MK12: what the other makers charge (the probe's answers per class of parlay) and how the market model's price would have done on the same trades.
        if (r.market.isNotEmpty()) {
            tiles += DashboardModel.Tile("Other makers (probe)", "${r.market.size} classes", r.market.take(3).joinToString(" · ") { it.replace(Regex(" \\(p25.*$"), "") } + (if (r.probe.isNotBlank()) " · ${r.probe.removePrefix("probe: ")}" else ""))
        }
        if (r.modelWouldWin > 0 || (r.market.isNotEmpty() && r.pricedTrades > 0)) {
            tiles += DashboardModel.Tile("Market model would have won", if (r.pricedTrades > 0) "${r.modelWouldWin} of ${r.pricedTrades}" else "—", r.modelWouldEarnPoints?.let { "earning ${pts(it)} pts of pot each if our fair is true" + (if (c.marketPricing) " · pricing from it" else " · not used yet") } ?: "needs probe answers per class first")
        }
        // MK13: Novig's own count of the maker's last looks (`GET /rfq/v3/maker`): the 99% confirm-rate bar, the tier, any lock.
        val counted = r.confirmCounted
        if (counted != null) {
            val thr = r.confirmThreshold
            val short = counted >= (r.confirmMinSample ?: 0) && thr != null && counted > 0 && (r.confirmConfirmed ?: 0L).toDouble() / counted < thr
            tiles += DashboardModel.Tile(
                "Novig's confirm rate", if (counted > 0) "${r.confirmConfirmed ?: 0} of $counted" else "no last looks yet",
                (thr?.let { "bar ${"%.0f".format(Locale.US, it * 100)}%" + (r.confirmMinSample?.let { m -> " once $m are counted" } ?: "") } ?: "") + (r.makerTier?.let { " · tier $it" } ?: "") +
                    (r.lockSource?.let { src -> " · LOCKED ($src)" + (r.lockUntilMs?.let { u -> " until ${java.time.Instant.ofEpochMilli(u)}" } ?: "") } ?: ""),
                if (r.lockSource != null || short) Tone.BAD else if (counted > 0) Tone.GOOD else Tone.NEUTRAL,
            )
        }
        if (c.mode == RfqConfig.Mode.LIVE) {
            tiles += DashboardModel.Tile("Quotes", "${r.quotesSent}", "${r.won} won · ${r.confirmed} confirmed · ${r.filled} filled")
            val d24 = r.realizedPnl24h
            tiles += DashboardModel.Tile(
                "Settled parlays", r.realizedPnl?.let { (if (it >= 0) "+" else "-") + "\$" + String.format(Locale.US, "%.2f", kotlin.math.abs(it)) } ?: "—",
                "${r.makerWon} won · ${r.makerLost} lost · ${r.openParlays} open" + (d24?.let { " · 24 h " + (if (it >= 0) "+" else "-") + "\$" + String.format(Locale.US, "%.2f", kotlin.math.abs(it)) } ?: ""),
                if ((r.realizedPnl ?: 0.0) > 0.005) Tone.GOOD else if ((r.realizedPnl ?: 0.0) < -0.005) Tone.BAD else Tone.NEUTRAL,
            )
            tiles += DashboardModel.Tile("Collateral tied up", usd(r.openCollateral), if (c.walletCollateral && r.walletTotal != null) "of your ${usd(r.walletTotal)} wallet, ${usd(r.walletCash ?: 0.0)} still free to post (the wallet is the limit, not a fixed cap)" else "of ${usd(c.maxOpenCollateral)} allowed in all",
                if (!c.walletCollateral && r.openCollateral > c.maxOpenCollateral * 0.9) Tone.WARN else Tone.NEUTRAL)
        }
        val headline = when (c.mode) {
            RfqConfig.Mode.OFF -> "Off"
            RfqConfig.Mode.SHADOW -> if (r.connected) "Shadow: listening" else "Shadow: not connected"
            RfqConfig.Mode.LIVE -> if (r.halted != null) "Live: stopped" else if (r.connected) "Live: quoting" else "Live: not connected"
        }
        val tone = when {
            r.halted != null -> Tone.BAD
            c.mode == RfqConfig.Mode.OFF -> Tone.NEUTRAL
            r.connected -> Tone.GOOD
            else -> Tone.WARN
        }
        val explain = when (c.mode) {
            RfqConfig.Mode.OFF -> "The engine is not registered as a parlay maker and listens to nothing. Shadow listens to every parlay Novig is asked to price and keeps a record of what it would have quoted: it sends nothing and risks nothing."
            RfqConfig.Mode.SHADOW -> "Listening to every parlay Novig is asked to price (about 60 a second) and every parlay that trades. It prices what it can from the engine's fair lines and records the quote it would have made. It sends nothing and risks nothing."
            RfqConfig.Mode.LIVE -> "Quoting real prices on parlays it can price, within the caps below. A parlay it wins ties up the collateral (what it pays if the parlay hits) from the Vigilant wallet until the games settle."
        }
        val ev = "Evidence for going live: ${r.pricedTrades} of ${RfqStatus.EVIDENCE_TRADES} priced trades" + (r.medianGapPoints?.let { "; the market paid ${pts(it)} pts of pot over our fair" } ?: "") + "."
        val rule = (if (c.allowProps) "Quote rule (player props allowed): " else "Quote rule: game-line parlays only (the market pays 38% over our fair on parlays with a player prop, 2.6% on game lines); ") + "at least ${"%.1f".format(Locale.US, c.minReturn * 100)}% return on the collateral (more for longer lock-ups), ${"%.1f".format(Locale.US, c.minPoints * 100)} pt per extra leg, ${"%.1f".format(Locale.US, c.margin * 100)}% floor; 2 to ${c.maxLegs} legs, never a parlay likelier than ${"%.0f".format(Locale.US, c.maxFair * 100)}% to win or with a leg likelier than ${"%.0f".format(Locale.US, c.maxLegFair * 100)}%, " +
            (if (c.walletCollateral) "stake as asked up to ${usd(c.maxStake)}; the wallet is the collateral limit: one parlay up to ${"%.0f".format(Locale.US, c.walletShare * 100)}% of it, everything posted together up to the cash in it."
            else "stake as asked up to ${usd(c.maxStake)}, ${usd(c.maxCollateral)} per parlay, ${usd(c.maxOpenCollateral)} in all.")
        val warning = when {
            r.halted != null -> "Stopped: ${r.halted}"
            c.mode == RfqConfig.Mode.LIVE && !r.connected -> "Not connected: ${r.note.ifBlank { "waiting for the socket" }}"
            c.mode != RfqConfig.Mode.OFF && !r.connected && r.note.isNotBlank() -> "Socket: ${r.note}"
            else -> null
        }
        // The rule is judged on its own quotes: 200 priced trades, and over them our rule would have won at least a fifth and earned something (if our fair is true) per win.
        val met = r.pricedTrades >= RfqStatus.EVIDENCE_TRADES && r.wouldWin * 5 >= r.pricedTrades && (r.wouldEarnPoints ?: 0.0) > 0.5
        return View(headline, tone, explain, tiles, ev, rule, warning, met)
    }

    /** The LIVE switch's confirmation: what is at stake in words, from the applied caps and the evidence so far. */
    fun liveWarning(r: RfqStatus?): String {
        val c = r?.config ?: RfqConfig()
        val have = r?.pricedTrades ?: 0
        val limits = if (c.walletCollateral) "up to ${"%.0f".format(Locale.US, c.walletShare * 100)}% of the Vigilant wallet (everything posted together up to the cash in it)" else "up to ${usd(c.maxCollateral)} of the Vigilant wallet (${usd(c.maxOpenCollateral)} in all)"
        return "Live quotes are real prices on Novig. Each parlay it wins ties up $limits until the games finish, and it pays that out if the parlay hits. " +
            (if (have < RfqStatus.EVIDENCE_TRADES) "The record so far is $have of ${RfqStatus.EVIDENCE_TRADES} priced trades: shadow is the cheaper way to learn more first." else "The shadow record has ${RfqStatus.EVIDENCE_TRADES}+ priced trades.")
    }
}
