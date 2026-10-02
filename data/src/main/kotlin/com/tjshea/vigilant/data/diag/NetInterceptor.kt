package com.tjshea.vigilant.data.diag

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What an endpoint looks like with nothing in it that is a person's or a key's: "api.example.com" and "/v1/sports/{id}/odds", never a query. A path segment that
 * is a number of five digits or more, a UUID, or a long run of letters and digits (an id, a token, a key put in a path) is `{id}`; at most four segments are kept.
 */
object NetShape {
    private val NUMBER = Regex("^\\d{5,}$")
    private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val TOKEN = Regex("^[A-Za-z0-9_\\-.~%]{20,}$")
    private val HEX = Regex("^[0-9a-fA-F]{12,}$")

    fun of(url: HttpUrl): String {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val shown = segments.take(MAX_SEGMENTS).map(::segment)
        return "/" + shown.joinToString("/") + if (segments.size > MAX_SEGMENTS) "/…" else ""
    }

    fun segment(s: String): String = when {
        NUMBER.matches(s) || UUID.matches(s) || HEX.matches(s) || (TOKEN.matches(s) && s.any { it.isDigit() }) || s.length > 40 -> "{id}"
        else -> s
    }

    const val MAX_SEGMENTS = 4
}

/**
 * Records every call through the app's shared HTTP client ([vigilantHttpClient]) into [NetStats] and, for a failure, a rate limit or a slow call, an [EventLog]
 * line (Tj, 2026-10-02): which host and endpoint, the status, how long to the headers and to the end of the body, how many bytes, on which network. The body is
 * passed through untouched and counted as it is read; the call is recorded when its body ends or is closed (a websocket's upgrade at once). The URL's query,
 * the request, the headers and the body are never read into the record.
 */
class NetInterceptor(
    private val stats: NetStats,
    private val events: EventLog?,
    /** "Wi-Fi", "mobile" or "other" now, null when unknown. */
    private val network: () -> String? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        val shape = NetShape.of(request.url)
        val net = runCatching(network).getOrNull()
        val t0 = clock()
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            val ms = clock() - t0
            val kind = kindOf(e)
            stats.record(host, shape, null, kind, ms, ms, 0, net, error = "${e.javaClass.simpleName}: ${e.message ?: ""}".let { ProblemLog.clean(it) })
            // A call cancelled on purpose (a scan stopped, a screen closed) is not a failure to report.
            if (kind != "cancelled") events?.record("NET", Level.WARN, "$host$shape failed: $kind after ${ms} ms")
            throw e
        }
        val ttfb = clock() - t0
        val status = response.code
        val limit = if (status == 429 || status == 403) response.header("Retry-After")?.let { "Retry-After: ${it.take(20)}" } else null
        val body = response.body
        if (status == 101 || body == null) {
            finish(host, shape, status, ttfb, ttfb, 0, net, limit)
            return response
        }
        val done = AtomicBoolean(false)
        val counting = object : ForwardingSource(body.source()) {
            var bytes = 0L
            override fun read(sink: Buffer, byteCount: Long): Long {
                val n = try {
                    super.read(sink, byteCount)
                } catch (e: IOException) {
                    end()
                    throw e
                }
                if (n < 0) end() else bytes += n
                return n
            }

            override fun close() {
                end()
                super.close()
            }

            private fun end() {
                if (done.compareAndSet(false, true)) finish(host, shape, status, ttfb, clock() - t0, bytes, net, limit)
            }
        }
        val buffered = counting.buffer()
        val wrapped = object : ResponseBody() {
            override fun contentType(): MediaType? = body.contentType()
            override fun contentLength(): Long = body.contentLength()
            override fun source(): BufferedSource = buffered
        }
        return response.newBuilder().body(wrapped).build()
    }

    private fun finish(host: String, shape: String, status: Int, ttfb: Long, total: Long, bytes: Long, net: String?, limit: String?) {
        stats.record(host, shape, status, null, ttfb, total, bytes, net, limit = limit)
        val ev = events ?: return
        when {
            status == 429 -> ev.record("NET", Level.WARN, "$host$shape rate-limited (429${limit?.let { ", $it" } ?: ""})")
            status >= 500 -> ev.record("NET", Level.ERROR, "$host$shape server error $status after ${ttfb} ms")
            status >= 400 -> ev.record("NET", Level.WARN, "$host$shape refused with $status after ${ttfb} ms")
            total >= SLOW_MS -> ev.record("NET", Level.WARN, "$host$shape slow: ${total} ms for ${bytes / 1024} KB", ms = total)
        }
    }

    companion object {
        /** A call whose whole body took this long is an event of its own. */
        const val SLOW_MS = 8_000L

        fun kindOf(e: IOException): String = when {
            e is UnknownHostException -> "dns"
            e is SocketTimeoutException || (e.message ?: "").contains("timeout", true) -> "timeout"
            (e.message ?: "").contains("canceled", true) || (e.message ?: "").contains("cancelled", true) -> "cancelled"
            e is javax.net.ssl.SSLException -> "tls"
            e is java.net.ConnectException -> "connect"
            (e.message ?: "").contains("reset", true) || (e.message ?: "").contains("broken pipe", true) || (e.message ?: "").contains("EOF", true) -> "reset"
            else -> "other"
        }
    }
}
