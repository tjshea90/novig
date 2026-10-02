package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.SharpVeto
import com.tjshea.vigilant.data.tracker.AtBet
import com.tjshea.vigilant.data.tracker.AtBets

/**
 * The bet as placed ([AtBet], Tj 2026-10-02 17:01Z: "record all types of information on the bet as placed"), from what the app has now: the row, its book
 * page, Novig's live price, CNO's list, the settings and the wallet.
 */
object BetRecord {

    fun cno(
        state: UiState,
        row: CnoRow,
        live: Boolean,
        how: String,
        scanner: String,
        now: Long,
        stake: Double? = null,
        check: CnoBooks.Check? = null,
        veto: SharpVeto.Result? = null,
        sharpConfirm: String? = null,
    ): AtBet = AtBets.cno(
        row, live, state.booksAt(row.key, now)?.view, check, state.livePrice(row, now), state.cno.snapshot?.fetchedAtMs, state.settings, now, how, scanner,
        BuildConfig.VERSION_NAME, stake = stake, wallet = state.betting.balance ?: state.autoBetStatus.balance, veto = veto, sharpConfirm = sharpConfirm,
    )

    fun opportunity(state: UiState, o: Opportunity, how: String, now: Long, stake: Double? = null): AtBet =
        AtBets.opportunity(o, state.settings, now, how, BuildConfig.VERSION_NAME, stake = stake, wallet = state.betting.balance)
}
