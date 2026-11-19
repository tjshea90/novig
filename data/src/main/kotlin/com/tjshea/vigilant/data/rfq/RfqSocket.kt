package com.tjshea.vigilant.data.rfq

import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * The maker's connection to Novig's RFQ socket (`GET /rfq/v3/ws`, MK 2026-11-18): registers the subaccount as a maker once (idempotent), opens one signed websocket, subscribes the channels [channels] names
 * for the mode, hands every frame to [maker], and reconnects by the rules in [RfqReconnect] (MK13: backoff after every close or refusal, none at all for a refused location, key or account, nothing faster
 * than the docs' "reconnect with backoff"). Nothing here decides anything: [RfqMaker] does, and in SHADOW it sends nothing back. Novig's upgrade answers `404 MAKER_NOT_FOUND` for a maker that has not reached
 * every server yet (up to a minute after registering): the registration is repeated and the upgrade retried.
 *
 * The docs allow 10 sockets per key and say the connection carries the firehose: Novig queues 256 frames per connection and DROPS EVENT FRAMES when it is full, and `quote_accepted` is an event frame with a
 * 1-second answer window. [lastLook] is a second socket on the same key that subscribes to `quotes` ONLY (a handful of frames, never a full queue), so the last look has a quiet line of its own beside the
 * pricing socket's `rfq` stream; a `quote_accepted` is answered on whichever socket gets it first ([RfqMaker] sends one confirm per quote).
 */
class RfqSocket(
    private val http: OkHttpClient,
    private val client: () -> NovigTradingClient?,
    private val maker: RfqMaker,
    private val config: () -> RfqConfig,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
    /** What this socket subscribes to in each mode, or null when it should not be open in that mode. */
    private val channels: (RfqConfig.Mode) -> List<String>? = PRICING,
    private val name: String = "RFQ socket",
    /** A second socket waits for this (the pricing socket's registration) before it connects. */
    private val ready: () -> Boolean = { true },
) {
    @Volatile private var ws: WebSocket? = null
    @Volatile var connected = false; private set
    @Volatile var makerId: String? = null; private set
    @Volatile var lastNote: String = ""; private set
    @Volatile var connects = 0L; private set
    /** When the next attempt may start (the plan of the last failure); 0 = now. */
    @Volatile var nextAttemptMs = 0L; private set
    private var job: Job? = null
    private var consecutive = 0
    private var openedAtMs = 0L
    private var registered = false
    private var mode = RfqConfig.Mode.OFF
    private var wanted: List<String>? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { keep() }
    }

    fun stop() { job?.cancel(); job = null; close("stopped") }

    private fun close(why: String) { ws?.close(1000, why); ws = null; connected = false }

    /** Sends one frame; false when there is no connection. */
    fun send(text: String): Boolean = ws?.send(text) == true

    private fun plan(cause: RfqReconnect.Cause) {
        val p = RfqReconnect.plan(cause, consecutive)
        consecutive++
        nextAttemptMs = clock() + p.delayMs
        if (p.reregister) registered = false
        lastNote = p.why.take(160)
        log("$name: ${p.why} · next try in ${p.delayMs / 1000}s")
    }

    private suspend fun keep() {
        while (scope.isActive) {
            val cfg = config()
            val c = client()
            val want = channels(cfg.mode)
            if (cfg.mode == RfqConfig.Mode.OFF || c == null || want == null) {
                if (ws != null) close("mode off")
                lastNote = if (c == null) "no trading key" else if (want == null) "not needed in ${cfg.mode}" else "off"
                delay(5_000L); continue
            }
            if (ws != null && (mode != cfg.mode || wanted != want)) { close("mode changed"); consecutive = 0; nextAttemptMs = 0L }
            mode = cfg.mode; wanted = want
            if (ws == null && clock() >= nextAttemptMs && ready()) {
                try {
                    if (!registered) {
                        makerId = c.rfqRegister()
                        registered = true
                        log("$name: maker registered${makerId?.let { " ($it)" }.orEmpty()}")
                        // A new maker can take a minute to reach every server.
                        if (makerId != null) delay(4_000L)
                    }
                    open(c, want)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: NovigApiException) {
                    plan(RfqReconnect.Cause.Upgrade(e.status, e.code, null))
                } catch (e: Exception) {
                    plan(RfqReconnect.Cause.Error("${e.javaClass.simpleName}: ${e.message?.take(100)}"))
                }
            }
            delay(1_000L)
        }
    }

    private fun open(c: NovigTradingClient, want: List<String>) {
        connects++
        val request = c.signedRequest("GET", "/rfq/v3/ws")
        ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== ws) return
                connected = true; openedAtMs = clock()
                lastNote = "connected"
                // `quotes` is needed only to quote: without it a won quote is never seen and Novig rejects the trade. Subscriptions belong to the connection: every reconnect subscribes again.
                webSocket.send(RfqProtocol.subscribe(0, want))
                log("$name open (${config().mode}, $want)")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== ws) return
                RfqProtocol.parse(text, clock())?.let { e -> runCatching { maker.onEvent(e) { frame -> webSocket.send(frame) } } }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null); gone(webSocket, RfqReconnect.Cause.Closed(code, reason, openFor())) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = gone(webSocket, RfqReconnect.Cause.Closed(code, reason, openFor()))
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (response != null && response.code != 101) {
                    // The upgrade was refused: the JSON body names the code (TOO_MANY_SOCKETS, MAKER_NOT_FOUND, ANONYMIZED_NETWORK...).
                    val body = runCatching { response.peekBody(2_048).string() }.getOrDefault("")
                    val code = Regex("\"code\"\\s*:\\s*\"([A-Z0-9_]+)\"").find(body)?.groupValues?.get(1)
                    gone(webSocket, RfqReconnect.Cause.Upgrade(response.code, code, response.header("Retry-After")?.trim()?.toLongOrNull()))
                } else gone(webSocket, RfqReconnect.Cause.Error("failed ${t.message?.take(80) ?: t.javaClass.simpleName}".trim()))
            }
        })
    }

    private fun openFor(): Long = if (openedAtMs > 0L) clock() - openedAtMs else 0L

    private fun gone(webSocket: WebSocket, cause: RfqReconnect.Cause) {
        if (webSocket !== ws) return
        ws = null; connected = false
        if (cause is RfqReconnect.Cause.Closed && cause.openForMs >= RfqReconnect.STABLE_MS) consecutive = 0
        openedAtMs = 0L
        plan(cause)
    }

    companion object {
        /** The pricing socket: the RFQ firehose and the market's fills, and the quotes channel in LIVE. */
        val PRICING: (RfqConfig.Mode) -> List<String>? = { m -> if (m == RfqConfig.Mode.LIVE) listOf("rfq", "quotes", "executions") else listOf("rfq", "executions") }
        /** The last-look socket: `quotes` only, and only in LIVE (nothing is quoted before). */
        val LAST_LOOK: (RfqConfig.Mode) -> List<String>? = { m -> if (m == RfqConfig.Mode.LIVE) listOf("quotes") else null }
    }
}
