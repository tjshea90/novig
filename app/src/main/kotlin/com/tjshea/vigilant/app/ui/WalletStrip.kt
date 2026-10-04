package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.WalletBalance

/** The wallet strip's words, free of Compose so they're tested. */
object WalletStripText {
    /** Past this, the strip says how old the balance is. */
    const val SAY_AGE_AFTER_MS = 60_000L

    /** "Vigilant wallet $18.51". */
    fun balance(r: WalletBalance.Reading?): String = "Vigilant wallet " + (r?.let { Format.money(it.dollars) } ?: "not read yet")

    /** The bids up are worth more than the wallet holds (Novig holds nothing for a resting bid; the app takes the extra down, [com.tjshea.vigilant.app.MakerRunner.fitToWallet]). */
    fun over(r: WalletBalance.Reading?, bids: Int, bidDollars: Double): Boolean = r != null && bids > 0 && bidDollars > r.dollars + 0.005

    /** " · 3 bids up ($8.20) · 2m ago" (each part only when it says something), "· over the wallet" while the bids up are worth more than it holds. */
    fun detail(r: WalletBalance.Reading?, bids: Int, bidDollars: Double, now: Long): String = listOfNotNull(
        bids.takeIf { it > 0 }?.let { "$it bid${if (it == 1) "" else "s"} up (${Format.money(bidDollars)})" },
        "over the wallet".takeIf { over(r, bids, bidDollars) },
        r?.takeIf { now - it.atMs >= SAY_AGE_AFTER_MS }?.let { Format.age(it.atMs, now) },
    ).joinToString(" · ")
}

/**
 * The Vigilant wallet's balance on every tab, just above the tab bar (Tj, 2026-10-03: "make a quick way inside the app where I can see my vigilant
 * wallet balance, maybe show it somewhere in the app at all times"), with the bids resting from it. Tapping it reads the balance again now.
 * Its own composable with its own clock, so a new balance redraws only this line.
 */
@Composable
fun WalletStrip(reading: WalletBalance.Reading?, bids: Int, bidDollars: Double, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val now = rememberNow(15_000)
    Surface(
        modifier.fillMaxWidth().clickable(onClickLabel = "Read the wallet again", role = Role.Button, onClick = onRefresh).testTag("walletStrip"),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(WalletStripText.balance(reading), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            val detail = WalletStripText.detail(reading, bids, bidDollars, now)
            if (detail.isNotEmpty()) {
                Text(
                    " · $detail", style = MaterialTheme.typography.labelMedium,
                    color = if (WalletStripText.over(reading, bids, bidDollars)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
