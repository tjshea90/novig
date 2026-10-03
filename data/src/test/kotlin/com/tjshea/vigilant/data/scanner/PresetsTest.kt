package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.cno.CnoDevig
import com.tjshea.vigilant.data.novig.trading.AutoBet
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tj, 2026-10-02 17:01Z: "make a preset section in the settings that sets all the settings to ideal settings for volume but safe clv scanning … make it so I
 * can make my own settings presets. The auto bet feature must abide the preset rules." RESEARCH.md §66.5.
 */
class PresetsTest {

    /** Settings far from both built-ins, with Tj's own money limits set: a preset must change the rules and leave those. */
    private val mine = ScanSettings(
        autoBetMinEv = 0.035, autoBetBooks = 2, autoBetTwoSided = 1, autoBetAllAgree = true, autoBetMaxOdds = 300, autoBetMinOdds = 0,
        autoBetStake = AutoBetStake.ONE_DOLLAR, sharpAutoBet = SharpMode.OFF, sharpAlerts = SharpMode.CONFIRM, alertMinEv = 0.04, autoScanSeconds = 600,
        bankroll = 777.0, autoBetMaxStake = 6.0, apiMaxPerDay = 33.0, autoBet = true, autoScan = AutoScanMode.CNO,
    )

    @Test
    fun `the research preset sets every rule at once, from RESEARCH section 65-66`() {
        val v = Presets.apply(mine, Presets.VOLUME)
        assertEquals(0.025, v.autoBetMinEv, 0.0)
        assertEquals(3, v.autoBetBooks)
        assertEquals(3, v.autoBetTwoSided)
        assertFalse(v.autoBetAllAgree)
        assertEquals(150, v.autoBetMaxOdds)
        assertEquals(-200, v.autoBetMinOdds)
        assertEquals(setOf(BetKind.PROP, BetKind.MONEYLINE, BetKind.SPREAD), v.autoBetKinds)
        assertEquals(AutoBetStake.QUARTER_KELLY, v.autoBetStake)
        assertEquals(SharpMode.VETO, v.sharpAutoBet)
        assertEquals(SharpMode.VETO, v.sharpAlerts)
        assertEquals(0.025, v.alertMinEv, 0.0)
        assertEquals(CnoDevig.CONSERVATIVE, v.cnoFilters.devig)
        assertEquals(4, v.cnoFilters.minBooks)
        assertEquals(100, v.cnoFilters.rows)
        assertEquals(150, v.cnoFilters.maxOdds)
        assertEquals(30, v.autoScanSeconds)
        assertEquals("Volume + safe CLV", v.presetName)
        // His money, limits and what's switched on are his.
        assertEquals(777.0, v.bankroll, 0.0)
        assertEquals(6.0, v.autoBetMaxStake, 0.0)
        assertEquals(33.0, v.apiMaxPerDay, 0.0)
        assertTrue(v.autoBet)
        assertEquals(AutoScanMode.CNO, v.autoScan)
        // Every value is one the Settings chips offer.
        assertTrue(v.autoBetMinEv in ScanSettings.AUTO_BET_MIN_EV_CHOICES)
        assertTrue(v.autoBetBooks in ScanSettings.AUTO_BET_BOOKS_CHOICES)
        assertTrue(v.autoBetTwoSided in ScanSettings.AUTO_BET_TWO_SIDED_CHOICES)
        assertTrue(v.autoBetMaxOdds in ScanSettings.AUTO_BET_MAX_ODDS_CHOICES)
        assertTrue(v.autoBetMinOdds in ScanSettings.AUTO_BET_MIN_ODDS_CHOICES)
        assertTrue(v.autoScanSeconds in ScanSettings.AUTO_SCAN_SECONDS_CHOICES)
        assertTrue(v.cnoFilters.rows in ScanSettings.CNO_ROWS_CHOICES)
    }

    @Test
    fun `the strict preset asks more of each bet`() {
        val s = Presets.apply(mine, Presets.STRICT)
        assertEquals(0.04, s.autoBetMinEv, 0.0)
        assertEquals(4, s.autoBetBooks)
        assertEquals(130, s.autoBetMaxOdds)
        assertEquals(SharpMode.VETO, s.sharpAutoBet)
        assertEquals("Strict CLV", s.presetName)
        assertTrue(s.autoBetMaxOdds in ScanSettings.AUTO_BET_MAX_ODDS_CHOICES)
    }

    @Test
    fun `the auto-bet reads exactly what a preset set`() {
        val r = AutoBet.rules(Presets.apply(mine, Presets.VOLUME))
        assertEquals(0.025, r.minEv, 0.0)
        assertEquals(3, r.minBooks)
        assertEquals(3, r.twoSided)
        assertEquals(150, r.maxOdds)
        assertEquals(-200, r.minOdds)
        assertEquals(setOf(BetKind.PROP, BetKind.MONEYLINE, BetKind.SPREAD), r.kinds)
        assertEquals(AutoBetStake.QUARTER_KELLY, r.stake)
    }

    @Test
    fun `a preset stays in force until a rule changes, and only then`() {
        val v = Presets.apply(mine, Presets.VOLUME)
        assertEquals(Presets.VOLUME, Presets.active(v))
        // Money and switches aren't rules: changing them keeps it in force.
        assertEquals(Presets.VOLUME, Presets.active(v.copy(bankroll = 10.0, autoBet = false)))
        val changed = v.copy(autoBetMinEv = 0.03)
        assertNull(Presets.active(changed))
        assertEquals("Volume + safe CLV", changed.presetName)
        assertNull(Presets.active(mine))
    }

