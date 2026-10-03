package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode

/**
 * What bids are set to (the Bids tab's one choice): none, recommended for Tj to approve or deny, or posted by themselves (Tj, 2026-10-03: "I want to
 * have an option for it to be fully automatic and make the bids itself").
 */
enum class BidMode(val label: String) {
    OFF("Off"),
    RECOMMEND("Recommend"),
    AUTOMATIC("Fully automatic"),
    ;

    companion object {
        fun of(s: ScanSettings): BidMode = when {
            s.maker -> AUTOMATIC
            s.makerRecommend -> RECOMMEND
            else -> OFF
        }
    }
}

/**
 * Switching bids on turns on everything they need (Tj, 2026-10-03: "As soon as I turn on make bidding or auto make bidding, the app will
 * automatically toggle on everything it needs including vigilant scanning"): Vigilant's scanner (bids are priced from its fair odds), the background
 * scan with Vigilant in it (fresh fairs every few minutes, so bids stay up between Tj's own scans), at least once a minute, and scanning resumed.
 * Pure, so the tab can say what it will change before it does.
 */
object MakerSetup {

    /** The background interval bids want at most: Vigilant's scan runs back to back at it (never more often than every 4 min, [ScanSettings]). */
    const val MAX_INTERVAL_SECONDS = 60

    /** [settings] with bids set to [mode] and, for a mode that bids, what it needs turned on; [turnedOn] says what changed, in words. */
    data class Change(val settings: ScanSettings, val turnedOn: List<String>, val startsAutoBet: Boolean)

    fun set(s: ScanSettings, mode: BidMode): Change {
        val modeSet = when (mode) {
            BidMode.OFF -> s.copy(maker = false, makerRecommend = false)
            BidMode.RECOMMEND -> s.copy(maker = false, makerRecommend = true)
            BidMode.AUTOMATIC -> s.copy(maker = true)
        }
        if (mode == BidMode.OFF) return Change(modeSet, emptyList(), startsAutoBet = false)
        val on = ArrayList<String>()
        var next = modeSet
        if (next.scanner == ScannerMode.CNO) {
            next = next.copy(scanner = ScannerMode.BOTH)
            on += "Vigilant's scanner (Scanner: Both)"
        }
        if (next.autoScan != AutoScanMode.BOTH) {
            next = next.copy(autoScan = AutoScanMode.BOTH)
            on += "the background scan with Vigilant in it (CNO + Vigilant)"
        }
        if (next.autoScanSeconds > MAX_INTERVAL_SECONDS) {
            next = next.copy(autoScanSeconds = MAX_INTERVAL_SECONDS)
            on += "the background scan every ${ScanSettings.intervalLabel(MAX_INTERVAL_SECONDS)} (fresh fair prices for the bids)"
        }
        if (next.paused) {
            next = next.copy(paused = false)
            on += "scanning (it was paused)"
        }
        return Change(next, on, startsAutoBet = !s.autoBetsNow && next.autoBetsNow)
    }

    /** What bids still lack that this switch can't turn on: null when nothing. */
    fun missing(s: ScanSettings, bettingSetUp: Boolean): String? = when {
        !bettingSetUp -> "betting through Novig's API (Settings › Betting & Novig account): bids are posted from the Vigilant wallet"
        s.leagues.isEmpty() -> "a league to scan (Settings › Leagues)"
        s.makerKinds.isEmpty() -> "a kind of bet to bid on (the rules below)"
        else -> null
    }
}
