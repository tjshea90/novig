package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.keys.KeyState
import com.tjshea.vigilant.data.keys.ProviderView
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageViews
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * `DASHBOARD.md` on the `cloud-feed` branch (Tj, 2026-10-15: "a way where I can see which apis are working and active at any time on GitHub and with accurate usage meters"): GitHub renders it, so
 * `github.com/<repo>/blob/cloud-feed/DASHBOARD.md` is the page. Pure text from what the engine already has: [CloudStatus] (what each feed answered on the last scan, the Novig route, the address streak, trading and
 * the wallet) and the engine's [UsageBook] (the same ledger the phone's meters are built from, through the same [UsageViews.build]).
 *
 * The repository is public, so **no key is ever written here**: a key is its [CloudUsage.keyId] (10 hex characters of a hash), the book comes in already keyed by those ids, and a note is the ledger's scrubbed one.
 * The usage shown is the ENGINE's (carried from job to job); calls the phone makes itself are not in it (while the cloud scan drives, the phone makes almost none), and a "left" marked "provider" is the provider's own
 * figure with the time it was read, otherwise it is the engine's count.
 */
object CloudDashboard {
    /** A feed id ("sgo-props", "parlay_1h", "oddspapi_free-props") to the provider whose meter it spends. */
    fun providerOf(feedId: String): String {
        val base = feedId.removeSuffix("-props").removeSuffix("_props").removeSuffix("_1h")
        return if (base == "bolt") "boltodds" else base
    }

    /** What the table says about one provider after the last scan. */
    enum class Health(val label: String) {
        WORKING("✅ working"), STANDING_BY("💤 standing by"), HELD_BACK("⏸ held back"), FAILING("❌ failing"), SILENT("⚠️ answered nothing"), NOT_ASKED("— not asked"), OFF("⛔ off / no key"), UNKNOWN("… no scan report yet"),
    }

