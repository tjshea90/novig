package com.tjshea.vigilant.data.scanner

import kotlinx.serialization.Serializable

/**
 * Live tail bets (Tj, 2026-10-10: "I want to live bet today"; RESEARCH.md §125): buys, immediate-or-cancel, a far strike the late-game model (ESPN score and clock, Novig's own centre, the CONSERVATIVE rules)
 * calls decided while Novig still offers it under that fair by [minEdge] after its fee. Needs no outside price. [on] switches it on (paper: it decides and journals and sends nothing), [bet] makes it buy for
 * real: at most [stake] dollars a bet, [maxGame] a game, [maxDay] a day; a day's settled loss of [haltLoss] halts it ([halted] says why until Tj resumes it). One field of [ScanSettings] (a Kotlin
 * constructor takes at most 255 argument slots, and ScanSettings was at the edge).
 */
@Serializable
data class TailLiveSettings(
    val on: Boolean = false,
    val bet: Boolean = false,
    val stake: Double = 1.0,
    val maxGame: Double = 3.0,
    val maxDay: Double = 10.0,
    val haltLoss: Double = 5.0,
    val minEdge: Double = 0.05,
    val halted: String? = null,
    /** Live light's push lane (Tj, 2026-10-14): judge each live game the moment Novig's feed or the score changes, and score-only (a stale price is found even when the whole ladder is stale). Off = the slower 20-second pass. */
    val light: Boolean = true,
    /** Live light's bids: post-only bids far under the model's fair on decided live games (a second desk of its own). */
    val bids: Boolean = false,
    /** Run the live tail lane on the GitHub engine (Tj, 2026-10-17, TCL109) whenever the engine drives and trades (paper or live); the phone's own copy then stands down. Off = the phone runs it. */
    val engine: Boolean = true,
)
