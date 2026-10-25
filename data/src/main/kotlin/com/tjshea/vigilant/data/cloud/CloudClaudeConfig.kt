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

    /**
     * [adopt] without losing what the phone changed since its last push (PX10e, 2026-10-25: a session set Pinnodds off at 21:46; at 21:48 a pull to refresh made the phone push its whole settings, still
     * with Pinnodds on, before its once-a-minute adoption had run, and the session's change was gone). [base] is what the phone last pushed: a setting the phone changed since then keeps the phone's value
     * (Tj's own tap wins a clash), every other setting takes [remote]'s; no [base] (a phone that never pushed) is [adopt]. Null when [remote] is not a Claude write or changes nothing.
     */
    fun merge(base: ScanSettings?, remote: CloudConfig, local: ScanSettings, json: Json): ScanSettings? {
        if (!isClaude(remote)) return null
        if (base == null) return adopt(remote, local)
        val ser = ScanSettings.serializer()
        val merged = merge3(json.encodeToJsonElement(ser, base), json.encodeToJsonElement(ser, remote.settings), json.encodeToJsonElement(ser, local)) ?: return null
        val settings = runCatching { json.decodeFromJsonElement(ser, merged) }.getOrNull() ?: return adopt(remote, local)
        return adopt(remote.copy(settings = settings), local)
    }

    /** One setting (a leaf, or an object read field by field) from the three copies: unchanged on the phone = GitHub's; unchanged on GitHub = the phone's; changed on both = the phone's. */
    private fun merge3(b: JsonElement?, r: JsonElement?, l: JsonElement?): JsonElement? = when {
        l == b -> r
        r == b -> l
        l is JsonObject && r is JsonObject -> JsonObject((l.keys + r.keys).mapNotNull { k -> merge3((b as? JsonObject)?.get(k), r[k], l[k])?.let { k to it } }.toMap())
        else -> l
    }

    /** The settings the phone should hold after reading [remote], or null when [remote] is not a Claude write (or changes nothing). [local] keeps its STOP ALL; a wanted-on engine un-pauses the scan. */
    fun adopt(remote: CloudConfig, local: ScanSettings): ScanSettings? {
        if (!isClaude(remote)) return null
        var next = remote.settings.copy(killed = local.killed, killedAtMs = local.killedAtMs)
        if (remote.on && !next.killed) next = next.copy(pausedByHand = false, gates = next.gates.copy(cloudScan = true))
        return next.takeIf { it != local }
    }
}
