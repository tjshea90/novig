package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.BidSource
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode

/**
 * What bids are set to (the Bids tab's one choice): none, recommended for Tj to approve or deny, or posted by themselves (Tj, 2026-10-03: "I want to
 * have an option for it to be fully automatic and make the bids itself").
 */
enum class BidMode(val label: String, /** On the tab's three-way choice, where a phone has room for about ten letters a choice. */ val short: String = label) {
    OFF("Off"),
    RECOMMEND("Recommend"),
    AUTOMATIC("Fully automatic", "Automatic"),
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
        if (next.makerSource == BidSource.CNO) return forCno(next, s, on)
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
            // Low API usage: the cycle (fills, expiries, the upkeep of the bids) is every minute, but Vigilant's own scan, the one that spends credits, waits its own pace.
            on += if (s.makerFocus == com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE) {
                "the background cycle every ${ScanSettings.intervalLabel(MAX_INTERVAL_SECONDS)} (fills and the bids' upkeep; Vigilant's own scan keeps its own pace: ${if (s.lowUsagePace == com.tjshea.vigilant.data.scanner.LowUsageBids.AUTO) "Auto" else "at most every ${s.lowUsagePace} min"})"
            } else {
                "the background scan every ${ScanSettings.intervalLabel(MAX_INTERVAL_SECONDS)} (fresh fair prices for the bids)"
            }
        }
        if (next.paused) {
            next = next.copy(pausedByHand = false)
            on += "scanning (it was paused)"
        }
        return Change(next, on, startsAutoBet = !s.autoBetsNow && next.autoBetsNow)
    }

    /**
     * Bids priced from CrazyNinjaOdds ([BidSource.CNO], RESEARCH.md §114) need CNO's list read in the background, not Vigilant's scan: the scanner must include CNO (Vigilant only becomes
     * CNO only; Both stays Both, Vigilant's scan still serving the Positive EV tab), the background scan must include CNO (Off or Vigilant becomes CNO; CNO + Vigilant stays), at
     * the interval Tj set up to [MAX_INTERVAL_SECONDS], and scanning resumed. Nothing turns Vigilant's scan on, and nothing turns auto-bet on.
     */
    private fun forCno(modeSet: ScanSettings, original: ScanSettings, on: ArrayList<String>): Change {
        var next = modeSet
        if (next.scanner == ScannerMode.VIGILANT) {
            next = next.copy(scanner = ScannerMode.CNO)
            on += "CrazyNinjaOdds' scanner (Scanner: CNO only)"
        }
        if (!next.autoScan.cno) {
            next = next.copy(autoScan = AutoScanMode.CNO)
            on += "the background scan on CrazyNinjaOdds (CNO)"
        } else if (next.autoScan == AutoScanMode.OFF) {
            next = next.copy(autoScan = AutoScanMode.CNO)
        }
        if (next.autoScanSeconds > MAX_INTERVAL_SECONDS) {
            next = next.copy(autoScanSeconds = MAX_INTERVAL_SECONDS)
            on += "the background scan every ${ScanSettings.intervalLabel(MAX_INTERVAL_SECONDS)} (CrazyNinjaOdds' odds change every 13-33 seconds)"
        }
        if (next.paused) {
            next = next.copy(pausedByHand = false)
            on += "scanning (it was paused)"
        }
        return Change(next, on, startsAutoBet = !original.autoBetsNow && next.autoBetsNow)
    }

    /** What bids still lack that this switch can't turn on: null when nothing. */
    fun missing(s: ScanSettings, bettingSetUp: Boolean): String? = when {
        !bettingSetUp -> "betting through Novig's API (Settings › Betting & Novig account): bids are posted from the Vigilant wallet"
        s.makerSource == BidSource.CNO && s.pinnacleOnly -> "Pinnacle only to be off (Settings › Scanning): it reads Novig and Pinnacle alone, so CrazyNinjaOdds is never read for bids"
        s.leagues.isEmpty() -> "a league to scan (Settings › Leagues)"
        s.makerKinds.isEmpty() -> "a kind of bet to bid on (the rules below)"
        else -> null
    }
}
