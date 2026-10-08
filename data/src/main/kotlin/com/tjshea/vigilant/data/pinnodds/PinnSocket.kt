package com.tjshea.vigilant.data.pinnodds

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

sealed interface PinnSocketState {
    data object Off : PinnSocketState
    data object Connecting : PinnSocketState
    data class Live(val sinceMs: Long) : PinnSocketState

    /** Not connected; [retryAtMs] is when it tries again (null = not by itself: the key or the plan has to change). */
    data class Down(val message: String, val atMs: Long, val retryAtMs: Long?) : PinnSocketState
}

/**
 * The Pinnodds raw feed (`wss://pinnodds.com/ws/feed`, docs https://pinnodds.com/llms-full.txt, read 2026-10-08), used the way the docs ask:
 *  - **One connection per account**: a second one evicts the oldest (`1001 evicted by newer connection`), so this is the only one the app opens and an eviction is reported, not fought.
 *  - The key goes in the `x-api-key` header (the docs allow it on the upgrade), not in the URL, which would land in logs.
 *  - `subscribe` is sent the moment the socket opens (the docs close a socket that has not subscribed within 5 s); the server's `ping` (every 30 s) is answered with `{"type":"pong"}` (it
 *    drops a socket after ~75 s without one); OkHttp's own protocol pings are off.
 *  - **Compression is off**: OkHttp offers `permessage-deflate` on every upgrade, and the docs measured it adding a 1.2 s p99 tail during Pinnacle's update bursts (187 ms without), so the
 *    offer is removed from the request and the server then sends plain frames.
 *  - Reconnect with backoff 1 s, 2 s, 4 s ... 30 s and subscribe again (the server keeps nothing). `1011 deregistered:` is retried at once; `1001 evicted` waits a minute and says why; a
 *    `403 plan_lacks_ws` or a refused key does not retry by itself.
 */
class PinnSocket(
    http: OkHttpClient,
    private val key: () -> String?,
    private val scope: CoroutineScope,
    private val onFrame: (text: String, atMs: Long) -> Unit,
    private val sports: List<Int> = DEFAULT_SPORTS,
    private val url: String = "wss://pinnodds.com/ws/feed",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val client: OkHttpClient = http.newBuilder()
        .pingInterval(0, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain -> chain.proceed(chain.request().newBuilder().removeHeader("Sec-WebSocket-Extensions").build()) }
        .build()

    private val _state = MutableStateFlow<PinnSocketState>(PinnSocketState.Off)
    val state: StateFlow<PinnSocketState> = _state.asStateFlow()

    @Volatile
    private var job: Job? = null

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    var lastFrameAtMs: Long = 0L
        private set

    @Volatile
    var frames: Long = 0L
        private set

    val running: Boolean get() = job?.isActive == true

    @Synchronized
    fun start() {
        if (running) return
        job = scope.launch { loop() }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        socket?.close(1000, "stopped")
        socket = null
        _state.value = PinnSocketState.Off
    }

    private class Ended(val code: Int, val reason: String, val httpStatus: Int?, val failure: String?)

    private suspend fun loop() {
        var backoff = 1_000L
        while (scope.isActive && job?.isActive == true) {
            val k = key()?.trim().orEmpty()
            if (k.isEmpty()) {
                _state.value = PinnSocketState.Down("No Pinnodds key saved (Settings › Pinnodds live).", clock(), null)
                return
            }
            _state.value = PinnSocketState.Connecting
            val ended = CompletableDeferred<Ended>()
            val request = Request.Builder().url(url).header("x-api-key", k).build()
            val ws = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"type":"subscribe","streams":["live"],"sport_ids":[${sports.joinToString(",")}]}""")
                    lastFrameAtMs = clock()
                    _state.value = PinnSocketState.Live(clock())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val now = clock()
                    lastFrameAtMs = now
                    frames++
                    // The heartbeat is answered before anything else is done with the frame.
                    if (text.length < 120 && text.contains("\"ping\"")) {
                        webSocket.send("""{"type":"pong"}""")
                        return
                    }
                    onFrame(text, now)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    ended.complete(Ended(code, reason, null, null))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    ended.complete(Ended(0, "", response?.code, t.message ?: t.javaClass.simpleName))
                }
            })
            socket = ws
            // A socket that opened and then says nothing for 90 s is dead (the server pings every 30 s).
            val watchdog = scope.launch {
                while (isActive) {
                    delay(15_000L)
                    if (_state.value is PinnSocketState.Live && clock() - lastFrameAtMs > STALE_MS) {
                        ended.complete(Ended(0, "", null, "no frame for ${STALE_MS / 1000} s"))
                        ws.cancel()
                    }
                }
            }
            val e = withTimeoutOrNull(Long.MAX_VALUE) { ended.await() } ?: break
            watchdog.cancel()
            socket = null
            val wasLive = _state.value is PinnSocketState.Live
            if (job?.isActive != true) break
            val (message, wait) = explain(e)
            val now = clock()
            if (wait == null) {
                _state.value = PinnSocketState.Down(message, now, null)
                return
            }
            if (wasLive && e.httpStatus == null) backoff = 1_000L
            val retry = if (e.reason.startsWith("deregistered")) 0L else maxOf(wait, backoff)
            _state.value = PinnSocketState.Down(message, now, now + retry)
            delay(retry)
            backoff = minOf(30_000L, backoff * 2)
        }
    }

    /** What an ended connection means, in words for the status line, and how long to wait before trying again (null: not without a change by Tj). */
    internal fun explain(e: Ended): Pair<String, Long?> = when {
        e.httpStatus == 401 -> "Pinnodds refused the key (401): check it in Settings › Pinnodds live." to null
        e.httpStatus == 403 -> "This Pinnodds key has no WebSocket access (403 plan_lacks_ws): the 3-day demo has ended, or the plan needs the WebSocket add-on." to null
        e.httpStatus == 429 -> "Pinnodds says too many connections or requests (429)." to 30_000L
        e.code == 1001 && e.reason.startsWith("evicted") -> "Another connection on this Pinnodds account took over (a second app, a script, a study): only one is allowed per account." to 60_000L
        e.code == 1001 -> "Pinnodds closed the feed (${e.reason.ifBlank { "restart" }})." to 1_000L
        e.reason.startsWith("deregistered") -> "Pinnodds dropped the feed (${e.reason}): reconnecting." to 0L
        e.failure != null -> "The Pinnodds feed dropped (${e.failure})." to 1_000L
        else -> "The Pinnodds feed closed (${e.code} ${e.reason})." to 1_000L
    }

    companion object {
        /** Soccer, tennis, basketball, hockey, football, baseball, rugby, MMA, boxing, other, esports, golf, cricket (14 and 15 are REST-only; the docs allow up to 32 per subscribe). */
        val DEFAULT_SPORTS = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)

        /** The server pings every 30 s: nothing for this long means the connection is dead. */
        const val STALE_MS = 90_000L
    }
}
