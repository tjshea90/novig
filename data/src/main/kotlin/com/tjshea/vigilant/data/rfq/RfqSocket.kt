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
 * The maker's connection to Novig's RFQ socket (`GET /rfq/v3/ws`, MK 2026-11-18): registers the subaccount as a maker once (idempotent), opens one signed websocket, subscribes `rfq` and `executions`
 * (and `quotes` in LIVE, without which "we reject every trade you win"), hands every frame to [maker], and reconnects with back-off. Nothing here decides anything: [RfqMaker] does, and in SHADOW it sends
 * nothing back. Novig's upgrade answers `404 MAKER_NOT_FOUND` for a maker that has not reached every server yet (up to a minute after registering): the registration is repeated and the upgrade retried.
 */
class RfqSocket(
    private val http: OkHttpClient,
    private val client: () -> NovigTradingClient?,
    private val maker: RfqMaker,
    private val config: () -> RfqConfig,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) {
    @Volatile private var ws: WebSocket? = null
    @Volatile var connected = false; private set
    @Volatile var makerId: String? = null; private set
    @Volatile var lastNote: String = ""; private set
    @Volatile var connects = 0L; private set
    private var job: Job? = null
    private var backoffMs = 2_000L
    private var registered = false
    private var mode = RfqConfig.Mode.OFF

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { keep() }
    }

    fun stop() { job?.cancel(); job = null; close("stopped") }

    private fun close(why: String) { ws?.close(1000, why); ws = null; connected = false }

    /** Sends one frame; false when there is no connection. */
    fun send(text: String): Boolean = ws?.send(text) == true

    private suspend fun keep() {
        while (scope.isActive) {
            val cfg = config()
            val c = client()
            if (cfg.mode == RfqConfig.Mode.OFF || c == null) {
                if (ws != null) close("mode off")
                lastNote = if (c == null) "no trading key" else "off"
                delay(5_000L); continue
            }
            if (ws != null && mode != cfg.mode) { close("mode changed"); mode = cfg.mode }
            mode = cfg.mode
            if (ws == null) {
                try {
                    if (!registered) {
                        makerId = c.rfqRegister()
                        registered = true
                        log("RFQ maker registered${makerId?.let { " ($it)" }.orEmpty()}")
                        // A new maker can take a minute to reach every server.
                        if (makerId != null) delay(4_000L)
                    }
                    open(c, cfg)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: NovigApiException) {
                    lastNote = "Novig answered ${e.status} ${e.code ?: ""} ${e.serverMessage ?: ""}".trim().take(160)
                    log("RFQ maker: $lastNote")
                    // 404 on the upgrade or the register route: not (yet) a maker here, or the route is not on this host; ask again later.
                    if (e.code == "MAKER_NOT_FOUND") registered = false
                    backoffMs = minOf(backoffMs * 2, 5 * 60_000L)
                    delay(backoffMs)
                } catch (e: Exception) {
                    lastNote = "${e.javaClass.simpleName}: ${e.message?.take(100)}"
                    log("RFQ maker: $lastNote")
                    backoffMs = minOf(backoffMs * 2, 60_000L)
                    delay(backoffMs)
                }
            }
            delay(1_000L)
        }
    }

    private fun open(c: NovigTradingClient, cfg: RfqConfig) {
        connects++
        val request = c.signedRequest("GET", "/rfq/v3/ws")
        ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== ws) return
                connected = true; backoffMs = 2_000L
                lastNote = "connected"
                // `quotes` is needed only to quote: without it a won quote is never seen and Novig rejects the trade.
                val channels = if (cfg.mode == RfqConfig.Mode.LIVE) listOf("rfq", "quotes", "executions") else listOf("rfq", "executions")
                webSocket.send(RfqProtocol.subscribe(0, channels))
                log("RFQ socket open (${cfg.mode}, $channels)")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== ws) return
                RfqProtocol.parse(text, clock())?.let { e -> runCatching { maker.onEvent(e) } }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null); gone(webSocket, "closing $code $reason") }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = gone(webSocket, "closed $code $reason")
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (response?.code == 404) registered = false     // MAKER_NOT_FOUND (not reached every server yet) or no such route here
                gone(webSocket, "failed ${response?.code ?: ""} ${t.message?.take(80) ?: t.javaClass.simpleName}".trim())
            }
        })
    }

    private fun gone(webSocket: WebSocket, why: String) {
        if (webSocket !== ws) return
        ws = null; connected = false
        lastNote = why
        log("RFQ socket $why")
    }
}
