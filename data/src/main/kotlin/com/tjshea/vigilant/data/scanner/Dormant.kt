package com.tjshea.vigilant.data.scanner

/**
 * Features Tj has put to sleep: their code stays (a revival is cheap) but nothing runs and nothing is shown.
 *
 * **Pinnodds is dormant (Tj, 2026-10-09: "Stop using the pinnodds API").** No Pinnodds connection is opened by the app (the live feed, its paper mode, Research mode and the paper lab's
 * Pinnacle alternate lines all stay off), its Settings page and search entries are hidden, and no Claude session uses the API, its key or its recorders (`tools/research/pinn_*.py`,
 * `pinnodds_tape.py`) or schedules anything that does. To revive it (only when Tj asks): set [PINNODDS] false, and re-read `PINNODDS_API.md` and RESEARCH.md §116-§118, §120.
 */
object Dormant {
    /** True = dormant. A variable only so the (kept) Pinnodds UI tests can wake it for their own run and put it back. */
    @Volatile
    var PINNODDS: Boolean = true
}
