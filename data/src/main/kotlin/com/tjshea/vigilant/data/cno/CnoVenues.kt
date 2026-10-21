package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.data.kalshitrade.KalshiFees
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Which books the CNO scan lists (Tj, 2026-10-21, CK: "Add kalshi to the cno scanner. It should work just as it does now, but with both novig and kalshi. Also add toggles to the cno tab where I can
 * turn on and off novig and kalshi for the scan"). Kept inside [CnoFilters] (ScanSettings is at its size limit), so a toggle is a new read at once ([CnoFeed.matches]) and reaches the GitHub engine
 * with the rest of the CNO settings.
 */
@Serializable
data class CnoVenues(
    val novig: Boolean = true,
    val kalshi: Boolean = true,
) {
    /** CNO's `site_id` for each book that is on, Novig first. */
    val siteIds: List<String> get() = buildList {
        if (novig) add(CnoView.NOVIG_SITE_ID)
        if (kalshi) add(CnoView.KALSHI_SITE_ID)
    }

    /** Both books listed: the cards are coloured by venue as on the +EV tab. */
    val both: Boolean get() = novig && kalshi
}

/**
 * The CNO scan over both books: CNO's +EV page is read once per book that is on ([CnoVenues]), the same view and filters with the link's `site_id` set to that book, each through its own
 * [CnoSource] (so each keeps its own page session: one postback a refresh, as before), and the two lists are merged, best EV first.
 *
 * A Kalshi row's EV is re-stated net of Kalshi's taker fee (0.07 x P x (1 - P) a contract, always charged, [KalshiFees]): CNO does not say whether its EV nets an exchange's fee, so the app takes
 * the fee off itself (the cautious reading) and drops a row the fee takes under the EV floor. A Novig row is untouched: it is the list exactly as before.
 * One book failing does not empty the list: the other's rows are kept and the snapshot's [CnoSnapshot.note] says which book could not be read; only when every book fails is the read an error.
 */
class CnoVenueSource(
    private val novig: CnoSource,
    private val kalshi: CnoSource,
) : CnoSource {

    override suspend fun fetch(url: String, filters: CnoFilters): CnoSnapshot = merged(url, filters) { src, u -> src.fetch(u, filters) }

    override suspend fun fetchWide(url: String, filters: CnoFilters, rows: Int): CnoSnapshot? = merged(url, filters, optional = true) { src, u -> src.fetchWide(u, filters, rows) }

    override suspend fun books(row: CnoRow): CnoBooksView? = sourceFor(row).books(row)

    override suspend fun booksBulk(row: CnoRow): CnoBooksView? = sourceFor(row).booksBulk(row)

    override suspend fun novigLink(row: CnoRow): String? = if (isKalshi(row)) null else novig.novigLink(row)

    private fun sourceFor(row: CnoRow): CnoSource = if (isKalshi(row)) kalshi else novig

    private suspend fun merged(url: String, filters: CnoFilters, optional: Boolean = false, read: suspend (CnoSource, String) -> CnoSnapshot?): CnoSnapshot {
        val ids = filters.venues.siteIds
        if (ids.isEmpty()) return CnoSnapshot(url, emptyList(), System.currentTimeMillis(), filters = filters, note = OFF_NOTE)
        val got = mutableListOf<CnoSnapshot>()
        val failed = mutableListOf<Pair<String, Exception>>()
        for (id in ids) {
            val src = if (id == CnoView.KALSHI_SITE_ID) kalshi else novig
            try {
                read(src, CnoView.forSite(url, id))?.let { got += if (id == CnoView.KALSHI_SITE_ID) netOfKalshiFee(it, filters) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += id to e
            }
        }
        if (got.isEmpty()) {
            if (failed.isEmpty() && optional) return CnoSnapshot(url, emptyList(), System.currentTimeMillis(), filters = filters)
            val (id, e) = failed.firstOrNull() ?: return CnoSnapshot(url, emptyList(), System.currentTimeMillis(), filters = filters)
            throw if (failed.size == 1 && ids.size == 1) e else CnoException(
                failed.joinToString("; ") { (i, x) -> "${CnoView.bookName(i)}: ${x.message ?: x.javaClass.simpleName}" },
                retryAfterSeconds = (e as? CnoException)?.retryAfterSeconds,
                cause = e,
            )
        }
        val first = got.first()
        val notes = got.mapNotNull { it.note } + failed.map { (i, x) -> "${CnoView.bookName(i)} could not be read: ${x.message ?: x.javaClass.simpleName}" }
        return first.copy(
            url = url,
            rows = got.flatMap { it.rows }.sortedByDescending { it.ev },
            fetchedAtMs = got.minOf { it.fetchedAtMs },
            // The oldest of the books' "Last Updated": the list is as old as its oldest part.
            cnoAgeSeconds = got.mapNotNull { it.cnoAgeSeconds }.maxOrNull(),
            note = notes.distinct().joinToString(" · ").ifBlank { null },
            filters = filters,
            limit = got.mapNotNull { it.limit }.maxOrNull(),
        )
    }

    companion object {
        const val OFF_NOTE = "Novig and Kalshi are both off for the CNO scan (switch one on at the top of the CNO tab)"

        fun isKalshi(row: CnoRow): Boolean = CnoBooks.codeFor(row.book) == "KI" || row.book.equals("Kalshi", ignoreCase = true)

        /** [row]'s EV net of Kalshi's taker fee: the fair probability (CNO's, else implied by its EV) over the price plus the fee a contract. */
        fun kalshiNetEv(row: CnoRow): Double {
            val p = com.tjshea.vigilant.engine.Odds.americanToProbability(row.odds)
            if (p <= 0.0 || p >= 1.0) return row.ev
            val fair = row.fairProbability ?: ((1.0 + row.ev) * p)
            return fair / (p + KalshiFees.takerPerContract(p)) - 1.0
        }

        /** The Kalshi read's rows (every one is at Kalshi: the read is scoped to its `site_id`) re-stated net of the fee, and a row the fee takes under [filters]' EV floor dropped. */
        fun netOfKalshiFee(s: CnoSnapshot, filters: CnoFilters): CnoSnapshot =
            s.copy(rows = s.rows.map { r -> r.copy(ev = kalshiNetEv(r)) }.filter { s.wide || it.ev >= filters.minEv - 1e-9 })
    }
}

/** The link for one book: [url] with every `site_id` replaced by [siteId]. */
fun CnoView.forSite(url: String, siteId: String): String {
    val u = url.toHttpUrlOrNull() ?: return url
    return u.newBuilder().removeAllQueryParameters("site_id").addQueryParameter("site_id", siteId).build().toString()
}
