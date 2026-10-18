package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.serialization.json.JsonObject

/**
 * How each of the phone's settings reaches the GitHub engine (Tj, 2026-10-15: "make sure the settings are properly mirrored to GitHub ... look for contradictory or unneeded settings"). The whole [ScanSettings] is
 * written to GitHub on every change; the engine then treats each setting by its [Kind]:
 *  - [Kind.FETCH]: changes WHAT is read (a league, a feed, days ahead, alternate lines, per-scan caps): the running scan starts again (at most once in 30 s);
 *  - [Kind.PRICING]: changes how what was read is priced (devig method, sharp books, minimum books, bankroll, guards): applied from the next scan (a scan holds the scanner for minutes, and re-pricing needs it);
 *  - [Kind.FILTER]: only which priced bets the list keeps (minimum and maximum edge, odds limits, order): applied to the very next list, no scan;
 *  - [Kind.BIDS]: the bid desk's rules (margin, kinds, windows, popularity, limits; Tj, 2026-10-15: the candidate lines are made on GitHub under them): the engine makes the candidate lines again from the board it holds,
 *    at once, with no scan;
 *  - [Kind.PHONE]: never reaches a scan (the auto-bet's rules, alerts, the widget, CNO, live games and sockets, which stay on the phone): ignored by the engine.
 * Every serialized setting, and every member of the nested groups (`gates.*`, `vigGuards.*`, `guards.*`), is named here; [CloudScanKeyTest] fails when a new one is added without a decision, so a setting can no longer
 * be mirrored (or not) by accident.
 */
object CloudScanKey {
    enum class Kind { FETCH, PRICING, FILTER, BIDS, PHONE }

    private val fetch = setOf(
        "leagues", "families", "daysAhead", "sgoPro", "sgoExtraBooks", "sgoAltLines", "oddsPapi", "opExtraBooks", "opAltLines", "unabated", "oddsPapiFree",
        "usePinnacle", "usePolymarket", "useKalshi", "useOddsApi", "useParlay", "usePropLine", "oddsApiReuseMinutes", "staleReferenceMinutes",
        "linesPerGame", "propsPerGame", "maxBooksPerScan", "fillBudget", "useBookProps", "bookPropSet", "bookPropCreditsPerScan", "bookPropHours", "propLineGamesPerScan", "bookPropReuseMinutes",
        // bids on, and low-usage bids, change the scan's own profile (`ScanSettings.effective`) and whether candidate lines are made at all
        "maker", "makerRecommend", "makerSource", "makerFocus", "lowUsageBooks", "gates.cloudBids",
        // BoltOdds and Novig's feed run on the engine too (Tj, 2026-10-15): the switches decide which sockets it keeps and what it reads from them
        "gates.useBoltOdds", "gates.useSxBet", "gates.boltAllBooks", "gates.boltSocket", "gates.novigSocket", "gates.novigBbo", "gates.novigKeepOpen", "gates.novigCompress",
        "gates.usePinnwire", "gates.usePinnapi", "gates.opAllBooks", "gates.op4Fair", "gates.sgoAllBooks", "gates.propLineAllBooks", "gates.askAllSources", "gates.rankByMeasure",
    )
    private val pricing = setOf(
        "fairSource", "devigMethod", "sharpBooks", "sharpWeight", "fallbackToAverage", "minBooks", "referenceBooks", "bankroll", "kellyMultiplier", "sgoMaxAgeMinutes", "ignoreWideQuotes",
        "exchangeMaxSpread", "outlierGuard", "gates.freshestWins", "vigGuards.oneVotePerCompany", "vigGuards.ignoreBadOdds", "vigGuards.thinEarlyHaircut", "vigGuards.propHaircut",
    )
    private val filter = setOf("minEvPercent", "maxEvPercent", "minOdds", "maxOdds", "feedSort")
    private val bids = setOf(
        "makerMargin", "makerStakeMode", "makerStake", "makerMaxStake", "makerSharpVeto", "makerPopularFirst", "makerRequireSharp", "makerAnchorSharp", "makerGuard", "makerHalted", "makerGuardFromMs",
        "makerCnoMaxAgeSeconds", "makerMaxBids", "makerMaxDollars", "makerKinds", "makerTtlMinutes", "makerStopMinutes", "makerMinPrice", "makerMaxPrice", "makerMaxOdds", "makerMinOdds", "makerQuickMinBooks",
        "makerObscureFill", "makerObscureMargin", "makerObscureSharpMinEv", "makerObscureAgreePoints", "makerObscureMinBooks", "makerObscureStake", "makerBothSides", "makerMinBooks", "makerLongRun",
        "lowUsagePace", "lowUsageMargin", "sharpVetoMinEv", "trapEarlyHours", "trapBidHours", "trapNovigMove", "trapFirstListed", "apiMaxPerGame", "apiMaxStake",
        "gates.profitProven", "gates.repeatGuard", "gates.repeatMaxBets", "gates.repeatMaxDollars", "gates.repeatHedge", "gates.repeatMaxLines",
        "vigGuards.bidLevel", "vigGuards.bidMany", "vigGuards.bidDeploy", "vigGuards.bidDeployPct", "vigGuards.bidDeployKelly", "vigGuards.bidPerGameDollars", "vigGuards.bidPerGameBids",
        "vigGuards.bidWhileScanning", "vigGuards.bidFarLane", "vigGuards.bidVolume", "guards.bidGuard", "guards.bidGuardMinutes",
    )

