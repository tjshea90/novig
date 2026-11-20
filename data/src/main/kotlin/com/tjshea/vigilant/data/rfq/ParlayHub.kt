package com.tjshea.vigilant.data.rfq

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the parlay hub says (MK15, Tj 2026-11-19): the parlays the maker holds and held, in the words and numbers of a MAKER, from the [ParlayBook] the engine publishes. Pure, so every number is
 * tested; the screen only draws. Everything is from the maker's side: the BUYER is the person who bought the parlay from us; we keep their stake if any leg loses and pay out if every leg wins.
 *  - stake = the buyer's money, collateral = ours, put up to cover the payout (collateral = stake x (1 - price) / price);
 *  - buyer loses: we keep the stake; buyer wins: we lose the collateral (the buyer gets stake + collateral);
 *  - a leg that WON moves the buyer closer to winning (bad for us); a leg that LOST ends the parlay (we keep the stake once Novig settles it).
 */
object ParlayHub {
    enum class LegState { WON, LOST, PUSH, IN_PLAY, UPCOMING }

    data class LegView(val title: String, val detail: String, val state: LegState, val startsMs: Long, val chance: Double?, val stateText: String)

    enum class Status { WAITING, BUYER_AHEAD, BUYER_DEAD, ALL_WON }

    data class OpenView(
        val rfqId: String,
        val title: String,
        val status: Status,
        val statusText: String,
        /** "1 of 3 legs won · 2 still to play". */
        val progress: String,
        val legs: List<LegView>,
        val stake: Double,
        val collateral: Double,
        /** Our profit if the buyer loses (their stake) and our loss if the buyer wins (our collateral). */
        val ifBuyerLoses: Double,
        val ifBuyerWins: Double,
        /** The chance the buyer still wins by OUR fair, after the legs already decided; null when unknown. */
        val buyerChance: Double?,
        val expected: Double?,
        val expectedFinalMs: Long?,
        val finalText: String,
        val lastLegText: String,
        val filledText: String,
    )

    data class ClosedView(val rfqId: String, val title: String, val result: String, val pnl: Double, val won: Boolean?, val settledText: String, val detail: String, val legs: List<LegView>)

    data class Summary(
        val realizedPnl: Double,
        val pnl24h: Double,
        val settled: Int,
        val makerWon: Int,
        val makerLost: Int,
        val open: Int,
        /** Our money tied up in the open parlays now. */
        val collateral: Double,
        /** Sum of the buyers' stakes in the open parlays: what we keep if EVERY open parlay is lost by its buyer. */
        val ifAllBuyersLose: Double,
        /** What we lose if every open parlay that is still alive is won by its buyer. */
        val worstCase: Double,
        /** Stakes of open parlays whose buyer already lost a leg: ours once Novig settles them. */
        val lockedIn: Double,
        val lockedInCount: Int,
        /** The open parlays' expected profit by our fair, after the legs already decided. */
        val expectedOpen: Double,
        val nextFinalMs: Long?,
        val lastFinalMs: Long?,
        val expectedTotal: Double,
        /** The most collateral the maker may have up: the whole wallet (cash + what is posted) when the wallet is the limit (MK17), else the fixed cap. */
        val maxOpenCollateral: Double?,
        /** The cash in the wallet still free to post (wallet mode), else null. */
        val walletFree: Double? = null,
    )

    data class Hub(val summary: Summary, val open: List<OpenView>, val closed: List<ClosedView>, val empty: Boolean, val note: String)

    /** How long a game usually runs, hours, by league: the parlay is final about this long after its LAST leg starts. */
    fun gameHours(league: String): Double = when (league.uppercase()) {
        "NFL", "NCAAF" -> 3.6
        "NBA" -> 2.8
        "NHL" -> 2.8
        "NCAAB", "NCAAWB" -> 2.4
        "MLB" -> 3.3
        "MLS", "EPL", "LA LIGA", "SERIE A", "BUNDESLIGA", "LIGUE 1", "CHAMPIONS LEAGUE", "LIGA MX" -> 2.2
        "ATP", "WTA" -> 2.6
        "UFC", "BOXING" -> 3.0
        else -> 3.2
    }

