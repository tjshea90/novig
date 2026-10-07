package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.AtBet
import com.tjshea.vigilant.data.tracker.TrackedBet
import java.util.Locale

/**
 * The small-prop guard (Tj, 2026-10-07: "right now most of the auto bet feature is betting nhl player shots on goal ... I'm worried it is not betting on sharp information with this
 * type of volatile bet. First see if it is wise to have a lot of player shots on goal nhl bets and if not, set up some type of guard for obscure auto betting on small props like
 * this"; RESEARCH.md §109). Pure.
 *
 * What the evidence said (two independent studies of the v0.71.2 files and a skeptic who re-derived the numbers): NHL shots on goal is not obscure by Novig's volume (its
 * most-traded skater prop), the app's own sharp check passed every one of the 35 recorded SOG auto-bets, and no independent close shows an edge or a loss (10 closes, CLV -0.7%,
 * interval -3.8 to +1.8): **unproven**, not unwise. What is clearly out of line is **concentration**: SOG was 16 of 34 taker auto-bets on Oct 6 (47%), with no cap of any kind on how
 * much of the day one kind of prop may take, and an NHL game lists 20-30 SOG lines, so a slate with no football can fill the day with one market whose fair price rests on thinner sharp
 * information (Kalshi never lists it, ProphetX prices both sides on half the pages). So the guard limits how much ONE kind of player prop (league + stat) may take, without banning
 * it, raising its edge bar or special-casing any market: it is diversification, not an edge filter, and it says so.
 *
 *  - **Share** ([ScanSettings.propGuardShare], 25% by default, 0 = off): once the last 24 hours hold [ScanSettings.propGuardMinSample] auto-bets (8), a bet that would take one kind of
 *    prop above this share of them waits ([effectiveShare]: never stricter than an even split of the kinds being bet, or a day with three kinds of prop would stop the auto-bet). A
 *    back-test on the 178 taker auto-bets: 25% from the 6th bet holds back only the SOG-heavy days (Oct 3, 5, 6).
 *  - **Per game** ([ScanSettings.propGuardPerGame], 3 by default, 0 = off): at most this many auto-bets on one kind of prop in one game (two games had four SOG bets).
 *
 * Only player props are held to it (a spread or a total has its own sharp books and its own caps); the history is the Tracker's auto-bets (a filled bid is not a taker bet).
 */
object PropGuard {

    const val DEFAULT_SHARE = 0.25
    const val DEFAULT_MIN_SAMPLE = 8
    const val DEFAULT_PER_GAME = 3

    /** The window the share is judged over. */
    const val WINDOW_MS = 24 * 3_600_000L

    /** The window Diagnostics also shows. */
    const val WEEK_MS = 7 * 24 * 3_600_000L

    val SHARE_CHOICES = listOf(0.0, 0.15, 0.25, 0.35, 0.50)
    val MIN_SAMPLE_CHOICES = listOf(5, 8, 12, 20)
    val PER_GAME_CHOICES = listOf(0, 2, 3, 4)

    /** The most a typed share can be: 100% is no cap at all, so 0 is the way to say that. */
    const val MAX_SHARE = 0.95

    /** The guard's limits as [ScanSettings] set them. */
    data class Rules(val share: Double, val minSample: Int, val perGame: Int) {
        val shareOn: Boolean get() = share > 1e-9
        val perGameOn: Boolean get() = perGame > 0
        val on: Boolean get() = shareOn || perGameOn
    }

    fun rules(s: ScanSettings): Rules = Rules(
        share = if (s.propGuardShare <= 1e-9) 0.0 else s.propGuardShare.coerceIn(0.01, MAX_SHARE),
        minSample = s.propGuardMinSample.coerceAtLeast(1),
        perGame = s.propGuardPerGame.coerceAtLeast(0),
    )

    /** One auto-bet in the guard's history: its kind of prop (null: not a player prop, never held back), its game, and when it was placed. */
    data class Placed(val key: String?, val game: String, val atMs: Long)

    /** One kind of prop's part of the auto-bets in a window. */
    data class Share(val key: String, val count: Int, val total: Int) {
        val share: Double get() = if (total == 0) 0.0 else count.toDouble() / total
        val label: String get() = labelOf(key)
    }

    private val TOKEN = Regex("""([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+)\s*$""")
    private val AFTER_LINE = Regex("""\d+(?:\.\d+)?\s+([A-Za-z][A-Za-z_ ]*)$""")

    /**
     * "NHL|SHOTS_ON_GOAL": the kind of prop a bet is, from its league and the market and bet as CrazyNinjaOdds (or the Tracker's copy) names them; null for anything that is not a player
     * prop. The same words give the same key whoever wrote them: "Player Shots On Goal", "Connor McDavid 3.5 SHOTS_ON_GOAL" and a Novig market type all say SHOTS_ON_GOAL.
     */
    fun key(league: String, market: String, bet: String): String? {
        if (BetKind.of(market, bet) != BetKind.PROP) return null
        val lg = league.trim().uppercase(Locale.US)
        if (lg.isEmpty()) return null
        val stat = statOf(market) ?: return null
        return "$lg|$stat"
    }

