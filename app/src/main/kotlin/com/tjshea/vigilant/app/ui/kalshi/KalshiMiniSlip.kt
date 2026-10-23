package com.tjshea.vigilant.app.ui.kalshi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tjshea.vigilant.app.BetAmount
import com.tjshea.vigilant.app.BetSheetUi
import com.tjshea.vigilant.app.kalshi.KalshiHub
import com.tjshea.vigilant.app.kalshi.KalshiUi
import com.tjshea.vigilant.app.ui.ApiBetSheet
import com.tjshea.vigilant.data.kalshitrade.KalshiBetTargets
import com.tjshea.vigilant.data.novig.trading.PlanResult
import com.tjshea.vigilant.data.scanner.Opportunity
import kotlinx.coroutines.delay

/**
 * The mini bet slip of a Kalshi card's Bet button (Tj, 2026-10-23: "uniform design and function between the two exchanges, the only difference is the bets go to different companies"): the SAME slip as
 * Novig's ([ApiBetSheet]: amount, chips, price, what you pay, what a win pays, the edge now, the Place button), driven by Kalshi's own plan ([KalshiHub.plan]) and order ([KalshiHub.placeAsync]).
 * A bet the slip finds unbettable leaves the list at once ([KalshiHub.plan] marks it) and returns only after a newer price read lists it again.
 */
@Composable
fun KalshiMiniSlip(hub: KalshiHub, ui: KalshiUi, o: Opportunity, onDismiss: () -> Unit) {
    val target = remember(o.key) { KalshiBetTargets.of(o) }
    val scan = ui.config.scan
    val (base, note) = remember(o.key) { BetAmount.base(scan, o.suggestedStake) }
    val oneContract = o.quote?.let { Math.ceil((it.cost + it.fee) * 100 - 1e-9) / 100 } ?: 0.0
    var stake by remember(o.key) { mutableStateOf(maxOf(BetAmount.starting(base, scan.apiMaxStake, ui.balance), oneContract.coerceAtMost(scan.apiMaxStake))) }
    var chosen by remember(o.key) { mutableStateOf(false) }
    var plan by remember(o.key) { mutableStateOf<PlanResult?>(null) }
    var look by remember(o.key) { mutableStateOf(0) }
    var sent by remember(o.key) { mutableStateOf(false) }
    LaunchedEffect(o.key, stake, look) {
        plan = null
        if (stake <= 0.0) return@LaunchedEffect
        delay(300)
        plan = hub.plan(o, stake)
    }
    val ready = plan as? PlanResult.Ready
    val refusal = (plan as? PlanResult.Refused)?.reason ?: if (target == null) "This bet has no fair price." else if (!ui.config.tradingEnabled) "Orders are off: turn on \"Place orders on Kalshi\" in Kalshi › Setup to use this button." else null
    val sheet = BetSheetUi(
        title = o.selection, subtitle = "${o.marketLabel} · ${o.event.description}", target = target, stake = stake, resolving = false,
        plan = ready?.plan, refusal = refusal, placing = sent, sent = sent, balance = ui.balance, maxStake = scan.apiMaxStake,
        stakeChosen = chosen, baseStake = base, stakeNote = note, venue = "Kalshi",
    )
    ApiBetSheet(
        sheet = sheet,
        onStake = { stake = it; chosen = true },
        onConfirm = {
            val r = ready ?: return@ApiBetSheet
            if (!ui.config.tradingEnabled || sent) return@ApiBetSheet
            sent = true
            hub.placeAsync(o, stake, r.plan.limitPrice)
        },
        onRefresh = { look += 1 },
        onRepeat = {},
        onDismiss = onDismiss,
        onTypeStake = { stake = it; chosen = true },
    )
}
