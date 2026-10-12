package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The 1-100 safety / volume slider of the Auto-bet and Bids tabs (Tj, 2026-10-11): 1 is the strictest, safest, highest-edge, lowest-volume; 100 the most volume, still +EV. Moving it (on release) calls [onLevel], which sets every
 * rule it owns ([com.tjshea.vigilant.data.scanner.BidSlider], [com.tjshea.vigilant.data.scanner.BetSlider]). [level] is the saved position, [custom] true when a rule was edited since.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LevelSliderCard(
    tag: String, title: String, hint: String, level: Int, custom: Boolean, name: (Int) -> String, stops: List<Pair<Int, String>>,
    describe: List<String>, enabled: Boolean = true, onLevel: (Int) -> Unit,
) {
    val shown = if (level in 1..100) level else 55
    var drag by remember(shown) { mutableStateOf(shown.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            name(drag.toInt()) + " · " + drag.toInt() + " of 100" + if (custom && drag.toInt() == shown) " (custom)" else "",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("${tag}Label"),
        )
        Slider(
            value = drag, onValueChange = { drag = it }, valueRange = 1f..100f, steps = 98,
            enabled = enabled,
            onValueChangeFinished = { onLevel(drag.toInt().coerceIn(1, 100)) },
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stops.forEach { (pos, label) ->
                FilterChip(selected = level == pos && !custom, onClick = { onLevel(pos) }, enabled = enabled, label = { Text(label) }, modifier = Modifier.testTag("$tag-$pos"))
            }
        }
        Column(Modifier.padding(top = 4.dp).testTag("${tag}Describe"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            describe.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