    internal fun statOf(market: String): String? {
        val m = market.trim()
        TOKEN.find(m)?.let { return it.groupValues[1] }
        val words = (AFTER_LINE.find(m)?.groupValues?.get(1) ?: m)
            .replace(Regex("(?i)\\bplayer\\b"), " ").replace(Regex("[^A-Za-z0-9_ ]"), " ").trim()
            .split(Regex("[\\s_]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        return words.joinToString("_") { it.uppercase(Locale.US) }
    }

    /** "NHL|SHOTS_ON_GOAL" as words: "NHL shots on goal". */
    fun labelOf(key: String): String {
        val league = key.substringBefore('|')
        val stat = key.substringAfter('|').lowercase(Locale.US).replace('_', ' ')
        return "$league $stat"
    }

    /** The game a bet is on, as the Tracker and a resolved Novig target both name it: Novig's event id when known, else the event's name and start. */
    fun gameKey(eventId: String?, event: String, startsTs: Long): String =
        eventId?.takeIf { it.isNotBlank() } ?: "${event.trim().lowercase(Locale.US)}|$startsTs"

    /** The auto-bets of the Tracker within [windowMs] of [now] (a filled bid and a lock are not taker bets). */
    fun history(bets: Collection<TrackedBet>, now: Long, windowMs: Long = WINDOW_MS): List<Placed> = bets.asSequence()
        .filter { it.auto && it.lockFor == null && it.atBet?.how != AtBet.HOW_BID && now - it.createdAtMs <= windowMs }
        .map { Placed(key(it.league, it.marketLabel, it.selection), gameKey(it.eventId, it.eventName, it.startsTs), it.createdAtMs) }
        .toList()

    /** Why a bet of kind [key] on [game] waits, or null. [history]: the auto-bets so far in the window (those placed earlier in this pass included). */
    fun judge(r: Rules, history: List<Placed>, key: String?, game: String, now: Long): String? {
        if (key == null || !r.on) return null
        val recent = history.filter { now - it.atMs <= WINDOW_MS }
        if (r.shareOn && recent.size >= r.minSample) {
            val same = recent.count { it.key == key }
            if ((same + 1).toDouble() / (recent.size + 1) > effectiveShare(r.share, recent, key) + 1e-9) return shareReason(key, r.share)
        }
        if (r.perGameOn && history.count { it.key == key && it.game == game } >= r.perGame) return perGameReason(key, r.perGame)
        return null
    }

    /**
     * The cap as it is applied: [share], but never under an even split of the kinds being bet plus one bet. A cap of 25% cannot be met when only three kinds are in play (each is a third
     * of the day), and holding every bet back would stop the auto-bet altogether, which is not what a diversification guard is for. Every bet that is not a player prop counts as one more
     * kind (null), so a day of game lines and a few props is not held to a share it cannot reach either.
     */
    fun effectiveShare(share: Double, recent: List<Placed>, key: String): Double {
        val kinds = (recent.map { it.key } + key).toSet().size
        return maxOf(share, 1.0 / kinds + 1.0 / (recent.size + 1))
    }

    fun shareReason(key: String, share: Double): String =
        "${labelOf(key)} already holds its share of the last 24 hours' auto-bets (the small-prop guard's ${percent(share)} cap, Auto-bet tab)"

    fun perGameReason(key: String, perGame: Int): String =
        "${labelOf(key)} is at the small-prop guard's limit of $perGame bets on one game (Auto-bet tab)"

    /** Each kind of player prop's part of [history], biggest first (the total counts every auto-bet, props or not). */
    fun shares(history: List<Placed>): List<Share> {
        val total = history.size
        return history.mapNotNull { it.key }.groupingBy { it }.eachCount().map { (k, n) -> Share(k, n, total) }.sortedWith(compareByDescending<Share> { it.count }.thenBy { it.key })
    }

    /** The share cap as the screens say it. */
    fun percent(v: Double): String = String.format(Locale.US, "%.2f", v * 100).trimEnd('0').trimEnd('.') + "%"

    fun shareLabel(v: Double): String = if (v <= 1e-9) "Off" else percent(v)

    fun perGameLabel(n: Int): String = if (n <= 0) "Off" else "$n"

    /** One line: what the guard does at [r] (for the Auto-bet tab, Diagnostics and the confirm). */
    fun summary(r: Rules): String = when {
        !r.on -> "off: no cap on how much one kind of player prop may take"
        else -> listOfNotNull(
            if (r.shareOn) "no kind of player prop over ${percent(r.share)} of the last 24 h's auto-bets (once there are ${r.minSample}; never under an even split of the kinds being bet)" else null,
            if (r.perGameOn) "at most ${r.perGame} auto-bets on one kind of prop in one game" else null,
        ).joinToString(" · ")
    }

    /** "NHL shots on goal 8 of 21 (38%), NFL receiving yards 3 (14%)": the biggest kinds of [history], for the screens and Diagnostics. */
    fun sharesLine(history: List<Placed>, top: Int = 3): String {
        if (history.isEmpty()) return "no auto-bets"
        val shown = shares(history).take(top)
        return if (shown.isEmpty()) "${history.size} auto-bets, none of them player props"
        else "${history.size} auto-bets: " + shown.joinToString(", ") { "${it.label} ${it.count} (${Math.round(it.share * 100)}%)" }
    }
}
