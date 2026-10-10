@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tjshea.vigilant.app.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.R
import com.tjshea.vigilant.app.SampleScan
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.app.ui.FeedScreen
import com.tjshea.vigilant.app.ui.Format
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.VigilantTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * UI design candidates for Tj to choose from (TASKS.md SU, 2026-10-10: "come up with different designs and ui for this app,
 * send me screenshots of different candidates but don't change anything yet"). Nothing here touches the app: each candidate is
 * a mock of the +EV tab drawn from the SAME real sample bets ([SampleScan], priced by the real Planner/Pricing code), at the
 * Moto G size the app's own screenshots use. Skipped unless recording (`-Pscreenshots`), so the test floor and CI never run it.
 *
 * Render: put the fonts in app/build/design-fonts (see research/design/2026-10-10/README.md), then
 * `ANDROID_HOME=/opt/android-sdk ./gradlew :app:testDebugUnitTest --tests '*DesignCandidatesTest' -Pscreenshots`;
 * PNGs land in app/screenshots/design/.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-xxhdpi")
class DesignCandidatesTest {

    @get:Rule val compose = createComposeRule()

    @Before fun onlyWhenRecording() = assumeTrue(System.getProperty("roborazzi.test.record") == "true")

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { CompositionLocalProvider(LocalClock provides { SampleScan.NOW }) { content() } }
        compose.onRoot().captureRoboImage("screenshots/design/$name.png")
    }

    @Test fun a_current() = shoot("A_current") { CurrentDesign(sampleState()) }
    @Test fun b_desk() = shoot("B_desk") { DeskDesign(sampleBets()) }
    @Test fun c_expressive() = shoot("C_expressive") { ExpressiveDesign(sampleBets()) }
    @Test fun d_signal() = shoot("D_signal") { SignalDesign(sampleBets()) }
    @Test fun e_daylight() = shoot("E_daylight") { DaylightDesign(sampleBets()) }
    @Test fun f_neon() = shoot("F_neon") { NeonDesign(sampleBets()) }
}

// ---------------------------------------------------------------------------------------------------------------------------
// Shared: the sample bets, fonts, a status bar and the gesture handle (Android 16 draws edge to edge).
// ---------------------------------------------------------------------------------------------------------------------------

/** Every candidate shows the same board: the sample scan with the EV floor at 0.5%, so a list has enough rows to judge density. */
private fun sampleState(): UiState = SampleScan.state(SampleScan.settings.copy(minEvPercent = 0.005))

private data class Bet(
    val ev: Double,
    val pick: String,
    val market: String,
    val event: String,
    val league: String,
    val emoji: String,
    val start: String,
    val price: String,
    val fair: String,
    val pricePct: Double,
    val fairPct: Double,
    val stake: Double?,
    val books: List<String>,
    val ageSec: Long,
    val fillable: Double?,
)

private fun sampleBets(): List<Bet> = sampleState().feedAt(SampleScan.NOW).mapNotNull { o ->
    val q = o.quote ?: return@mapNotNull null
    val f = o.fairProbability ?: return@mapNotNull null
    Bet(
        ev = q.evPercent, pick = o.selection, market = o.marketLabel, event = o.eventName, league = o.league.displayName,
        emoji = o.league.emoji, start = Format.startTime(o.event.startsTs), price = Format.american(q.cost), fair = Format.american(f),
        pricePct = q.cost, fairPct = f, stake = o.suggestedStake, books = o.fair?.booksUsed.orEmpty(),
        ageSec = (SampleScan.NOW - (o.bookFetchedAtMs ?: SampleScan.NOW)) / 1000, fillable = o.depth?.dollarCost,
    )
}

private val LEAGUES = listOf("🏈" to "NFL", "🏈" to "NCAAF", "⚾" to "MLB", "🏀" to "WNBA", "🏒" to "NHL", "🏀" to "NBA")
private val PICKED = setOf("NFL", "MLB")
private val WINDOWS = listOf("Any", "3h", "6h", "12h", "24h", "48h")

/** Fonts read from app/build/design-fonts; a missing file falls back to the platform font, so a render never fails on one. */
private object Fonts {
    private val dir = File("build/design-fonts")
    private fun family(vararg files: Pair<String, FontWeight>, fallback: FontFamily): FontFamily {
        val fonts = files.mapNotNull { (name, w) -> File(dir, name).takeIf { it.exists() }?.let { Font(it, w) } }
        return if (fonts.isEmpty()) fallback else FontFamily(fonts)
    }
    val inter = family("Inter-Regular.otf" to FontWeight.Normal, "Inter-Medium.otf" to FontWeight.Medium, "Inter-SemiBold.otf" to FontWeight.SemiBold, "Inter-Bold.otf" to FontWeight.Bold, fallback = FontFamily.SansSerif)
    val interDisplay = family("InterDisplay-Bold.otf" to FontWeight.Bold, "InterDisplay-ExtraBold.otf" to FontWeight.ExtraBold, fallback = FontFamily.SansSerif)
    val mono = family("JetBrainsMono_400Regular.ttf" to FontWeight.Normal, "JetBrainsMono_500Medium.ttf" to FontWeight.Medium, "JetBrainsMono_700Bold.ttf" to FontWeight.Bold, "JetBrainsMono_800ExtraBold.ttf" to FontWeight.ExtraBold, fallback = FontFamily.Monospace)
    val grotesk = family("SpaceGrotesk_400Regular.ttf" to FontWeight.Normal, "SpaceGrotesk_500Medium.ttf" to FontWeight.Medium, "SpaceGrotesk_700Bold.ttf" to FontWeight.Bold, fallback = FontFamily.SansSerif)
    val manrope = family("Manrope_400Regular.ttf" to FontWeight.Normal, "Manrope_600SemiBold.ttf" to FontWeight.SemiBold, "Manrope_800ExtraBold.ttf" to FontWeight.ExtraBold, fallback = FontFamily.SansSerif)
}

