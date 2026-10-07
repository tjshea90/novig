package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.TrapGuard

/**
 * The trap guard's words (Tj, 2026-10-03: "find these trap bets and avoid them"; RESEARCH.md §71), free of Compose so they're testable. One setting
 * shared by the auto-bet, the alerts and the bids: each place it's shown says so.
 */
object TrapGuardText {

    /** "Off", "3 h", "6 h". */
    fun hoursLabel(hours: Int): String = if (hours <= 0) "Off" else "$hours h"

    /** Shown under the hours field when what was typed is not a window the guard takes. */
    const val HOURS_ERROR = "A whole number of hours from 1 to ${TrapGuard.MAX_EARLY_HOURS}; pick Off for no limit"

    const val INTRO =
        "Skips the \"gifts\" the market later proves wrong. A price that beats the books often means someone on Novig knows something the books " +
            "haven't caught up to; these two rules come from your own closes and Novig's own trades."

    /** What the early rule does at [hours] (the setting the three places share). */
    fun earlyNote(hours: Int): String =
        if (hours <= 0) {
            "Off: the auto-bet, alerts and bids take games however far off. Your own bets placed 6 h or more before the start lost to the close " +
                "to Oct 3 (−0.6%, 46% beat it); those under 6 h beat it (+2.2%, 78%)."
        } else {
            "The auto-bet, alerts and bids leave alone any game starting more than $hours h from now (the lists still show it). Your own bets placed 6 h " +
                "or more before the start lost to the close to Oct 3 (−0.6%, 46% beat it; −9.8% returned); those under 6 h beat it (+2.2%, 78%; +8.3% returned)."
        }

    /**
     * The bet sheet's warning for a game further off than the guard's [hours] at [now] (RESEARCH.md §71), or null. The lists still show such a bet; the
     * sheet says why the auto-bet, the alerts and the bids leave it alone, so a bet by hand is a choice made knowing it.
     */
    fun sheetNote(startsAtMs: Long?, now: Long, hours: Int): String? {
        if (!TrapGuard.isEarly(startsAtMs, now, hours)) return null
        val left = ((startsAtMs!! - now) / 3_600_000L).coerceAtLeast(1)
        return "Trap guard: this game starts in about $left h, more than $hours h off, so auto-bet, alerts and bids leave it alone. Your bets placed 6 h or " +
            "more before a start lost to the close (the books' lines aren't settled that early, and a Novig price that beats them is often the better-informed one)."
    }

    /** What the first-listed rule does (Tj, 2026-10-07: "remember a bet's first-listed time"), at the early rule's [hours]. */
    fun firstListedNote(on: Boolean, hours: Int): String = when {
        hours <= 0 -> "Needs the hours above (they are off)."
        on -> "A bet first seen more than $hours h before its game started is left alone by the auto-bet and the alerts even once the game is inside the window (the lists still show it). " +
            "In the first three days of data such bets closed at +0.07%, against +1.29% for bets first listed inside the window. Bets listed before this was switched on count from when the app first saw them."
        else -> "Off: a bet is judged only by how far off its game is now."
    }

    /** What the move rule does. */
    fun moveNote(on: Boolean): String =
        if (on) {
            "Before the auto-bet places a moneyline, spread or game total (or a game-line bid goes up, when the Bids tab takes game lines), Novig's own " +
                "trades in it are read (one free request). A price that just fell 2¢+ under where it traded this hour while \$100+ was bought on the other " +
                "side isn't bet or bid on: on Novig such bets lost 2.3¢ to the close, and such bids kept +1.2% a fill instead of +6.5%. Props aren't " +
                "checked: the same move on a prop isn't a trap."
        } else {
            "Off: game lines are bet and bid on without reading Novig's recent trades."
        }
}

/**
 * The trap guard's two switches. [showMove]: the move rule is for placing game lines (the Auto-bet tab; the Bids tab shows the same switch once it takes
 * game lines); the alerts show the early rule only.
 * [tag] prefixes the test tags so each place's controls can be told apart.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrapGuardSection(s: ScanSettings, showMove: Boolean, tag: String, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val subtle = MaterialTheme.colorScheme.onSurfaceVariant
    SectionTitle("Trap guard")
    Text(TrapGuardText.INTRO, style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.padding(vertical = 4.dp))
    Text("Only games starting within", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("$tag-trapEarly")) {
        TrapGuard.EARLY_CHOICES.forEach { h ->
            FilterChip(selected = h == s.trapEarlyHours, onClick = { onUpdate { it.copy(trapEarlyHours = h) } }, label = { Text(TrapGuardText.hoursLabel(h)) })
        }
    }
    TrapEarlyHoursField(s.trapEarlyHours, tag) { v -> onUpdate { it.copy(trapEarlyHours = v) } }
    Text(TrapGuardText.earlyNote(s.trapEarlyHours), style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.padding(top = 4.dp).testTag("$tag-trapEarlyNote"))
    Row(
        Modifier.fillMaxWidth().toggleable(value = s.trapFirstListed, role = Role.Switch, onValueChange = { v -> onUpdate { it.copy(trapFirstListed = v) } })
            .padding(top = 8.dp, bottom = 2.dp).testTag("$tag-trapFirstListed"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Skip bets listed too early", style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = s.trapFirstListed, onCheckedChange = null)
    }
    Text(TrapGuardText.firstListedNote(s.trapFirstListed, s.trapEarlyHours), style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.testTag("$tag-trapFirstListedNote"))
    if (showMove) {
        Row(
            Modifier.fillMaxWidth().toggleable(value = s.trapNovigMove, role = Role.Switch, onValueChange = { v -> onUpdate { it.copy(trapNovigMove = v) } })
                .padding(top = 8.dp, bottom = 2.dp).testTag("$tag-trapMove"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Skip game lines Novig just moved", style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = s.trapNovigMove, onCheckedChange = null)
        }
        Text(TrapGuardText.moveNote(s.trapNovigMove), style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.testTag("$tag-trapMoveNote"))
    }
}

/**
 * The trap guard's window typed in as hours (Tj, 2026-10-06: "or an amount in hours I type in"), under the chips. The field always shows the saved window ([hours]; blank = Off), so a chip
 * and the field never disagree; a typed value saves as soon as it is a whole number from 1 to [TrapGuard.MAX_EARLY_HOURS], anything else saves nothing and says why. [tag] prefixes the
 * test tag like [TrapGuardSection]'s other controls.
 */
@Composable
fun TrapEarlyHoursField(hours: Int, tag: String, onSet: (Int) -> Unit) {
    var text by remember(hours) { mutableStateOf(if (hours > 0) "$hours" else "") }
    val bad = text.isNotEmpty() && TrapGuard.parseHours(text) == null
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter { it.isDigit() }.take(3)
            TrapGuard.parseHours(text)?.let(onSet)
        },
        label = { Text("Or type your own (hours)") },
        isError = bad,
        supportingText = { if (bad) Text(TrapGuardText.HOURS_ERROR) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag("$tag-trapEarlyField"),
    )
}
