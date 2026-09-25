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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.keys.UsageViews
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
    keys: KeyActions = KeyActions(),
    onNovigConnect: (String, String) -> Unit = { _, _ -> },
    onNovigTest: () -> Unit = {},
    onNovigDisconnect: () -> Unit = {},
) {
    val s = state.settings
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(keys.exportTo) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(keys.importFrom) }
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
            // ---- Usage meters ------------------------------------------------------------------
            SectionTitle("API usage")
            UsageSection(state)

            // ---- Fair odds ---------------------------------------------------------------------
            SectionTitle("Fair odds source")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                FairSource.entries.forEachIndexed { i, source ->
                    SegmentedButton(
                        selected = s.fairSource == source,
                        onClick = { onUpdate { it.copy(fairSource = source) } },
                        shape = SegmentedButtonDefaults.itemShape(i, FairSource.entries.size),
                    ) { Text(source.shortName, maxLines = 1) }
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
                        label = { Text(TheOddsApiClient.bookTitle(key)) },
                    )
                }
            }
            Hint("Pinnacle comes from your pinnapi key (or The Odds API). Polymarket and Kalshi are exchanges: their prices count as sharp when the market is tight (3¢ or less).")

            Text("Minimum books for an average: ${s.minBooks}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            ChoiceChips((1..5).toList(), s.minBooks, { it.toString() }) { v -> onUpdate { it.copy(minBooks = v) } }

            SectionTitle("Devig method")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DevigMethod.entries.forEach { m ->
                    FilterChip(selected = s.devigMethod == m, onClick = { onUpdate { it.copy(devigMethod = m) } }, label = { Text(m.displayName) })
                }
            }
            Hint(s.devigMethod.blurb)

            // ---- Sources ------------------------------------------------------------------------
            SectionTitle("Fair odds sources")
            Hint("Each is called only when you scan. Polymarket and Kalshi need no key.")
            SwitchRow(
                "Pinnacle (pinnapi)",
                if (state.pinnapiKeys.isEmpty()) "Needs a free key from pinnapi.com (100 requests a day, about 1–2 per scan)."
                else "About 1–2 of a key's 100 daily requests per scan. Keys are used in order.",
                s.usePinnacle,
            ) { v -> onUpdate { it.copy(usePinnacle = v) } }
            if (s.usePinnacle) {
                KeyListEditor(ApiProvider.PINNAPI, state.pinnapiKeys, keys, "Add a pinnapi key")
                if (state.pinnapiKeys.size > 1) {
                    Hint(
                        "Heads up: pinnapi's terms forbid circumventing its rate limits, so using extra keys to get past " +
                            "100 a day could get the keys suspended. Each key is still kept inside its own limit.",
                    )
                }
            }
            SwitchRow("Polymarket", "Free. NFL, college football, NBA, WNBA, MLB, NHL, UFC.", s.usePolymarket) { v -> onUpdate { it.copy(usePolymarket = v) } }
            SwitchRow("Kalshi", "Free. NFL, college football, MLB, NBA, NHL, UFC.", s.useKalshi) { v -> onUpdate { it.copy(useKalshi = v) } }
            SwitchRow(
                "The Odds API",
                if (state.oddsApiKeys.isEmpty()) "Optional: US sportsbooks plus Pinnacle. Free key at the-odds-api.com (500 credits a month)."
                else "US sportsbooks plus Pinnacle. Keys are used in order; the next takes over when one runs out.",
                s.useOddsApi,
            ) { v -> onUpdate { it.copy(useOddsApi = v) } }
            if (s.useOddsApi) {
                KeyListEditor(ApiProvider.THE_ODDS_API, state.oddsApiKeys, keys, "Add an Odds API key")
                Text("Re-use The Odds API between scans for", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                ChoiceChips(ScanSettings.ODDS_API_REUSE_CHOICES, s.oddsApiReuseMinutes, { if (it == 0) "Every scan" else "${it}m" }) { v ->
                    onUpdate { it.copy(oddsApiReuseMinutes = v) }
                }
                Hint(creditEstimate(s))

                SectionTitle("The Odds API books (${s.referenceBooks.size}/10)")
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
                Hint("Up to 10 books cost the same: one credit per market type per league.")
            }

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
            Text("Alternate lines per game: ${s.linesPerGame}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            ChoiceChips(ScanSettings.LINES_PER_GAME_CHOICES, s.linesPerGame, { it.toString() }) { v -> onUpdate { it.copy(linesPerGame = v) } }
            Hint("Spreads and totals each. Every line is one Novig request per scan: fewer lines scan faster and stay well under Novig's rate limit.")
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

            // ---- Keys backup ------------------------------------------------------------------
            SectionTitle("Keys backup")
            Hint(
                "Keys are saved on this phone, kept through every app update, and included in Android's backup. " +
                    "Export a copy to keep them even if the app is uninstalled.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exporter.launch("vigilant-keys.json") }) { Text("Export keys") }
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import keys") }
            }

            SectionTitle("Novig API key")
            NovigKeySection(state.novig, onNovigConnect, onNovigTest, onNovigDisconnect)

            SectionTitle("About")
            Hint(
                "Vigilant ${BuildConfig.VERSION_NAME} · Novig prices: api.novig.com · Fair odds: Pinnacle (pinnapi), " +
                    "Polymarket, Kalshi, The Odds API. Nothing is fetched until you tap Scan or pull to refresh, " +
                    "and nothing runs in the background.",
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


/** What a scan costs in Odds API credits, so the free tier's 500 isn't a surprise. */
fun creditEstimate(s: ScanSettings): String {
    val perScan = s.families.size.coerceAtLeast(1) * s.leagues.size.coerceAtLeast(1)
    val base = "A scan that refreshes it costs about $perScan credit${if (perScan == 1) "" else "s"} " +
        "(${s.leagues.size} league${if (s.leagues.size == 1) "" else "s"} × ${s.families.size} market type${if (s.families.size == 1) "" else "s"})."
    return if (s.oddsApiReuseMinutes == 0) {
        "$base Every scan refreshes it."
    } else {
        val perHour = perScan * (60 / s.oddsApiReuseMinutes)
        "$base Scanning as often as you like costs at most $perHour an hour."
    }
}

/** Key list callbacks from the view model; the file pickers live in [SettingsScreen]. */
class KeyActions(
    val add: (ApiProvider, String) -> Unit = { _, _ -> },
    val remove: (ApiProvider, String) -> Unit = { _, _ -> },
    val moveUp: (ApiProvider, String) -> Unit = { _, _ -> },
    val exportTo: (android.net.Uri) -> Unit = {},
    val importFrom: (android.net.Uri) -> Unit = {},
)

/** A provider's keys in rotation order (key 1 is always tried first), with add, remove, reorder. */
@Composable
private fun KeyListEditor(provider: ApiProvider, keys: List<String>, actions: KeyActions, addLabel: String) {
    Column(Modifier.padding(start = 8.dp, bottom = 4.dp)) {
        keys.forEachIndexed { i, key ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                Text(UsageViews.mask(key), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                if (i > 0) IconButton(onClick = { actions.moveUp(provider, key) }) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Try this key earlier") }
                IconButton(onClick = { actions.remove(provider, key) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove key") }
            }
        }
        var newKey by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newKey,
                onValueChange = { newKey = it.trim() },
                label = { Text(if (keys.isEmpty()) addLabel else "Add another key") },
                singleLine = true,
                // Autocorrect off: an IME "fixing" a random token silently breaks it (found 2026-09-20).
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { actions.add(provider, newKey); newKey = "" }, enabled = newKey.length >= 8) { Text("Add") }
        }
    }
}