    private fun usd(x: Double) = (if (x < 0) "-" else "") + "\$" + String.format(Locale.US, "%.2f", kotlin.math.abs(x))
    fun signed(x: Double) = (if (x >= 0.005) "+" else if (x <= -0.005) "-" else "") + "\$" + String.format(Locale.US, "%.2f", kotlin.math.abs(x))

    /** "Today 9:40 PM", "Tomorrow 1:10 PM", "Sat 9:40 PM" (within a week), "Nov 28, 9:40 PM". */
    fun clock(ms: Long, now: Long, zone: ZoneId): String {
        val t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
        val n = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        val time = t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
        val days = java.time.temporal.ChronoUnit.DAYS.between(n.toLocalDate(), t.toLocalDate())
        return when {
            days == 0L -> "Today $time"
            days == 1L -> "Tomorrow $time"
            days == -1L -> "Yesterday $time"
            days in 2..6 -> t.format(DateTimeFormatter.ofPattern("EEE", Locale.US)) + " $time"
            else -> t.format(DateTimeFormatter.ofPattern("MMM d", Locale.US)) + ", $time"
        }
    }

    /** "in 5 h 20 min", "in 40 min", "now", "3 h ago". */
    fun relative(ms: Long, now: Long): String {
        val d = ms - now
        val a = kotlin.math.abs(d)
        val h = a / 3_600_000L
        val m = (a % 3_600_000L) / 60_000L
        val body = when {
            a < 60_000L -> return "now"
            h >= 48 -> "${h / 24} days"
            h >= 1 -> if (m == 0L) "$h h" else "$h h $m min"
            else -> "$m min"
        }
        return if (d >= 0) "in $body" else "$body ago"
    }

    private fun resultOf(l: BookLeg): LegState? = when (l.result?.uppercase()) { "WIN" -> LegState.WON; "LOSS" -> LegState.LOST; "PUSH" -> LegState.PUSH; else -> null }

    fun legViews(p: BookParlay, now: Long, zone: ZoneId): List<LegView> = p.legs.map { l ->
        val decided = resultOf(l)
        val state = decided ?: if (l.startsMs in 1..now) LegState.IN_PLAY else LegState.UPCOMING
        val started = if (l.startsMs > 0L) clock(l.startsMs, now, zone) else "start time unknown"
        val text = when (state) {
            LegState.WON -> "Won for the buyer"
            LegState.LOST -> "Lost: the buyer's parlay is dead"
            LegState.PUSH -> "Void (pushed)"
            LegState.IN_PLAY -> when {
                // Tj 2026-11-20: small tennis tournaments (Challengers) are on no free score feed, so the result may never come: say that instead of "waiting".
                (l.league == "ATP" || l.league == "WTA") && now - l.startsMs > 12 * 3_600_000L -> "Match over; no score source for this tournament, result unknown"
                l.startsMs + (gameHours(l.league) * 3_600_000L).toLong() < now -> "Game over, waiting for the result"
                else -> "In play since ${clock(l.startsMs, now, zone)}"
            }
            LegState.UPCOMING -> "Starts $started"
        }
        LegView(l.label.ifBlank { "Leg" }, listOf(l.desc, l.league).filter { it.isNotBlank() }.joinToString(" · "), state, l.startsMs, l.p.takeIf { it > 0.0 }, text)
    }

