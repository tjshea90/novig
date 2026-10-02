package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.data.scanner.Presets
import com.tjshea.vigilant.data.scanner.SavedPreset
import com.tjshea.vigilant.data.scanner.ScanSettings

/** The Presets tab's sentences, free of Compose so they're testable. */
object PresetsText {

    fun intro(): String =
        "A preset sets every rule the auto-bet, the sharp veto, the alerts and the CrazyNinjaOdds scanner use, at once: the smallest edge, the books that " +
            "must price both sides and agree, the odds range, the kinds of bet, the stake rule, CNO's filters and the background scan's interval. The " +
            "auto-bet bets only what those rules let through. Your bankroll, wallet, most per bet and per day, keys, and whether auto-bet and auto-scan " +
            "are on stay as they are. CLV (closing line value): getting a better price than the final odds just before the game; beating the close " +
            "over many bets is the best sign an edge is real."

    /** What a built-in preset is for; null for Tj's own. */
    fun why(p: SavedPreset): String? = when (p.name) {
        Presets.VOLUME.name ->
            "Recommended. Bets often while keeping the close on your side: your own 2–3% bets beat the close by +1.3% and 1–2% bets showed no clear " +
                "edge; game totals, team totals and 1st-half lines lost to the close, so they're left out; a book that disagrees only matters when it's " +
                "the sharpest one for that kind of bet (the veto)."
        Presets.STRICT.name ->
            "Fewer bets, stronger ones: your 4%+ bets beat the close 83% of the time. Four books must agree and nothing longer than +130."
        else -> null
    }

    /** Which preset is in force, or that the settings changed since one was applied. */
    fun inForce(s: ScanSettings): String {
        val active = Presets.active(s)
        return when {
            active != null -> "In force: ${active.name}."
            s.presetName != null -> "Last applied: ${s.presetName}, changed since (your own settings now)."
            else -> "No preset applied: your own settings."
        }
    }
}

/**
 * the Auto-bet tab › Presets (Tj, 2026-10-02 17:01Z: "make a preset section in the settings that sets all the settings to ideal settings for volume but safe clv
 * scanning … make it so I can make my own settings presets"): the built-in presets, Tj's own, and saving the current settings as one.
 */
@Composable
fun ColumnScope.PresetsTab(s: ScanSettings, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val subtle = MaterialTheme.colorScheme.onSurfaceVariant
    SectionTitle("Presets")
    Text(PresetsText.intro(), style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.padding(vertical = 4.dp))
    Text(PresetsText.inForce(s), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp).testTag("presetInForce"))
    val active = Presets.active(s)
    Presets.all(s).forEach { p ->
        PresetCard(
            preset = p,
            inForce = p == active,
            builtIn = Presets.builtIn(p.name),
            onApply = { onUpdate { Presets.apply(it, p) } },
            onDelete = { onUpdate { Presets.delete(it, p.name) } },
        )
    }

    SectionTitle("Save your own")
    var name by rememberSaveable { mutableStateOf("") }
    val trimmed = name.trim()
    val taken = Presets.builtIn(trimmed)
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(Presets.MAX_NAME) },
        label = { Text("Preset name") },
        isError = taken,
        supportingText = { Text(if (taken) "That's a built-in preset's name" else if (s.presets.any { it.name.equals(trimmed, true) }) "Replaces your preset of that name" else "Saves the rules in force now") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("presetName"),
    )
    Button(
        onClick = {
            onUpdate { Presets.save(it, name) ?: it }
            name = ""
        },
        enabled = trimmed.isNotEmpty() && !taken,
        modifier = Modifier.testTag("presetSave"),
    ) { Text("Save current settings as a preset") }
}

@Composable
private fun PresetCard(preset: SavedPreset, inForce: Boolean, builtIn: Boolean, onApply: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("preset:${preset.name}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(preset.name + if (builtIn) "" else " (yours)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            PresetsText.why(preset)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(preset.rules.summary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (inForce) {
                    Text("In force", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("presetActive:${preset.name}"))
                } else {
                    Button(onClick = onApply, modifier = Modifier.testTag("presetApply:${preset.name}")) { Text("Apply") }
                }
                if (!builtIn) OutlinedButton(onClick = onDelete, modifier = Modifier.testTag("presetDelete:${preset.name}")) { Text("Delete") }
            }
        }
    }
}
