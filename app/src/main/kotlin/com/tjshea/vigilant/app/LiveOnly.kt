package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.LiveOnlyMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode

/**
 * Live games only mode (Tj, 2026-10-11: "only apis which can provide rapid odds are used, probably just pinnodds and novig. Only live bets are shown ... when I toggle live betting mode off, the apis that
 * were on before turn back on automatically, live betting is turned off ... Live betting mode should turn off anything not needed, for example, cno scanner").
 *
 * [enter] remembers what was on ([LiveOnlyMode]) and switches off every outside API but Pinnodds (the lane's fair), CNO, the background scan, bids, the auto-bet (pregame only) and the recorders, with
 * Include live games on; [leave] puts them back and turns Include live games OFF (nothing then spends a call on a live game). Both live in the saved settings, so every reader (the scan, the sources,
 * the auto-scan, the widget) sees the same thing with no check of its own; [ScanSettings.liveOnly] additionally keeps the REST scan and its rows out of the list.
 */
object LiveOnly {
    /** The APIs the real-time lane does not need: all but Pinnodds (its own socket is the fair value). */
    private val OFF = ApiEntry.entries.filter { it != ApiEntry.PINNODDS }

    fun enter(s: ScanSettings): ScanSettings {
        if (s.gates.liveOnly.on) return s
        val saved = LiveOnlyMode(
            on = true,
            apis = OFF.filter { it.isOn(s) }.map { it.name }.toSet(),
            scanner = s.scanner.name, autoScan = s.autoScan.name,
            maker = s.maker, autoBet = s.autoBet, burstRecorder = s.burstRecorder, feedRace = s.feedRace,
            altLab = s.altLab, researchMode = s.researchMode, burstTrade = s.burstTrade,
        )
        var n = OFF.fold(s) { acc, e -> e.setOn(acc, false) }
        // The Pinnacle umbrella switch was left on by PINNWIRE/PINNAPI/PINNODDS setOn; the boards themselves are off.
        n = n.copy(
            scanner = ScannerMode.VIGILANT, autoScan = AutoScanMode.OFF, maker = false, autoBet = false, burstRecorder = false,
            feedRace = false, altLab = false, researchMode = false, burstTrade = false, includeLive = true,
        )
        return n.copy(gates = n.gates.copy(liveOnly = saved))
    }

    fun leave(s: ScanSettings): ScanSettings {
        val m = s.gates.liveOnly
        if (!m.on) return s
        var n = OFF.fold(s) { acc, e -> e.setOn(acc, e.name in m.apis) }
        n = n.copy(
            scanner = runCatching { ScannerMode.valueOf(m.scanner) }.getOrDefault(n.scanner),
            autoScan = runCatching { AutoScanMode.valueOf(m.autoScan) }.getOrDefault(n.autoScan),
            maker = m.maker, autoBet = m.autoBet, burstRecorder = m.burstRecorder, feedRace = m.feedRace,
            altLab = m.altLab, researchMode = m.researchMode, burstTrade = m.burstTrade,
            includeLive = false,
        )
        return n.copy(gates = n.gates.copy(liveOnly = LiveOnlyMode()))
    }

    fun set(s: ScanSettings, on: Boolean): ScanSettings = if (on) enter(s) else leave(s)
}
