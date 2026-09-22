package com.tjshea.vigilant.data.novig

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response

/**
 * One parsed message off Novig's `tape` WebSocket. See RESEARCH.md §4.1/§7.
 *
 * Same caveat as [NovigApiClient] (RESEARCH.md §4.4): this shape was inferred from a
 * doc-summarizing fetch of docs.novig.com, and a second, independently-verified source (real
 * working code, not documentation) found that same doc site's assumed OAuth/developer-portal
 * system "does not hold up." Treat this class as dormant and unconfirmed, not just its field
 * shapes, until Novig's still-unanswered developers@novig.com reply settles whether this official,
 * credentialed path exists at all.
 */
sealed interface NovigTapeEvent {
    data class OrderBookUpdate(
        val marketId: String,
        val outcomeId: String,
        val price: Double,
        /** PLACE or CANCEL, per RESEARCH.md §4.1. */
        val eventType: String,
    ) : NovigTapeEvent

    data class MarketLifecycle(
        val marketId: String,
        /** OPEN, END, CLOSE, START, EVENT_GOLIVE, EVENT_UNLIVE, per RESEARCH.md §4.1. */
        val state: String,
    ) : NovigTapeEvent

    /** A message we received but don't have a typed shape for yet — surfaced, never dropped silently. */
    data class Unknown(val raw: String) : NovigTapeEvent
}

/**
 * Streams live updates off Novig's WebSocket. A single persistent connection is intentional —
 * RESEARCH.md §7's battery guidance is explicit that one socket beats one-per-market.
 */
interface NovigLiveFeed {
    fun connect(channels: List<String> = listOf("tape", "lifecycle")): Flow<NovigTapeEvent>
}

/**
 * Real OkHttp-backed implementation of [NovigLiveFeed]. As with [NovigApiClient], the message
 * shapes below (and the subscribe-message shape) are inferred from documentation summaries, not
 * a real captured message — RESEARCH.md §10 item 5. [parseTapeMessage] is kept as a standalone
 * pure function for exactly that reason: it's the one thing likely to need a fast, isolated fix.
 *
 * Reconnection with exponential backoff (RESEARCH.md §7) is deliberately NOT implemented in this
 * class — that belongs in whatever owns this feed's lifecycle (the `app` module's foreground
 * service, since backoff needs to know about app foreground/background state to be battery-
 * sensible), not baked into the low-level socket wrapper.
 */
class NovigWebSocketLiveFeed(
    private val httpClient: OkHttpClient,
    private val tokenProvider: NovigTokenProvider,
    private val json: Json,
    private val wsUrl: String = "wss://api.novig.com/tape",
) : NovigLiveFeed {

    override fun connect(channels: List<String>): Flow<NovigTapeEvent> = callbackFlow {
        val token = tokenProvider.getValidToken()
        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Bearer $token")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                for (channel in channels) {
                    webSocket.send(json.encodeToString(SubscribeMessage.serializer(), SubscribeMessage(channel)))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                trySend(parseTapeMessage(text, json))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        }

        val socket = httpClient.newWebSocket(request, listener)
        awaitClose { socket.close(1000, "client closed") }
    }

    companion object {
        fun parseTapeMessage(raw: String, json: Json): NovigTapeEvent {
            return try {
                val envelope = json.decodeFromString(TapeEnvelope.serializer(), raw)
                when (envelope.type) {
                    "PLACE", "CANCEL" -> {
                        val body = envelope.orderBook
                        if (body != null) {
                            NovigTapeEvent.OrderBookUpdate(
                                marketId = body.marketId,
                                outcomeId = body.outcomeId,
                                price = body.price,
                                eventType = envelope.type,
                            )
                        } else {
                            NovigTapeEvent.Unknown(raw)
                        }
                    }
                    "OPEN", "END", "CLOSE", "START", "EVENT_GOLIVE", "EVENT_UNLIVE" -> {
                        val body = envelope.lifecycle
                        if (body != null) {
                            NovigTapeEvent.MarketLifecycle(marketId = body.marketId, state = envelope.type)
                        } else {
                            NovigTapeEvent.Unknown(raw)
                        }
                    }
                    else -> NovigTapeEvent.Unknown(raw)
                }
            } catch (e: Exception) {
                NovigTapeEvent.Unknown(raw)
            }
        }
    }
}

@Serializable
private data class SubscribeMessage(val channel: String, val action: String = "subscribe")

@Serializable
private data class TapeEnvelope(
    val type: String,
    @SerialName("orderBook") val orderBook: OrderBookBody? = null,
    @SerialName("lifecycle") val lifecycle: LifecycleBody? = null,
    val extra: JsonElement? = null,
)

@Serializable
private data class OrderBookBody(val marketId: String, val outcomeId: String, val price: Double)

@Serializable
private data class LifecycleBody(val marketId: String)
