package com.tjshea.vigilant.data.novig.lab.gh

import kotlinx.serialization.Serializable

/**
 * One line of the GitHub lab's EDGE LOG (Tj, 2026-10-09: "copy as much of the research lab into GitHub as possible"): a Novig outcome priced by Vigilant's own scanner against the outside books' fair
 * (SportsGameOdds Pro, Kalshi, Polymarket), written when the fair or the ask moved. [ask] is Novig's taker price, [bestBid] the best resting bid, [ev] the EV of taking at [ask] (fee included).
 * From it a session can rebuild any would-be take, the last pre-start fair (the app's own closing line), and how fast Novig follows a move of the outside fair.
 */
@Serializable
data class EdgeRow(
    val atMs: Long,
    val league: String,
    val eventId: String,
    val event: String,
    val startsTs: Long,
    val live: Boolean,
    val kind: String,
    val selection: String,
    val marketId: String,
    val outcomeId: String,
    val fair: Double,
    val ask: Double?,
    val bestBid: Double?,
    val ev: Double?,
    val books: Int,
    val fairAgeSec: Int,
)

/**
 * One change of one book's main-line price on SportsGameOdds, as the SGO TAPE saw it: [atMs] when this read happened, [updatedMs] the time SGO says it last saw the price at the book. The gaps between
 * a key's [updatedMs] are how often SGO really refreshes that book; [atMs] - [updatedMs] is how old a price is when Vigilant reads it.
 */
@Serializable
data class SgoTick(
    val atMs: Long,
    val league: String,
    val eventId: String,
    val live: Boolean,
    val oddId: String,
    val book: String,
    val odds: Double?,
    val point: Double?,
    val updatedMs: Long?,
    val available: Boolean,
)

/** A book's price at the start of an event (SGO `includeOpenCloseOdds`), written once per event, book and market: the independent closing line for the EDGE LOG's pre-start fairs. */
@Serializable
data class SgoCloseRow(
    val atMs: Long,
    val league: String,
    val eventId: String,
    val home: String,
    val away: String,
    val startsMs: Long?,
    val oddId: String,
    val book: String,
    val openOdds: Double?,
    val closeOdds: Double?,
    val openPoint: Double?,
    val closePoint: Double?,
    val score: String?,
)
