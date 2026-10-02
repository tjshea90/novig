package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.cno.CnoDevig
import com.tjshea.vigilant.data.cno.CnoFilters
import kotlinx.serialization.Serializable

/**
 * Every rule a preset sets at once (Tj, 2026-10-02 17:01Z: "make a preset section in the settings that sets all the settings to ideal settings for volume but
 * safe clv scanning … make it so I can make my own settings presets. The auto bet feature must abide the preset rules"): the auto-bet's criteria and stake
 * rule, the sharp veto, the alerts' edge, the CNO scanner's filters and the background scan's interval. Not in it: Tj's own money and limits (bankroll,
 * wallet, most per bet and per day), keys, and whether auto-bet or auto-scan is on. Applying one writes these into the settings the auto-bet reads, so
 * it abides by them ([com.tjshea.vigilant.data.novig.trading.AutoBet.rules]).
 */
@Serializable
data class PresetRules(
    val autoBetMinEv: Double,
    val autoBetBooks: Int,
    val autoBetTwoSided: Int,
    val autoBetAllAgree: Boolean,
    val autoBetMaxOdds: Int,
    val autoBetMinOdds: Int,
    val autoBetKinds: Set<BetKind>,
    val autoBetStake: AutoBetStake,
    val sharpAutoBet: SharpMode,
    val sharpAlerts: SharpMode,
    val alertMinEv: Double,
    val cnoFilters: CnoFilters,
    val autoScanSeconds: Int,
) {
    /** [s] with these rules in force, named [name] ([ScanSettings.presetName]). */
    fun applyTo(s: ScanSettings, name: String?): ScanSettings = s.copy(
        autoBetMinEv = autoBetMinEv, autoBetBooks = autoBetBooks, autoBetTwoSided = autoBetTwoSided, autoBetAllAgree = autoBetAllAgree,
        autoBetMaxOdds = autoBetMaxOdds, autoBetMinOdds = autoBetMinOdds, autoBetKinds = autoBetKinds, autoBetStake = autoBetStake,
        sharpAutoBet = sharpAutoBet, sharpAlerts = sharpAlerts, alertMinEv = alertMinEv, cnoFilters = cnoFilters, autoScanSeconds = autoScanSeconds,
        presetName = name,
    )

    /** These rules in one line, for Settings, the bet's record and Diagnostics. */
    fun summary(): String = buildList {
        add("edge ≥ ${pct(autoBetMinEv)}")
        add("$autoBetTwoSided+ books price both sides, $autoBetBooks+ agree" + if (autoBetAllAgree) ", every one" else "")
        add("odds " + (if (autoBetMinOdds < 0) "${autoBetMinOdds} to " else "up to ") + (if (autoBetMaxOdds > 0) "+$autoBetMaxOdds" else "any"))
        add(if (autoBetKinds.size == BetKind.entries.size) "every kind of bet" else autoBetKinds.sortedBy { it.ordinal }.joinToString(", ") { it.label.lowercase() })
        add(autoBetStake.label + " stakes")
        add("sharp check: " + sharpAutoBet.displayName.lowercase())
        add("CNO: ${cnoFilters.devig.displayName.lowercase()} devig, ${cnoFilters.minBooks}+ books, ${cnoFilters.rows} rows")
        add("alerts ≥ ${pct(alertMinEv)}")
        add("auto-scan every ${ScanSettings.intervalLabel(autoScanSeconds)}")
    }.joinToString(" · ")

    companion object {
        /** The rules [s] has now (the Auto-bet tab › Presets › Save current settings). */
        fun of(s: ScanSettings): PresetRules = PresetRules(
            s.autoBetMinEv, s.autoBetBooks, s.autoBetTwoSided, s.autoBetAllAgree, s.autoBetMaxOdds, s.autoBetMinOdds, s.autoBetKinds, s.autoBetStake,
            s.sharpAutoBet, s.sharpAlerts, s.alertMinEv, s.cnoFilters, s.autoScanSeconds,
        )

        /** "2.5%", "3%" (0.03 × 100 is 3.0000000000000004 in floating point: rounded to tenths first). */
        private fun pct(v: Double): String = Math.round(v * 1000).let { t -> if (t % 10 == 0L) "${t / 10}%" else String.format(java.util.Locale.US, "%.1f%%", t / 10.0) }
    }
}

