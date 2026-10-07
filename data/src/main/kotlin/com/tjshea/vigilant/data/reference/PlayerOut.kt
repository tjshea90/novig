package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.scanner.Opportunity

/**
 * Whether a bet or a bid is on a player who is not playing (Tj, 2026-10-07, a WNBA screenshot: "it says Allisha is out for the game ... yet the auto bid
 * feature offered bids on her"). Until then the injury reports ([InjuryBook]) only labelled cards; no bid, auto-bet or alert read them. Pure: every
 * path that offers, bids on or places a player bet asks here.
 *
 * Only a report that says he is certainly not playing counts ([InjuryLevel.RED]: out, injured reserve, injured list, suspended, inactive). A doubtful
 * or questionable player ([InjuryLevel.AMBER]) is not blocked, and neither is a player no report covers: an unknown is not a block (a bet on him can
 * still be taken, and the card carries whatever tag there is).
 */
object PlayerOut {

    /** The reason text for [injury] when it says he is not playing, else null. Stable per tag, so the reports count one reason a tag. */
    fun reasonOf(injury: Injury?): String? =
        injury?.takeIf { it.level == InjuryLevel.RED }?.let { "The player is out (${it.tag ?: it.status}): no bet or bid on a player who isn't playing" }

    /** [want]'s player's report in [book] at [now], as [reasonOf]. */
    fun reason(book: InjuryBook, want: InjuryTags.Want?, now: Long): String? =
        want?.let { reasonOf(book.find(it.sportKey, it.player, it.teams, now)) }

    /** [o] is a player prop on a player who is not playing. */
    fun forOpportunity(book: InjuryBook, o: Opportunity, now: Long): String? = reason(book, InjuryTags.wantOf(o), now)

    /** [r] is a CNO player bet on a player who is not playing ([espnTeam]: ESPN's team for the row when known). */
    fun forCnoRow(book: InjuryBook, r: CnoRow, now: Long, espnTeam: String? = null): String? = reason(book, InjuryTags.wantOf(r, espnTeam), now)

    /** An already-built tag ([UiState.injuries]'s value) that says he is not playing, as [reasonOf]. */
    fun forTag(injury: Injury?): String? = reasonOf(injury)
}
