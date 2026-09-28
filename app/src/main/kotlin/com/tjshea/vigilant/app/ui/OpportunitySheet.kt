package com.tjshea.vigilant.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.AppBook
import com.tjshea.vigilant.data.scanner.CrossCheck
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.Odds
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpportunitySheet(
    o: Opportunity,
    settings: ScanSettings,
    onDismiss: () -> Unit,
    onTrack: (Double) -> Unit,
    onRecheck: (() -> Unit)? = null,
    rechecking: Boolean = false,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        OpportunityDetail(o, settings, onRecheck = onRecheck, rechecking = rechecking, onTrack = onTrack)
    }
}

/**
 * Opens Novig (its app when installed) and, with the mini window on, floats Vigilant over it.
 * Provided by the activity; null (tests, previews) opens novig.com.
 */
/** Opens Novig's app on a link (a bet's `novigapp://events/<outcome>` bet slip), or Novig itself for null. */
val LocalOpenNovig = androidx.compose.runtime.staticCompositionLocalOf<((String?) -> Unit)?> { null }

/**
 * A maker order waits to be taken, and the takers most eager to fill it are the ones who know the
 * line is moving against it. So a suggested bid asks for at least this much edge.
 */
const val MAKER_MIN_EV = 0.02

@Composable
fun OpportunityDetail(
    o: Opportunity,
    settings: ScanSettings,
    onRecheck: (() -> Unit)? = null,
    rechecking: Boolean = false,
    onTrack: (Double) -> Unit,
) {
    val context = LocalContext.current
    val now = rememberNow(15_000)
    fun open(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
    val q = o.quote
    var stakeText by remember(o.key) { mutableStateOf(o.suggestedStake?.let { String.format(Locale.US, "%.2f", it) } ?: "") }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(o.selection, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${o.marketLabel} · ${o.league.displayName} · ${Format.startTime(o.event.startsTs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(o.eventName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            q?.takeIf { !o.fairIsOld(now) }?.let { EvBadge(it.evPercent, large = true) }
        }
        // Left open past a few minutes: the other books' prices behind this EV aren't current (RESEARCH.md §24).
        if (o.fairIsOld(now)) {
            Banner(
                "The other books' prices behind this are over ${com.tjshea.vigilant.data.scanner.Freshness.maxAgeMs(o.event.startsTs, now) / 60_000} minutes " +
                    "old, so its EV isn't shown: scan again before betting.",
                color = Edge.colors.warning,
            )
        }

        SectionTitle("Price")
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LabeledValue(AppBook.name, q?.let { "${Format.american(it.cost)} · ${Format.percent(it.cost)}" } ?: "no offers")
            LabeledValue("Fair", o.fairProbability?.let { "${Format.american(it)} · ${Format.percent(it)}" } ?: "—")
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (AppBook.exchange) {
                LabeledValue("Taker fee", q?.let { if (it.fee == 0.0) "none (pregame)" else Format.percent(it.fee, 2) } ?: "—")
                LabeledValue("Full Kelly", q?.let { Format.percent(it.kellyFraction) } ?: "—")
                LabeledValue("Novig width", o.novigWidth?.let { Format.percent(it) } ?: "—")
            } else {
                // A sportsbook's price is all-in; its hold is the vig on both sides of this line.
                LabeledValue("Full Kelly", q?.let { Format.percent(it.kellyFraction) } ?: "—")
                LabeledValue("${AppBook.name} hold", o.novigWidth?.let { Format.percent(it) } ?: "—")
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${AppBook.name} price read ${Format.age(o.bookFetchedAtMs, now)}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = if (o.priceIsOld(now)) Edge.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onRecheck != null) {
                TextButton(onClick = onRecheck, enabled = !rechecking) { Text(if (rechecking) "Rechecking…" else "Recheck price") }
            }
        }

        // An exchange's order book and resting bids; a sportsbook has neither.
        if (AppBook.exchange && o.ladder.isNotEmpty()) {
            SectionTitle("Novig order book (you take)")
            o.ladder.take(6).forEach { level ->
                val ev = o.fairProbability?.let { EvMath.quote(it, level.price, o.market.fee!!, o.isLive).evPercent }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Format.american(level.price), Modifier.width(72.dp), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    Text(Format.percent(level.price), Modifier.width(64.dp), style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${Format.contractsAsPayout(level.contracts)} payout",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ev?.let { Text(Format.evPercent(it), color = if (it >= 0) Edge.colors.positive else Edge.colors.negative, style = MaterialTheme.typography.labelMedium) }
                }
            }
            o.depth?.takeIf { it.contracts > 0 }?.let { d ->
                Text(
                    "${Format.money(d.dollarCost)} can be bet at +EV (expected profit ${Format.money(d.dollarEv)}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Edge.colors.positive,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        o.makerBid(maxOf(settings.minEvPercent, MAKER_MIN_EV))?.takeIf { AppBook.exchange }?.let { bid ->
            SectionTitle("Or post a bid (maker)")
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LabeledValue("Bid up to", "${Format.american(bid.price)} · ${Format.percent(bid.price)}")
                LabeledValue("EV if filled", Format.evPercent(bid.evPercent), valueColor = Edge.colors.positive)
                LabeledValue("Best bid now", o.bestBid?.let { "${Format.american(it)} · ${Format.percent(it)}" } ?: "none")
            }
            Text(
                "Makers pay no fee on Novig. A resting bid fills only when someone takes it, often after the line has moved " +
                    "against it, so this asks for at least ${Format.percent(maxOf(settings.minEvPercent, MAKER_MIN_EV))} EV at today's fair price.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        o.fair?.let { fair ->
            SectionTitle("Fair odds: ${fair.sourceUsed.displayName} · ${fair.method.displayName}")
            if (fair.sourceUsed != fair.requestedSource) {
                Text(
                    "No sharp book quoted this line, so the market average is used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Edge.colors.warning,
                )
            }
            val idx = o.referenceIndex
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Book", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Odds", Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No-vig", Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Hold", Modifier.width(52.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            fair.perBook.sortedByDescending { it.isSharp }.forEach { b ->
                val used = b.book.bookTitle in fair.booksUsed
                val color = if (used) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        b.book.bookTitle + if (b.isSharp) "  ★" else "",
                        Modifier.weight(1f),
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        idx?.let { Odds.formatAmerican(Odds.decimalToAmerican(b.book.decimalOdds[it])) } ?: "—",
                        Modifier.width(64.dp),
                        fontFamily = FontFamily.Monospace,
                        color = color,
                    )
                    Text(idx?.let { Format.percent(b.fairProbabilities[it]) } ?: "—", Modifier.width(64.dp), color = color, style = MaterialTheme.typography.bodySmall)
                    Text(Format.percent(b.hold), Modifier.width(52.dp), color = color, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                "★ sharp book · greyed rows weren't used for this line's fair price",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (q != null && o.fairProbability != null) {
            SectionTitle("Track this bet")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = stakeText,
                    onValueChange = { stakeText = it.filter { c -> c.isDigit() || c == '.' }.take(10) },
                    label = { Text("Stake $") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { stakeText.toDoubleOrNull()?.takeIf { it > 0 }?.let(onTrack) }, enabled = (stakeText.toDoubleOrNull() ?: 0.0) > 0) {
                    Text("Track")
                }
            }
            o.suggestedStake?.let {
                Text(
                    "Suggested: ${Format.money(it)} (${Format.kellyLabel(settings.kellyMultiplier)} of a ${Format.money(settings.bankroll)} bankroll" +
                        (if (AppBook.exchange) ", capped at +EV liquidity)." else ")."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // A second opinion from an independent calculator, prefilled with the sharpest book's line.
        CrossCheck.devigger(o)?.let { link ->
            OutlinedButton(onClick = { open(link.url) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("Double-check on CrazyNinjaOdds (${link.bookTitle})", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // This exact bet in Novig's bet slip, the way the widget and CNO tab open theirs (Tj, 2026-09-27:
        // "the button only opens the app, not the exact bet slip like the cno scanner does").
        val openNovig = LocalOpenNovig.current
        OutlinedButton(
            onClick = { betSlipLinkWithStake(o, settings).let { link -> openNovig?.invoke(link) ?: open(if (AppBook.isNovig) "https://novig.com" else link) } },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
        ) { Text("Open this bet in ${AppBook.name}" + slipStakeSuffix(o, settings)) }
        if (!AppBook.isNovig && settings.bookState.isBlank()) {
            Text(
                "Pick your ${AppBook.name} state in Settings so this opens the exact bet slip (${AppBook.name}'s sites are per state).",
                style = MaterialTheme.typography.labelSmall,
                color = Edge.colors.warning,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

/** [o]'s bet slip in Novig's app: Novig's own link to that outcome (RESEARCH.md §20). */
fun betSlipLink(o: Opportunity): String = "novigapp://events/${o.outcome.outcomeId}"

/** [o]'s bet slip in the app's own book: Novig's link above, or BetMGM's ([AppBook.betLink]) for a player in [state]. */
fun betSlipLink(o: Opportunity, state: String): String =
    AppBook.betLink(o.outcome.outcomeId, o.outcome.bookRef, state) ?: AppBook.home

/** [o]'s bet slip, opened with the stake Settings asks for ($1, Kelly, or a set amount; Tj, 2026-09-28). */
fun betSlipLinkWithStake(o: Opportunity, settings: ScanSettings): String =
    betSlipLink(o, settings.bookState).let { link -> NovigLinks.withStake(link, settings.slipStakeFor(o.suggestedStake)) ?: link }

/** " · $12.27" when [o]'s bet slip opens with a stake, else nothing. */
fun slipStakeSuffix(o: Opportunity, settings: ScanSettings): String =
    if (!AppBook.isNovig) "" else settings.slipStakeFor(o.suggestedStake)?.let { " · $" + NovigLinks.amountText(it) }.orEmpty()

/**
 * One tap to [o]'s bet slip in Novig, the way the widget opens its bets (Tj, 2026-09-28: "make easy one press buttons next
 * to each bet to Open the bet in novig, just as the widget does"), with the stake Settings asks for.
 */
@Composable
fun OpenBetButton(o: Opportunity, settings: ScanSettings, modifier: Modifier = Modifier) {
    val openNovig = LocalOpenNovig.current
    val context = LocalContext.current
    androidx.compose.material3.FilledTonalButton(
        onClick = {
            val link = betSlipLinkWithStake(o, settings)
            openNovig?.invoke(link) ?: runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(if (AppBook.isNovig) "https://novig.com" else link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        modifier = modifier.testTag("openBet"),
    ) { Text("Open in ${AppBook.name}" + slipStakeSuffix(o, settings), maxLines = 1) }
}
