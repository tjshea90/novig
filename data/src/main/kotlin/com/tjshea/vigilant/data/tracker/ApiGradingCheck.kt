package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.trading.LedgerRow
import com.tjshea.vigilant.data.novig.trading.NovigPosition
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * "Grading check" (Tj, 2026-09-29: "tell me what you need me to do or show you to make sure the grading works after the bets are done"): what Novig's
 * own books say about every bet placed through the API, set beside what the Tracker did with it, as plain text Tj can copy or screenshot. The things
 * the design could only take from the docs are exactly what shows: what a `SETTLEMENT` row's `ref` names, whether a loss leaves no row, whether a
 * settled position leaves the positions list, and which rows the time window brings back. Only Tj's own trading data, never a key.
 */
class ApiGradingCheck(
    private val tracker: BetTracker,
    private val trading: NovigTradingClient,
    private val subaccountKeyId: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: TimeZone = TimeZone.getDefault(),
) {
    suspend fun report(): String {
        val now = clock()
        val all = tracker.all()
        val bets = all.filter { it.orderId != null }.sortedBy { it.startsTs }
        val time = SimpleDateFormat("MMM d, h:mm a", Locale.US).apply { timeZone = zone }
        fun at(ms: Long) = time.format(Date(ms))
        val out = StringBuilder()
        out.appendLine("GRADING CHECK · ${at(now)}")
        out.appendLine(
            "API bets in the Tracker: ${bets.size} (open ${bets.count { it.status == BetStatus.PENDING }}, graded by Novig ${bets.count { it.settledBy == BetSettler.BY_NOVIG }}, " +
                "by the score feeds ${bets.count { it.settledBy == BetSettler.BY_SCORES }}, by you ${bets.count { it.settledBy == BetSettler.BY_YOU }}).",
        )
        if (bets.isEmpty()) {
            out.appendLine("Nothing to check yet: place a bet through the API first.")
            return out.toString().trimEnd()
        }

        // ---- What Novig says ----
        out.appendLine()
        out.appendLine("== What Novig says (read just now) ==")
        val balance = tryRead { trading.balance(subaccountKeyId) }
        out.appendLine("Wallet: " + balance.fold({ String.format(Locale.US, "$%.2f", it) }, { "couldn't be read: ${it.message}" }))
        val positionsRead = tryRead { trading.positions() }
        val positions: List<NovigPosition> = positionsRead.getOrDefault(emptyList())
        positionsRead.fold(
            {
                out.appendLine("Positions held: ${it.size}" + if (it.isEmpty()) " (none)" else "")
                it.forEach { p -> out.appendLine("  market ${p.marketId} · outcome ${p.outcomeId} · ${p.qty} contracts · cost ${String.format(Locale.US, "$%.5f", p.cost)}") }
            },
            { out.appendLine("Positions: couldn't be read: ${it.message}") },
        )
        val from = bets.minOf { it.startsTs } - WINDOW_MS
        val to = bets.maxOf { it.startsTs } + WINDOW_MS
        val ledgerResult = tryRead { trading.ledger(subaccountKeyId, null, from, to, maxRows = MAX_ROWS) }
        val ledger: List<LedgerRow> = ledgerResult.getOrDefault(emptyList())
        ledgerResult.exceptionOrNull()?.let { out.appendLine("Ledger: couldn't be read: ${it.message}") }
        out.appendLine("Ledger, games scheduled ${at(from)} to ${at(to)}: ${ledger.size} rows" + if (ledger.size >= MAX_ROWS) " (stopped at $MAX_ROWS)" else "")
        val names = HashMap<String, String>()
        for (b in bets) {
            names[b.marketId] = "market of ${b.selection}"
            b.orderId?.let { names[it] = "order of ${b.selection}" }
            b.fillIds.forEach { names[it] = "fill of ${b.selection}" }
        }
        for (r in ledger.sortedByDescending { it.ts }.take(SHOWN_ROWS)) {
            val what = r.ref?.let { names[it] } ?: if (r.ref == null) "no ref" else "not one of your bets' ids"
            out.appendLine("  ${at(r.ts)} · ${r.kind} · ${String.format(Locale.US, "%+.5f", r.amount)} · ref ${r.ref ?: "—"} (${what})")
        }
        if (ledger.size > SHOWN_ROWS) out.appendLine("  … ${ledger.size - SHOWN_ROWS} older rows not shown")

        // ---- Each bet ----
        out.appendLine()
        out.appendLine("== Each API bet, and what the Tracker did with it ==")
        for ((i, b) in bets.withIndex()) {
            val ids = setOf(b.marketId, b.orderId) + b.fillIds
            val rows = ledger.filter { it.ref in ids }
            val held = positions.firstOrNull { it.marketId == b.marketId && it.outcomeId == b.outcomeId }
            val started = if (now >= b.startsTs) "started ${ago(now - b.startsTs)} ago" else "starts in ${ago(b.startsTs - now)}"
            out.appendLine("${i + 1}. ${b.selection} · ${b.marketLabel} · ${b.league} ${b.eventName} · game ${at(b.startsTs)} ($started)")
            out.appendLine("   market ${b.marketId} · outcome ${b.outcomeId}")
            out.appendLine("   order ${b.orderId} · fills ${b.fillIds.size}${if (b.fillIds.isNotEmpty()) " (${b.fillIds.joinToString(", ")})" else ""}")
            out.appendLine(
                "   ${b.contracts ?: "?"} contracts · paid ${b.paid?.let { String.format(Locale.US, "$%.5f", it) } ?: "?"} · fee ${String.format(Locale.US, "$%.5f", b.fee ?: 0.0)} · stake ${String.format(Locale.US, "$%.2f", b.stake)}" +
                    (b.contracts?.let { " · a win pays ${String.format(Locale.US, "$%.2f", it * 0.01)}" } ?: ""),
            )
            out.appendLine("   Tracker: ${b.status}${b.settledBy?.let { ", graded by $it" } ?: ""}${b.gradeNote?.let { ": $it" } ?: ""}")
            out.appendLine("   Novig's ledger rows naming it: " + if (rows.isEmpty()) "none" else rows.joinToString("; ") { "${it.kind} ${String.format(Locale.US, "%+.5f", it.amount)} (ref ${names[it.ref] ?: it.ref})" })
            out.appendLine("   Novig's position on it: " + (held?.let { "held ${it.qty} contracts, cost ${String.format(Locale.US, "$%.5f", it.cost)}" } ?: "not held"))
        }
        out.appendLine()
        out.appendLine(
            "How to read it: a won bet has a SETTLEMENT row of (contracts × 1¢) and no position; a push, its cost back; a fair-value settlement, another amount; " +
                "a lost bet should have NO row and no position. Anything else is what to send back.",
        )
        return out.toString().trimEnd()
    }

    private suspend fun <T> tryRead(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.message ?: e.javaClass.simpleName))
    }

    private fun ago(ms: Long): String {
        val m = ms / 60_000L
        return when {
            m < 60 -> "${m}m"
            m < 48 * 60 -> "${m / 60}h ${m % 60}m"
            else -> "${m / (24 * 60)}d"
        }
    }

    companion object {
        /** As wide as [ApiSettler]'s window: the ledger filters by Novig's own scheduled start, which a bet's start can differ from. */
        const val WINDOW_MS = ApiSettler.START_SLACK_MS
        const val MAX_ROWS = 600
        const val SHOWN_ROWS = 60
    }
}
