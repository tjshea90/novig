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
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings

/** Pinnacle only's sentences, free of Compose so they're testable (Tj, 2026-10-05; RESEARCH.md §88.5). */
object PinnacleOnlyText {
    const val TITLE = "Pinnacle only"

    /** 30 → "30 s", 90 → "1 min 30 s", 120 → "2 min". */
    fun ageLabel(seconds: Int): String = when {
        seconds < 60 -> "$seconds s"
        seconds % 60 == 0 -> "${seconds / 60} min"
        else -> "${seconds / 60} min ${seconds % 60} s"
    }

    /** The switch's line. */
    fun subtitle(on: Boolean): String =
        if (on) "On: Novig's price is compared with Pinnacle's devigged price for the same bet, and nothing else is read."
        else "Off. Compare Novig's price with Pinnacle's devigged price alone, on any market and sport, and read nothing but Novig and Pinnacle."

    /** What it reads and bets when it is on. */
    fun explanation(s: ScanSettings): String =
        "Vigilant's scan prices every Novig market against Pinnacle's two-sided price for the same bet, devigged four ways with the lowest kept (the most cautious), and nothing " +
            "else: CrazyNinjaOdds, Kalshi, Polymarket and The Odds API are never read. Pinnacle itself comes from PinnWire, then pinnapi; PropLine (or ParlayAPI, if PropLine " +
            "has no key) is asked, for Pinnacle's book alone, only for a league those can't answer. " +
            if (s.autoBet) "Auto-bet then bets what beats Pinnacle by your smallest edge after each scan, on a Pinnacle price read again within ${ageLabel(s.pinnacleMaxAgeSeconds)} of the order."
            else "Turn auto-bet on (the Auto-bet tab) to have it bet what beats Pinnacle for you."

    /** The age chips' note. */
    fun ageNote(s: ScanSettings): String =
        "Auto-bet never bets on a Pinnacle price older than this: a price older than a third of it is read again first (one request of the feed per sport, from PinnWire's 100 a day " +
            "per free key). Now ${ageLabel(s.pinnacleMaxAgeSeconds)}."

    /** The settings turned Pinnacle only [on] (the background scan then runs Vigilant's scan, never CNO's list). */
    fun set(s: ScanSettings, on: Boolean): ScanSettings =
        s.copy(pinnacleOnly = on, autoScan = if (on && s.autoScan == AutoScanMode.CNO) AutoScanMode.BOTH else s.autoScan)
}

/**
 * Pinnacle only's switch and its age limit, for Settings › Scanning and the Auto-bet tab (Tj, 2026-10-05: "an option in the auto bet and scanner settings for pinnacle
 * only to calculate EV"). [onUpdate] edits the settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PinnacleOnlyRows(s: ScanSettings, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = s.pinnacleOnly, role = Role.Switch, onValueChange = { on -> onUpdate { PinnacleOnlyText.set(it, on) } })
            .padding(vertical = 6.dp).testTag("pinnacleOnlySwitch"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(PinnacleOnlyText.TITLE, style = MaterialTheme.typography.bodyMedium)
            Text(PinnacleOnlyText.subtitle(s.pinnacleOnly), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = s.pinnacleOnly, onCheckedChange = null)
    }
    if (s.pinnacleOnly) {
        Text(
            PinnacleOnlyText.explanation(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp).testTag("pinnacleOnlyExplain"),
        )
        Text("Newest Pinnacle price to bet on", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("pinnacleOnlyAge")) {
            ScanSettings.PINNACLE_MAX_AGE_CHOICES.forEach { sec ->
                FilterChip(
                    selected = sec == s.pinnacleMaxAgeSeconds,
                    onClick = { onUpdate { it.copy(pinnacleMaxAgeSeconds = sec) } },
                    label = { Text(PinnacleOnlyText.ageLabel(sec)) },
                    modifier = Modifier.testTag("pinnacleAge$sec"),
                )
            }
        }
        Text(
            PinnacleOnlyText.ageNote(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}
