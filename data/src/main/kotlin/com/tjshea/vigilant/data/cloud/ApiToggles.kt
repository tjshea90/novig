package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * Every API's on/off switch as the engine, a Claude session and the phone all name it (Tj, 2026-10-15: "All the settings should be mirrored between the phone app and the GitHub app, including API toggles").
 * The phone's `ApiEntry.setOn` is the same table; `ApiTogglesParityTest` (app) fails when the two drift. An id here is the lower-case name of the phone's `ApiEntry`.
 */
object ApiToggles {
    /** The ids the engine may ask to switch: the odds feeds (never Novig, never the trading switches). Pinnodds and Kalshi's socket stay the phone's own. */
    val IDS = listOf("sgo", "boltodds", "oddspapi", "oddspapi_free", "unabated", "parlay", "propline", "odds_api", "pinnwire", "pinnapi", "kalshi", "polymarket")

    /** The meter id ([com.tjshea.vigilant.data.keys.QuotaPolicy.id]) of a switch id, for matching the usage ledger and the feeds' answers. */
    fun policyId(id: String): String = if (id == "odds_api") "oddsapi" else id

    fun isOn(s: ScanSettings, id: String): Boolean? = when (id) {
        "sgo" -> s.sgoPro
        "boltodds" -> s.gates.useBoltOdds
        "oddspapi" -> s.oddsPapi
        "oddspapi_free" -> s.oddsPapiFree
        "unabated" -> s.unabated
        "parlay" -> s.useParlay
        "propline" -> s.usePropLine
        "odds_api" -> s.useOddsApi
        "pinnwire" -> s.gates.usePinnwire
        "pinnapi" -> s.gates.usePinnapi
        "pinnodds" -> s.gates.usePinnodds
        "kalshi" -> s.useKalshi
        "polymarket" -> s.usePolymarket
        else -> null
    }

    /** [s] with the switch [id] set; an unknown id changes nothing. Same table as the phone's `ApiEntry.setOn`. */
    fun setOn(s: ScanSettings, id: String, on: Boolean): ScanSettings = when (id) {
        "sgo" -> s.copy(sgoPro = on)
        "boltodds" -> s.copy(gates = s.gates.copy(useBoltOdds = on))
        "oddspapi" -> s.copy(oddsPapi = on)
        "oddspapi_free" -> s.copy(oddsPapiFree = on)
        "unabated" -> s.copy(unabated = on)
        "parlay" -> s.copy(useParlay = on)
        "propline" -> s.copy(usePropLine = on)
        "odds_api" -> s.copy(useOddsApi = on)
        "pinnwire" -> s.copy(gates = s.gates.copy(usePinnwire = on), usePinnacle = s.usePinnacle || on)
        "pinnapi" -> s.copy(gates = s.gates.copy(usePinnapi = on), usePinnacle = s.usePinnacle || on)
        "pinnodds" -> s.copy(gates = s.gates.copy(usePinnodds = on), usePinnacle = s.usePinnacle || on)
        "kalshi" -> s.copy(useKalshi = on)
        "polymarket" -> s.copy(usePolymarket = on)
        else -> s
    }

    /** [s] with every id in [ids] switched OFF (the engine's resting APIs: its own copy of the settings, never written back to the phone). */
    fun rest(s: ScanSettings, ids: Collection<String>): ScanSettings = ids.fold(s) { acc, id -> if (id in IDS) setOn(acc, id, false) else acc }
}
