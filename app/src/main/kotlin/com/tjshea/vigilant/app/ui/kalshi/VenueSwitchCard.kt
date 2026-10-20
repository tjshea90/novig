package com.tjshea.vigilant.app.ui.kalshi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.VigilantApp
import com.tjshea.vigilant.app.kalshi.KalshiHub

/** The Kalshi side of the app for any composable that needs it (null in previews and tests that do not run the app). */
@Composable
fun rememberKalshiHub(): KalshiHub? {
    val ctx = LocalContext.current.applicationContext
    return androidx.compose.runtime.remember(ctx) { (ctx as? VigilantApp)?.container?.kalshiHub }
}

/**
 * Which venues the app runs (KV, Tj 2026-10-20): Novig, Kalshi, or both. A venue that is off has no tab and makes no calls. Turning Novig off pauses the phone's Novig scanning
 * and auto-bet (as the Pause button does) and turning it back on resumes only what this switch paused; the GitHub engine is controlled from the Engine tab.
 */
@Composable
fun VenueSwitchCard(modifier: Modifier = Modifier) {
    val hub = rememberKalshiHub() ?: return
    val ui by hub.ui.collectAsState()
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("venueSwitchCard"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Where to bet", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "Run Novig, Kalshi or both. A venue that is off has no tab and makes no requests. Kalshi's arbitrage (sure profit between the two) needs both on.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Use Novig")
                Switch(checked = ui.venues.novig, onCheckedChange = { hub.setVenues(novig = it) }, modifier = Modifier.testTag("venueNovig"))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Use Kalshi")
                Switch(checked = ui.venues.kalshi, onCheckedChange = { hub.setVenues(kalshi = it) }, modifier = Modifier.testTag("venueKalshi"))
            }
        }
    }
}