@Composable
private fun StatusBar(fg: Color, family: FontFamily = Fonts.inter) {
    Row(Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("9:41", color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = family)
        Spacer(Modifier.weight(1f))
        Canvas(Modifier.size(16.dp, 11.dp)) {
            val w = size.width / 4.6f
            for (i in 0..3) {
                val h = size.height * (i + 1) / 4f
                drawRoundRect(fg, Offset(i * w * 1.2f, size.height - h), Size(w, h), CornerRadius(1.5f))
            }
        }
        Spacer(Modifier.width(6.dp))
        Text("5G", color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = family)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(22.dp, 11.dp).border(1.dp, fg.copy(alpha = 0.6f), RoundedCornerShape(3.dp)).padding(2.dp)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(0.82f).background(fg, RoundedCornerShape(1.dp)))
        }
    }
}

@Composable
private fun GestureHandle(fg: Color) {
    Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(108.dp, 4.dp).background(fg.copy(alpha = 0.55f), CircleShape))
    }
}

private fun pct(ev: Double, digits: Int = 2) = (if (ev >= 0) "+" else "−") + String.format(java.util.Locale.US, "%.${digits}f%%", kotlin.math.abs(ev) * 100)
private fun money(d: Double?) = d?.let { Format.money(it) } ?: "—"
private fun shortBook(b: String) = when (b) {
    "Pinnacle" -> "PIN"; "Polymarket" -> "POLY"; "Kalshi" -> "KAL"; "DraftKings" -> "DK"; "FanDuel" -> "FD"; "BetMGM" -> "MGM"; "Caesars" -> "CZR"
    else -> b.take(4).uppercase()
}
private fun books(b: List<String>, n: Int = 3) = b.take(n).joinToString(" · ") + if (b.size > n) " +${b.size - n}" else ""

/** The seven tabs the app shows today (scanner mode Both). */
@Composable
private fun tabIcons(): List<Pair<String, Painter>> = listOf(
    "+EV" to rememberVectorPainter(Icons.Filled.Star),
    "CNO" to painterResource(R.drawable.ic_cno),
    "Games" to rememberVectorPainter(Icons.Filled.DateRange),
    "Auto-bet" to painterResource(R.drawable.ic_autobet),
    "Bids" to painterResource(R.drawable.ic_bids),
    "Tracker" to rememberVectorPainter(Icons.AutoMirrored.Filled.List),
    "Settings" to rememberVectorPainter(Icons.Filled.Settings),
)

/** The five-tab bar several candidates propose: Games, Auto-bet and Settings move under More. */
@Composable
private fun fiveTabs(): List<Pair<String, Painter>> = listOf(
    "+EV" to rememberVectorPainter(Icons.Filled.Star),
    "CNO" to painterResource(R.drawable.ic_cno),
    "Bids" to painterResource(R.drawable.ic_bids),
    "Tracker" to rememberVectorPainter(Icons.AutoMirrored.Filled.List),
    "More" to rememberVectorPainter(Icons.Filled.Menu),
)

// ---------------------------------------------------------------------------------------------------------------------------
// A. Today's app, for comparison: the real FeedScreen and the real seven-tab bar.
// ---------------------------------------------------------------------------------------------------------------------------

