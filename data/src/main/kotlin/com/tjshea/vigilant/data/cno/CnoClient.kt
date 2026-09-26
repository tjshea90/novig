package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.data.await
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/** Reads one CNO view. [CnoClient] in the app; a fake in tests. */
fun interface CnoSource {
    suspend fun fetch(url: String): CnoSnapshot
}

/**
 * Reads Tj's CrazyNinjaOdds view the way the page itself loads (Tj, 2026-09-26: "whatever the
 * best way is"; RESEARCH.md §18). The first read is the page (GET) plus the postback its loader
 * timer makes; after that one postback per refresh, the page's own Refresh button, with the
 * state the last reply handed back. So a refresh is one request, not two, as long as the session
 * lasts (ASP.NET's are ~20 minutes); anything odd falls back to a fresh page load once.
 *
 * Not thread-safe: [CnoFeed] calls it one read at a time.
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

    override suspend fun fetch(url: String): CnoSnapshot {
        val reuse = session?.takeIf { it.view == url && clock() - it.usedAtMs < SESSION_MS && it.form.button != null }
        if (reuse != null) {
            try {
                return postback(reuse, useTimer = false)
            } catch (e: IOException) {
                session = null
                throw CnoException("Couldn't reach CrazyNinjaOdds (${e.message ?: "network error"})", cause = e)
            } catch (e: CnoException) {
                // A lapsed session or a hiccup: start over with a fresh page load, once.
                if (e.retryAfterSeconds != null) throw e
                session = null
            }
        }
        return try {
            postback(open(url), useTimer = true)
        } catch (e: IOException) {
            session = null
            throw CnoException("Couldn't reach CrazyNinjaOdds (${e.message ?: "network error"})", cause = e)
        } catch (e: CnoException) {
            session = null
            throw e
        }
    }

    /** Loads the page: its form, cookies and filters. */
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

    /** One postback: the table and CNO's "last updated", plus the state for the next one. */
    private suspend fun postback(s: Session, useTimer: Boolean): CnoSnapshot {
        val form = s.form
        val timer = form.timer
        val button = form.button
        val body = FormBody.Builder()
        if (useTimer && timer != null) {
            body.add(form.scriptManager, "${form.gridPanel}|$timer")
            s.fields.forEach { (k, v) ->
                body.add(k, when (k) { "__EVENTTARGET" -> timer; "__EVENTARGUMENT" -> ""; else -> v })
            }
            if ("__EVENTTARGET" !in s.fields) body.add("__EVENTTARGET", timer)
        } else {
            val (name, value) = button ?: throw CnoException("CrazyNinjaOdds' page has no Refresh button")
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
        val grid = records.firstOrNull { it.type == "updatePanel" && it.id.endsWith("UpdatePanelGridView") }
            ?: throw CnoException("CrazyNinjaOdds' reply had no table")
        val info = records.firstOrNull { it.type == "updatePanel" && it.id.endsWith("UpdatePanelServerInfo") }
        // The state the next postback must carry.
        records.filter { it.type == "hiddenField" }.forEach { s.fields[it.id] = it.content }
        records.firstOrNull { it.type == "formAction" }?.content?.let { a -> s.view.toHttpUrl().resolve(CnoPage.unescape(a))?.let { s.postUrl = it } }
        s.usedAtMs = clock()
        session = s
        val table = CnoPage.table(grid.content, s.view.toHttpUrl())
        return CnoSnapshot(
            url = s.view,
            rows = table.rows,
            fetchedAtMs = clock(),
            cnoAgeSeconds = info?.let { CnoPage.lastUpdatedSeconds(it.content) },
            evLabel = table.evLabel,
            note = table.note,
        )
    }

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
