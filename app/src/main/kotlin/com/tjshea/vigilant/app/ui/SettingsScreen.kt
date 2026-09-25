package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.BuildConfig
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSettings
import com.tjshea.vigilant.engine.FairSource
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: UiState,
    onUpdate: ((ScanSettings) -> ScanSettings) -> Unit,
    onAddKey: (String) -> Unit,
    onRemoveKey: (String) -> Unit,
) {
    val s = state.settings
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            // ---- Fair odds ---------------------------------------------------------------------
            SectionTitle("Fair odds source")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                FairSource.entries.forEachIndexed { i, source ->
                    SegmentedButton(
                        selected = s.fairSource == source,
                        onClick = { onUpdate { it.copy(fairSource = source) } },
                        shape = SegmentedButtonDefaults.itemShape(i, FairSource.entries.size),
                    ) { Text(source.displayName.substringBefore(" books")) }
                }
            }
            Hint(
                when (s.fairSource) {
                    FairSource.SHARP -> "Fair price from the sharp books you mark below (Pinnacle by default)."
                    FairSource.MARKET_AVERAGE -> "Each book is devigged on its own, then all of them are averaged."
                    FairSource.BLEND -> "A weighted mix: ${(s.sharpWeight * 100).roundToInt()}% sharp, ${100 - (s.sharpWeight * 100).roundToInt()}% market average."
                },
            )
            if (s.fairSource == FairSource.BLEND) {
                var weight by remember(s.sharpWeight) { mutableFloatStateOf(s.sharpWeight.toFloat()) }
                Text("Sharp weight: ${(weight * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = weight,
                    onValueChange = { weight = (it * 20).roundToInt() / 20f },
                    onValueChangeFinished = { onUpdate { it.copy(sharpWeight = weight.toDouble()) } },
                    valueRange = 0f..1f,
                    steps = 19,
                )
            }
            if (s.fairSource == FairSource.SHARP) {
                SwitchRow("Fall back to market average", "When no sharp book quotes a line, use every book instead of skipping it.", s.fallbackToAverage) { v ->
                    onUpdate { it.copy(fallbackToAverage = v) }
                }
            }

            Text("Sharp books", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FairSettings.KNOWN_SHARP_CANDIDATES.forEach { key ->
                    FilterChip(
                        selected = key in s.sharpBooks,
                        onClick = { onUpdate { it.copy(sharpBooks = if (key in it.sharpBooks) it.sharpBooks - key else it.sharpBooks + key) } },
                        label = { Text(TheOddsApiClient.KNOWN_BOOKMAKERS[key] ?: key) },
                    )
                }
            }
            Hint("A sharp book only counts if it's also in your reference books below.")

            Text("Minimum books for an average: ${s.minBooks}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            ChoiceChips((1..5).toList(), s.minBooks, { it.toString() }) { v -> onUpdate { it.copy(minBooks = v) } }

            SectionTitle("Devig method")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DevigMethod.entries.forEach { m ->
                    FilterChip(selected = s.devigMethod == m, onClick = { onUpdate { it.copy(devigMethod = m) } }, label = { Text(m.displayName) })
                }
            }
            Hint(s.devigMethod.blurb)

            // ---- Reference books -------------------------------------------------------------
            SectionTitle("Reference sportsbooks (${s.referenceBooks.size}/10)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TheOddsApiClient.KNOWN_BOOKMAKERS.forEach { (key, title) ->
                    val on = key in s.referenceBooks
                    FilterChip(
                        selected = on,
                        enabled = on || s.referenceBooks.size < TheOddsApiClient.MAX_BOOKMAKERS_ONE_REGION,
                        onClick = { onUpdate { it.copy(referenceBooks = if (on) it.referenceBooks - key else it.referenceBooks + key) } },
                        label = { Text(title) },
                    )
                }
            }
            Hint("Up to 10 books cost the same: 3 Odds API credits per sport per refresh (moneyline + spread + total).")

            // ---- Feed filters -----------------------------------------------------------------
            SectionTitle("+EV feed")
            var minEv by remember(s.minEvPercent) { mutableFloatStateOf((s.minEvPercent * 100).toFloat()) }
            Text("Minimum EV: ${String.format(Locale.US, "%.1f", minEv)}%", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = minEv,
                onValueChange = { minEv = (it * 2).roundToInt() / 2f },
                onValueChangeFinished = { onUpdate { it.copy(minEvPercent = minEv / 100.0) } },
                valueRange = 0f..10f,
                steps = 19,
            )
            Text("Markets", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarketFamily.entries.forEach { f ->
                    FilterChip(
                        selected = f in s.families,
                        onClick = { onUpdate { it.copy(families = if (f in it.families) it.families - f else it.families + f) } },
                        label = { Text(f.displayName) },
                    )
                }
            }
            Text("Days ahead: ${s.daysAhead}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            ChoiceChips(listOf(1, 2, 3, 5, 7), s.daysAhead, { "${it}d" }) { v -> onUpdate { it.copy(daysAhead = v) } }
            SwitchRow(
                "Include live games",
                "Off by default: reference odds lag in-game, and Novig charges its taker fee once a game is live.",
                s.includeLive,
            ) { v -> onUpdate { it.copy(includeLive = v) } }

            // ---- Stake sizing -----------------------------------------------------------------
            SectionTitle("Bankroll & Kelly")
            var bankroll by remember(s.bankroll) { mutableStateOf(String.format(Locale.US, "%.0f", s.bankroll)) }
            OutlinedTextField(
                value = bankroll,
                onValueChange = { t ->
                    bankroll = t.filter { it.isDigit() || it == '.' }.take(9)
                    bankroll.toDoubleOrNull()?.takeIf { it > 0 }?.let { v -> onUpdate { it.copy(bankroll = v) } }
                },
                label = { Text("Bankroll $") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            ChoiceChips(ScanSettings.KELLY_CHOICES, s.kellyMultiplier, Format::kellyLabel) { v -> onUpdate { it.copy(kellyMultiplier = v) } }
            Hint("Suggested stakes are capped at what Novig's book can actually fill at +EV.")

            // ---- Refresh ----------------------------------------------------------------------
            SectionTitle("Refresh")
            Text("Novig prices, while the app is open", style = MaterialTheme.typography.bodyMedium)
            ChoiceChips(ScanSettings.NOVIG_REFRESH_CHOICES, s.novigRefreshSeconds, { "${it}s" }) { v -> onUpdate { it.copy(novigRefreshSeconds = v) } }
            Text("Fair odds (uses Odds API credits)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            ChoiceChips(ScanSettings.REFERENCE_REFRESH_CHOICES, s.referenceRefreshMinutes, { if (it == 0) "Pull only" else "${it}m" }) { v ->
                onUpdate { it.copy(referenceRefreshMinutes = v) }
            }
            Hint(creditEstimate(s))

            // ---- Keys -------------------------------------------------------------------------
            SectionTitle("The Odds API keys")
            Hint("Free at the-odds-api.com (500 credits a month). Add several: when one runs out, the next is used automatically.")
            state.oddsApiKeys.forEach { key ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(mask(key), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { onRemoveKey(key) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove key") }
                }
            }
            var newKey by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it.trim() },
                    label = { Text("Add a key") },
                    singleLine = true,
                    // Autocorrect off: an IME "fixing" a random token silently breaks it (found 2026-09-20).
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { onAddKey(newKey); newKey = "" }, enabled = newKey.length >= 8) { Text("Add") }
            }

            SectionTitle("Novig API key")
            Hint(
                "Not needed: Vigilant reads Novig's official public prices with no key. Adding your Novig " +
                    "beta key (coming next) switches prices to Novig's real-time stream. VPNs and proxies " +
                    "are refused by Novig's signed routes.",
            )

            SectionTitle("About")
            Hint(
                "Vigilant ${BuildConfig.VERSION_NAME} · Novig prices: api.novig.com public routes · Fair odds: The Odds API. " +
                    "Novig refreshes only while the app is on screen, so it never runs in the background.",
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onPick(o) }, label = { Text(label(o)) }) }
    }
}

private fun mask(key: String): String = if (key.length <= 8) "••••" else key.take(4) + "••••••" + key.takeLast(4)

/** A rough monthly credit burn for the current settings, so the free tier's 500 isn't a surprise. */
fun creditEstimate(s: ScanSettings): String {
    val perRefresh = 3 * s.leagues.size.coerceAtLeast(1)
    return if (s.referenceRefreshMinutes == 0) {
        "Each pull-to-refresh costs about $perRefresh credits (${s.leagues.size} league(s) × 3)."
    } else {
        val perHour = perRefresh * (60.0 / s.referenceRefreshMinutes)
        "About ${perHour.roundToInt()} credits per hour the app is open. The free tier's 500 a month lasts ~${(500 / perHour).roundToInt()} open hours."
    }
}
