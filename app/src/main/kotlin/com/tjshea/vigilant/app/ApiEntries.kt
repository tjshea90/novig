package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * Every outside API the app can use, with the one switch that turns it on and off (Tj, 2026-10-11: "toggle on and off any apis, and vigilant will adapt to what apis are active at any time ... while not
 * using any API at all which is toggled off"). Settings › APIs lists these; each row's switch is [isOn]/[setOn], its Test button checks the key, its meter is [policy]'s.
 */
enum class ApiEntry(val title: String, val provider: ApiProvider?, val policy: QuotaPolicy?, val blurb: String, val usagePrompt: Boolean) {
    SGO("SportsGameOdds Pro", ApiProvider.SPORTSGAMEODDS, QuotaPolicy.SGO, "Odds, alternates, props, closes and scores from 80+ books", true),
    ODDSPAPI("OddsPapi (v5)", ApiProvider.ODDSPAPI, QuotaPolicy.ODDSPAPI, "Pinnacle, Circa and US books with change times, props, CLV and scores (trial or contract key)", false),
    ODDSPAPI_FREE("OddsPapi Free (v4)", ApiProvider.ODDSPAPI_FREE, QuotaPolicy.ODDSPAPI_FREE, "Pinnacle closing lines for CLV from free historical odds (free-plan key)", true),
    UNABATED("Unabated (free)", ApiProvider.UNABATED, QuotaPolicy.UNABATED, "DraftKings, FanDuel, MGM, Caesars, theScore, Fanatics lines and props; last in line", false),
    PARLAY("ParlayAPI", ApiProvider.PARLAY, QuotaPolicy.PARLAY, "Pinnacle and 30 books, props, injuries, closing lines", true),
    PROPLINE("PropLine", ApiProvider.PROPLINE, QuotaPolicy.PROPLINE, "30 books' lines and props", true),
    ODDS_API("The Odds API", ApiProvider.THE_ODDS_API, QuotaPolicy.ODDS_API, "Sportsbook lines and props", true),
    PINNWIRE("PinnWire (Pinnacle)", ApiProvider.PINNWIRE, QuotaPolicy.PINNWIRE, "Pinnacle's board with player props", false),
    PINNAPI("pinnapi (Pinnacle)", ApiProvider.PINNAPI, QuotaPolicy.PINNAPI, "Pinnacle's board (PinnWire's backup)", false),
    PINNODDS("Pinnodds", ApiProvider.PINNODDS, null, "Pinnacle's live push feed and REST board", false),
    KALSHI("Kalshi", null, QuotaPolicy.KALSHI, "Free exchange prices, no key", false),
    POLYMARKET("Polymarket", null, QuotaPolicy.POLYMARKET, "Free exchange prices, no key", false);

    /** Whether the app is allowed to call it at all. */
    fun isOn(s: ScanSettings): Boolean = when (this) {
        SGO -> s.sgoPro
        ODDSPAPI -> s.oddsPapi
        ODDSPAPI_FREE -> s.oddsPapiFree
        UNABATED -> s.unabated
        PARLAY -> s.useParlay
        PROPLINE -> s.usePropLine
        ODDS_API -> s.useOddsApi
        PINNWIRE -> s.usePinnacle && s.usePinnwire
        PINNAPI -> s.usePinnacle && s.usePinnapi
        PINNODDS -> s.usePinnodds
        KALSHI -> s.useKalshi
        POLYMARKET -> s.usePolymarket
    }

    fun setOn(s: ScanSettings, on: Boolean): ScanSettings = when (this) {
        SGO -> s.copy(sgoPro = on)
        ODDSPAPI -> s.copy(oddsPapi = on)
        ODDSPAPI_FREE -> s.copy(oddsPapiFree = on)
        UNABATED -> s.copy(unabated = on)
        PARLAY -> s.copy(useParlay = on)
        PROPLINE -> s.copy(usePropLine = on)
        ODDS_API -> s.copy(useOddsApi = on)
        PINNWIRE -> s.copy(usePinnwire = on, usePinnacle = s.usePinnacle || on)
        PINNAPI -> s.copy(usePinnapi = on, usePinnacle = s.usePinnacle || on)
        PINNODDS -> s.copy(usePinnodds = on)
        KALSHI -> s.copy(useKalshi = on)
        POLYMARKET -> s.copy(usePolymarket = on)
    }

    val keyless: Boolean get() = provider == null

    companion object {
        /** The entries that are on. */
        fun active(s: ScanSettings): List<ApiEntry> = entries.filter { it.isOn(s) }
    }
}
