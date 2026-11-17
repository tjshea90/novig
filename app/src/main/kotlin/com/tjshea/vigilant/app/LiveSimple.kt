package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * ONE plain choice for live betting (Tj, 2026-11-17, LV1: "half of the toggles I don't understand ... overhaul the live betting system"). OFF = no live lane, bid or socket;
 * PRACTICE = every live lane runs and records what it WOULD do, no real order (the evidence that earns REAL); REAL = the lanes place real orders (the engine's trading must still be LIVE).
 * It only sets the switches the old controls set, so every reader sees the same thing; the old controls stay under "Advanced".
 */
object LiveSimple {
    enum class Mode(val label: String, val note: String) {
        OFF("Off", "No live bets, bids or live sockets."),
        PRACTICE("Practice", "Live runs and records the bets and bids it would place, with no real money. Its results show whether live pays."),
        REAL("Real", "Live places real orders (engine trading must be Live), at your live limits."),
    }

    fun modeOf(s: ScanSettings): Mode = when {
        s.gates.liveMaster.off || !(s.tailLive.on || s.pinnLive || s.liveBid) -> Mode.OFF
        s.tailLive.bet || s.pinnLiveBet && s.pinnLive || s.liveBidReal && s.liveBid -> Mode.REAL
        else -> Mode.PRACTICE
    }

    fun set(s0: ScanSettings, mode: Mode): ScanSettings {
        if (mode == Mode.OFF) return LiveMaster.set(s0, false).let { it.copy(tailLive = it.tailLive.copy(bet = false)) }
        val s = LiveMaster.set(s0, true)
        val real = mode == Mode.REAL
        return s.copy(
            tailLive = s.tailLive.copy(on = true, light = true, bids = true, engine = true, bet = real),
            pinnLive = true, pinnLiveBet = real, liveBid = true, liveBidReal = real,
            gates = s.gates.copy(novigSocket = true),
        )
    }
}
