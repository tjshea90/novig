package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.data.HttpText
import com.tjshea.vigilant.data.awaitText
import com.tjshea.vigilant.engine.Odds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Reads CNO: the +EV list for a view, a bet's books, a bet's Novig link. [CnoClient] in the app; fakes in tests. */
interface CnoSource {
    suspend fun fetch(url: String, filters: CnoFilters): CnoSnapshot

    /**
     * The scan study's wide read (Tj, 2026-10-03: "log all cno finds on every scan … even if these bets don't meet my criteria"): [url]'s view read in a
     * session of its own with CNO's numeric filters opened right up (no EV floor, no odds cap, any book count, one-sided markets, no complete-book rule,
     * up to [rows] rows), every column kept ([CnoRow.cols]). [filters] are Tj's: the devig method is posted as his (so EV means the same), and the snapshot
     * carries them for the app's own screen to judge each row. Null where a source can't (fakes); it never touches [fetch]'s session.
     */
    suspend fun fetchWide(url: String, filters: CnoFilters, rows: Int = WIDE_ROWS): CnoSnapshot? = null

    /** Every book's price for [row] (its CNO game page). */
    suspend fun books(row: CnoRow): CnoBooksView? = null

    /**
     * [books] for a bet read alongside many others (the Tracker's "Check odds now" reads every open bet, several at a time): the
     * same read at a brisker pace. Fakes answer as [books] does.
     */
    suspend fun booksBulk(row: CnoRow): CnoBooksView? = books(row)

    /** The Novig app link for [row]'s game (`novigapp://events/<id>/cno`), from CNO's deeplink. */
    suspend fun novigLink(row: CnoRow): String? = null

    companion object {
        /** Rows the scan study's wide read asks for first ([CnoFeed.WIDE_ROW_STEPS] steps down if CNO won't send that many). */
        const val WIDE_ROWS = 1000
    }
}

/**
 * Reads Tj's CrazyNinjaOdds view the way the page itself loads (Tj, 2026-09-26: "whatever the
 * best way is"; RESEARCH.md §18–19). The first read is the page (GET) plus the postback its
 * loader timer makes; after that one postback per refresh, the page's own Refresh button, with
 * the state the last reply handed back. So a refresh is one request as long as the session lasts
 * (ASP.NET's are ~20 minutes); anything odd falls back to a fresh page load once.
 *
 * Every postback carries the scanner's [CnoFilters] in CNO's own form fields (devig method,
 * longest odds, fewest books, …): CNO honors them, whatever the Shared View link said.
 *
 * [fetch] is not thread-safe ([CnoFeed] calls it one read at a time); [books] and [novigLink]
 * keep no shared state.
 */