/** One of Tj's own presets. */
@Serializable
data class SavedPreset(val name: String, val rules: PresetRules)

/** The built-in presets and the saving and applying of Tj's own (RESEARCH.md §66.5). */
object Presets {

    /**
     * Volume with the best chance of beating the close (RESEARCH.md §65-§66): a 2.5% edge (Tj's 2–3% bets beat the close by +1.3%, 1–2% bets weren't
     * distinguishable from zero; estimated edges shrink by ~1.5 points), three books pricing both sides and three agreeing without needing every one (a
     * dissent isn't a red flag unless it is the sharpest book: the veto), odds −200 to +150 (betsharpmoney's range; long shots carry the favorite–longshot
     * bias), player props, moneylines and spreads (his game totals, team totals and 1st-half totals lose to the close), ¼ Kelly (edge estimates this
     * noisy), the sharp veto for the auto-bet and the alerts, CNO's conservative devig with 4+ books and 100 rows, alerts from 2.5%, a background scan
     * every 30 seconds (CNO publishes every 13–33 s: an edge is caught before it closes).
     */
    val VOLUME = SavedPreset(
        "Volume + safe CLV",
        PresetRules(
            autoBetMinEv = 0.025, autoBetBooks = 3, autoBetTwoSided = 3, autoBetAllAgree = false, autoBetMaxOdds = 150, autoBetMinOdds = -200,
            autoBetKinds = setOf(BetKind.PROP, BetKind.MONEYLINE, BetKind.SPREAD), autoBetStake = AutoBetStake.QUARTER_KELLY,
            sharpAutoBet = SharpMode.VETO, sharpAlerts = SharpMode.VETO, alertMinEv = 0.025,
            cnoFilters = CnoFilters(devig = CnoDevig.CONSERVATIVE, maxOdds = 150, minBooks = 4, minEv = 0.01, rows = 100, completeBook = true, minSides = 2),
            autoScanSeconds = 30,
        ),
    )

    /**
     * Fewer bets, the strongest closes (RESEARCH.md §65-§66): a 4% edge (Tj's 4%+ bets beat the close 83% of the time), four books agreeing, odds −200 to
     * +130; otherwise as [VOLUME].
     */
    val STRICT = SavedPreset(
        "Strict CLV",
        VOLUME.rules.copy(autoBetMinEv = 0.04, autoBetBooks = 4, autoBetMaxOdds = 130, alertMinEv = 0.04),
    )

    val BUILT_IN: List<SavedPreset> = listOf(VOLUME, STRICT)

    /** Every preset Tj can pick: the built-in ones, then his own. */
    fun all(s: ScanSettings): List<SavedPreset> = BUILT_IN + s.presets

    fun builtIn(name: String): Boolean = BUILT_IN.any { it.name.equals(name.trim(), ignoreCase = true) }

    /** [s] with [preset] applied. */
    fun apply(s: ScanSettings, preset: SavedPreset): ScanSettings = preset.rules.applyTo(s, preset.name)

    /** Whether [s] still has [preset]'s rules exactly (Settings says "changed since" when not). */
    fun matches(s: ScanSettings, preset: SavedPreset): Boolean = PresetRules.of(s) == preset.rules

    /** The preset [s] was set from, while its rules are still in force; null when none was, or something changed since. */
    fun active(s: ScanSettings): SavedPreset? = s.presetName?.let { n -> all(s).firstOrNull { it.name == n } }?.takeIf { matches(s, it) }

    /**
     * [s] with the current rules saved as Tj's own preset [name] (replacing his own of that name), and marked as in force. Null when the name is empty
     * or a built-in's (those can't be overwritten).
     */
    fun save(s: ScanSettings, name: String): ScanSettings? {
        val n = name.trim().take(MAX_NAME)
        if (n.isEmpty() || builtIn(n)) return null
        val mine = SavedPreset(n, PresetRules.of(s))
        return s.copy(presets = s.presets.filterNot { it.name.equals(n, ignoreCase = true) } + mine, presetName = n)
    }

    /** [s] without Tj's own preset [name] (a built-in can't be deleted). */
    fun delete(s: ScanSettings, name: String): ScanSettings =
        s.copy(presets = s.presets.filterNot { it.name == name }, presetName = s.presetName.takeIf { it != name })

    const val MAX_NAME = 40
}
