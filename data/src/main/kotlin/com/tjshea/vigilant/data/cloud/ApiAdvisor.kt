package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.keys.KeyState
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.keys.UsageViews
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.serialization.Serializable

/**
 * Which APIs to refill, buy, fix or switch off (Tj, 2026-10-15: "recommend to me what apis to buy or refill"). Pure: the engine's usage ledger, what each feed answered on the last scan, which feeds have been
 * silent for scans in a row and the phone's switches go in, a short list of advice comes out. It goes to the phone in `status.advice`, to the Messages screen (daily, once per advice) and to DASHBOARD.md.
 * Only APIs the phone has switched ON are judged (an off API costs nothing and is never called). Words are plain; numbers come from the ledger, never guessed.
 */
object ApiAdvisor {
    @Serializable
    data class Advice(
        /** Stable key of the advice ("refill:propline"), so one problem is one message a day. */
        val id: String,
        /** refill, buy, key, fix, off, ok. */
        val kind: String,
        /** 0 = for information, 1 = worth doing soon, 2 = do it now (something is out or broken). */
        val severity: Int,
        val api: String,
        val title: String,
        val body: String,
    )

    const val LOW_FRACTION = 0.20
    const val SILENT_NOTE = "answered nothing"

    fun advise(
        book: UsageBook,
        feeds: List<FeedLine>,
        silent: List<String>,
        settings: ScanSettings,
        keysOf: (QuotaPolicy) -> List<String>,
        now: Long,
    ): List<Advice> {
        val out = ArrayList<Advice>()
        val silentProviders = silent.map { CloudDashboard.providerOf(it.substringBefore(' ')) }.toSet()
        for (id in ApiToggles.IDS) {
            val policy = ApiToggles.policy(id) ?: continue
            if (ApiToggles.isOn(settings, id) != true) continue
            val name = policy.displayName
            val keys = keysOf(policy)
            if (keys.isEmpty()) {
                out += Advice("key:$id", "key", 2, id, "$name is on but GitHub has no key for it", "Add its key in Settings › APIs (the app uploads it by itself) or switch $name off; until then the engine cannot use it.")
                continue
            }
            val v = UsageViews.build(policy, keys, book.providers[policy.id], now)
            val refused = v.keys.count { it.state == KeyState.REFUSED }
            if (refused > 0) out += Advice("fix:$id", "fix", 2, id, "$name refused ${if (refused == 1) "a key" else "$refused keys"}", "The provider rejected the key (wrong, expired or cancelled). Replace it in Settings › APIs, or the engine goes without $name.")
            val allowance = v.totalAllowance
            val left = v.totalLeft
            val resets = v.nextReset?.let { UsageMeter.whenText(it, now) }
            if (allowance != null && left != null && allowance > 0) {
                val frac = left.toDouble() / allowance
                when {
                    left <= 0 -> out += Advice("refill:$id", "refill", 2, id, "$name is used up", "0 of $allowance ${policy.unit} left" + (resets?.let { ", resets $it" } ?: "") + ". Refill it (a higher plan or a new key) or the engine runs without it until then.")
                    frac < LOW_FRACTION -> out += Advice("refill:$id", "refill", 1, id, "$name is running low", "$left of $allowance ${policy.unit} left (${(frac * 100).toInt()}%)" + (resets?.let { ", resets $it" } ?: "") + ". Add a key or move to a bigger plan before it runs out.")
                }
                // The day's pace against what is left: a monthly allowance that will not last to its reset.
                val calls = (book.providers[policy.id]?.callsToday ?: 0)
                val reset = v.nextReset
                if (left > 0 && calls > 0 && reset != null && frac >= LOW_FRACTION) {
                    val dayStart = QuotaPolicy.NOVIG.periodStart(now)
                    val dayFraction = ((now - dayStart).coerceAtLeast(1L)).toDouble() / 86_400_000.0
                    val perDay = calls / dayFraction.coerceAtLeast(0.25)
                    val daysLeft = left / perDay.coerceAtLeast(1.0)
                    val daysToReset = (reset - now) / 86_400_000.0
                    if (policy.period == com.tjshea.vigilant.data.keys.QuotaPeriod.MONTH_UTC && daysLeft < daysToReset * 0.8 && daysToReset > 1.0) {
                        out += Advice("buy:$id", "buy", 1, id, "$name will not last to its reset", "At today's pace ($calls ${policy.unit} so far) its $left remaining last about ${String.format(java.util.Locale.US, "%.0f", daysLeft)} days, but it resets in ${String.format(java.util.Locale.US, "%.0f", daysToReset)}. A bigger plan, or letting it rest, would cover it.")
                    }
                }
            }
            if (id.let { ApiToggles.policy(it)?.id } in silentProviders) {
                out += Advice("off:$id", "off", 1, id, "$name $SILENT_NOTE", "It has returned no leagues for several scans in a row while still being asked. Check its key and plan; the engine rests it for a while to save requests and turns it off if it stays silent.")
            }
        }
        return out.sortedWith(compareByDescending<Advice> { it.severity }.thenBy { it.id })
    }

    /**
     * What the manager's scorecard adds to the advice (TCL61): a paid plan whose credits would expire unspent (so the engine now uses it more), and free keys of a useful API that are mostly spent, with the
     * numbers. Information (severity 0), once a day each.
     */
    fun manage(cards: List<ApiScorecard.Card>, book: UsageBook, keysOf: (QuotaPolicy) -> List<String>, settings: ScanSettings, now: Long): List<Advice> {
        val out = ArrayList<Advice>()
        for (c in cards) {
            if (c.surplus && c.leftFraction != null && c.timeFraction != null) {
                out += Advice("use:${c.id}", "ok", 0, c.id, "${c.name}: using more of the plan you pay for", "${(c.leftFraction * 100).toInt()}% of its paid credits are left with ${(c.timeFraction * 100).toInt()}% of the month to go, so the engine now asks it as a second opinion for each league now and then (credits that expire unused are the only waste).")
            }
            val policy = ApiToggles.policy(c.id) ?: continue
            if (!c.paid && c.role == "active" && ApiToggles.isOn(settings, c.id) == true) {
                val keys = book.providers[policy.id]?.keys?.values.orEmpty()
                val spent = keys.count { (it.remaining ?: ((it.limit ?: 0) - it.used)) <= (it.limit ?: 0) * 0.1 }
                if (keys.size >= 3 && spent * 2 >= keys.size) {
                    out += Advice("keys:${c.id}", "key", 0, c.id, "${c.name}: $spent of ${keys.size} free keys are nearly spent", "It is answering well (${c.answeredPct ?: 100}% of scans), so another free key would keep it going until its reset; the engine leaves each key's last credits for closing lines.")
                }
            }
        }
        return out
    }
}
