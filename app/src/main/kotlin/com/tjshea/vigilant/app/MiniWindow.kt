package com.tjshea.vigilant.app

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.util.Rational
import com.tjshea.vigilant.data.cno.CnoPick
import com.tjshea.vigilant.data.cno.CnoSnapshot
import com.tjshea.vigilant.data.match.Picks
import com.tjshea.vigilant.data.tracker.PlacedBet
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlin.math.roundToInt

/**
 * The mini window: Vigilant shrunk to a floating picture-in-picture window, so the scan and the
 * top +EV bets stay in view while Tj bets in Novig (his request, 2026-09-26).
 *
 * Picture-in-picture needs no special permission (a draggable overlay would need "display over
 * other apps"), Android keeps it above whatever app is open, and the system handles moving,
 * resizing (pinch, or double-tap for the large size) and closing it. Its limit is that it takes no
 * touches: scrolling and tapping a bet mean expanding back to the full app. Instead it has three
 * buttons, shown when tapped once: Scan, Recheck and Next (the next page of bets).
 *
 * It opens by itself when Tj leaves Vigilant while a scan runs or bets are on the feed (Android 12+
 * "auto-enter"; the Settings switch turns that off), from the mini-window button on the feed, and
 * when "Open Novig" is tapped on a bet.
 *
 * Since v0.13.0 it also lists CrazyNinjaOdds' +EV rows (Tj, 2026-09-26; RESEARCH.md §18), mixed
 * with Vigilant's by EV and tagged "CNO", or either list alone (Settings). CNO's list keeps
 * itself current while the window is up; showing CNO alone swaps Scan/Recheck for Refresh.
 */
object MiniWindow {

    const val ACTION = "com.tjshea.vigilant.MINI_WINDOW"
    const val EXTRA_BUTTON = "button"
    const val SCAN = 1
    const val RECHECK = 2
    const val NEXT = 3
    const val REFRESH = 4
    const val BOOKS = 5
    /** Up / down a page (CNO's list; in the Books view, the bet above or below). */
    const val UP = 6
    const val DOWN = 7

    /** A CNO row whose odds are older than this (CNO's own age included) shows in the warning color. */
    const val CNO_OLD_MS = 5 * 60_000L

    /** Novig's Android app (Google Play `us.novig.app`). */
    const val NOVIG_PACKAGE = "us.novig.app"

    /** Wider than tall: bets read as one line of text each, so width matters more than height. */
    val ASPECT = Rational(3, 2)

    /** One line in the mini window, from Vigilant's scan or CrazyNinjaOdds' list. */
    data class Item(
        val key: String,
        val ev: Double,
        val title: String,
        val subtitle: String,
        /** American odds, as it should read. */
        val price: String,
        /** Dollars available at that price, when known ("$109"). */
        val available: String? = null,
        /** The price may have moved: recheck (Vigilant) or refresh (CNO) before betting. */
        val old: Boolean = false,
        val fromCno: Boolean = false,
        /** The CNO bet behind a CNO row (its books are what the Books view shows). */
        val cno: CnoPick? = null,
        /** The game has started (Novig's taker fee is already taken out of [ev]). */
        val live: Boolean = false,
        val event: String = "",
        val market: String = "",
        val startsAtMs: Long? = null,
        /** Novig's outcome id, for Vigilant's own bets (opens Novig's bet slip on it). */
        val outcomeId: String? = null,
        /** The player's team ("HOU"), for player bets whose team is known. */
        val team: String? = null,
        /** The green check: several books agree it's +EV ([com.tjshea.vigilant.data.cno.CnoBooks.agrees]). */
        val agrees: Boolean = false,
        /** Tj placed this bet at another line: that line, short ("O5.5"). */
        val placedOther: String? = null,
        /** CNO lists this Vigilant bet too (the same Novig outcome): CNO's EV for it. */
        val alsoCnoEv: Double? = null,
        /** The same bet's key in CNO's list, so marking it placed or removed covers both. */
        val aliases: List<String> = emptyList(),
    ) {
        /** The same bet at any line, in this game and market. */
        val family: String get() = Picks.familyKey(event, market, title)
    }

    /** The key a CNO bet has in the widget (and in placed.json). */
    fun cnoKey(row: com.tjshea.vigilant.data.cno.CnoRow): String = "cno:${row.key}"

    /** A widget bet as a placed-bet record ([hidden]: removed with ✕, not bet). */
    fun placed(item: Item, now: Long, hidden: Boolean = false): PlacedBet = PlacedBet(
        key = item.key,
        aliases = item.aliases,
        title = item.title,
        detail = item.subtitle,
        family = item.family,
        odds = item.price,
        placedAtMs = now,
        startsAtMs = item.startsAtMs,
        hidden = hidden,
    )

