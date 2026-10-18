package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * A Claude session may change any setting or switch of the app and the engine, except STOP ALL (Tj, 2026-10-18, TCL113: "any Claude session to be able to change and toggle on and off any setting and any switch
 * anywhere in the app and on GitHub except the stop all master switch"). A session writes `cloud-config/settings.json` with `tools/engine_set.py`, which marks the file `device = "claude:<account>"`; the engine reads
 * it at once, and the phone adopts it here the next time it looks (and then publishes its own copy as usual, which clears the mark), so the phone and GitHub show the same settings and a later change on the phone wins.
 * STOP ALL ([ScanSettings.killed], [ScanSettings.killedAtMs]) always stays the phone's own: a session can neither press it nor release it. Pure.
 */
object CloudClaudeConfig {
    const val MARK = "claude:"

    fun isClaude(c: CloudConfig): Boolean = c.device.startsWith(MARK)

    /** The settings the phone should hold after reading [remote], or null when [remote] is not a Claude write (or changes nothing). [local] keeps its STOP ALL; a wanted-on engine un-pauses the scan. */
    fun adopt(remote: CloudConfig, local: ScanSettings): ScanSettings? {
        if (!isClaude(remote)) return null
        var next = remote.settings.copy(killed = local.killed, killedAtMs = local.killedAtMs)
        if (remote.on && !next.killed) next = next.copy(pausedByHand = false, gates = next.gates.copy(cloudScan = true))
        return next.takeIf { it != local }
    }
}
