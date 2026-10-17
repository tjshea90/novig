package com.tjshea.vigilant.cloud

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.tjshea.vigilant.data.cloud.CloudLive
import com.tjshea.vigilant.data.cloud.CloudStatus
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * The engine's live status for the phone (TCL80, Tj 2026-10-17: "tunnel yes"): a tiny read-only web server on the runner, bound to 127.0.0.1 and reached from outside only through the Cloudflare quick tunnel the workflow
 * starts ([CloudLive]). It serves ONE thing, the engine's current [CloudStatus] (the same public status the repo holds, rebuilt from memory every second), at `/s/<token>` (one reading) and `/s/<token>/stream`
 * (server-sent events, one reading a second). The token is random per run and part of the path; any other path is 404. It accepts no input, holds no key, serves no list and runs no command: the phone's controls
 * stay on GitHub. At most [MAX_STREAMS] streams at once; a stream ends after [STREAM_MAX_MS] (the phone reconnects).
 */
class LiveStatusServer(
    private val token: String,
    private val status: () -> CloudStatus?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val everyMs: Long = 1_000L,
) {
    private var server: HttpServer? = null
    private val streams = AtomicInteger(0)

    /** Starts on [port] (0 = any free port) and returns the port it bound. */
    fun start(port: Int = 0): Int {
        require(CloudLive.validToken(token)) { "the live token must be 24+ lowercase hex letters" }
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", port), 16)
        s.executor = Executors.newCachedThreadPool { r -> Thread(r, "live-status").apply { isDaemon = true } }
        s.createContext("/") { ex -> runCatching { handle(ex) }.onFailure { runCatching { ex.close() } } }
        s.start()
        server = s
        return s.address.port
    }

    fun stop() { server?.stop(0); server = null }

    private fun handle(ex: HttpExchange) {
        val path = ex.requestURI.path.trimEnd('/')
        val base = "/s/$token"
        if (ex.requestMethod != "GET" || (path != base && path != "$base/stream")) { reply(ex, 404, "not found"); return }
        if (path == base) { reply(ex, 200, json()); return }
        if (streams.incrementAndGet() > MAX_STREAMS) { streams.decrementAndGet(); reply(ex, 429, "too many streams"); return }
        try {
            ex.responseHeaders.add("Content-Type", "text/event-stream")
            ex.responseHeaders.add("Cache-Control", "no-store")
            ex.responseHeaders.add("X-Accel-Buffering", "no")
            ex.sendResponseHeaders(200, 0)
            val out = ex.responseBody
            val end = clock() + STREAM_MAX_MS
            while (clock() < end) {
                out.write(("data: " + json() + "\n\n").toByteArray())
                out.flush()
                Thread.sleep(everyMs)
            }
        } catch (_: IOException) {
        } catch (_: InterruptedException) {
        } finally {
            streams.decrementAndGet()
            runCatching { ex.close() }
        }
    }

    private fun json(): String = status()?.let { CloudStatus.text(it) } ?: "{}"

    private fun reply(ex: HttpExchange, code: Int, body: String) {
        val bytes = body.toByteArray()
        ex.responseHeaders.add("Content-Type", if (code == 200) "application/json" else "text/plain")
        ex.responseHeaders.add("Cache-Control", "no-store")
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    companion object {
        const val MAX_STREAMS = 4
        const val STREAM_MAX_MS = 15 * 60_000L
    }
}
