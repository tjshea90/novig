package com.tjshea.vigilant.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The app's one HTTP client. OkHttp runs at most 5 requests at once per host unless told otherwise, and an open
 * websocket holds one of those for as long as it's open (OkHttp 4). So a connected Novig key's websocket left its
 * reads 4 lanes to api.novig.com, not the 6 (now [NOVIG_KEYED_IN_FLIGHT]) Vigilant paces for, and anything else
 * reading Novig at the time (the CNO list's live prices, the board) took from the same 4 (Tj, 2026-09-28: "now it is
 * reading the API very slow"). [MAX_PER_HOST] leaves room for all of them; each client still paces its own host.
 */
fun vigilantHttpClient(interceptors: List<okhttp3.Interceptor> = emptyList()): OkHttpClient = OkHttpClient.Builder()
    .dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_PER_HOST })
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .apply { interceptors.forEach { addInterceptor(it) } }
    .build()

/** Requests at once to one host: the key's 10 book reads, its websocket, and room for the rest of the app. */
const val MAX_PER_HOST = 16

/** Book reads a connected Novig key keeps in flight ([com.tjshea.vigilant.data.novig.NovigPublicClient]). */
const val NOVIG_KEYED_IN_FLIGHT = 10

/** A coroutine-friendly way to run an OkHttp call without blocking a thread on `execute()`. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }
    })
}

/** A whole HTTP reply, read to the end: status, headers, body. */
class HttpText(val code: Int, val headers: okhttp3.Headers, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
    fun header(name: String): String? = headers[name]
}

/**
 * Runs the call and reads its whole body on OkHttp's own thread, never the caller's. A body read
 * on the caller's thread is a network wait on whatever thread that is (the main thread, for the
 * CNO list and its books: the screen stalls on a slow network). Cancelling the coroutine cancels
 * the call, headers or body, and throws [kotlinx.coroutines.CancellationException], never a
 * network error.
 */
suspend fun Call.awaitText(): HttpText = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            val text = try {
                response.use { HttpText(it.code, it.headers, it.body?.string().orEmpty()) }
            } catch (e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
                return
            }
            continuation.resume(text)
        }
    })
}
