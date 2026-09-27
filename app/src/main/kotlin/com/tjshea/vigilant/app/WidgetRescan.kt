package com.tjshea.vigilant.app

/**
 * When Vigilant's own scan runs again by itself (Tj, 2026-09-27: "an option to also use the
 * regular scan in addition to cno and put all the results in the widget together"): only with
 * [com.tjshea.vigilant.data.scanner.ScanSettings.widgetRescanMinutes] set, only while CNO's list
 * is on screen, [minutes] after the later of the last finished scan and the last one started
 * (so a scan that keeps failing isn't retried every few seconds).
 */
object WidgetRescan {

    /** Turning Vigilant's scan on from the widget scans at once when the last one is older than this (or missing). */
    const val STALE_ON_SWITCH_MS = 5 * 60_000L

    /** Milliseconds until the next scan (0 = now), or null when rescans are off. */
    fun dueInMs(minutes: Int, lastScanMs: Long?, lastStartedMs: Long?, now: Long): Long? {
        if (minutes <= 0) return null
        val from = maxOf(lastScanMs ?: Long.MIN_VALUE, lastStartedMs ?: Long.MIN_VALUE)
        if (from == Long.MIN_VALUE) return 0L
        return (from + minutes * 60_000L - now).coerceAtLeast(0L)
    }

    /** Whether switching Vigilant's scan on should start one now. */
    fun scanOnSwitch(lastScanMs: Long?, now: Long): Boolean = lastScanMs == null || now - lastScanMs > STALE_ON_SWITCH_MS

    /** A recheck this close to the fair odds' limit scans instead: it takes a few seconds, then the EVs must still stand. */
    const val RECHECK_MARGIN_MS = 30_000L

    /**
     * Whether the other books' prices behind [marketIds] (the oldest of them, else the last scan's time)
     * are too old, or nearly, for a Novig-only recheck to give EVs that can be shown.
     */
    fun fairTooOldToRecheck(state: UiState, marketIds: Collection<String>, now: Long): Boolean {
        val ids = marketIds.toSet()
        val oldest = state.result?.opportunities?.filter { it.market.marketId in ids }?.mapNotNull { it.fairAsOfMs }?.minOrNull()
            ?: state.status.scannedAtMs
            ?: return false
        return now - oldest > com.tjshea.vigilant.data.scanner.Freshness.MAX_QUOTE_AGE_MS - RECHECK_MARGIN_MS
    }
}