    /**
     * Novig's app on a Vigilant bet: its outcome in Novig's bet slip (Novig's own link format,
     * RESEARCH.md §20). CNO bets get theirs from CNO or Novig's catalog ([MainViewModel.betLink]).
     */
    fun novigLink(item: Item): String? = item.outcomeId?.let { "novigapp://events/$it" }

    /** Whether the mini window lists CNO's rows (both scanners, or CNO only). */
    fun showsCno(settings: ScanSettings): Boolean = settings.cnoOn

    /** Whether it lists Vigilant's own (both scanners, or Vigilant only). */
    fun showsVigilant(settings: ScanSettings): Boolean = settings.vigilantOn

    /**
     * What the mini window lists: Vigilant's feed (in the feed's order), CNO's rows (CNO's
     * order), or both merged best EV first. [now] marks old prices.
     */
    fun items(state: UiState, now: Long): List<Item> {
        val s = state.settings
        val ours = if (showsVigilant(s)) state.feed.mapNotNull { it.miniItem(now) } else emptyList()
        val snap = state.cno.snapshot
        // CNO's rows only after the app's own checks (thin markets, odds cap, one-way devigs, …),
        // placed and removed ones left out, and only green-check ones when that setting is on.
        val theirs = if (snap == null) emptyList() else state.cnoShown(now).map { itemFor(it, snap, state, now) }
        val all = when {
            theirs.isEmpty() -> ours
            ours.isEmpty() -> theirs
            else -> merge(ours, theirs, state, now)
        }
        // Placed bets are gone for good (Tj: "so the bet doesn't come back up after a refresh");
        // the same bet at another line says so.
        if (state.placed.isEmpty()) return all
        val placed = state.placedKeys
        val families = state.placedFamilies
        return all.filter { it.key !in placed && it.aliases.none { k -> k in placed } }.map { item ->
            families[item.family]?.let { p -> item.copy(placedOther = Picks.shortLine(p.title)) } ?: item
        }
    }

    /**
     * Both lists, best EV first, and a bet both scanners list (the same Novig outcome: CNO's link,
     * read ahead of time, names it) shown once: Vigilant's row (its own live Novig price and fair
     * value, tap opens that outcome) tagged with CNO's EV, with CNO's books, ✓ and team (Tj,
     * 2026-09-27: "put all the results in the widget together"). Every Vigilant bet also carries
     * the key of CNO's copy, listed or not, so marking either one placed hides both.
     */
    private fun merge(ours: List<Item>, theirs: List<Item>, state: UiState, now: Long): List<Item> {
        fun outcomeOf(row: com.tjshea.vigilant.data.cno.CnoRow): String? =
            com.tjshea.vigilant.data.cno.CnoFeed.outcomeIdOf(state.cnoLinks[com.tjshea.vigilant.data.cno.CnoFeed.linkKey(row)])
        // CNO's key for each outcome it lists, placed, removed or held back included.
        val cnoKeyOf = state.cnoPicks(now)?.picks.orEmpty().mapNotNull { p -> outcomeOf(p.row)?.let { it to cnoKey(p.row) } }.toMap()
        val shownByOutcome = theirs.mapNotNull { t -> t.cno?.row?.let(::outcomeOf)?.let { it to t } }.toMap()
        val used = HashSet<String>()
        val mine = ours.map { o ->
            val oid = o.outcomeId ?: return@map o
            val alias = cnoKeyOf[oid]?.let { listOf(it) }.orEmpty()
            val t = shownByOutcome[oid]
            if (t == null || !used.add(t.key)) o.copy(aliases = alias)
            else o.copy(cno = t.cno, alsoCnoEv = t.ev, agrees = t.agrees, team = o.team ?: t.team, aliases = alias)
        }
        return (mine + theirs.filter { it.key !in used }).sortedByDescending { it.ev }
    }

    /** One CNO bet as the widget (and the CNO tab's placed button) sees it. */
    fun itemFor(pick: CnoPick, snap: CnoSnapshot, state: UiState, now: Long): Item {
        val row = pick.row
        return pick.miniItem(snap, now).copy(team = state.teams[row.key], agrees = state.cnoAgrees(pick))
    }

    private fun Opportunity.miniItem(now: Long): Item? {
        val q = quote ?: return null
        return Item(
            key = key,
            ev = q.evPercent,
            title = selection,
            subtitle = "$marketLabel · $eventName",
            price = com.tjshea.vigilant.app.ui.Format.american(q.cost),
            available = depth?.takeIf { it.contracts > 0 }?.let { "$" + it.dollarCost.roundToInt() },
            old = priceIsOld(now),
            event = eventName,
            market = marketLabel,
            startsAtMs = event.startsTs,
            outcomeId = outcome.outcomeId,
        )
    }

