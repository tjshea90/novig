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
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.CnoSnapshot
import com.tjshea.vigilant.data.scanner.MiniSource
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
    )

    /** Whether the mini window lists CNO's rows for these settings. */
    fun showsCno(settings: ScanSettings): Boolean = settings.cnoEnabled && settings.miniSource != MiniSource.VIGILANT

    fun showsVigilant(settings: ScanSettings): Boolean = settings.miniSource != MiniSource.CNO || !settings.cnoEnabled

    /**
     * What the mini window lists: Vigilant's feed (in the feed's order), CNO's rows (CNO's
     * order), or both merged best EV first. [now] marks old prices.
     */
    fun items(state: UiState, now: Long): List<Item> {
        val s = state.settings
        val ours = if (showsVigilant(s)) state.feed.mapNotNull { it.miniItem(now) } else emptyList()
        val snap = state.cno.snapshot?.takeIf { showsCno(s) && it.url == state.cnoUrl }
        val theirs = snap?.rows?.map { it.miniItem(snap, now) } ?: emptyList()
        return when {
            theirs.isEmpty() -> ours
            ours.isEmpty() -> theirs
            else -> (ours + theirs).sortedByDescending { it.ev }
        }
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
        )
    }

    private fun CnoRow.miniItem(snap: CnoSnapshot, now: Long) = Item(
        key = "cno:$key",
        ev = ev,
        title = bet,
        subtitle = "$market · $event" + if (book != "Novig") " · $book" else "",
        price = american(odds),
        available = available?.let { "$" + it.roundToInt() },
        old = now - snap.dataAtMs > CNO_OLD_MS,
        fromCno = true,
    )

    fun american(odds: Int): String = com.tjshea.vigilant.engine.Odds.formatAmerican(odds)

    /** Whether leaving Vigilant should shrink it to the mini window. [rows] is what it would list. */
    fun shouldAutoEnter(settings: ScanSettings, status: ScanStatus, rows: Int): Boolean =
        settings.miniWindow && (status.scanning || status.rechecking || rows > 0)

    /**
     * The rows of the feed on page [next] (wrapping), when [fit] rows fit the window. Empty for an
     * empty feed. "Next" just counts taps, so a window resized mid-way still pages sensibly.
     */
    fun page(size: Int, fit: Int, next: Int): IntRange {
        if (size <= 0) return IntRange.EMPTY
        val per = fit.coerceAtLeast(1)
        val pages = (size + per - 1) / per
        val p = ((next % pages) + pages) % pages
        val start = p * per
        return start until minOf(size, start + per)
    }

    fun supported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /**
     * [cnoOnly]: the window lists only CNO's rows, so its buttons are Refresh and Next (a scan
     * or recheck wouldn't change what it shows).
     */
    fun params(context: Context, autoEnter: Boolean, scanning: Boolean, cnoOnly: Boolean = false): PictureInPictureParams {
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
                        action(REFRESH, R.drawable.ic_recheck, "Refresh"),
                        action(NEXT, R.drawable.ic_next, "Next"),
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
