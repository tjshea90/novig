package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * What the Kalshi scan shares with the Novig scan (KV, Tj 2026-10-20: "adopt as many features as possible ... no double work"): the fair-odds side. Which APIs are switched on, which books are the
 * references and the sharp ones, and how the fair line is made are the account's, set once under Settings (Fair odds, APIs & keys) and read here at scan time. Everything about what Kalshi
 * does with money (the leagues, the edge floors, the auto-bet, the bids, the limits, the arbitrage) is the Kalshi config's own and is never taken from Novig's settings.
 */
object KalshiMirror {
    /** [kalshi] with the fair-odds switches and method of [novig]; Kalshi itself is never one of its own fair feeds. */
    fun fairOdds(kalshi: ScanSettings, novig: ScanSettings?): ScanSettings {
        if (novig == null) return kalshi.copy(useKalshi = false)
        return kalshi.copy(
            useKalshi = false,
            sgoPro = novig.sgoPro, oddsPapi = novig.oddsPapi, unabated = novig.unabated, oddsPapiFree = novig.oddsPapiFree,
            usePinnacle = novig.usePinnacle, usePolymarket = novig.usePolymarket, useOddsApi = novig.useOddsApi, useParlay = novig.useParlay,
            usePropLine = novig.usePropLine, useBookProps = novig.useBookProps,
            referenceBooks = novig.referenceBooks, fairSource = novig.fairSource, devigMethod = novig.devigMethod, sharpBooks = novig.sharpBooks,
            sharpWeight = novig.sharpWeight, fallbackToAverage = novig.fallbackToAverage, minBooks = novig.minBooks,
            exchangeMaxSpread = novig.exchangeMaxSpread, staleReferenceMinutes = novig.staleReferenceMinutes,
            gates = kalshi.gates.copy(
                usePinnwire = novig.gates.usePinnwire, usePinnapi = novig.gates.usePinnapi, usePinnodds = novig.gates.usePinnodds,
                opAllBooks = novig.gates.opAllBooks, op4Fair = novig.gates.op4Fair, sgoAllBooks = novig.gates.sgoAllBooks, propLineAllBooks = novig.gates.propLineAllBooks,
                useBoltOdds = novig.gates.useBoltOdds, useSxBet = novig.gates.useSxBet, boltAllBooks = novig.gates.boltAllBooks,
            ),
        )
    }
}