class CnoClient(
    private val http: OkHttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
    /** One pace for all of CNO's requests, the list's, the books' and the links' alike. */
    private val pace: CnoPace = CnoPace(),
    /**
     * A brisker pace for reading many bets' books at once ([booksBulk]): a page is two requests, so at the shared one-second pace
     * a hundred open bets took over three minutes (Tj, 2026-09-29: "it scanned very slow"). Still never a burst: two requests a second.
     */
    private val bulkPace: CnoPace = CnoPace(BULK_GAP_MS),
    /**
     * Where replies are parsed: never the caller's thread, which is the main one (the list's
     * page is hundreds of KB of HTML every few seconds; parsed there, the widget stuttered).
     */
    private val work: kotlin.coroutines.CoroutineContext = Dispatchers.Default,
) : CnoSource {

    private class Session(
        val view: String,
        var postUrl: HttpUrl,
        val cookies: MutableMap<String, String>,
        val form: CnoPage.Form,
        val fields: LinkedHashMap<String, String>,
        var usedAtMs: Long,
    ) {
        /** The last list read of this session asked for more rows than the list's limit (the app filters what CNO's form can't): if CNO refuses it, the next asks for the limit again. */
        var widened: Boolean = false
    }

    private var session: Session? = null

    /** When CNO last refused a widened read ([CnoScope.WIDE_ROWS] rows): until [WIDEN_RETRY_MS] after it, the list asks for its own row limit. */
    private var widenRefusedAtMs: Long = 0L

    /** The wide read's own session (its own cookies, form and fields): the list's values are never changed by it. Guarded by [CnoFeed]'s wide mutex. */
    private var wideSession: Session? = null

    override suspend fun fetch(url: String, filters: CnoFilters): CnoSnapshot = withContext(work) { fetchHere(url, filters) }

    override suspend fun fetchWide(url: String, filters: CnoFilters, rows: Int): CnoSnapshot = withContext(work) { fetchWideHere(url, filters, rows) }

    private suspend fun fetchWideHere(url: String, filters: CnoFilters, rows: Int): CnoSnapshot {
        val reuse = wideSession?.takeIf { it.view == url && clock() - it.usedAtMs < SESSION_MS && it.form.button != null }
        if (reuse != null) {
            try {
                return list(reuse, useTimer = false, filters, wideRows = rows)
            } catch (e: IOException) {
                wideSession = null
                throw unreachable(e)
            } catch (e: CnoException) {
                if (e.retryAfterSeconds != null) throw e
                wideSession = null
            }
        }
        return try {
            list(open(url), useTimer = true, filters, wideRows = rows)
        } catch (e: IOException) {
            wideSession = null
            throw unreachable(e)
        } catch (e: CnoException) {
            wideSession = null
            throw e
        }
    }

    private suspend fun fetchHere(url: String, filters: CnoFilters): CnoSnapshot {
        val reuse = session?.takeIf { it.view == url && clock() - it.usedAtMs < SESSION_MS && it.form.button != null }
        if (reuse != null) {
            try {
                return list(reuse, useTimer = false, filters)
            } catch (e: IOException) {
                session = null
                throw unreachable(e)
            } catch (e: CnoException) {
                // A lapsed session or a hiccup: start over with a fresh page load, once.
                if (e.retryAfterSeconds != null) throw e
                session = null
            }
        }
        return try {
            list(open(url), useTimer = true, filters)
        } catch (e: IOException) {
            session = null
            throw unreachable(e)
        } catch (e: CnoException) {
            session = null
            throw e
        }
    }

    override suspend fun books(row: CnoRow): CnoBooksView? = withContext(work) { booksHere(row, pace) }

    override suspend fun booksBulk(row: CnoRow): CnoBooksView? = withContext(work) { booksHere(row, bulkPace) }

    private suspend fun booksHere(row: CnoRow, paceWith: CnoPace): CnoBooksView? {
        val url = row.gameUrl ?: return null
        val records = try {
            postback(open(url, paceWith), useTimer = true, paceWith)
        } catch (e: IOException) {
            throw unreachable(e)
        }
        val grid = records.grid()
        // The game page's own "Last Updated", when it carries one (a sharp-book confirmation judges CNO's Pinnacle column by it).
        val age = records.firstOrNull { it.type == "updatePanel" && it.id.endsWith("UpdatePanelServerInfo") }?.let { CnoPage.lastUpdatedSeconds(it.content) }
        return CnoBooks.parse(grid, row.sideId, row.bet, clock())?.copy(cnoAgeSeconds = age)
    }

    override suspend fun novigLink(row: CnoRow): String? = withContext(work) { novigLinkHere(row) }

    private suspend fun novigLinkHere(row: CnoRow): String? {
        val url = row.betUrl ?: return null
        // CNO's deeplink page asks for consent once and remembers it in this cookie.
        val request = Request.Builder().url(url).get()
            .header("User-Agent", USER_AGENT)
            .header("Cookie", "BetaDeepLinkIntro=Read=1")
            .build()
        val html = try {
            call(request).also(::check).body
        } catch (e: IOException) {
            throw unreachable(e)
        }
        val target = Regex("""location\.(?:replace|href)\s*\(?\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1) ?: return null
        return target.takeIf { it.startsWith("novigapp://") || it.startsWith("https://") }
    }

    /** Loads a page: its form and cookies. */
    private suspend fun open(url: String, paceWith: CnoPace = pace): Session {
        val pageUrl = url.toHttpUrl()
        val request = Request.Builder().url(pageUrl).get().header("User-Agent", USER_AGENT).build()
        val cookies = LinkedHashMap<String, String>()
        val html = call(request, paceWith).also { check(it); keepCookies(it, cookies) }.body
        val form = CnoPage.form(html)
        val postUrl = form.action?.let { pageUrl.resolve(it) } ?: pageUrl
        return Session(url, postUrl, cookies, form, LinkedHashMap(form.fields.toMap()), clock())
    }

    /** One +EV list read: the table and CNO's "last updated", plus the state for the next read. [wideRows] set: the study's wide read ([fetchWide]) in [wideSession]. */
    private suspend fun list(s: Session, useTimer: Boolean, filters: CnoFilters, wideRows: Int? = null): CnoSnapshot {
        val asked = if (wideRows != null) applyWide(s, filters, wideRows) else { applyFilters(s, filters); null }
        val records = try {
            postback(s, useTimer)
        } catch (e: CnoException) {
            // A longer read CNO answered with an error (or an unreadable page): the next read, on a fresh page, asks for the usual row limit.
            if (wideRows == null && s.widened && e.retryAfterSeconds == null) widenRefusedAtMs = clock()
            throw e
        }
        val grid = records.grid()
        val info = records.firstOrNull { it.type == "updatePanel" && it.id.endsWith("UpdatePanelServerInfo") }
        if (wideRows != null) wideSession = s else session = s
        val table = CnoPage.table(grid, s.view.toHttpUrl(), keepColumns = wideRows != null)
        return CnoSnapshot(
            url = s.view,
            rows = table.rows,
            fetchedAtMs = clock(),
            cnoAgeSeconds = info?.let { CnoPage.lastUpdatedSeconds(it.content) },
            evLabel = table.evLabel,
            note = table.note,
            filters = filters,
            wide = wideRows != null,
            asked = asked,
            limit = wideRows,
        )
    }

    /** One postback, as the page's loader timer or its Refresh button would make it. */
    private suspend fun postback(s: Session, useTimer: Boolean, paceWith: CnoPace = pace): List<CnoPage.Record> {
        val form = s.form
        val timer = form.timer
        val body = FormBody.Builder()
        if ((useTimer || form.button == null) && timer != null) {
            body.add(form.scriptManager, "${form.gridPanel}|$timer")
            s.fields.forEach { (k, v) ->
                body.add(k, when (k) { "__EVENTTARGET" -> timer; "__EVENTARGUMENT" -> ""; else -> v })
            }
            if ("__EVENTTARGET" !in s.fields) body.add("__EVENTTARGET", timer)
        } else {
            val (name, value) = form.button ?: throw CnoException("CrazyNinjaOdds' page has no Refresh button")
            body.add(form.scriptManager, "${form.gridPanel}|$name")
            s.fields.forEach { (k, v) -> body.add(k, if (k == "__EVENTTARGET" || k == "__EVENTARGUMENT") "" else v) }
            body.add(name, value)
        }
        body.add("__ASYNCPOST", "true")
        val request = Request.Builder().url(s.postUrl).post(body.build())
            .header("User-Agent", USER_AGENT)
            .header("X-MicrosoftAjax", "Delta=true")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", s.view)
            .apply { if (s.cookies.isNotEmpty()) header("Cookie", s.cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }) }
            .build()
        val reply = call(request, paceWith).also { check(it); keepCookies(it, s.cookies) }.body
        val records = CnoPage.delta(reply)
        records.firstOrNull { it.type == "error" }?.let { throw CnoException("CrazyNinjaOdds answered with an error: ${it.content.take(120)}") }
        if (records.any { it.type == "pageRedirect" }) throw CnoException("CrazyNinjaOdds restarted the page")
        // The state the next postback must carry.
        records.filter { it.type == "hiddenField" }.forEach { s.fields[it.id] = it.content }
        records.firstOrNull { it.type == "formAction" }?.content?.let { a -> s.view.toHttpUrl().resolve(CnoPage.unescape(a))?.let { s.postUrl = it } }
        s.usedAtMs = clock()
        return records
    }

    private fun List<CnoPage.Record>.grid(): String =
        firstOrNull { it.type == "updatePanel" && it.id.endsWith("UpdatePanelGridView") }?.content
            ?: throw CnoException("CrazyNinjaOdds' reply had no table")

    /**
     * Puts the scanner's filters in CNO's form. Where the Shared View link already set a stricter
     * value (a shorter odds cap, a higher minimum EV), the link's stays. The fewest books is always
     * the app's: 1 or 2 picked in Settings must reach CNO even when the link says more (Tj, 2026-09-28).
     */
    private fun applyFilters(s: Session, f: CnoFilters) {
        val fields = s.fields
        fun key(suffix: String): String? = fields.keys.firstOrNull { it.endsWith(suffix) }
        fun number(suffix: String): Double? = key(suffix)?.let { fields[it] }?.replace(Regex("[^0-9.\\-−+]"), "")?.replace('−', '-')?.toDoubleOrNull()
        fun put(suffix: String, value: String) { key(suffix)?.let { fields[it] = value } }

        put("DropDownListDevigMethod", f.devig.code.toString())
        if (f.maxOdds > 0) {
            val linkMax = number("TextBoxMaximumOdds")?.toInt()?.takeIf { it >= 100 || it <= -100 }
            val stricter = linkMax != null && Odds.americanToDecimal(linkMax) < Odds.americanToDecimal(f.maxOdds)
            if (!stricter) put("TextBoxMaximumOdds", "+${f.maxOdds}")
        }
        put("TextBoxMinimumOddsProviderCount", f.minBooks.toString())
        val ev = maxOf(f.minEv * 100, number("TextBoxMinimumEVPercentage") ?: 0.0)
        put("TextBoxMinimumEVPercentage", (if (ev == Math.floor(ev)) ev.toInt().toString() else String.format(java.util.Locale.US, "%.1f", ev)) + "%")
        put("TextBoxMinimumSubMarketSideCount", maxOf(f.minSides, number("TextBoxMinimumSubMarketSideCount")?.toInt() ?: 0).toString())
        // The games Tj looks at (CnoScope, 2026-10-07). League and sport reach CNO's form only when ONE league or ONE sport covers the pick, so its row limit (best edge first, cut BEFORE
        // the app drops anything) is spent on those games; anything the form can't say, the app's own screen does, from a longer read.
        val plan = f.scope.plan(f.rows, { label -> s.form.optionValue("DropDownListLeague", label)?.toIntOrNull() ?: builtInLeague(s, label) }, { sport -> s.form.optionValue("DropDownListSport", sport.label)?.toIntOrNull() ?: builtInSport(s, sport) })
        val linkLeague = linkValue(s, "DropDownListLeague")
        val linkSport = linkValue(s, "DropDownListSport")
        val linkScopes = (linkLeague != null && linkLeague != "0") || (linkSport != null && linkSport != "0")
        if (linkScopes && (plan.leagueId != null || plan.sportId != null)) {
            // The Shared View link already scopes the list: it wins (as a stricter odds cap does), and the app's own screen intersects it with the picks.
        } else {
            // Written back every time: the session lives 15 minutes and keeps what was posted last, so a pick Tj cleared must go back to what the page began with.
            put("DropDownListLeague", plan.leagueId?.toString() ?: linkLeague ?: "0")
            put("DropDownListSport", plan.sportId?.toString() ?: linkSport ?: "0")
        }
        val liquidity = f.scope.minLiquidity
        if (liquidity > 0) {
            val linkMin = number("TextBoxMinimumLiquidity")?.toInt() ?: 0
            put("TextBoxMinimumLiquidity", "\$${maxOf(liquidity, linkMin)}")
        }
        val widened = plan.askRows > f.rows && clock() - widenRefusedAtMs > WIDEN_RETRY_MS
        put("TextBoxMaximumResultCount", (if (widened) plan.askRows else f.rows).toString())
        s.widened = widened
        if (f.completeBook) s.form.checkboxes.firstOrNull { it.endsWith("CheckBoxRequireACompleteSportsbook") }?.let { fields[it] = "on" }
    }

    /** What the page began with for the dropdown whose name ends with [suffix] (the Shared View link's own pick, or its default), from the form as first read. */
    private fun linkValue(s: Session, suffix: String): String? = s.form.fields.firstOrNull { it.first.endsWith(suffix) }?.second

    /** The League dropdown's id for [label] from the built-in table, only when the page has no such dropdown to ask (a page this build doesn't know) is it not used: a value the page doesn't render is refused. */
    private fun builtInLeague(s: Session, label: String): Int? = if (s.form.hasSelect("DropDownListLeague")) null else CnoLeagues.byLabel(label)?.id

    private fun builtInSport(s: Session, sport: CnoLeagues.Sport): Int? = if (s.form.hasSelect("DropDownListSport")) null else sport.id

    /**
     * The wide read's form: Tj's devig method, and every numeric filter CNO has opened up, in this session's own fields. What the view's link scopes (book, sport,
     * league, main lines, live, and any filter this doesn't name) stays as the link says. Returns the filter fields as posted ([CnoSnapshot.asked]).
     */
    private fun applyWide(s: Session, f: CnoFilters, rows: Int): String {
        val fields = s.fields
        fun key(suffix: String): String? = fields.keys.firstOrNull { it.endsWith(suffix) }
        fun put(suffix: String, value: String) { key(suffix)?.let { fields[it] = value } }
        put("DropDownListDevigMethod", f.devig.code.toString())
        put("TextBoxMaximumOdds", "")
        put("TextBoxMinimumOdds", "")
        put("TextBoxMinimumOddsProviderCount", "1")
        put("TextBoxMinimumEVPercentage", "0%")
        put("TextBoxMinimumSubMarketSideCount", "1")
        put("TextBoxMaximumResultCount", rows.toString())
        s.form.checkboxes.firstOrNull { it.endsWith("CheckBoxRequireACompleteSportsbook") }?.let { fields.remove(it) }
        return fields.entries.filter { (k, _) -> CONTROL.containsMatchIn(k) }
            .joinToString(", ") { (k, v) -> "${k.substringAfterLast('$')}=$v" }
    }

    /**
     * One request to CNO, in [pace], read off the caller's thread. A network failure (a dead
     * connection left from before the phone slept, a timeout, a DNS hiccup) is tried once more
     * straight away on a fresh connection: most of the "timeout" and "unable to resolve" Tj saw
     * (2026-09-27) were one-off, and the second try goes through.
     */
    private suspend fun call(request: Request, paceWith: CnoPace = pace): HttpText {
        paceWith.await()
        return try {
            http.newCall(request).awaitText()
        } catch (e: IOException) {
            http.connectionPool.evictAll()
            paceWith.await()
            http.newCall(request).awaitText()
        }
    }

    private fun unreachable(e: IOException) = CnoException("Couldn't reach CrazyNinjaOdds (${why(e)})", cause = e)

    private fun check(response: HttpText) {
        if (response.isSuccessful) return
        val retry = response.header("Retry-After")?.trim()?.toIntOrNull()
        throw when (response.code) {
            429, 503 -> CnoException("CrazyNinjaOdds is busy (HTTP ${response.code}); trying again later", retryAfterSeconds = retry ?: 120)
            403 -> CnoException("CrazyNinjaOdds refused the request (HTTP 403)", retryAfterSeconds = retry ?: 600)
            else -> CnoException("CrazyNinjaOdds answered HTTP ${response.code}")
        }
    }

    private fun keepCookies(response: HttpText, into: MutableMap<String, String>) {
        response.headers.values("Set-Cookie").forEach { header ->
            val pair = header.substringBefore(';')
            val name = pair.substringBefore('=').trim()
            if (name.isNotEmpty() && '=' in pair) into[name] = pair.substringAfter('=').trim()
        }
    }

    companion object {
        /**
         * A network failure in plain words (Tj, 2026-09-27: "sometimes it says unable to resolve
         * cno sometimes it says timeout"): which of the phone's network, its DNS or CNO itself
         * is the trouble.
         */
        fun why(e: IOException): String = when (e) {
            is java.net.UnknownHostException -> "the phone couldn't look up its address: no signal, or a VPN reconnecting"
            is java.net.SocketTimeoutException -> "it didn't answer in time"
            is java.net.ConnectException -> "no connection to it"
            is javax.net.ssl.SSLException -> "secure connection failed: ${e.message ?: "TLS error"}"
            else -> when {
                e.message.orEmpty().contains("timeout", ignoreCase = true) -> "it didn't answer in time"
                else -> e.message ?: "network error"
            }
        }

        /** A phone browser's, so CNO serves its normal page; "Vigilant" at the end says who's asking. */
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16; moto g) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36 Vigilant"

        /** Re-use a session this long after its last read; ASP.NET's own timeout is 20 minutes. */
        const val SESSION_MS = 15 * 60_000L

        /** How long after CNO refused a widened read the list goes back to asking for it. */
        const val WIDEN_RETRY_MS = 10 * 60_000L

        /** The least time between two requests of a bulk read ([booksBulk]): two a second at most. */
        const val BULK_GAP_MS = 500L

        /** A field of the form that is one of the filters (as opposed to ASP.NET's own state). */
        private val CONTROL = Regex("(TextBox|DropDownList|CheckBox)[A-Za-z_]*$")
    }
}
