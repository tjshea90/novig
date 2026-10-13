package com.tjshea.vigilant.data.kalshi

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import com.tjshea.vigilant.data.await

/**
 * Settings › Kalshi › Test key (KALSHI_API.md): is the Key ID with its private key accepted, what tier and read budget the account has, and does the socket's signed handshake open and answer a subscribe.
 * Two short calls: `GET /account/limits` (signed, 10 tokens) and one connection that subscribes the lifecycle channel and closes. Said in words for the settings page.
 */
object KalshiKeyTest {
    data class Result(val ok: Boolean, val summary: String)

    suspend fun run(
        http: OkHttpClient,
        signer: KalshiSigner,
        restBase: String = DEFAULT_REST,
        wsUrl: String = KalshiStream.DEFAULT_WS_URL,
        clock: () -> Long = System::currentTimeMillis,
        socketWaitMs: Long = 6_000L,
    ): Result {
        val lines = ArrayList<String>()
        var ok = true
        // 1. The signed REST call.
        try {
            val url = "$restBase/account/limits".toHttpUrl()
            val req = Request.Builder().url(url).get().apply { signer.headers("GET", url, clock()).forEach { (k, v) -> header(k, v) } }.build()
            http.newCall(req).await().use { r ->
                val body = r.body?.string().orEmpty()
                when {
                    r.isSuccessful -> {
                        val o = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                        val tier = (o?.get("usage_tier") as? JsonPrimitive)?.contentOrNull
                        fun bucket(k: String): String {
                            val b = o?.get(k) as? JsonObject
                            val rate = (b?.get("refill_rate") as? JsonPrimitive)?.contentOrNull
                            val cap = (b?.get("bucket_capacity") as? JsonPrimitive)?.contentOrNull
                            return if (rate == null) "?" else "$rate tokens/s (bucket $cap)"
                        }
                        lines += "✓ Key accepted (${signer.algorithm}). Tier ${tier ?: "?"}: reads ${bucket("read")}, writes ${bucket("write")}. A request costs 10 tokens by default."
                    }
                    r.code == 401 || r.code == 403 -> {
                        ok = false
                        lines += "✗ Kalshi refused the key (HTTP ${r.code}). Check the Key ID, and that the private key is from the same key pair (Kalshi never shows it again after the page closes)."
                    }
                    else -> { ok = false; lines += "✗ Kalshi answered HTTP ${r.code} to the account check." }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ok = false
            lines += "✗ Could not reach Kalshi (${e.message?.take(80) ?: e.javaClass.simpleName})."
        }
        // 2. The socket's signed handshake and one subscribe.
        val answer = CompletableDeferred<String>()
        val opened = CompletableDeferred<Unit>()
        var socket: WebSocket? = null
        try {
            val ts = clock()
            val request = Request.Builder().url(wsUrl).apply { signer.headers("GET", KalshiSigner.WS_PATH, ts).forEach { (k, v) -> header(k, v) } }.build()
            socket = http.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        opened.complete(Unit)
                        webSocket.send("""{"id":1,"cmd":"subscribe","params":{"channels":["market_lifecycle_v2"]}}""")
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val t = runCatching { (Json.parseToJsonElement(text) as? JsonObject)?.get("type") as? JsonPrimitive }.getOrNull()?.contentOrNull
                        if (t == "subscribed" || t == "error") answer.complete(text.take(160))
                    }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { answer.complete("closed by Kalshi: code $code ${reason.take(80)}") }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        answer.complete("failed${response?.let { " (HTTP ${it.code})" } ?: ""}: ${t.message?.take(80) ?: t.javaClass.simpleName}")
                    }
                },
            )
            val said = withTimeoutOrNull(socketWaitMs) { answer.await() }
            lines += when {
                said == null -> { ok = false; "✗ The socket opened no conversation within ${socketWaitMs / 1000} s." }
                said.contains("\"subscribed\"") -> "✓ Socket: the signed handshake opened and Kalshi acknowledged a subscription."
                else -> { ok = false; "✗ Socket: $said" }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ok = false
            lines += "✗ Socket: ${e.message?.take(80) ?: e.javaClass.simpleName}"
        } finally {
            socket?.close(1000, "test done")
        }
        return Result(ok, lines.joinToString("\n"))
    }

    const val DEFAULT_REST = "https://external-api.kalshi.com/trade-api/v2"
}
