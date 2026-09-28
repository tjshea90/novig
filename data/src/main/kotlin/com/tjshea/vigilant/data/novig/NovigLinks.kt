package com.tjshea.vigilant.data.novig

import java.util.Locale
import kotlin.math.roundToLong

/**
 * What Novig's bet slip is opened with (Tj, 2026-09-28: "Can the app automatically enter 1 dollar on every betslip inside
 * novig when I click on a bet? If it can, make an option to automatically enter 1 dollar per bet, the kelly value per bet,
 * or an amount I can type into the settings"). Novig still asks you to confirm the bet.
 */
enum class SlipStake(val label: String) {
    OFF("Off"),
    ONE_DOLLAR("$1"),
    KELLY("Kelly"),
    CUSTOM("My amount"),
}

/**
 * Novig's bet-slip links with a stake filled in. Novig's deeplinking docs (docs.novig.com/affiliates/deeplinking):
 * `novig.com/events/<outcome_ids>/<partner_id>/<wager_amount>`, the amount "Optional pre-filled wager amount (requires
 * partner_id)" in dollars ("10 Novig Cash" in their example); the app's own link config has the same
 * `events/:orderslip_outcomes?/:partner_id?/:amount?` (NOVIG_API.md §9.1), so `novigapp://` takes it the same way.
 */
object NovigLinks {
    /** The partner tag when a link has none: Novig's own, from its docs' example (`…/novig/10`). CNO's links keep `cno`. */
    const val PARTNER = "novig"

    private val EVENTS = Regex("^(novigapp://events/|https://(?:www\\.)?novig\\.com/events/)([^?#]*)(.*)$")

    /** The dollars to pre-fill for [mode]: none, $1, the bet's Kelly stake (never under $1), or [custom]. */
    fun stake(mode: SlipStake, custom: Double, kelly: Double?): Double? = when (mode) {
        SlipStake.OFF -> null
        SlipStake.ONE_DOLLAR -> 1.0
        SlipStake.KELLY -> kelly?.takeIf { it > 0 }?.let { maxOf(1.0, cents(it)) }
        SlipStake.CUSTOM -> custom.takeIf { it > 0 }?.let { cents(it) }
    }

    /**
     * [link] with [amount] dollars in its bet slip: only a bet-slip link (`novigapp://events/<outcomes>…` or
     * `novig.com/events/…`), keeping its outcomes, partner tag and query; anything else (a game's markets, BetMGM) and a
     * null [amount] come back as they were.
     */
    fun withStake(link: String?, amount: Double?): String? {
        if (link == null || amount == null || amount <= 0) return link
        val m = EVENTS.find(link) ?: return link
        val (prefix, path, rest) = m.destructured
        val parts = path.trimEnd('/').split('/')
        val outcomes = parts[0].takeIf { it.isNotBlank() } ?: return link
        val partner = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: PARTNER
        return "$prefix$outcomes/$partner/${amountText(amount)}$rest"
    }

    /** "1", "12.5", "12.27": dollars, at most two decimals. */
    fun amountText(amount: Double): String {
        val c = (amount * 100).roundToLong()
        return if (c % 100 == 0L) (c / 100).toString() else String.format(Locale.US, "%.2f", c / 100.0).trimEnd('0')
    }

    private fun cents(v: Double) = (v * 100).roundToLong() / 100.0
}
