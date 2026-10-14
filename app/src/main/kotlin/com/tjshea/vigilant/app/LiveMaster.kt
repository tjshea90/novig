package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.scanner.LiveMasterMode
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * The Live features master switch (Tj, 2026-10-14: "I need a way to turn off all live features and sockets on one main toggle"). [set] with `live = false` remembers every live switch ([LiveMasterMode]) and
 * turns it off: Live light (bets and bids), the live bids, the Pinnodds live engine, Include live games, the live and streaming engines, and every socket (Novig's market feed, BoltOdds, Pinnodds, Kalshi), so
 * nothing live is read or sent; `live = true` puts each back as it was. Both live in the saved settings, so every reader sees the same thing with no check of its own. Games only mode is left first.
 */
object LiveMaster {
    /** True while the master is ON (live features run as each is set). */
    fun isOn(s: ScanSettings): Boolean = !s.gates.liveMaster.off

    fun set(s: ScanSettings, live: Boolean): ScanSettings = if (live) on(s) else off(s)

    private fun off(s0: ScanSettings): ScanSettings {
        if (s0.gates.liveMaster.off) return s0
        val s = if (s0.gates.liveOnly.on) LiveOnly.leave(s0) else s0
        val g = s.gates
        val saved = LiveMasterMode(
            off = true, tailOn = s.tailLive.on, tailBids = s.tailLive.bids, liveBid = s.liveBid, pinnLive = s.pinnLive, includeLive = s.includeLive, streamLive = g.streamLive, streamScan = g.streamScan,
            novigSocket = g.novigSocket, boltSocket = g.boltSocket, pinnSocket = g.pinnSocket, kalshiSocket = g.kalshiSocket,
        )
        return s.copy(
            tailLive = s.tailLive.copy(on = false, bids = false, bet = false), liveBid = false, pinnLive = false, includeLive = false,
            gates = g.copy(streamLive = false, streamScan = false, novigSocket = false, boltSocket = false, pinnSocket = false, kalshiSocket = false, liveMaster = saved),
        )
    }

    private fun on(s: ScanSettings): ScanSettings {
        val m = s.gates.liveMaster
        if (!m.off) return s
        return s.copy(
            tailLive = s.tailLive.copy(on = m.tailOn, bids = m.tailBids), liveBid = m.liveBid, pinnLive = m.pinnLive, includeLive = m.includeLive,
            gates = s.gates.copy(
                streamLive = m.streamLive, streamScan = m.streamScan, novigSocket = m.novigSocket, boltSocket = m.boltSocket, pinnSocket = m.pinnSocket, kalshiSocket = m.kalshiSocket,
                liveMaster = LiveMasterMode(),
            ),
        )
    }
}