@Composable
private fun CurrentDesign(state: UiState) {
    VigilantTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                StatusBar(MaterialTheme.colorScheme.onBackground)
                Box(Modifier.weight(1f)) { FeedScreen(state, {}, {}, {}, { _, _ -> }) }
                NavigationBar {
                    tabIcons().forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(
                            selected = i == 0, onClick = {},
                            icon = {
                                if (i == 0) BadgedBox(badge = { Badge { Text(state.feedAt(SampleScan.NOW).size.toString()) } }) { Icon(icon, label) } else Icon(icon, label)
                            },
                            label = { Text(label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp)) },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------
// B. Desk: a trading terminal. Pure-black OLED, monospace numbers in columns, one dense row per bet, amber for chrome.
// ---------------------------------------------------------------------------------------------------------------------------

private object Desk {
    val bg = Color(0xFF000000)
    val panel = Color(0xFF0C0C0E)
    val line = Color(0xFF1F1F23)
    val text = Color(0xFFEDEDED)
    val dim = Color(0xFF8A8A92)
    val amber = Color(0xFFFFB020)
    val green = Color(0xFF2BE07F)
}

@Composable
private fun DeskText(s: String, size: TextUnit, color: Color = Desk.text, weight: FontWeight = FontWeight.Normal, family: FontFamily = Fonts.mono, modifier: Modifier = Modifier, align: TextAlign? = null, spacing: TextUnit = 0.sp) =
    Text(s, modifier, color = color, fontSize = size, fontWeight = weight, fontFamily = family, textAlign = align, letterSpacing = spacing, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun DeskDesign(bets: List<Bet>) {
    Column(Modifier.fillMaxSize().background(Desk.bg)) {
        StatusBar(Desk.dim, Fonts.mono)
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            DeskText("VIGILANT", 16.sp, Desk.amber, FontWeight.ExtraBold, spacing = 2.sp)
            DeskText("  ▸ +EV", 16.sp, Desk.text, FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(Modifier.border(1.dp, Desk.line, RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) { DeskText("❚❚", 12.sp, Desk.dim) }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.background(Desk.amber, RoundedCornerShape(4.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) { DeskText("SCAN", 13.sp, Color.Black, FontWeight.ExtraBold, spacing = 1.sp) }
        }
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(Desk.green, CircleShape))
            Spacer(Modifier.width(6.dp))
            DeskText("SCANNED 1m · 24 PRICES · PIN 5 POLY 5 KAL 4", 10.sp, Desk.dim, spacing = 0.5.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LEAGUES.forEach { (_, l) ->
                val on = l in PICKED
                Box(
                    Modifier.border(1.dp, if (on) Desk.amber else Desk.line, RoundedCornerShape(3.dp))
                        .background(if (on) Desk.amber.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(3.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) { DeskText(l, 11.sp, if (on) Desk.amber else Desk.dim, FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            DeskText("WINDOW", 10.sp, Desk.dim, spacing = 1.sp)
            Spacer(Modifier.width(10.dp))
            WINDOWS.forEach { w ->
                val on = w == "Any"
                Box(Modifier.background(if (on) Desk.text else Color.Transparent, RoundedCornerShape(2.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    DeskText(w.uppercase(), 11.sp, if (on) Color.Black else Desk.dim, FontWeight.Bold)
                }
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.weight(1f))
            DeskText("SORT EV ▾", 10.sp, Desk.amber, FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Desk.line))
        Row(Modifier.background(Desk.panel).padding(horizontal = 14.dp, vertical = 6.dp)) {
            DeskText("EV%", 9.5.sp, Desk.dim, spacing = 1.sp, modifier = Modifier.width(60.dp))
            DeskText("SELECTION", 9.5.sp, Desk.dim, spacing = 1.sp, modifier = Modifier.weight(1f))
            DeskText("NOVIG", 9.5.sp, Desk.dim, spacing = 1.sp, modifier = Modifier.width(52.dp), align = TextAlign.End)
            DeskText("FAIR", 9.5.sp, Desk.dim, spacing = 1.sp, modifier = Modifier.width(48.dp), align = TextAlign.End)
            DeskText("¼K", 9.5.sp, Desk.dim, spacing = 1.sp, modifier = Modifier.width(54.dp), align = TextAlign.End)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Desk.line))
        Column(Modifier.weight(1f)) {
            bets.forEachIndexed { i, b ->
                Column(Modifier.background(if (i == 0) Desk.amber.copy(alpha = 0.06f) else Color.Transparent)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(60.dp)) {
                            // A bar under the number: the longer, the bigger the edge (10% fills it).
                            Box(Modifier.height(20.dp).fillMaxWidth((b.ev / 0.10).toFloat().coerceIn(0.08f, 0.92f)).background(Desk.green.copy(alpha = 0.16f), RoundedCornerShape(2.dp)))
                            DeskText(pct(b.ev), 13.sp, Desk.green, FontWeight.Bold, modifier = Modifier.align(Alignment.CenterStart).padding(start = 3.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            DeskText(b.pick, 14.sp, Desk.text, FontWeight.SemiBold, family = Fonts.inter)
                            DeskText("${b.league} ${b.start.uppercase()} │ ${b.market}", 9.5.sp, Desk.dim)
                        }
                        DeskText(b.price, 14.sp, Desk.text, FontWeight.Bold, modifier = Modifier.width(52.dp), align = TextAlign.End)
                        DeskText(b.fair, 12.sp, Desk.dim, modifier = Modifier.width(48.dp), align = TextAlign.End)
                        DeskText(money(b.stake).removeSuffix(".00"), 12.sp, Desk.green, FontWeight.Bold, modifier = Modifier.width(54.dp), align = TextAlign.End)
                    }
                    if (i == 0) {
                        // The selected row opens in place: depth, books, and the two actions, no sheet.
                        Row(Modifier.padding(start = 78.dp, end = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                DeskText("DEPTH ${money(b.fillable)} @ +EV", 10.sp, Desk.dim)
                                DeskText(b.books.joinToString(" ") { shortBook(it) } + " · ${b.ageSec}s", 10.sp, Desk.dim)
                            }
                            Box(Modifier.border(1.dp, Desk.amber, RoundedCornerShape(3.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) { DeskText("OPEN ↗", 11.sp, Desk.amber, FontWeight.Bold) }
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.background(Desk.green, RoundedCornerShape(3.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) { DeskText("BET ${money(b.stake).removeSuffix(".00")}", 11.sp, Color.Black, FontWeight.ExtraBold) }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Desk.line))
                }
            }
            Spacer(Modifier.height(10.dp))
            DeskText("  ${bets.size} BETS ≥ +0.5% · FAIR 70% SHARP BLEND, POWER DEVIG", 9.5.sp, Desk.dim, modifier = Modifier.padding(horizontal = 14.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Desk.line))
        Row(Modifier.fillMaxWidth().background(Desk.panel).height(52.dp)) {
            listOf("+EV" to "${bets.size}", "CNO" to "", "GAMES" to "", "AUTO" to "", "BIDS" to "", "TRACK" to "", "SET" to "").forEachIndexed { i, (t, n) ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (i == 0) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth(0.7f).height(2.dp).background(Desk.amber))
                    Row(verticalAlignment = Alignment.Top) {
                        DeskText(t, 10.5.sp, if (i == 0) Desk.amber else Desk.dim, FontWeight.Bold)
                        if (n.isNotEmpty()) DeskText(n, 8.sp, Desk.green, FontWeight.Bold)
                    }
                }
            }
        }
        Box(Modifier.background(Desk.panel)) { GestureHandle(Desk.dim) }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------
// C. Expressive: Android 16's Material 3 Expressive. Tonal colors from one green seed, big type, 28dp shapes, a floating toolbar.
// ---------------------------------------------------------------------------------------------------------------------------

private val ExpressiveColors = darkColorScheme(
    primary = Color(0xFF7EDBA1), onPrimary = Color(0xFF003920), primaryContainer = Color(0xFF00522F), onPrimaryContainer = Color(0xFF9AF8BB),
    secondary = Color(0xFFB4CCB9), onSecondary = Color(0xFF203527), secondaryContainer = Color(0xFF374B3D), onSecondaryContainer = Color(0xFFD0E8D5),
    tertiary = Color(0xFFA2CEDB), tertiaryContainer = Color(0xFF214D59), onTertiaryContainer = Color(0xFFBEEAF7),
    background = Color(0xFF0F1511), onBackground = Color(0xFFDEE4DD), surface = Color(0xFF0F1511), onSurface = Color(0xFFDEE4DD),
    surfaceVariant = Color(0xFF404942), onSurfaceVariant = Color(0xFFC0C9C0), surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF171D19), surfaceContainer = Color(0xFF1B211D), surfaceContainerHigh = Color(0xFF252B27),
    surfaceContainerHighest = Color(0xFF303632), outline = Color(0xFF8A938B), outlineVariant = Color(0xFF404942),
)

@Composable
private fun ExpressiveDesign(bets: List<Bet>) {
    MaterialTheme(colorScheme = ExpressiveColors) {
        val c = MaterialTheme.colorScheme
        Box(Modifier.fillMaxSize().background(c.surface)) {
            Column(Modifier.fillMaxSize()) {
                StatusBar(c.onSurface)
                Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Positive EV", color = c.onSurface, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Fonts.interDisplay, letterSpacing = (-0.5).sp)
                        Text("Scanned 1 min ago · 24 prices · 3 sources", color = c.onSurfaceVariant, fontSize = 14.sp, fontFamily = Fonts.inter)
                    }
                    Box(Modifier.size(48.dp).background(c.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_pause), "Pause", tint = c.onSecondaryContainer)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LEAGUES.take(4).forEach { (e, l) ->
                        FilterChip(
                            selected = l in PICKED, onClick = {}, shape = CircleShape,
                            label = { Text("$e $l", fontFamily = Fonts.inter, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.primaryContainer, selectedLabelColor = c.onPrimaryContainer),
                        )
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    WINDOWS.forEachIndexed { i, w ->
                        SegmentedButton(
                            selected = i == 0, onClick = {}, shape = SegmentedButtonDefaults.itemShape(i, WINDOWS.size), icon = {},
                            colors = SegmentedButtonDefaults.colors(activeContainerColor = c.secondaryContainer),
                        ) { Text(w, fontFamily = Fonts.inter, fontSize = 13.sp, maxLines = 1, softWrap = false) }
                    }
                }
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${bets.size} bets above +0.5%", color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = Fonts.inter, modifier = Modifier.weight(1f))
                    Text("Best EV", color = c.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = Fonts.inter)
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = c.primary, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    bets.take(4).forEach { b -> ExpressiveCard(b) }
                }
            }
            // The floating toolbar and its FAB: five places, Scan always one thumb away.
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(36.dp).background(Brush.verticalGradient(listOf(Color.Transparent, c.surface))))
                Row(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = c.surfaceContainerHighest, shadowElevation = 6.dp, modifier = Modifier.weight(1f)) {
                        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            fiveTabs().forEachIndexed { i, (label, icon) ->
                                if (i == 0) {
                                    Row(Modifier.background(c.primary, CircleShape).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(icon, label, tint = c.onPrimary, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("+EV · ${bets.size}", color = c.onPrimary, fontWeight = FontWeight.Bold, fontFamily = Fonts.inter, fontSize = 14.sp)
                                    }
                                } else {
                                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icon(icon, label, tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp)) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Surface(shape = RoundedCornerShape(20.dp), color = c.primaryContainer, shadowElevation = 6.dp, modifier = Modifier.size(60.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_scan), "Scan", tint = c.onPrimaryContainer, modifier = Modifier.size(26.dp)) }
                    }
                }
                Box(Modifier.background(c.surface)) { Spacer(Modifier.height(10.dp)) }
                Box(Modifier.background(c.surface)) { GestureHandle(c.onSurface) }
            }
        }
    }
}

@Composable
private fun ExpressiveCard(b: Bet) {
    val c = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = c.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).background(c.primaryContainer, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(pct(b.ev, 1).removeSuffix("%"), color = c.onPrimaryContainer, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Fonts.interDisplay)
                        Text("% EV", color = c.onPrimaryContainer.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, fontFamily = Fonts.inter)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("${b.emoji} ${b.league} · ${b.start}", color = c.onSurfaceVariant, fontSize = 12.sp, fontFamily = Fonts.inter, fontWeight = FontWeight.Medium)
                    Text(b.pick, color = c.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.inter, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${b.market} · ${b.event}", color = c.onSurfaceVariant, fontSize = 13.sp, fontFamily = Fonts.inter, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExpressivePill("Novig", b.price, c.secondaryContainer, c.onSecondaryContainer)
                ExpressivePill("Fair", b.fair, c.secondaryContainer, c.onSecondaryContainer)
                ExpressivePill("¼K", money(b.stake), c.tertiaryContainer, c.onTertiaryContainer)
                Spacer(Modifier.weight(1f))
                Row(Modifier.background(c.primary, CircleShape).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Bet", color = c.onPrimary, fontWeight = FontWeight.Bold, fontFamily = Fonts.inter, fontSize = 14.sp)
                    Spacer(Modifier.width(4.dp))
                    Icon(painterResource(R.drawable.ic_open), null, tint = c.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ExpressivePill(label: String, value: String, bg: Color, fg: Color) {
    Row(Modifier.background(bg, CircleShape).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = fg.copy(alpha = 0.75f), fontSize = 11.sp, fontFamily = Fonts.inter, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(4.dp))
        Text(value, color = fg, fontSize = 13.sp, fontFamily = Fonts.inter, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------------------------------------------------------------
// D. Signal: the numbers drawn, not just written. EV heat on each card's edge, a price-vs-fair gauge, a freshness ring.
// ---------------------------------------------------------------------------------------------------------------------------

private object Sig {
    val bg = Color(0xFF0A0F18)
    val card = Color(0xFF111827)
    val card2 = Color(0xFF182235)
    val line = Color(0xFF243250)
    val text = Color(0xFFE6EDF7)
    val dim = Color(0xFF8A98AE)
    val low = Color(0xFF4FA3FF)
    val mid = Color(0xFF2BD98B)
    val high = Color(0xFFFFC94D)
    fun tier(ev: Double) = when { ev >= 0.04 -> high; ev >= 0.02 -> mid; else -> low }
}

@Composable
private fun SignalDesign(bets: List<Bet>) {
    MaterialTheme(colorScheme = darkColorScheme(surface = Sig.card, onSurface = Sig.text, onSurfaceVariant = Sig.dim, secondaryContainer = Sig.card2, primary = Sig.mid)) {
        Column(Modifier.fillMaxSize().background(Sig.bg)) {
            StatusBar(Sig.text, Fonts.grotesk)
            Row(Modifier.padding(start = 18.dp, end = 14.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("+EV board", color = Sig.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.grotesk)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(Sig.mid, CircleShape))
                        Text("  Scanned 1m ago · NFL, MLB · any time", color = Sig.dim, fontSize = 12.sp, fontFamily = Fonts.grotesk)
                    }
                }
                Row(Modifier.border(1.5.dp, Sig.mid, CircleShape).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_scan), null, tint = Sig.mid, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan", color = Sig.mid, fontWeight = FontWeight.Bold, fontFamily = Fonts.grotesk, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SigTile("Bets", "${bets.size}", Sig.text, Modifier.weight(1f))
                SigTile("Best edge", pct(bets.maxOf { it.ev }, 1), Sig.high, Modifier.weight(1f))
                SigTile("¼ Kelly total", money(bets.sumOf { it.stake ?: 0.0 }), Sig.mid, Modifier.weight(1.2f))
            }
            Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("EV heat ", color = Sig.dim, fontSize = 11.sp, fontFamily = Fonts.grotesk)
                listOf("0.5–2%" to Sig.low, "2–4%" to Sig.mid, "4%+" to Sig.high).forEach { (t, col) ->
                    Box(Modifier.size(9.dp).background(col, RoundedCornerShape(2.dp)))
                    Text(" $t   ", color = Sig.dim, fontSize = 11.sp, fontFamily = Fonts.grotesk)
                }
                Spacer(Modifier.weight(1f))
                Text("Best EV ▾", color = Sig.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.grotesk)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                bets.take(4).forEach { SignalCard(it) }
            }
            NavigationBar(containerColor = Sig.card, tonalElevation = 0.dp) {
                fiveTabs().forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = i == 0, onClick = {},
                        icon = { if (i == 0) BadgedBox(badge = { Badge(containerColor = Sig.high, contentColor = Color.Black) { Text("${bets.size}") } }) { Icon(icon, label) } else Icon(icon, label) },
                        label = { Text(label, fontFamily = Fonts.grotesk, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Sig.mid.copy(alpha = 0.18f), selectedIconColor = Sig.mid, selectedTextColor = Sig.mid, unselectedIconColor = Sig.dim, unselectedTextColor = Sig.dim),
                    )
                }
            }
            Box(Modifier.background(Sig.card)) { GestureHandle(Sig.text) }
        }
    }
}

@Composable
private fun SigTile(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.background(Sig.card, RoundedCornerShape(16.dp)).border(1.dp, Sig.line, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, color = Sig.dim, fontSize = 11.sp, fontFamily = Fonts.grotesk)
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.grotesk, maxLines = 1)
    }
}

@Composable
private fun SignalCard(b: Bet) {
    val tier = Sig.tier(b.ev)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(18.dp)).background(Sig.card)) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(tier))
        Column(Modifier.padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${b.emoji} ${b.league} · ${b.start}", color = Sig.dim, fontSize = 12.sp, fontFamily = Fonts.grotesk, modifier = Modifier.weight(1f))
                FreshRing(b.ageSec, tier)
                Text(" ${b.ageSec}s", color = Sig.dim, fontSize = 11.sp, fontFamily = Fonts.grotesk)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.pick, color = Sig.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.grotesk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${b.market} · ${b.event}", color = Sig.dim, fontSize = 12.sp, fontFamily = Fonts.grotesk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(pct(b.ev), color = tier, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.grotesk)
                    Text("${b.price} at Novig", color = Sig.text, fontSize = 12.sp, fontFamily = Fonts.grotesk)
                }
            }
            Spacer(Modifier.height(8.dp))
            PriceGauge(b.pricePct, b.fairPct, tier)
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text("Novig ${Format.percent(b.pricePct)}", color = Sig.dim, fontSize = 11.sp, fontFamily = Fonts.grotesk)
                Spacer(Modifier.weight(1f))
                Text("Fair ${Format.percent(b.fairPct)} (${b.fair})", color = tier, fontSize = 11.sp, fontFamily = Fonts.grotesk)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(books(b.books), color = Sig.dim, fontSize = 11.sp, fontFamily = Fonts.grotesk, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.border(1.dp, Sig.line, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text("Open ↗", color = Sig.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.grotesk)
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.background(tier, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text("Bet ${money(b.stake)}", color = Color(0xFF07101C), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.grotesk)
                }
            }
        }
    }
}

/** Novig's price (hollow) and the fair chance (filled) on one track: the lit stretch between them is the edge. */
@Composable
private fun PriceGauge(price: Double, fair: Double, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val lo = minOf(price, fair) - 0.06
        val hi = maxOf(price, fair) + 0.06
        fun x(p: Double) = ((p - lo) / (hi - lo)).toFloat() * size.width
        val y = size.height / 2
        drawLine(Sig.line, Offset(0f, y), Offset(size.width, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(x(price), y), Offset(x(fair), y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Sig.bg, 6.dp.toPx(), Offset(x(price), y))
        drawCircle(Sig.text, 6.dp.toPx(), Offset(x(price), y), style = Stroke(2.dp.toPx()))
        drawCircle(color, 6.dp.toPx(), Offset(x(fair), y))
    }
}

/** How much of the 5-minute freshness limit the price has used. */
@Composable
private fun FreshRing(ageSec: Long, color: Color) {
    Canvas(Modifier.size(12.dp)) {
        drawCircle(Sig.line, style = Stroke(2.dp.toPx()))
        drawArc(color, -90f, 360f * (1f - (ageSec / 300f).coerceIn(0f, 1f)), false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

// ---------------------------------------------------------------------------------------------------------------------------
// E. Daylight: a light, fintech-style watchlist. White rows on warm grey, a dark hero card, black pills. Readable outdoors.
// ---------------------------------------------------------------------------------------------------------------------------

private object Day {
    val bg = Color(0xFFF4F4EF)
    val card = Color(0xFFFFFFFF)
    val line = Color(0xFFE7E7E1)
    val ink = Color(0xFF111413)
    val dim = Color(0xFF6A706C)
    val green = Color(0xFF00875A)
    val greenBg = Color(0xFFDDF3E8)
    val track = Color(0xFFE9E9E3)
}

@Composable
private fun DayText(s: String, size: TextUnit, color: Color = Day.ink, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier) =
    Text(s, modifier, color = color, fontSize = size, fontWeight = weight, fontFamily = Fonts.manrope, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFeatureSettings = "tnum"))

@Composable
private fun DaylightDesign(bets: List<Bet>) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Day.ink, surface = Day.card, background = Day.bg, secondaryContainer = Day.ink, onSecondaryContainer = Color.White)) {
        Column(Modifier.fillMaxSize().background(Day.bg)) {
            StatusBar(Day.ink, Fonts.manrope)
            Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    DayText("Vigilant · scanned 1 min ago", 13.sp, Day.dim, FontWeight.SemiBold)
                    DayText("Positive EV", 30.sp, Day.ink, FontWeight.ExtraBold)
                }
                Row(Modifier.background(Day.ink, CircleShape).padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_scan), null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    DayText("Scan", 15.sp, Color.White, FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.height(14.dp))
            // The hero: what the board is worth right now.
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(Day.ink, RoundedCornerShape(24.dp)).padding(18.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    DayText("Suggested now (¼ Kelly)", 12.sp, Color(0xFFA9B0AC), FontWeight.SemiBold)
                    DayText(money(bets.sumOf { it.stake ?: 0.0 }), 34.sp, Color.White, FontWeight.ExtraBold)
                    DayText("${bets.size} bets · best ${pct(bets.maxOf { it.ev })}", 12.sp, Color(0xFFA9B0AC), FontWeight.SemiBold)
                }
                // Each bet's EV as a bar, best first.
                Row(Modifier.height(56.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    val top = bets.maxOf { it.ev }
                    bets.forEach { b -> Box(Modifier.width(9.dp).fillMaxHeight((b.ev / top).toFloat().coerceIn(0.12f, 1f)).background(Color(0xFF3DDC97), RoundedCornerShape(3.dp))) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(Day.track, CircleShape).padding(4.dp)) {
                WINDOWS.forEachIndexed { i, w ->
                    Box(
                        Modifier.weight(if (i == 0) 1.2f else 1f).let { if (i == 0) it.shadow(2.dp, CircleShape).background(Day.card, CircleShape) else it }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) { DayText(if (i == 0) "Any time" else w, 13.sp, if (i == 0) Day.ink else Day.dim, if (i == 0) FontWeight.ExtraBold else FontWeight.SemiBold) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LEAGUES.take(5).forEach { (e, l) ->
                    val on = l in PICKED
                    Box(Modifier.background(if (on) Day.ink else Color.Transparent, CircleShape).border(1.dp, if (on) Day.ink else Day.line, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp)) {
                        DayText("$e $l", 13.sp, if (on) Color.White else Day.ink, FontWeight.SemiBold)
                    }
                }
            }
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                DayText("${bets.size} bets", 17.sp, Day.ink, FontWeight.ExtraBold, Modifier.weight(1f))
                DayText("Sort: Best EV ▾", 13.sp, Day.dim, FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Column(Modifier.fillMaxWidth().background(Day.card, RoundedCornerShape(24.dp)).padding(vertical = 4.dp)) {
                    bets.forEachIndexed { i, b ->
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(Day.bg, CircleShape), contentAlignment = Alignment.Center) { Text(b.emoji, fontSize = 18.sp) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                DayText(b.pick, 15.sp, Day.ink, FontWeight.ExtraBold)
                                DayText("${b.market} · ${b.start}", 12.sp, Day.dim, FontWeight.SemiBold)
                                DayText("Fair ${b.fair} · ¼K ${money(b.stake)}", 12.sp, Day.dim)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                DayText(b.price, 18.sp, Day.ink, FontWeight.ExtraBold)
                                Box(Modifier.background(Day.greenBg, RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                                    DayText(pct(b.ev), 12.sp, Day.green, FontWeight.ExtraBold)
                                }
                            }
                        }
                        if (i < bets.lastIndex) Box(Modifier.padding(start = 66.dp).fillMaxWidth().height(1.dp).background(Day.line))
                    }
                }
            }
            NavigationBar(containerColor = Day.card, tonalElevation = 0.dp) {
                fiveTabs().forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = i == 0, onClick = {}, icon = { Icon(icon, label) },
                        label = { Text(label, fontFamily = Fonts.manrope, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Day.ink, selectedIconColor = Color.White, selectedTextColor = Day.ink, unselectedIconColor = Day.dim, unselectedTextColor = Day.dim),
                    )
                }
            }
            Box(Modifier.background(Day.card)) { GestureHandle(Day.ink) }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------
// F. Neon: OLED black to violet, glass cards with a cyan-to-magenta rim, gradient EV pills. The loudest of the six.
// ---------------------------------------------------------------------------------------------------------------------------

private object Neon {
    val top = Color(0xFF05030B)
    val bottom = Color(0xFF150B2E)
    val text = Color(0xFFF4F1FF)
    val dim = Color(0xFFA59EC4)
    val cyan = Color(0xFF00E5FF)
    val magenta = Color(0xFFD24CFF)
    val mint = Color(0xFF2CFFB0)
    val glass = Color(0x0FFFFFFF)
    val rim = Brush.linearGradient(listOf(Color(0x8000E5FF), Color(0x80D24CFF)))
    val evFill = Brush.horizontalGradient(listOf(Color(0xFF2CFFB0), Color(0xFF00E5FF)))
}

@Composable
private fun NeonText(s: String, size: TextUnit, color: Color = Neon.text, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier, spacing: TextUnit = 0.sp) =
    Text(s, modifier, color = color, fontSize = size, fontWeight = weight, fontFamily = Fonts.grotesk, letterSpacing = spacing, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun BoxScope.Glow(color: Color, x: Float, y: Float, radius: Float) {
    Canvas(Modifier.matchParentSize()) {
        drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), Offset(size.width * x, size.height * y), radius * size.width), radius * size.width, Offset(size.width * x, size.height * y))
    }
}

@Composable
private fun NeonDesign(bets: List<Bet>) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Neon.top, Neon.bottom)))) {
        Glow(Color(0x405B2DFF), 0.95f, 0.05f, 0.75f)
        Glow(Color(0x2600E5FF), 0.0f, 0.6f, 0.6f)
        Column(Modifier.fillMaxSize()) {
            StatusBar(Neon.text, Fonts.grotesk)
            Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("VIGILANT", fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.grotesk, letterSpacing = 4.sp, style = TextStyle(brush = Brush.horizontalGradient(listOf(Neon.cyan, Neon.magenta))))
                    NeonText("+EV radar · scanned 1m ago", 13.sp, Neon.dim)
                }
                Box(Modifier.size(52.dp).drawBehind {
                    drawCircle(Brush.radialGradient(listOf(Color(0x6600E5FF), Color.Transparent)), size.minDimension * 0.9f)
                }.border(1.5.dp, Brush.linearGradient(listOf(Neon.cyan, Neon.magenta)), CircleShape).background(Color(0x22000000), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_scan), "Scan", tint = Neon.cyan, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LEAGUES.take(4).forEach { (e, l) ->
                    val on = l in PICKED
                    Box(
                        Modifier.clip(CircleShape).background(if (on) Neon.evFill else Brush.linearGradient(listOf(Neon.glass, Neon.glass)))
                            .border(1.dp, if (on) Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)) else Neon.rim, CircleShape).padding(horizontal = 13.dp, vertical = 7.dp),
                    ) { NeonText("$e $l", 13.sp, if (on) Color(0xFF051018) else Neon.text, FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                WINDOWS.forEach { w ->
                    val on = w == "Any"
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        NeonText(if (on) "Any time" else w, 13.sp, if (on) Neon.cyan else Neon.dim, if (on) FontWeight.Bold else FontWeight.Medium)
                        Box(Modifier.padding(top = 3.dp).size(if (on) 18.dp else 0.dp, 2.dp).background(Neon.cyan, CircleShape))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                bets.take(4).forEach { NeonCard(it) }
            }
        }
        // A floating glass bar.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0xCC120A24))
                    .border(1.dp, Neon.rim, RoundedCornerShape(28.dp)).padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                fiveTabs().forEachIndexed { i, (label, icon) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, label, tint = if (i == 0) Neon.cyan else Neon.dim, modifier = Modifier.size(24.dp))
                        NeonText(label, 11.sp, if (i == 0) Neon.cyan else Neon.dim, FontWeight.Medium)
                        Box(Modifier.padding(top = 2.dp).size(if (i == 0) 5.dp else 0.dp).background(Neon.cyan, CircleShape))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            GestureHandle(Neon.text)
        }
    }
}

@Composable
private fun NeonCard(b: Bet) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Neon.glass).border(1.dp, Neon.rim, RoundedCornerShape(22.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(CircleShape).background(Neon.evFill).padding(horizontal = 10.dp, vertical = 4.dp)) {
                NeonText(pct(b.ev), 13.sp, Color(0xFF051018), FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            NeonText("${b.emoji} ${b.league} · ${b.start}", 12.sp, Neon.dim, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Close, null, tint = Neon.dim, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(8.dp))
        NeonText(b.pick, 18.sp, Neon.text, FontWeight.Bold)
        NeonText("${b.market} · ${b.event}", 12.sp, Neon.dim)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            NeonStat("NOVIG", b.price, Neon.text)
            Spacer(Modifier.width(18.dp))
            NeonStat("FAIR", b.fair, Neon.dim)
            Spacer(Modifier.width(18.dp))
            NeonStat("¼ KELLY", money(b.stake), Neon.mint)
            Spacer(Modifier.weight(1f))
            Box(Modifier.border(1.dp, Brush.linearGradient(listOf(Neon.cyan, Neon.magenta)), CircleShape).padding(horizontal = 14.dp, vertical = 8.dp)) {
                NeonText("Bet ↗", 13.sp, Neon.text, FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NeonStat(label: String, value: String, color: Color) {
    Column {
        NeonText(label, 9.5.sp, Neon.cyan.copy(alpha = 0.8f), FontWeight.Bold, spacing = 1.sp)
        NeonText(value, 18.sp, color, FontWeight.Bold)
    }
}
