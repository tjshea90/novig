package com.tjshea.vigilant.data.scanner

import kotlinx.serialization.Serializable

/** Where the live engine's Pinnacle prices come from. */
@Serializable
enum class PinnFeedChoice(val label: String, val blurb: String) {
    SOCKET("Pinnodds socket", "Pinnacle's prices pushed by the Pinnodds WebSocket (your Pinnodds key; the trial ends 2026-10-10 23:34Z, then at least \$198 a month)."),
    BOLT("BoltOdds soft-book average", "NOT Pinnacle. The average of the soft books BoltOdds pushes for a live game (devigged, one vote per company, the highest and lowest dropped, only books that pushed in the last few seconds and agree). It trails the truth by seconds and has no scores, so it is judged by 'any move' / 'any edge' and starts PAPER ONLY: real orders need their own switch below."),
    SX("SX Bet exchange mid", "NOT Pinnacle: SX Bet's exchange book, the mid of its spread (a sharp venue, one market), pushed by its WebSocket with the live scores. A line is trusted only when its book was pushed after the last score change. Starts PAPER ONLY: real orders need their own switch below."),
    WEBSITE("Pinnacle website (free)", "Pinnacle's own website API, polled from your phone: no key, no account, no cost. MEASURED 2026-10-10: Pinnacle serves it through a cache (Cloudflare, up to 15 minutes), and the answers were 90-400 seconds old, with no way around it. It is NOT fresh enough for live betting, so real live orders are blocked while it is selected; paper only."),
}

/**
 * The Pinnacle website feed (Tj, 2026-10-10: "build the Pinnacle website feed behind a switch"; RESEARCH.md §126-§127). [feed] picks the source of the live engine's Pinnacle prices; [compare] runs the website
 * feed beside the Pinnodds socket, writing nothing to the engine, only timing each price version against the socket's (Diagnostics says how late the website feed is). [pollMs] is the pause between polls of
 * each live game, [maxGames] the most live games followed, [key] the public key Pinnacle's own site sends every visitor (empty = the built-in one; set it only if Pinnacle changes it). One field of
 * [ScanSettings] (a constructor takes at most 255 argument slots).
 */
@Serializable
data class PinnWebsiteSettings(
    val feed: PinnFeedChoice = PinnFeedChoice.SOCKET,
    val compare: Boolean = false,
    val pollMs: Int = 2_000,
    val maxGames: Int = 24,
    val key: String = "",
) {
    val website: Boolean get() = feed == PinnFeedChoice.WEBSITE

    /** The live fair is BoltOdds' soft-book average ([com.tjshea.vigilant.data.boltodds.BoltLiveConsensus]). */
    val bolt: Boolean get() = feed == PinnFeedChoice.BOLT

    /** The live fair is SX Bet's exchange mid ([com.tjshea.vigilant.data.sxbet.SxBetLiveFeed]). */
    val sx: Boolean get() = feed == PinnFeedChoice.SX

    /** The feed needs no Pinnodds key (the website feed, or BoltOdds / SX Bet, which have their own). */
    val noPinnKey: Boolean get() = website || bolt || sx
}
