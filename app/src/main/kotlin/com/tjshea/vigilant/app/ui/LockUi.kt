package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.LockView
import com.tjshea.vigilant.data.novig.trading.LockResult
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.Odds

/** A lock's words (RESEARCH.md §67), free of Compose so they're testable. */
object LockText {

    private fun american(p: Double) = Odds.formatAmerican(Odds.probabilityToAmerican(p.coerceIn(0.001, 0.999)))

    /** What letting it ride pays: if the side held more of wins, and if it loses. */
    fun rideRange(v: LockView): Pair<Double, Double> {
        val counts = v.holding.held.values.map { it.contracts }
        val most = counts.maxOrNull() ?: 0L
        val least = if (counts.size >= 2) counts.min() else 0L
        return (most * EvMath.CONTRACT_PAYOUT_DOLLARS - v.holding.spent) to (least * EvMath.CONTRACT_PAYOUT_DOLLARS - v.holding.spent)
    }

    /** The card's main line: locked, on offer, or why not. */
    fun headline(v: LockView): String = when {
        v.locked != null -> "Locked: ${Format.signedMoney(v.locked)} whichever side wins (both sides held equally)."
        v.result is LockResult.Ready -> {
            val p = (v.result as LockResult.Ready).plan
            "Lock in at least ${Format.signedMoney(p.guaranteed)} whichever side wins: buy ${"%,d".format(p.contracts)} contracts of ${v.otherName} at " +
                "${american(p.limitPrice)} or better" + (if (p.expected > p.guaranteed + 0.005) " (${Format.signedMoney(p.expected)} if it fills as Novig's book reads now)" else "") + "."
        }
        else -> "No lock yet: " + (v.result as LockResult.None).reason
    }

    /** Lock or let it ride, in numbers. */
    fun ride(v: LockView): String {
        val (win, lose) = rideRange(v)
        return "Letting it ride: ${Format.signedMoney(win)} if ${v.heldName} wins, ${Format.signedMoney(lose)} if not" +
            (v.holdValue?.let { ", worth about ${Format.signedMoney(it)} at Novig's middle price now" } ?: "") + "."
    }

    /** What locking means, in plain words. */
    fun explain(v: LockView): String =
        "A lock buys the other side of this same Novig market so both sides pay the same: you're paid back more than everything you spent here whichever " +
            "side wins. It's one fill-or-kill order: it fills completely at that price or better, or nothing is bought. It cashes in the move your bet already " +
            "got, for a little less than letting it ride is worth on average: certainty instead of risk." +
            (if (v.live) " The game is under way, so Novig's in-game fee is included." else "") +
            (if (v.pushable && v.result is LockResult.Ready) " If this line pushes, both bets are refunded (break-even)." else "")

    fun confirm(v: LockView): String {
        val p = (v.result as LockResult.Ready).plan
        return "Buy ${"%,d".format(p.contracts)} contracts of ${v.otherName} at ${american(p.limitPrice)} or better (up to ${Format.money(p.worstCost)})? " +
            "Whichever side wins, you get back at least ${Format.signedMoney(p.guaranteed)} more than the ${Format.money(p.spent + p.worstCost)} spent in this market. " +
            "If the price moves first, nothing is bought. Novig's positions are checked against the Tracker before the order goes."
    }

    /** The Tracker row's tag: on offer or locked; null for nothing to say. */
    fun badge(v: LockView?): String? = when {
        v == null -> null
        v.locked != null -> "🔒 Locked ${Format.signedMoney(v.locked)}"
        v.result is LockResult.Ready -> "🔓 Lock ${Format.signedMoney((v.result as LockResult.Ready).plan.guaranteed)}"
        else -> null
    }
}

/** The bet sheet's lock card, for a bet placed through the API: the lock on offer (with a confirm), the profit locked, or why there's none. */
@Composable
fun LockCard(v: LockView, locking: Boolean, onLock: (marketId: String, guaranteed: Double) -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("lockCard")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Lock in a profit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(LockText.headline(v), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("lockHeadline"))
            if (v.locked == null) Text(LockText.ride(v), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(LockText.explain(v), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val ready = v.result as? LockResult.Ready
            if (ready != null && v.locked == null) {
                Button(onClick = { confirming = true }, enabled = !locking, modifier = Modifier.testTag("lockButton")) {
                    Text(if (locking) "Locking…" else "Lock in ${Format.signedMoney(ready.plan.guaranteed)}")
                }
            }
        }
    }
    val ready = v.result as? LockResult.Ready
    if (confirming && ready != null) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Lock in ${Format.signedMoney(ready.plan.guaranteed)}?") },
            text = { Text(LockText.confirm(v)) },
            confirmButton = {
                Button(onClick = { confirming = false; onLock(v.marketId, ready.plan.guaranteed) }, modifier = Modifier.testTag("lockConfirm")) { Text("Lock it in") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}
