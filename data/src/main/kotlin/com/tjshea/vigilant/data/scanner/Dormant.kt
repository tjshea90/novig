package com.tjshea.vigilant.data.scanner

/**
 * Features that can be put to sleep: their code stays and nothing runs or is shown while the flag is set.
 *
 * **Pinnodds is AWAKE in the app** (Tj, 2026-10-09, clarified: "When I told you stop using pinnodds I meant I didn't want Claude to use the API because I was using it at that time and it only
 * allows one connection. I still want to use it in the app"). [PINNODDS] stays false; the earlier "dormant" wording was only ever about Claude's sessions: **a session never opens a Pinnodds connection,
 * uses its key or runs its recorders** (`tools/research/pinn_*.py`, `pinnodds_tape.py`), because the account allows ONE WebSocket and Tj's app is using it. In the app, Settings › Pinnodds live,
 * the live feed and its switches work as built; with SGO Pro on, its REST Pinnacle board also prices the scan first (`PinnapiClient`).
 */
object Dormant {
    /** True = asleep. False (awake) for the app; a variable so a test can put it to sleep for its own run. */
    @Volatile
    var PINNODDS: Boolean = false
}