    fun markdown(st: CloudStatus, book: UsageBook, keyIdsOf: (QuotaPolicy) -> List<String>, now: Long, repo: String = "tjshea90/novig"): String = buildString {
        val beat = if (st.heartbeatMs > 0) now - st.heartbeatMs else -1L
        val alive = beat in 0..180_000L
        appendLine("# Vigilant GitHub engine: live status")
        appendLine()
        appendLine("**${if (st.idle) "Idle (the phone's switch is off)" else if (!alive) "NOT RESPONDING" else if (st.scanning) "Scanning" else "Running"}** · updated ${clock(st.heartbeatMs)} (${ago(beat)}) · run `${st.runId.ifBlank { "?" }}` · pass ${st.passes}")
        appendLine()
        appendLine("_Reload the page for the latest. Files: [status.json](https://github.com/$repo/blob/cloud-feed/status.json) · [usage.json](https://github.com/$repo/blob/cloud-feed/usage.json) · [trades.json](https://github.com/$repo/blob/cloud-feed/trades.json) · [engine-log.txt](https://github.com/$repo/blob/cloud-feed/engine-log.txt)_")
        appendLine()
        appendLine("## Engine")
        appendLine()
        appendLine("| | |")
        appendLine("|:--|:--|")
        if (st.scanning) appendLine("| Scan now | ${st.progressStep.ifBlank { "starting" }}${if (st.progressTotal > 0) " (${st.progressDone}/${st.progressTotal})" else ""} · running ${dur(now - st.scanStartedAtMs)} |")
        appendLine("| Last scan | ${if (st.lastScanEndedAtMs > 0) "ended ${clock(st.lastScanEndedAtMs)} (${ago(now - st.lastScanEndedAtMs)}), took ${st.scanMs / 1000} s, ${st.rows} bets listed" else "none finished yet"} |")
        appendLine("| List age | Novig prices median ${secs(st.novigAgeMedianS)} (oldest ${secs(st.novigAgeMaxS)}) · fair odds median ${secs(st.fairAgeMedianS)} (oldest ${secs(st.fairAgeMaxS)}) |")
        appendLine("| Novig | ${st.novigRoute.ifBlank { "not said yet" }} |")
        st.novigAddress.takeIf { it.isNotBlank() }?.let { appendLine("| Novig and this machine's address | $it |") }
        appendLine("| Runner address | ${if (st.addressHandovers == 0) "accepted by Novig (no refusal; ${st.addressHandovers} replaced runners in a row)" else "REFUSED ${st.addressHandovers} time(s) in a row, last ${clock(st.addressHandoverAtMs)}: the job ends early and a new runner is drawn"} |")
        appendLine("| Trading | ${st.tradeMode.ifBlank { "off" }.uppercase()}${if (st.tradeNote.isNotBlank()) ": ${st.tradeNote}" else ""}${if (st.tradeStopped.isNotBlank()) " · **STOPPED: ${st.tradeStopped}**" else ""} |")
        appendLine("| Wallet | ${wallet(st, now)} |")
        if (st.problem != null) appendLine("| Problem | ${st.problem} |")
        if (st.settingsPending) appendLine("| Settings | changed on the phone since this scan began: the next scan applies them |")
        appendLine()
        appendLine("## APIs: working or not, and what each has left")
        appendLine()
        appendLine("Health is from the **last finished scan** (${if (st.feedReportAtMs > 0) "${clock(st.feedReportAtMs)}, ${ago(now - st.feedReportAtMs)}" else "no report yet: this engine build is older than the page, the next job fills it in"}): leagues answered fresh+reused, Novig games matched. An API the phone has switched off, or with no key on GitHub, is never called and shows as off.")
        if (st.feedAlerts.isNotEmpty()) { appendLine(); appendLine("**Silent feeds (answered nothing for ${FeedWatch.LIMIT}+ scans in a row):** " + st.feedAlerts.joinToString(", ")) }
        appendLine()
        appendLine("| API | Health | Leagues (fresh+reused) | Games matched | Left of allowance | Resets | Calls today | Throttled | Note |")
        appendLine("|:--|:--|--:|--:|--:|:--|--:|--:|:--|")
        val byProvider = st.feedReport.groupBy { providerOf(it.id) }
        val policies = QuotaPolicy.ALL
        val views = ArrayList<ProviderView>()
        val shown = HashSet<String>()
        for (policy in policies) {
            val feeds = byProvider[policy.id].orEmpty()
            val view = UsageViews.build(policy, keyIdsOf(policy), book.providers[policy.id], now)
            views += view
            shown += policy.id
            val health = if (st.feedReportAtMs <= 0L) Health.UNKNOWN else health(feeds, policy.id in st.sources.map { providerOf(it) } || feeds.isNotEmpty() || !policy.keyed)
            val left = when {
                !policy.keyed -> "no key, not metered"
                view.totalLeft != null && view.totalAllowance != null -> "${num(view.totalLeft)} of ${num(view.totalAllowance)} ${policy.unit} (${(100 * view.totalLeft / view.totalAllowance.coerceAtLeast(1))}%)"
                view.totalLeft != null -> "${num(view.totalLeft)} ${policy.unit}"
                else -> "no allowance published"
            }
            val reset = if (policy.keyed && view.nextReset != null && view.totalAllowance != null) clock(view.nextReset) else "—"
            val note = (feeds.mapNotNull { f -> f.error ?: f.heldBack }.firstOrNull() ?: "").replace('|', '/').take(90)
            appendLine("| ${policy.displayName} | ${health.label} | ${feeds.joinToString(" · ") { "${it.id} ${it.fetched}+${it.reused}" }.ifBlank { "—" }} | ${feeds.sumOf { it.matched }.takeIf { feeds.isNotEmpty() } ?: "—"} | $left | $reset | ${num(view.callsToday)} | ${view.throttledToday} | $note |")
        }
        // Feeds that map to no known meter (BoltOdds' two sockets are under boltodds; anything new lands here so it is never invisible).
        val extra = st.feedReport.filter { providerOf(it.id) !in shown }
        for (f in extra) appendLine("| ${f.name.ifBlank { f.id }} | ${health(listOf(f), true).label} | ${f.id} ${f.fetched}+${f.reused} | ${f.matched} | not metered | — | — | — | ${(f.error ?: f.heldBack ?: "").replace('|', '/').take(90)} |")
        appendLine()
        if (st.advice.isNotEmpty()) {
            appendLine("## What to refill, buy or fix")
            appendLine()
            st.advice.forEach { a -> appendLine("- ${when (a.severity) { 2 -> "🔴"; 1 -> "🟠"; else -> "🟢" }} **${a.title}**: ${a.body}") }
            appendLine()
        }
        if (st.resting.isNotEmpty() || st.governorOff.isNotEmpty()) {
            appendLine("## Resting to save usage")
            appendLine()
            st.resting.forEach { appendLine("- 💤 $it") }
            st.governorOff.forEach { appendLine("- ⛔ ${ApiToggles.name(it)}: the engine asked the phone to switch it off (silent after every rest); switch it back on after fixing its key or plan") }
            appendLine()
        }
        val keyed = views.filter { it.policy.keyed && it.keys.isNotEmpty() }
        if (keyed.isNotEmpty()) {
            appendLine("## Usage meters per key")
            appendLine()
            appendLine("A key is shown by an id (never the key). **provider** = the provider's own figure, read at the time shown; **count** = the engine's own tally. The active key is the one the next call uses.")
            appendLine()
            appendLine("| API | Key | State | Used | Left | Figure | Until |")
            appendLine("|:--|:--|:--|--:|--:|:--|:--|")
            for (v in keyed) for (k in v.keys) {
                val state = when (k.state) { KeyState.ACTIVE -> "▶ active"; KeyState.STANDBY -> "standby"; KeyState.SPENT -> "spent"; KeyState.COOLING -> "cooling"; KeyState.REFUSED -> "refused by provider" }
                val fig = if (k.serverReported) "provider${k.figureAtMs?.let { ", read ${dur(now - it)} ago" } ?: ""}" else "count"
                appendLine("| ${v.policy.displayName} | `${k.masked}` | $state | ${num(k.used)}${k.allowance?.let { " / ${num(it)}" } ?: ""} | ${k.left?.let { num(it) } ?: "—"} | $fig | ${k.until?.let { clock(it) } ?: "—"} |")
            }
            appendLine()
        }
        if (st.ranking.isNotBlank()) {
            appendLine("## Order the engine asks the APIs (best first, measured)")
            appendLine()
            st.ranking.lines().filter { it.isNotBlank() }.forEach { appendLine("- $it") }
            appendLine()
        }
        appendLine("_Rules this page follows: only the engine's own calls are counted; an API the phone switched off is never called; no key or call time is ever written to this public repository._")
    }

    /** How a provider did on the last scan, from its feeds' lines. [onForEngine] = the engine is configured to ask it (a switch on and a key), or it needs no key. */
    fun health(feeds: List<FeedLine>, onForEngine: Boolean): Health = when {
        feeds.isEmpty() -> if (onForEngine) Health.NOT_ASKED else Health.OFF
        feeds.any { it.error != null } && feeds.none { it.fetched + it.reused > 0 } -> Health.FAILING
        feeds.any { it.fetched + it.reused > 0 } -> Health.WORKING
        feeds.any { it.heldBack != null } -> Health.HELD_BACK
        feeds.any { it.standingBy > 0 } -> Health.STANDING_BY
        else -> Health.SILENT
    }

    private fun wallet(st: CloudStatus, now: Long): String = when {
        st.walletSleep.isNotBlank() -> "SLEEPING: ${st.walletSleep}"
        st.walletReadAtMs <= 0L -> if (st.tradeMode.equals("live", true)) "not read yet${if (st.walletCheckedAtMs > 0) " (asked ${dur(now - st.walletCheckedAtMs)} ago and got no answer)" else ""}: NOT zero, just unknown" else "not applicable (trading is not live)"
        else -> "cash ${money(st.walletCash)} · equity ${money(st.walletEquity)} · read ${dur(now - st.walletReadAtMs)} ago"
    }

    private fun money(x: Double) = if (x < 0) "unknown" else String.format(java.util.Locale.US, "$%.2f", x)
    private fun num(n: Int) = String.format(java.util.Locale.US, "%,d", n)
    private fun secs(s: Long) = if (s < 0) "n/a" else if (s < 120) "$s s" else "${s / 60} min"
    private val fmt = DateTimeFormatter.ofPattern("MMM d HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)
    private fun clock(ms: Long) = if (ms <= 0) "never" else fmt.format(Instant.ofEpochMilli(ms))
    private fun dur(ms: Long): String = when {
        ms < 0 -> "n/a"
        ms < 90_000 -> "${ms / 1000} s"
        ms < 90 * 60_000L -> "${ms / 60_000} min"
        else -> "${ms / 3_600_000} h"
    }
    private fun ago(ms: Long): String = if (ms < 0) "n/a" else "${dur(ms)} ago"
}
