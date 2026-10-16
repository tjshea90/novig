package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.keys.QuotaPeriod
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SourceScore
import kotlinx.serialization.Serializable

/**
 * The smart API manager's view of every API (Tj, 2026-10-16, TCL61: "a smart api manager that is autonomous and uses the best apis for their features and speed while also taking full advantage of my paid
 * providers"). Pure. One [Card] per odds API from what the engine already measures: how old its quotes are ([SourceScore.Measure]), how far from Pinnacle, whether it answered the last scans, its budget against
 * the clock, and whether it is a plan Tj pays for. Two decisions come out of it, and the existing [ApiChain] and [ApiGovernor] act on them:
 *  - **paid** providers win a tie on freshness (a paid feed that is as fresh as a free one goes first, so the plan is used and the free credits are kept);
 *  - **surplus**: a paid, monthly-metered provider that is ahead of the calendar (more credits left than days left) is asked as a second opinion even where a better feed already answered,
 *    because credits that expire unused are the only real waste ("let it use as much access as needed but do not be wasteful").
 * Free and scarce keys are never given a surplus: their few credits are kept for gaps, fallbacks and closing lines.
 */
object ApiScorecard {
    @Serializable
    data class Card(
        val id: String,
        val name: String,
        /** A plan Tj pays for (Pro/Starter or a flat-rate subscription). */
        val paid: Boolean,
        /** off, nokey, rested, failing, active, standby. */
        val role: String,
        /** 90th-percentile age of its quotes by the books' own stamps; null = not stamped or not measured. */
        val p90AgeS: Long? = null,
        val offPinnaclePts: Double? = null,
        /** Share of the last scans (up to [WINDOW]) in which it answered when asked; null = never asked in them. */
        val answeredPct: Int? = null,
        /** Fraction of the paid allowance still unspent, and of the period still to come; null for a flat-rate or daily plan. */
        val leftFraction: Double? = null,
        val timeFraction: Double? = null,
        val surplus: Boolean = false,
        val note: String = "",
        /** Priced lines whose fair odds it helped build (of the last scan), and those it was the ONLY API behind: the unique contribution that justifies keeping it on. */
        val fedLines: Int = 0,
        val soleLines: Int = 0,
    )

    const val WINDOW = 12
    const val SURPLUS_MARGIN = 0.15
    /** A key allowance above the free tier's 1,000 marks a paid plan on the metered providers. */
    const val PAID_ABOVE = 1000

    /** Providers that are a subscription whatever their meter says (flat-rate, no free tier). */
    private val FLAT_PAID = setOf("sgo", "boltodds", "oddspapi")

    private val history = HashMap<String, ArrayDeque<Boolean>>()

    /** Remember what each API did on a finished scan (asked and answered, or asked and did not). */
    fun observe(feeds: List<FeedLine>) {
        val byApi = feeds.groupBy { ApiToggles.idOfPolicy(CloudDashboard.providerOf(it.id)) }
        for ((id, fs) in byApi) {
            if (id == null) continue
            val asked = fs.any { it.fetched + it.reused > 0 || it.error != null } || fs.all { it.standingBy == 0 && it.skipped == 0 && it.heldBack == null }
            if (!asked) continue
            val answered = fs.any { it.fetched + it.reused > 0 }
            val d = history.getOrPut(id) { ArrayDeque() }
            d.addLast(answered); while (d.size > WINDOW) d.removeFirst()
        }
    }

    fun reset() = history.clear()

    fun paidProviders(book: UsageBook, keyed: (String) -> Boolean): Set<String> = ApiToggles.IDS.filter { id ->
        if (!keyed(id)) return@filter false
        val p = ApiToggles.policy(id) ?: return@filter false
        when {
            id in FLAT_PAID -> true
            id == "parlay" || id == "propline" -> book.providers[p.id]?.keys?.values.orEmpty().any { (it.limit ?: 0) > PAID_ABOVE }
            else -> false
        }
    }.toSet()

    /** The paid monthly-metered providers that are ahead of the calendar: their credits would expire unspent. */
    fun surplus(book: UsageBook, paid: Set<String>, now: Long): Set<String> = paid.filter { id ->
        val p = ApiToggles.policy(id) ?: return@filter false
        if (p.period != QuotaPeriod.MONTH_UTC || SourceScore.SCARCE.any { it.startsWith(p.id) }) return@filter false
        val (left, limit, frac) = paidLeft(book, p.id, now) ?: return@filter false
        limit > 0 && left.toDouble() / limit - frac > SURPLUS_MARGIN
    }.toSet()

