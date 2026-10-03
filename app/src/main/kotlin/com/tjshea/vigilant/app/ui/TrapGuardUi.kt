package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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

    const val INTRO =
        "Skips the \"gifts\" the market later proves wrong. A price that beats the books often means someone on Novig knows something the books " +
            "haven't caught up to; these two rules come from your own closes and Novig's own trades."

    /** What the early rule does at [hours] (the setting the three places share). */
    fun earlyNote(hours: Int): String =
        if (hours <= 0) {
            "Off: the auto-bet, alerts and bids take games however far off. Your own bets placed 6 h or more before the start lost to the close " +
                "(−0.6%, 46% beat it); those under 6 h beat it (+2.2%, 78%)."
        } else {
            "The auto-bet, alerts and bids leave alone any game starting more than $hours h from now (the lists still show it). Your own bets placed 6 h " +
                "or more before the start lost to the close (−0.6%, 46% beat it; −9.8% returned); those under 6 h beat it (+2.2%, 78%; +8.3% returned)."
        }

    /**
     * The bet sheet's warning for a game further off than the guard's [hours] at [now] (RESEARCH.md §71), or null. The lists still show such a bet; the
     * sheet says why the auto-bet, the alerts and the bids leave it alone, so a bet by hand is a choice made knowing it.
     */
    fun sheetNote(startsAtMs: Long?, now: Long, hours: Int): String? {
        if (!TrapGuard.isEarly(startsAtMs, now, hours)) return null
        val left = ((startsAtMs!! - now) / 3_600_000L).coerceAtLeast(1)
        return "Trap guard: this game starts in about $left h, more than $hours h off. Bets placed this early lost to the close in your own record " +
            "(the books' lines aren't settled yet, and a Novig price that beats them is often the better-informed one), so auto-bet, alerts and bids leave it alone."
    }

    /** What the move rule does. */
    fun moveNote(on: Boolean): String =
        if (on) {
            "Before the auto-bet places a moneyline, spread or game total, Novig's own trades in it are read (one free request). A price that just fell " +
                "2¢+ under where it traded this hour while \$100+ was bought on the other side isn't bet: on Novig such bets lost 2.3¢ to the close. Props " +
                "aren't checked: the same move on a prop isn't a trap."
        } else {
            "Off: game lines are bet without reading Novig's recent trades."
        }
}

/**
 * The trap guard's two switches. [showMove]: the move rule is the auto-bet's only (the Auto-bet tab); the alerts and the bids show the early rule.
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
    Text(TrapGuardText.earlyNote(s.trapEarlyHours), style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.padding(top = 4.dp).testTag("$tag-trapEarlyNote"))
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
