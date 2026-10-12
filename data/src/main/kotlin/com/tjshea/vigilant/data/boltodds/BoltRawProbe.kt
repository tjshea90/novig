package com.tjshea.vigilant.data.boltodds

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Test key's look at a BoltOdds socket the app does not use (BOLTODDS_API.md §8): `/livescores` and `/playbyplay` (the docs call them Trial / Custom features; the first sample showed their `.../user` endpoints answer
 * 200 on a Pro key, which says nothing about whether a subscription is allowed). It connects with a key, records how the upgrade was answered, sends the optional subscription [subscribe] once the greeting arrives
 * (or after [greetingWaitMs] without one), then listens for [listenMs] and returns a readable account: the frames (kind, size, a preview), every close code and reason, and every failure. It sends and keeps nothing
 * else, and the key is cut out of everything it prints.
 */
internal object BoltRawProbe {
    suspend fun run(
        http: OkHttpClient,
        client: BoltOddsClient,
        wsUrl: String,
        path: String,
        subscribe: String?,
        listenMs: Long,
        greetingWaitMs: Long = 3_000L,
        clock: () -> Long = System::currentTimeMillis,
    ): String {
        val key = client.nextKey() ?: return "no usable key"
        val notes = CopyOnWriteArrayList<String>()
        val closed = CompletableDeferred<String>()
        val greeted = CompletableDeferred<Unit>()
        val t0 = clock()
        fun at() = "+${String.format(java.util.Locale.US, "%.1f", (clock() - t0) / 1000.0)}s"
        fun frame(kind: String, text: String, size: Int) {
            if (notes.count { it.contains("frame") } < MAX_FRAMES) notes += "${at()} $kind frame, $size ${if (kind == "binary") "bytes" else "chars"}: ${client.redact(text.take(220)).replace('\n', ' ')}"
            if (text.contains("socket_connected")) greeted.complete(Unit)
        }
        val ws = http.newWebSocket(
            Request.Builder().url("$wsUrl$path?key=$key").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    notes += "${at()} opened: HTTP ${response.code} · extensions ${response.header("Sec-WebSocket-Extensions") ?: "none"} · server ${response.header("Server") ?: "?"}"
                }
                override fun onMessage(webSocket: WebSocket, text: String) = frame("text", text, text.length)
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) = frame("binary", BoltFrames.decode(bytes), bytes.size)
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    notes += "${at()} closed by the server: code $code ${client.redact(reason)}".trim()
                    webSocket.close(code, null)
                    closed.complete("closed")
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { closed.complete("closed") }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    notes += "${at()} failed: ${listOfNotNull(response?.let { "HTTP ${it.code}" }, client.redact(t.message ?: t.javaClass.simpleName).take(160)).joinToString(": ")}"
                    closed.complete("failed")
                }
            },
        )
        try {
            if (subscribe != null) {
                withTimeoutOrNull(greetingWaitMs) { greeted.await() }
                if (!closed.isCompleted) {
                    notes += "${at()} ${if (greeted.isCompleted) "greeted" else "no greeting after ${greetingWaitMs / 1000}s: subscribing anyway"}; sending ${client.redact(subscribe.take(200))}"
                    ws.send(subscribe)
                }
            }
            withTimeoutOrNull(listenMs) { closed.await() }
            if (!closed.isCompleted) notes += "${at()} still open after ${listenMs / 1000}s of listening"
        } finally {
            ws.close(1000, "done")
            delay(100)
        }
        return notes.joinToString("\n").ifBlank { "nothing happened" }
    }

    private const val MAX_FRAMES = 8
}
