package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.keys.KeyState
import com.tjshea.vigilant.data.keys.KeyView
import com.tjshea.vigilant.data.keys.ProviderView
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.keys.UsageViews

/** The keys the app holds for a provider's meter (keyless providers have none). */
fun keysFor(state: UiState, policy: QuotaPolicy): List<String> = when (policy.id) {
    QuotaPolicy.ODDS_API.id -> state.oddsApiKeys
    QuotaPolicy.PINNAPI.id -> state.pinnapiKeys
    else -> emptyList()
}

fun providerFor(policy: QuotaPolicy): ApiProvider? = when (policy.id) {
    QuotaPolicy.ODDS_API.id -> ApiProvider.THE_ODDS_API
    QuotaPolicy.PINNAPI.id -> ApiProvider.PINNAPI
    else -> null
}

fun meterViews(state: UiState, now: Long): List<ProviderView> =
    QuotaPolicy.ALL.map { p -> UsageViews.build(p, keysFor(state, p), state.usage.providers[p.id], now) }

/** Green with plenty left, amber under half, red under a fifth or spent. */
@Composable
private fun meterColor(left: Int?, allowance: Int?): Color {
    val edge = Edge.colors
    if (left == null || allowance == null || allowance <= 0) return MaterialTheme.colorScheme.primary
    val f = left.toFloat() / allowance
    return when {
        f < 0.2f -> edge.negative
        f < 0.5f -> edge.warning
        else -> edge.positive
    }
}

/** Settings → API usage: one card per provider, one row per key. Moves after every call. */
@Composable
fun UsageSection(state: UiState, modifier: Modifier = Modifier) {
    val now = rememberNow(30_000)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        meterViews(state, now).forEach { ProviderMeter(it, now) }
    }
}

@Composable
private fun ProviderMeter(v: ProviderView, now: Long) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.policy.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(summary(v, now), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            v.fractionUsed?.let { used ->
                LinearProgressIndicator(
                    progress = { used.coerceIn(0f, 1f) },
                    color = meterColor(v.totalLeft, v.totalAllowance),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                )
            }
            if (v.policy.keyed && v.keys.isEmpty()) {
                Text("No key added.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            v.keys.forEach { KeyRow(v.policy, it, now) }
            if (!v.policy.keyed || v.callsToday > 0) {
                Text(
                    "${v.callsToday} request${if (v.callsToday == 1) "" else "s"} today" +
                        if (v.throttledToday > 0) " · ${v.throttledToday} slowed down" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (v.throttledToday > 0) Edge.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(v.policy.rule, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun summary(v: ProviderView, now: Long): String {
    if (!v.policy.keyed) return "no quota"
    if (v.keys.isEmpty()) return ""
    val left = v.totalLeft?.let { "$it ${v.policy.unit} left" } ?: "?"
    val reset = v.nextReset?.let { " · resets ${UsageMeter.whenText(it, now)}" } ?: ""
    return left + reset
}

@Composable
private fun KeyRow(policy: QuotaPolicy, k: KeyView, now: Long) {
    val edge = Edge.colors
    val (label, color) = when (k.state) {
        KeyState.ACTIVE -> "in use" to edge.positive
        KeyState.STANDBY -> "next" to MaterialTheme.colorScheme.onSurfaceVariant
        KeyState.SPENT -> "spent · ${k.until?.let { UsageMeter.untilText(it, now) } ?: ""}".trimEnd(' ', '·') to edge.negative
        KeyState.COOLING -> "waiting · ${k.until?.let { UsageMeter.whenText(it, now) } ?: ""}".trimEnd(' ', '·') to edge.warning
        KeyState.REFUSED -> "refused" to edge.negative
    }
    Column(Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text("Key ${k.index + 1}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(k.masked, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append("${k.used}")
                    k.allowance?.let { append("/$it") }
                    append(" used")
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (k.allowance != null && k.allowance!! > 0) {
            LinearProgressIndicator(
                progress = { (k.used.toFloat() / k.allowance!!).coerceIn(0f, 1f) },
                color = meterColor(k.left, k.allowance),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 3.dp),
            )
        }
        val detail = listOfNotNull(
            k.left?.let { "$it left" + if (!k.serverReported && policy.id == QuotaPolicy.PINNAPI.id) " (counted here)" else "" },
            k.lastCallMs?.let { "last call ${Format.age(it, now)}" },
            k.note?.takeIf { k.state == KeyState.REFUSED || k.state == KeyState.SPENT || k.state == KeyState.COOLING },
        ).joinToString(" · ")
        if (detail.isNotEmpty()) {
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp))
        }
    }
}

/** One line on the feed: what's left on each keyed provider, and today's Novig requests. */
@Composable
fun UsageStrip(state: UiState, modifier: Modifier = Modifier) {
    val now = rememberNow(30_000)
    val parts = meterViews(state, now).mapNotNull { v ->
        when {
            v.policy.keyed && v.keys.isNotEmpty() -> {
                val short = if (v.policy.id == QuotaPolicy.ODDS_API.id) "Odds API" else "Pinnacle"
                val left = v.totalLeft ?: return@mapNotNull null
                Triple(short, "$left left", meterColorKey(left, v.totalAllowance))
            }
            v.policy.id == QuotaPolicy.NOVIG.id && v.callsToday > 0 ->
                Triple("Novig", "${v.callsToday} today" + if (v.throttledToday > 0) ", ${v.throttledToday} slowed" else "", if (v.throttledToday > 0) 1 else 0)
            else -> null
        }
    }
    if (parts.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        parts.forEach { (name, value, level) ->
            val c = when (level) {
                2 -> Edge.colors.negative
                1 -> Edge.colors.warning
                else -> Edge.colors.positive
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(6.dp).background(c, CircleShape))
                Text("$name $value", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

private fun meterColorKey(left: Int, allowance: Int?): Int {
    if (allowance == null || allowance <= 0) return 0
    val f = left.toFloat() / allowance
    return when {
        f < 0.2f -> 2
        f < 0.5f -> 1
        else -> 0
    }
}
