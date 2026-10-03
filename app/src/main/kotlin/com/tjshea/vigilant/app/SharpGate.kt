package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.alerts.EvAlert
import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.reference.SharpBooks
import com.tjshea.vigilant.data.scanner.SharpConfirm
import kotlinx.coroutines.CancellationException

/**
 * The sharp-book check for one CrazyNinjaOdds bet (Tj, 2026-10-02), as the auto-bet and the alerts both ask it: CNO's own game page first, free (a fresh
 * Pinnacle column there that says the bet is not +EV ends it without a feed call), then, unless CNO's page alone may confirm and does
 * ([SharpConfirm.Rules.viaCno]), a Pinnacle feed with the quote's own time ([SharpBooks]). RESEARCH.md §60.
 */
object SharpGate {

    suspend fun check(
        sharp: SharpBooks,
        rules: SharpConfirm.Rules,
        bet: SharpBooks.Bet,
        view: CnoBooksView?,
        novigOdds: Int,
        live: Boolean,
        now: Long,
    ): SharpConfirm.Result {
        val fromCno = SharpConfirm.fromCno(view, rules)
        SharpConfirm.preVeto(fromCno, novigOdds, live, rules, now)?.let { return it }
        if (rules.viaCno) {
            val r = SharpConfirm.judge(fromCno, novigOdds, live, rules, now)
            if (r.confirmed) return r
        }
        val answer = sharp.quotes(bet, rules)
        return SharpConfirm.judge(answer.quotes, novigOdds, live, rules, now, answer.unavailable)
    }

    /**
     * [alerts] less those whose bet the sharpest book for its kind says isn't +EV ([SharpVeto], Tj 2026-10-02 17:01Z: the veto, not a requirement). [items]: the
     * candidates the alerts came from ([AlertPicks.cnoChecked]); [view]: a candidate's book page; [minEv]: the veto's bar
     * ([com.tjshea.vigilant.data.scanner.ScanSettings.sharpVetoMinEv]). An alert with no candidate, or no sharp book on its page, stays. [onVerdict]: each
     * verdict, for the counters.
     */
    fun unvetoedAlerts(
        alerts: List<EvAlert>,
        items: List<AlertPicks.CnoChecked>,
        view: (AlertPicks.CnoChecked) -> CnoBooksView?,
        minEv: Double,
        onVerdict: (com.tjshea.vigilant.data.scanner.SharpVeto.Result) -> Unit = {},
    ): List<EvAlert> {
        val byKey = items.associateBy { MiniWindow.cnoKey(it.pick.row) }
        return alerts.filter { a ->
            val item = byKey[a.key] ?: return@filter true
            val row = item.shown.row
            val veto = com.tjshea.vigilant.data.scanner.SharpVeto.judge(view(item), row.league, row.market, row.bet, row.odds, item.pick.live, minEv)
            onVerdict(veto)
            !veto.vetoed
        }
    }

    /**
     * [alerts] (CNO's, already judged by the books on its game page) that a sharp book also confirms (Tj, 2026-10-02: the same check for the push alerts as
     * for the auto-bet). [items]: the candidates they came from ([AlertPicks.cnoChecked]); [unseen]: the alerts never sent (one already sent is not asked
     * again, only a bet about to alert costs a feed call). [check]: the sharp check for one candidate. An alert whose bet has no verdict, or whose check
     * threw, is dropped: nothing alerts on a bet that couldn't be proven.
     */
    suspend fun confirmedAlerts(
        alerts: List<EvAlert>,
        unseen: List<EvAlert>,
        items: List<AlertPicks.CnoChecked>,
        check: suspend (AlertPicks.CnoChecked) -> SharpConfirm.Result,
    ): List<EvAlert> {
        val byKey = items.filter { it.check.verdict == CnoBooks.Verdict.CONFIRMED }.associateBy { MiniWindow.cnoKey(it.pick.row) }
        val toCheck = unseen.map { it.key }.toSet()
        return alerts.filter { a ->
            if (a.key !in toCheck) return@filter true
            val item = byKey[a.key] ?: return@filter false
            val result = try {
                check(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@filter false
            }
            result.confirmed
        }
    }
}
