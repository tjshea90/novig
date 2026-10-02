package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.reference.SharpBooks
import com.tjshea.vigilant.data.scanner.SharpConfirm

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
}
