package com.tjshea.vigilant.data.novig

import com.tjshea.vigilant.data.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicReference

/**
 * Client ID/secret obtained by "requesting them from Novig" (RESEARCH.md §4.1) — there is no
 * self-serve signup. Whether that's actually free is still unconfirmed (RESEARCH.md §10 item 1)
 * — this class doesn't assume an answer either way, it just holds whatever credentials Tj ends
 * up getting.
 */
data class NovigCredentials(val clientId: String, val clientSecret: String)

@Serializable
private data class TokenRequest(val client_id: String, val client_secret: String)

@Serializable
private data class TokenResponse(val access_token: String, val expires_in: Long)

private data class CachedToken(val value: String, val expiresAtEpochMs: Long)

/**
 * Fetches and caches a Novig OAuth2 access token, refreshing proactively before the documented
 * 30-minute expiry (RESEARCH.md §4.1) rather than waiting for a 401. The exact request/response
 * field names below are inferred from documentation summaries, not confirmed against a real
 * response (RESEARCH.md §10 item 5) — likely to need adjustment once real credentials exist.
 */
class NovigTokenProvider(
    private val httpClient: OkHttpClient,
    private val credentials: NovigCredentials,
    private val json: Json,
    private val authUrl: String = "https://api.novig.com/nbx/v1/auth/emm-token",
    private val refreshMarginMs: Long = 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val cached = AtomicReference<CachedToken?>(null)

    suspend fun getValidToken(): String {
        cached.get()?.let { token ->
            if (clock() < token.expiresAtEpochMs - refreshMarginMs) return token.value
        }
        return refresh()
    }

    private suspend fun refresh(): String {
        val body = json.encodeToString(TokenRequest(credentials.clientId, credentials.clientSecret))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(authUrl).post(body).build()

        httpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw NovigAuthException("Novig token request failed: HTTP ${response.code}")
            }
            val payload = json.decodeFromString(TokenResponse.serializer(), response.body?.string().orEmpty())
            val token = CachedToken(
                value = payload.access_token,
                expiresAtEpochMs = clock() + payload.expires_in * 1000,
            )
            cached.set(token)
            return token.value
        }
    }
}

class NovigAuthException(message: String) : Exception(message)
