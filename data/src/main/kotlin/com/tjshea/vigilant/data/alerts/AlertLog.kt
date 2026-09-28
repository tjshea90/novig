package com.tjshea.vigilant.data.alerts

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.serialization.Serializable

/** One bet an alert went out for. */
@Serializable
data class AlertedBet(
    /** [EvAlert.dedupeKey]: Novig's outcome id when known, else the scanner's own key for the bet. */
    val key: String,
    val atMs: Long,
    val startsAtMs: Long? = null,
)

@Serializable
data class AlertBook(val bets: List<AlertedBet> = emptyList())

/**
 * A +EV bet worth a push notification (Tj, 2026-09-28: "if it is scanning in the background and at
 * any time it finds positive EV bets of 3% or higher and multiple books agree on the price that it
 * sends me an android push notification and I can click on the notification and it will open the
 * exact bet in novig immediately").
 */
data class EvAlert(
    /** Which scanner found it: "CNO" or "Vigilant". */
    val scanner: String,
    /** The scanner's own key for the bet (a CNO row key, or Vigilant's `marketId/outcomeId`). */
    val key: String,
    /** Novig's outcome id, when known: the same bet found by both scanners alerts once. */
    val outcomeId: String?,
    /** "Justin Jefferson Under 69.5". */
    val bet: String,
    /** "Player Receiving Yards". */
    val market: String,
    /** "Minnesota Vikings @ Tampa Bay Buccaneers". */
    val event: String,
    val american: Int,
    val ev: Double,
    /** Books pricing both sides, and how many of them alone make it +EV. */
    val books: Int,
    val agreeing: Int,
    val startsAtMs: Long?,
    /** Where tapping opens: the bet slip itself when [exact], else its game. Null: Novig's home. */
    val link: String?,
    val exact: Boolean,
) {
    val dedupeKey: String get() = outcomeId?.let { "outcome:$it" } ?: "$scanner:$key"
}

/**
 * Which bets have had an alert (alerts.json), so each bet alerts once: not again on the next scan,
 * nor from the other scanner, nor after Vigilant restarts. An entry drops off [KEEP_AFTER_START_MS]
 * after its game starts (or [KEEP_WITHOUT_START_MS] after the alert when the start isn't known).
 */
class AlertLog(
    private val store: JsonFileStore<AlertBook>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** [alerts] that haven't had an alert yet, one per bet (the best EV when two share a bet), best EV first. */
    suspend fun unseen(alerts: List<EvAlert>): List<EvAlert> {
        if (alerts.isEmpty()) return emptyList()
        val seen = store.read().bets.mapTo(HashSet()) { it.key }
        return alerts.sortedByDescending { it.ev }.distinctBy { it.dedupeKey }.filter { it.dedupeKey !in seen }
    }

    /** Records [alerts] as sent (and drops expired entries). */
    suspend fun record(alerts: List<EvAlert>) {
        val now = clock()
        store.update { book ->
            val kept = book.bets.filter { !expired(it, now) }
            val have = kept.mapTo(HashSet()) { it.key }
            AlertBook(kept + alerts.filter { it.dedupeKey !in have }.distinctBy { it.dedupeKey }.map { AlertedBet(it.dedupeKey, now, it.startsAtMs) })
        }
    }

    companion object {
        const val KEEP_AFTER_START_MS = 12 * 60 * 60_000L
        const val KEEP_WITHOUT_START_MS = 2 * 24 * 60 * 60_000L

        fun expired(bet: AlertedBet, now: Long): Boolean =
            bet.startsAtMs?.let { now > it + KEEP_AFTER_START_MS } ?: (now > bet.atMs + KEEP_WITHOUT_START_MS)
    }
}
