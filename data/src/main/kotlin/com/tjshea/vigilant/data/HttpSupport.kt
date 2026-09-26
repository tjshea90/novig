package com.tjshea.vigilant.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
