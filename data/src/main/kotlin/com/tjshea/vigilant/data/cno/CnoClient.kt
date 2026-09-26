package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.engine.Odds
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/** Reads CNO: the +EV list for a view, a bet's books, a bet's Novig link. [CnoClient] in the app; fakes in tests. */
interface CnoSource {
    suspend fun fetch(url: String, filters: CnoFilters): CnoSnapshot

    /** Every book's price for [row] (its CNO game page). */
    suspend fun books(row: CnoRow): CnoBooksView? = null

    /** The Novig app link for [row]'s game (`novigapp://events/<id>/cno`), from CNO's deeplink. */
    suspend fun novigLink(row: CnoRow): String? = null
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
) : CnoSource {

    private class Session(
        val view: String,
        var postUrl: HttpUrl,
        val cookies: MutableMap<String, String>,
        val form: CnoPage.Form,
        val fields: LinkedHashMap<String, String>,
        var usedAtMs: Long,
    )

    private var session: Session? = null

    override suspend fun fetch(url: String, filters: CnoFilters): CnoSnapshot {
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

    override suspend fun books(row: CnoRow): CnoBooksView? {
        val url = row.gameUrl ?: return null
        return try {
            val s = open(url)
            val grid = postback(s, useTimer = true).grid()
            CnoBooks.parse(grid, row.sideId, row.bet, clock())
        } catch (e: IOException) {
            throw unreachable(e)
        }
    }

    override suspend fun novigLink(row: CnoRow): String? {
        val url = row.betUrl ?: return null
        // CNO's deeplink page asks for consent once and remembers it in this cookie.
        val request = Request.Builder().url(url).get()
            .header("User-Agent", USER_AGENT)
            .header("Cookie", "BetaDeepLinkIntro=Read=1")
            .build()
        val html = try {
            http.newCall(request).await().use { response ->
                check(response)
                response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw unreachable(e)
        }
        val target = Regex("""location\.(?:replace|href)\s*\(?\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1) ?: return null
        return target.takeIf { it.startsWith("novigapp://") || it.startsWith("https://") }
    }

    /** Loads a page: its form and cookies. */
    private suspend fun open(url: String): Session {
        val pageUrl = url.toHttpUrl()
        val request = Request.Builder().url(pageUrl).get().header("User-Agent", USER_AGENT).build()
        val cookies = LinkedHashMap<String, String>()
        val html = http.newCall(request).await().use { response ->
            check(response)
            keepCookies(response, cookies)
            response.body?.string().orEmpty()
        }
        val form = CnoPage.form(html)
        val postUrl = form.action?.let { pageUrl.resolve(it) } ?: pageUrl
        return Session(url, postUrl, cookies, form, LinkedHashMap(form.fields.toMap()), clock())
    }

    /** One +EV list read: the table and CNO's "last updated", plus the state for the next read. */
    private suspend fun list(s: Session, useTimer: Boolean, filters: CnoFilters): CnoSnapshot {
        applyFilters(s, filters)
        val records = postback(s, useTimer)
        val grid = records.grid()
        val info = records.firstOrNull { it.type == "updatePanel" && it.id.endsWith("UpdatePanelServerInfo") }
        session = s
        val table = CnoPage.table(grid, s.view.toHttpUrl())
        return CnoSnapshot(
            url = s.view,
            rows = table.rows,
            fetchedAtMs = clock(),
            cnoAgeSeconds = info?.let { CnoPage.lastUpdatedSeconds(it.content) },
            evLabel = table.evLabel,
            note = table.note,
            filters = filters,
        )
    }

    /** One postback, as the page's loader timer or its Refresh button would make it. */
    private suspend fun postback(s: Session, useTimer: Boolean): List<CnoPage.Record> {
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
        val reply = http.newCall(request).await().use { response ->
            check(response)
            keepCookies(response, s.cookies)
            response.body?.string().orEmpty()
        }
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
     * value (a shorter odds cap, more books, a higher minimum EV), the link's stays.
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
        put("TextBoxMinimumOddsProviderCount", maxOf(f.minBooks, number("TextBoxMinimumOddsProviderCount")?.toInt() ?: 0).toString())
        val ev = maxOf(f.minEv * 100, number("TextBoxMinimumEVPercentage") ?: 0.0)
        put("TextBoxMinimumEVPercentage", (if (ev == Math.floor(ev)) ev.toInt().toString() else "%.1f".format(ev)) + "%")
        put("TextBoxMinimumSubMarketSideCount", maxOf(f.minSides, number("TextBoxMinimumSubMarketSideCount")?.toInt() ?: 0).toString())
        put("TextBoxMaximumResultCount", f.rows.toString())
        if (f.completeBook) s.form.checkboxes.firstOrNull { it.endsWith("CheckBoxRequireACompleteSportsbook") }?.let { fields[it] = "on" }
    }

    private fun unreachable(e: IOException) = CnoException("Couldn't reach CrazyNinjaOdds (${e.message ?: "network error"})", cause = e)

    private fun check(response: Response) {
        if (response.isSuccessful) return
        val retry = response.header("Retry-After")?.trim()?.toIntOrNull()
        throw when (response.code) {
            429, 503 -> CnoException("CrazyNinjaOdds is busy (HTTP ${response.code}); trying again later", retryAfterSeconds = retry ?: 120)
            403 -> CnoException("CrazyNinjaOdds refused the request (HTTP 403)", retryAfterSeconds = retry ?: 600)
            else -> CnoException("CrazyNinjaOdds answered HTTP ${response.code}")
        }
    }

    private fun keepCookies(response: Response, into: MutableMap<String, String>) {
        response.headers("Set-Cookie").forEach { header ->
            val pair = header.substringBefore(';')
            val name = pair.substringBefore('=').trim()
            if (name.isNotEmpty() && '=' in pair) into[name] = pair.substringAfter('=').trim()
        }
    }

    companion object {
        /** A phone browser's, so CNO serves its normal page; "Vigilant" at the end says who's asking. */
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16; moto g) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36 Vigilant"

        /** Re-use a session this long after its last read; ASP.NET's own timeout is 20 minutes. */
        const val SESSION_MS = 15 * 60_000L
    }
}