    /** The chance the buyer still wins, by our fair, with every leg already won taken out; 0 if one lost; null when our fair for it was not kept. */
    fun buyerChance(p: BookParlay): Double? {
        if (p.legs.any { resultOf(it) == LegState.LOST }) return 0.0
        val settledLegs = p.legs.filter { resultOf(it) == LegState.WON || resultOf(it) == LegState.PUSH }
        if (settledLegs.size == p.legs.size && p.legs.isNotEmpty()) return 1.0
        val fair = p.fair ?: return null
        val done = settledLegs.fold(1.0) { a, l -> a * (l.p.takeIf { it > 0.0 } ?: 1.0) }
        return (fair / done.coerceAtLeast(0.01)).coerceIn(fair, 0.999)
    }

    fun expectedProfit(stake: Double, collateral: Double, chance: Double): Double = (stake + collateral) * (1.0 - chance) - collateral

    /** The maker's profit on one settled parlay (the buyer's result decides): the stake if the buyer lost, minus the collateral if the buyer won, a void 0. */
    fun settledPnl(p: BookParlay): Double? {
        if (!p.settled) return null
        val scalar = when (p.result) { "win" -> 1.0; "loss" -> 0.0; "fmv" -> p.fmv ?: return null; "push" -> return 0.0; else -> return null }
        return (p.wager + p.collateral) * (1.0 - scalar) - p.collateral
    }

    fun expectedFinal(p: BookParlay): Long? {
        val ends = p.legs.filter { it.startsMs > 0L }.map { it.startsMs + (gameHours(it.league) * 3_600_000L).toLong() }
        return ends.maxOrNull()
    }

    private fun openView(p: BookParlay, now: Long, zone: ZoneId): OpenView {
        val legs = legViews(p, now, zone)
        val won = legs.count { it.state == LegState.WON || it.state == LegState.PUSH }
        val lost = legs.count { it.state == LegState.LOST }
        val toPlay = legs.size - won - lost
        val chance = buyerChance(p)
        val status = when {
            lost > 0 -> Status.BUYER_DEAD
            legs.isNotEmpty() && won == legs.size -> Status.ALL_WON
            won > 0 -> Status.BUYER_AHEAD
            else -> Status.WAITING
        }
        val statusText = when (status) {
            Status.BUYER_DEAD -> "The buyer already lost a leg: we keep their ${usd(p.wager)} once Novig settles it"
            Status.ALL_WON -> "Every leg won: the buyer wins, we pay ${usd(p.collateral)} once Novig settles it"
            Status.BUYER_AHEAD -> "The buyer is ahead: $won of ${legs.size} legs won"
            Status.WAITING -> "Nothing decided yet"
        }
        val progress = when {
            legs.isEmpty() -> "${p.nLegs} legs"
            lost > 0 -> (if (lost == 1) "1 leg lost" else "$lost legs lost") + (if (won > 0) " · $won won" else "") + (if (toPlay > 0) " · the other $toPlay no longer matter" else "")
            else -> "$won of ${legs.size} legs won" + (if (toPlay > 0) " · $toPlay still to play" else "")
        }
        val end = expectedFinal(p)
        val last = p.legs.filter { it.startsMs > 0L }.maxByOrNull { it.startsMs }
        val finalText = when {
            end == null -> "Finish time unknown"
            status == Status.BUYER_DEAD -> "Decided; settles soon"
            end < now -> "Games should be over: waiting for Novig to settle"
            else -> "Final about ${clock(end, now, zone)} (${relative(end, now)})"
        }
        return OpenView(
            rfqId = p.rfqId, title = titleOf(p), status = status, statusText = statusText, progress = progress, legs = legs, stake = p.wager, collateral = p.collateral,
            ifBuyerLoses = p.wager, ifBuyerWins = -p.collateral, buyerChance = chance, expected = chance?.let { expectedProfit(p.wager, p.collateral, it) }, expectedFinalMs = end, finalText = finalText,
            lastLegText = last?.let { "Last leg: ${it.label}, starts ${clock(it.startsMs, now, zone)}" } ?: "", filledText = "Filled ${clock(p.filledMs, now, zone)}",
        )
    }