    @Test
    fun `Tj saves, applies and deletes his own, and the built-ins can't be overwritten or deleted`() {
        val saved = Presets.save(mine, "  My props  ")!!
        assertEquals(listOf("My props"), saved.presets.map { it.name })
        assertEquals(PresetRules.of(mine), saved.presets.single().rules)
        assertEquals("My props", Presets.active(saved)?.name)
        assertEquals(Presets.BUILT_IN.map { it.name } + "My props", Presets.all(saved).map { it.name })
        // Applying a built-in, then his own again, brings his rules back.
        val volume = Presets.apply(saved, Presets.VOLUME)
        val back = Presets.apply(volume, saved.presets.single())
        assertEquals(PresetRules.of(mine), PresetRules.of(back))
        assertEquals("My props", back.presetName)
        // Saving the same name replaces it.
        val again = Presets.save(volume, "my props")!!
        assertEquals(1, again.presets.size)
        assertEquals(Presets.VOLUME.rules, again.presets.single().rules)
        // A built-in's name, or no name, isn't saved.
        assertNull(Presets.save(mine, "Volume + safe CLV"))
        assertNull(Presets.save(mine, "strict clv"))
        assertNull(Presets.save(mine, "   "))
        // Deleting.
        val gone = Presets.delete(saved, "My props")
        assertTrue(gone.presets.isEmpty())
        assertNull(gone.presetName)
        assertEquals(2, Presets.all(gone).size)
    }

    @Test
    fun `presets and the new rules are saved with the settings and read back, and an older file reads with defaults`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val s = Presets.save(Presets.apply(mine, Presets.VOLUME), "Mine")!!
        assertEquals(s, json.decodeFromString(ScanSettings.serializer(), json.encodeToString(ScanSettings.serializer(), s)))
        val old = json.decodeFromString(ScanSettings.serializer(), "{}")
        assertEquals(BetKind.entries.toSet(), old.autoBetKinds)
        assertEquals(0, old.autoBetMinOdds)
        assertTrue(old.presets.isEmpty())
        assertNull(old.presetName)
        // A file from v0.45.0 or before, with settings since removed (apiMinEv, v0.46.0) or renamed: it reads, the rest kept.
        val older = json.decodeFromString(ScanSettings.serializer(), """{"apiMinEv":0.02,"sharpConfirmAutoBet":true,"bankroll":750.0,"apiMaxStake":20.0}""")
        assertEquals(750.0, older.bankroll, 0.0)
        assertEquals(20.0, older.apiMaxStake, 0.0)
    }

    @Test
    fun `a preset in one line`() {
        assertEquals(
            "edge ≥ 2.5% · 3+ books price both sides, 3+ agree · odds -200 to +150 · player props, moneylines, spreads · ¼ Kelly stakes · sharp check: veto under 1% · " +
                "CNO: conservative devig, 4+ books, 100 rows · alerts ≥ 2.5% · auto-scan every 30 sec",
            Presets.VOLUME.rules.summary(),
        )
        // Whole percents read whole: 0.03 × 100 is 3.0000000000000004 in floating point and once read "3.0%".
        val mine = Presets.VOLUME.rules.copy(autoBetMinEv = 0.03, alertMinEv = 0.07).summary()
        assertTrue(mine, mine.startsWith("edge ≥ 3% ·") && mine.contains("alerts ≥ 7% ·"))
        assertTrue(Presets.STRICT.rules.summary().startsWith("edge ≥ 4% ·"))
    }

    /** RESEARCH.md §72: what a bet keeps by the close is about the sharpest book's own edge, so the presets set the veto's bar too. */
    @Test
    fun `the presets set the sharp veto's bar - 1% for volume, 2% for strict - and a preset saved before has the default`() {
        val v = Presets.apply(mine.copy(sharpVetoMinEv = 0.0), Presets.VOLUME)
        assertEquals(0.01, v.sharpVetoMinEv, 0.0)
        assertTrue(Presets.matches(v, Presets.VOLUME))
        assertFalse("a changed bar is a change since the preset", Presets.matches(v.copy(sharpVetoMinEv = 0.005), Presets.VOLUME))
        val st = Presets.apply(mine, Presets.STRICT)
        assertEquals(0.02, st.sharpVetoMinEv, 0.0)
        assertTrue(st.sharpVetoMinEv in ScanSettings.SHARP_VETO_MIN_EV_CHOICES && v.sharpVetoMinEv in ScanSettings.SHARP_VETO_MIN_EV_CHOICES)
        // Saving keeps the bar Tj has; a preset saved by v0.55.0 (no bar in the file) reads as the default.
        assertEquals(0.015, PresetRules.of(mine.copy(sharpVetoMinEv = 0.015)).sharpVetoMinEv, 0.0)
        val json = Json { ignoreUnknownKeys = true }
        val saved = json.encodeToString(SavedPreset.serializer(), SavedPreset("old", Presets.VOLUME.rules))
            .replace(Regex(""",?"sharpVetoMinEv":[0-9.]+"""), "")
        assertFalse(saved, saved.contains("sharpVetoMinEv"))
        assertEquals(SharpVeto.DEFAULT_MIN_EV, json.decodeFromString(SavedPreset.serializer(), saved).rules.sharpVetoMinEv, 0.0)
        // The summary says the bar only with the veto on and a bar set.
        assertTrue(Presets.STRICT.rules.summary().contains("sharp check: veto under 2% ·"))
        assertTrue(Presets.VOLUME.rules.copy(sharpVetoMinEv = 0.0).summary().contains("sharp check: veto ·"))
        assertTrue(Presets.VOLUME.rules.copy(sharpAutoBet = SharpMode.OFF).summary().contains("sharp check: off ·"))
    }
}
