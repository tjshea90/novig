package com.tjshea.vigilant.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tjshea.vigilant.data.book.BetMgmLinks
import com.tjshea.vigilant.data.book.BookRef
import com.tjshea.vigilant.data.book.Sportsbook

/**
 * The book this build prices (Tj, 2026-09-27): Novig for Vigilant (`app`), BetMGM for Vigilant MGM
 * (`mgm`, built from these same sources with `BuildConfig.BOOK = "betmgm"`). Everything the user reads
 * that names the book, and every Novig-only part (order books, maker bids, the Novig key, Novig's live
 * prices and catalog), goes through here, so the Novig build reads and behaves exactly as before.
 */
object AppBook {
    /** Fixed by the build; tests may switch it to render the other app's screens. */
    @Volatile
    var current: Sportsbook = Sportsbook.of(BuildConfig.BOOK)

    val name: String get() = current.displayName

    val isNovig: Boolean get() = current == Sportsbook.NOVIG

    /** An exchange (Novig): order books, depth, maker bids, taker fees. A sportsbook has none of them. */
    val exchange: Boolean get() = current.exchange

    /** The book's site when there's no bet to open. */
    val home: String get() = if (isNovig) "https://novig.com" else BetMgmLinks.HOME

    /**
     * The bet slip for one of this app's own bets: Novig's outcome link, or BetMGM's link from the
     * feed's ids and the state Tj picked ([com.tjshea.vigilant.data.scanner.ScanSettings.bookState]).
     */
    fun betLink(outcomeId: String?, ref: BookRef?, state: String): String? =
        if (isNovig) outcomeId?.let { "novigapp://events/$it" } else BetMgmLinks.link(ref, state).url

    /** The book's app when it's installed (Novig), else its site. */
    fun homeIntent(context: Context): Intent =
        if (isNovig) MiniWindow.novigIntent(context)
        else Intent(Intent.ACTION_VIEW, Uri.parse(home)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