    /** Credits left, allowance and the share of the period still to come, over the paid keys only. */
    private fun paidLeft(book: UsageBook, provider: String, now: Long): Triple<Int, Int, Double>? {
        val keys = book.providers[provider]?.keys?.values.orEmpty().filter { (it.limit ?: 0) > PAID_ABOVE }
        if (keys.isEmpty()) return null
        val left = keys.sumOf { it.remaining ?: ((it.limit ?: 0) - it.used) }.coerceAtLeast(0)
        val limit = keys.sumOf { it.limit ?: 0 }
        val reset = keys.mapNotNull { it.resetAtMs }.maxOrNull() ?: QuotaPeriod.MONTH_UTC.let { _ -> nextMonth(now) }
        val frac = ((reset - now).toDouble() / MONTH_MS).coerceIn(0.0, 1.0)
        return Triple(left, limit, frac)
    }

    private fun nextMonth(now: Long): Long {
        val d = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneOffset.UTC).toLocalDate().withDayOfMonth(1).plusMonths(1)
        return d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    private const val MONTH_MS = 30L * 86_400_000L

    /** Per API id: (lines its books helped price, lines it was the only API behind) over every priced outcome of [result]. */
    fun contribution(result: com.tjshea.vigilant.data.scanner.ScanResult?, now: Long): Map<String, Pair<Int, Int>> {
        if (result == null) return emptyMap()
        val fed = HashMap<String, Int>()
        val sole = HashMap<String, Int>()
        for (g in result.games) for (o in g.outcomes) {
            if (o.fair == null) continue
            val ids = com.tjshea.vigilant.data.scanner.FairProvenance.of(o, now).mapNotNull { it.source }.map { CloudDashboard.providerOf(it).let { p -> ApiToggles.idOfPolicy(p) ?: p } }.toSet()
            for (id in ids) fed.merge(id, 1, Int::plus)
            if (ids.size == 1) sole.merge(ids.first(), 1, Int::plus)
        }
        return fed.mapValues { (id, n) -> n to (sole[id] ?: 0) }
    }

    fun cards(
        settings: ScanSettings,
        book: UsageBook,
        measures: Map<String, SourceScore.Measure>,
        feeds: List<FeedLine>,
        resting: Set<String>,
        keyed: (String) -> Boolean,
        now: Long,
        contribution: Map<String, Pair<Int, Int>> = emptyMap(),
    ): List<Card> {
        val paid = paidProviders(book, keyed)
        val surplus = surplus(book, paid, now)
        return ApiToggles.IDS.map { id ->
            val on = ApiToggles.isOn(settings, id) == true
            val mine = feeds.filter { ApiToggles.idOfPolicy(CloudDashboard.providerOf(it.id)) == id }
            val ms = measures.filterKeys { ApiToggles.idOfPolicy(CloudDashboard.providerOf(it)) == id }.values.filter { it.quotes >= SourceScore.MIN_QUOTES }
            val p90 = ms.mapNotNull { it.p90AgeMs }.maxOrNull()
            val pts = ms.mapNotNull { it.agreementPts }.maxOrNull()
            val h = history[id]
            val role = when {
                !on -> "off"
                ApiToggles.policy(id)?.keyed == true && !keyed(id) -> "nokey"
                id in resting -> "rested"
                mine.any { it.error != null && it.fetched + it.reused == 0 } -> "failing"
                mine.any { it.fetched + it.reused > 0 } -> "active"
                else -> "standby"
            }
            val pol = ApiToggles.policy(id)
            val left = pol?.let { paidLeft(book, it.id, now) }
            Card(
                id = id, name = ApiToggles.name(id), paid = id in paid, role = role,
                p90AgeS = p90?.div(1000), offPinnaclePts = pts,
                answeredPct = h?.takeIf { it.isNotEmpty() }?.let { it.count { a -> a } * 100 / it.size },
                leftFraction = left?.takeIf { it.second > 0 && id in paid && pol.period == QuotaPeriod.MONTH_UTC }?.let { it.first.toDouble() / it.second },
                timeFraction = left?.takeIf { id in paid && pol.period == QuotaPeriod.MONTH_UTC }?.third,
                surplus = id in surplus,
                fedLines = contribution[id]?.first ?: 0, soleLines = contribution[id]?.second ?: 0,
            )
        }
    }
}