    /** Everything else: the settings the engine never reads. Named one by one so that an unnamed one is a failing test, not a silent guess. */
    private val phone = setOf(
        // top level
        "includeLive", "slipStake", "slipCustomStake", "apiBetStake", "apiMaxPerDay", "autoBet", "autoBetTennis", "autoBetBooks", "autoBetMinEv", "autoBetAllAgree",
        "sharpAutoBet", "sharpAlerts", "sharpConfirmBooks", "sharpConfirmMaxAgeSeconds", "sharpConfirmMinEv", "sharpConfirmViaCno", "autoBetTwoSided", "autoBetStake", "autoBetCustomStake",
        "autoBetMaxStake", "autoBetMaxOdds", "autoBetMinOdds", "autoBetFavouriteExtraEv", "propGuardShare", "propGuardMinSample", "propGuardPerGame", "autoBetKinds", "autoLock", "trackerNovigOnly",
        "trackerHideLocked", "scanStudy", "scanStudyHidden", "autoLockMinPercent", "autoLockLive",
        "burstRecorder", "feedRace", "altLab", "quietNotifications", "researchMode", "burstLeagues", "burstTrade", "burstTradeStake", "burstTradeMaxGame", "burstTradeMaxDay",
        "burstTradeHaltLoss", "burstTradeHalted", "pinnLive", "pinnLiveBet", "pinnLiveStake", "pinnLiveMaxGame", "pinnLiveMaxDay", "pinnLiveHaltLoss", "pinnLiveHalted", "pinnLiveMinEv", "pinnLiveMinMove",
        "pinnLiveDevig", "pinnLiveTrigger", "pinnLivePregame", "pinnLiveHoldoffSeconds", "liveBid", "liveBidReal", "liveBidHalted", "liveBidQuality", "liveBidLimits", "liveBidPresetName", "liveBidPresets",
        "tailLive", "pinnWebsite",
        "presets", "presetName", "autoBetHalted", "kalshiFastPace", "miniWindow", "cnoEnabled", "cnoViewUrl", "cnoRefreshSeconds",
        "miniSource", "cnoFilters", "floatingWidget", "cnoCheckBooks", "cnoPlayerTeams", "cnoOnlyAgreed", "widgetRescanMinutes", "cnoLivePrices", "bookState", "startsWithinHours", "autoScan",
        "autoScanSeconds", "autoScanMinutes", "autoScanKeepAwake", "alertMinEv", "paused", "killed", "killedAtMs", "schema",
        // gates: sockets, the live lane, the streaming engine, the guards of orders: all the phone's
        "gates.usePinnodds", "gates.liveOnly", "gates.cloudScan", "gates.cloudSave", "gates.cloudHours", "gates.liveMaster",
        // the engine's own trading (TCL20d): read by the engine from the settings it is given, never a reason to restart a scan
        "gates.cloudTrading", "gates.cloudKelly", "gates.cloudBetCap", "gates.cloudGameCap", "gates.cloudWalletUse", "gates.cloudDayStop",
        "gates.pinnSocket", "gates.kalshiSocket", "gates.novigOrderPush", "gates.novigSocketOrders", "gates.moneyDeposited",
        "gates.streamScan", "gates.streamGapSeconds", "gates.streamLive", "gates.boltLiveMinCompanies",
        "gates.boltLiveMaxAgeSeconds", "gates.boltLiveMaxSpreadPoints", "gates.boltLiveReal",
        // vigGuards: the auto-bet's own rules (the bid desk's are in [bids])
        "vigGuards.autoBetVigilant", "vigGuards.autoBetMinStars", "vigGuards.autoBetOverPropExtraEv", "vigGuards.betArchive", "vigGuards.betLevel",
    )

    private val groups = setOf("gates", "vigGuards", "guards")

    fun kindOf(path: String): Kind? = when (path) {
        in fetch -> Kind.FETCH
        in pricing -> Kind.PRICING
        in filter -> Kind.FILTER
        in bids -> Kind.BIDS
        in phone -> Kind.PHONE
        else -> null
    }

    /** Every setting as a flat path (`leagues`, `gates.usePinnwire`...) with its JSON text. */
    fun flat(s: ScanSettings): Map<String, String> {
        val j = CloudConfig.json.encodeToJsonElement(ScanSettings.serializer(), s) as JsonObject
        val out = LinkedHashMap<String, String>()
        for ((k, v) in j) {
            if (k in groups && v is JsonObject) v.forEach { (k2, v2) -> out["$k.$k2"] = v2.toString() } else out[k] = v.toString()
        }
        return out
    }

    /** The settings named by [kinds], as one text: equal text, same scan. */
    fun key(s: ScanSettings, vararg kinds: Kind): String {
        val want = kinds.toSet()
        return flat(s).filter { (k, _) -> kindOf(k) in want }.entries.joinToString("\n") { "${it.key}=${it.value}" }
    }

    /** What decides what a scan reads: a change restarts it. */
    fun fetchKey(s: ScanSettings) = key(s, Kind.FETCH)

    /** What the scan's priced result depends on: a change is applied from the next scan. */
    fun scanKey(s: ScanSettings) = key(s, Kind.FETCH, Kind.PRICING)

    /** [base] (what the running scan reads and prices under) with the list filters of [from] (the phone's newest). */
    fun withFilters(base: ScanSettings, from: ScanSettings): ScanSettings =
        base.copy(minEvPercent = from.minEvPercent, maxEvPercent = from.maxEvPercent, minOdds = from.minOdds, maxOdds = from.maxOdds, feedSort = from.feedSort)

    /** Settings paths no decision has been made about (a new setting): empty while every one is classified. */
    fun unclassified(s: ScanSettings): List<String> = flat(s).keys.filter { kindOf(it) == null }
}
