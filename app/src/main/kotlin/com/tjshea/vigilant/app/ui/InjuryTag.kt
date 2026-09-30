package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.data.reference.Injury
import com.tjshea.vigilant.data.reference.InjuryLevel

/** The test tag of an injury chip. */
const val INJURY_TAG = "injuryTag"

/** Red for a player who won't play (Out, IR, IL), amber for one who may not (Doubtful, Questionable). */
@Composable
fun injuryColor(level: InjuryLevel): Color = if (level == InjuryLevel.RED) Edge.colors.negative else Edge.colors.warning

/**
 * A prop bet's injury tag (Tj, 2026-09-30, PARLAY_API.md §6.1): "OUT", "IR", "DOUBTFUL", "QUESTIONABLE" in red or amber, shown only when
 * the player isn't active. A tap shows ESPN's report (the body part, when he's expected back, the note, when it was reported).
 */
@Composable
fun InjuryTag(injury: Injury, modifier: Modifier = Modifier) {
    val tag = injury.tag ?: return
    var open by remember(injury) { mutableStateOf(false) }
    val color = injuryColor(injury.level)
    Surface(
        modifier = modifier
            .testTag(INJURY_TAG)
            .clickable(role = Role.Button, onClickLabel = "Show the injury report") { open = true }
            .semantics { contentDescription = "${injury.player}: ${injury.status}" },
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.16f),
    ) {
        Text(
            tag,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
    if (open) InjuryDialog(injury) { open = false }
}

/** ESPN's report for [injury]'s player, as ParlayAPI relays it. */
@Composable
fun InjuryDialog(injury: Injury, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("${injury.player}: ${injury.status}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOfNotNull(injury.position, injury.team ?: injury.teamAbbr).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(injury.details, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "ESPN's injury report, through ParlayAPI" + (injury.reportedAtMs?.let { " · reported ${Format.startTime(it)}" } ?: "") + ".",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