    private fun CnoPick.miniItem(snap: CnoSnapshot, now: Long) = Item(
        key = cnoKey(row),
        ev = ev,
        title = row.bet,
        subtitle = "${row.market} · ${row.event}" + if (row.book != "Novig") " · ${row.book}" else "",
        price = american(row.odds),
        available = row.available?.let { "$" + it.roundToInt() },
        old = now - snap.dataAtMs > CNO_OLD_MS,
        fromCno = true,
        cno = this,
        live = live,
        event = row.event,
        market = row.market,
        startsAtMs = row.startsAtMs,
    )

    fun american(odds: Int): String = com.tjshea.vigilant.engine.Odds.formatAmerican(odds)

    /**
     * A pick split into who and which side ("Justin Jefferson" + "Under 69.5", "Ohio" + "-33.5",
     * "" + "Under 8.5"), so a narrow window shortens the name and never the line Tj bets on.
     * Picks without a line ("Dallas Cowboys") come back whole.
     */
    fun splitPick(title: String): Pair<String, String?> = Picks.split(title)

    /**
     * Shorter names for a narrow window, longest first: the full name, then for a player an
     * initial and surname ("J. Jefferson", "A. St. Brown") and the surname alone ("Jefferson",
     * "St. Brown"); for a team its last word ("Cowboys"). The window uses the first that fits.
     */
    fun nameChoices(name: String, player: Boolean): List<String> {
        val full = name.trim()
        val words = full.split(Regex("\\s+"))
        if (words.size < 2) return listOf(full)
        val surname = words.drop(1).joinToString(" ")
        return if (player) listOf(full, "${words.first().first()}. $surname", surname).distinct() else listOf(full, words.last())
    }

    /** Whether leaving Vigilant should shrink it to the mini window. [rows] is what it would list. */
    fun shouldAutoEnter(settings: ScanSettings, status: ScanStatus, rows: Int): Boolean =
        settings.miniWindow && (status.scanning || status.rechecking || rows > 0)

    /**
     * The rows of the feed on page [next] when [fit] rows fit the window. Empty for an empty feed.
     * [wrap] (Next): past the last page comes the first. Otherwise (Up / Down) it stops at either
     * end. The page just counts taps, so a window resized mid-way still pages sensibly.
     */
    fun page(size: Int, fit: Int, next: Int, wrap: Boolean = true): IntRange {
        if (size <= 0) return IntRange.EMPTY
        val per = fit.coerceAtLeast(1)
        val pages = (size + per - 1) / per
        val p = if (wrap) ((next % pages) + pages) % pages else next.coerceIn(0, pages - 1)
        val start = p * per
        return start until minOf(size, start + per)
    }

    /** How many pages [size] rows make at [fit] a page. */
    fun pages(size: Int, fit: Int): Int = if (size <= 0) 0 else (size + fit.coerceAtLeast(1) - 1) / fit.coerceAtLeast(1)

    fun supported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /**
     * [cnoOnly]: the window lists only CNO's rows, so its buttons are Up and Down (Tj, 2026-09-26:
     * up and down instead of Next) and, as the third, Books (every book's odds for the bet at the
     * top, or back to the list when [books] is showing), or Refresh when CNO is read only on a
     * tap ([tapsOnly]). Picture-in-picture shows at most three buttons, and only once tapped:
     * the floating widget ([FloatingWidget]) is the one with buttons always showing.
     */
    fun params(context: Context, autoEnter: Boolean, scanning: Boolean, cnoOnly: Boolean = false, books: Boolean = false, tapsOnly: Boolean = false): PictureInPictureParams {
        fun action(id: Int, icon: Int, title: String, enabled: Boolean = true): RemoteAction {
            val intent = PendingIntent.getBroadcast(
                context, id,
                Intent(ACTION).setPackage(context.packageName).putExtra(EXTRA_BUTTON, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return RemoteAction(Icon.createWithResource(context, icon), title, title, intent).apply { isEnabled = enabled }
        }
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(ASPECT)
            .setActions(
                if (cnoOnly) {
                    listOf(
                        if (tapsOnly && !books) action(REFRESH, R.drawable.ic_scan, "Refresh")
                        else action(BOOKS, R.drawable.ic_books, if (books) "List" else "Books"),
                        action(UP, R.drawable.ic_up, "Up"),
                        action(DOWN, R.drawable.ic_down, "Down"),
                    )
                } else {
                    listOf(
                        action(SCAN, R.drawable.ic_scan, "Scan", enabled = !scanning),
                        action(RECHECK, R.drawable.ic_recheck, "Recheck", enabled = !scanning),
                        action(NEXT, R.drawable.ic_next, "Next"),
                    )
                },
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter).setSeamlessResizeEnabled(false)
        }
        return builder.build()
    }

    /** Novig's app if it's installed, else its website. */
    fun novigIntent(context: Context): Intent =
        (context.packageManager.getLaunchIntentForPackage(NOVIG_PACKAGE) ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://novig.com")))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
