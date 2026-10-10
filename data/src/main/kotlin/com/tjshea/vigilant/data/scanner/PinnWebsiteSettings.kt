package com.tjshea.vigilant.data.scanner

import kotlinx.serialization.Serializable

/** Where the live engine's Pinnacle prices come from. */
@Serializable
enum class PinnFeedChoice(val label: String, val blurb: String) {
    SOCKET("Pinnodds socket", "Pinnacle's prices pushed by the Pinnodds WebSocket (your Pinnodds key; the trial ends 2026-10-10 23:34Z, then at least \$198 a month)."),
    WEBSITE("Pinnacle website (free)", "Pinnacle's own website feed, polled every couple of seconds from your phone: no key, no account, no cost. Same prices as the socket, but seconds old instead of tens of milliseconds."),
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
}