    private fun titleOf(p: BookParlay): String {
        val n = p.legs.size.takeIf { it > 0 } ?: p.nLegs
        val first = p.legs.firstOrNull()?.label?.takeIf { it.isNotBlank() && !it.startsWith("Leg ") }
        return "$n-leg parlay" + (first?.let { ": $it" + (if (n > 1) " + ${n - 1} more" else "") } ?: "")
    }

    private fun closedView(p: BookParlay, now: Long, zone: ZoneId): ClosedView {
        val pnl = settledPnl(p) ?: 0.0
        val won: Boolean? = when { pnl > 0.005 -> true; pnl < -0.005 -> false; else -> null }
        val result = when (p.result) {
            "loss" -> "The buyer lost: we kept ${usd(p.wager)}"
            "win" -> "The buyer won: we paid ${usd(p.collateral)}"
            "push" -> "Void: everything returned"
            "fmv" -> "Voided at fair value"
            else -> "Settled"
        }
        return ClosedView(p.rfqId, titleOf(p), result, pnl, won, p.settledMs?.let { "Settled ${clock(it, now, zone)}" } ?: "Settled", "Stake ${usd(p.wager)} · our collateral ${usd(p.collateral)} · quoted ${"%.1f".format(Locale.US, p.price * 100)}%", legViews(p, now, zone))
    }

    fun of(book: ParlayBook?, rfq: RfqStatus?, now: Long, zone: ZoneId = ZoneId.systemDefault()): Hub {
        val parlays = book?.parlays.orEmpty()
        val open = parlays.filter { !it.settled }.map { openView(it, now, zone) }.sortedWith(compareBy({ it.expectedFinalMs ?: Long.MAX_VALUE }, { it.rfqId }))
        val closedRaw = parlays.filter { it.settled }.sortedByDescending { it.settledMs ?: it.filledMs }
        val closed = closedRaw.map { closedView(it, now, zone) }
        val pnl = closedRaw.sumOf { settledPnl(it) ?: 0.0 }
        val pnl24 = closedRaw.filter { now - (it.settledMs ?: it.filledMs) <= 24 * 3_600_000L }.sumOf { settledPnl(it) ?: 0.0 }
        val dead = open.filter { it.status == Status.BUYER_DEAD }
        val alive = open.filter { it.status != Status.BUYER_DEAD }
        val expectedOpen = open.sumOf { it.expected ?: 0.0 }
        val finals = open.mapNotNull { it.expectedFinalMs }
        val summary = Summary(
            realizedPnl = pnl, pnl24h = pnl24, settled = closedRaw.size, makerWon = closed.count { it.won == true }, makerLost = closed.count { it.won == false }, open = open.size,
            collateral = open.sumOf { it.collateral }, ifAllBuyersLose = open.sumOf { it.stake }, worstCase = -alive.sumOf { it.collateral }, lockedIn = dead.sumOf { it.stake }, lockedInCount = dead.size,
            expectedOpen = expectedOpen, nextFinalMs = finals.filter { it >= now }.minOrNull() ?: finals.minOrNull(), lastFinalMs = finals.maxOrNull(), expectedTotal = pnl + expectedOpen,
            maxOpenCollateral = if (rfq?.config?.walletCollateral == true && rfq.walletTotal != null) rfq.walletTotal else rfq?.config?.maxOpenCollateral,
            walletFree = if (rfq?.config?.walletCollateral == true) rfq.walletCash else null,
        )
        val note = when {
            book == null -> "Waiting for the engine to publish the parlay book."
            parlays.isEmpty() -> "No parlay has been filled yet. When the maker is live and a quote wins, it appears here."
            else -> ""
        }
        return Hub(summary, open, closed, parlays.isEmpty(), note)
    }

    /** The one sentence at the top: where the maker stands in money. */
    fun headline(s: Summary): String = when {
        s.open == 0 && s.settled == 0 -> "No parlays yet"
        else -> "${signed(s.realizedPnl)} so far on ${s.settled} settled · ${s.open} open"
    }
}
