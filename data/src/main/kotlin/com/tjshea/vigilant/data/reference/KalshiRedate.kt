package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.kalshi.KalshiTop

/**
 * Kalshi's fair-odds quotes kept current between scans (PX13b, Tj 2026-10-26: "The rest are slow, even kalshi which has its own socket"). A scan dates Kalshi's quotes at its own read, so a listed bet's
 * Kalshi price aged until the next scan read it again although the socket held that market the whole time. Each fair-odds quote is tagged ([tag]) with its market and the yes bid and ask it was priced
 * from; the engine's real-time list ([com.tjshea.vigilant.data.cloud.CloudSources.overlay]) re-dates it to now while the socket vouches for the market since the quote's stamp ([KalshiStreamView.trusted]:
 * live, subscribed before, no sequence gap since) and its top of book is still that bid and ask. A price that moved keeps its own age until the next scan reads it. Pure.
 */
object KalshiRedate {
    const val TAG = "kx|"

    /** What the socket says about one market. */
    interface StreamView {
        fun trusted(ticker: String, readAtMs: Long): Boolean
        fun top(ticker: String): KalshiTop?
    }

    fun tag(ticker: String, yesBid: String?, yesAsk: String?): String = "$TAG$ticker|${yesBid.orEmpty()}|${yesAsk.orEmpty()}"

    private data class Tagged(val ticker: String, val yesBid: Double?, val yesAsk: Double?)

    private fun parse(id: String?): Tagged? {
        if (id == null || !id.startsWith(TAG)) return null
        val p = id.substring(TAG.length).split('|')
        if (p.size != 3 || p[0].isEmpty()) return null
        return Tagged(p[0], p[1].toDoubleOrNull(), p[2].toDoubleOrNull())
    }

    private fun same(a: Double?, b: Double?) = (a == null && b == null) || (a != null && b != null && kotlin.math.abs(a - b) < 1e-6)

    /** [e] with every Kalshi quote the socket still vouches for dated [now]; the event itself when none changed. */
    fun overlay(e: RefEvent, now: Long, st: StreamView): RefEvent {
        var changed = false
        val markets = e.markets.map { m ->
            val stamp = m.lastUpdateMs
            if (m.bookKey != BOOK || stamp == null || stamp >= now) return@map m
            val tags = m.quotes.map { parse(it.feedOutcomeId) ?: return@map m }
            val current = tags.all { t ->
                st.trusted(t.ticker, stamp) && st.top(t.ticker).let { top -> top == null || top.atMs <= stamp || (same(top.yesBid, t.yesBid) && same(top.yesAsk, t.yesAsk)) }
            }
            if (!current) return@map m
            changed = true
            m.copy(lastUpdateMs = now)
        }
        if (changed) redated += 1
        return if (changed) e.copy(markets = markets) else e
    }

    /** Events re-dated, for the engine's status. */
    @Volatile var redated = 0L
        private set

    private const val BOOK = "kalshi"
}
