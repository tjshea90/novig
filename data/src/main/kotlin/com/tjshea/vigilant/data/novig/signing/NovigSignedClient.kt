package com.tjshea.vigilant.data.novig.signing

import com.tjshea.vigilant.data.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** A signed-route failure, with Novig's stable `code` (when sent) and a plain-English fix. */
class NovigApiException(val status: Int, val code: String?, val serverMessage: String?) :
    IOException("Novig HTTP $status" + (code?.let { " $it" } ?: "") + (serverMessage?.let { ": $it" } ?: "")) {

    /**
     * Novig judged the request's network (its internet address), not the phone: another connection can pass
     * (docs.novig.com/api/errors, 451: "The first two codes judge the request's network").
     */
    val networkRefusal: Boolean get() = status == 451 && code in NETWORK_CODES

    /**
     * One line for a scan's banner. A network refusal reads as what it is (Novig's verdict on an address), not "you
     * have a VPN" (Tj, 2026-09-28: "The app is telling me I have a proxy or vpn when I test the novig key, but I don't").
     */
    val brief: String
        get() = when {
            status == 451 && code == "ANONYMIZED_NETWORK" ->
                "Novig lists the internet address of the network this phone is on as a VPN or proxy ($code), so the key can't read here. Settings › Novig API › Test key checks your other connection."
            status == 451 && code == "RESTRICTED_NETWORK_REGION" ->
                "Novig places this network's internet address outside the states it serves ($code). Settings › Novig API › Test key checks your other connection."
            else -> advice
        }

    /** What Tj should actually do about it (NOVIG_API.md §4, §11; docs.novig.com/api/errors). */
    val advice: String
        get() = when {
            status == 451 && code == "ANONYMIZED_NETWORK" ->
                "Novig's network screen lists the internet address this phone is using as a VPN, proxy or Tor exit (Novig code $code). " +
                    "It judges the address, not the phone: with no VPN on, it's the Wi-Fi's or the carrier's shared address that's listed. " +
                    "Try the other connection (Wi-Fi or mobile data). If your usual one keeps failing, ask Novig support to review it, quoting $code."
            status == 451 && code == "RESTRICTED_NETWORK_REGION" ->
                "Novig places the internet address this phone is using in a state it doesn't serve (Novig code $code). Carrier addresses are " +
                    "often registered far from where you are: try the other connection (Wi-Fi or mobile data)."
            status == 451 && code == "RESTRICTED_GEOLOCATION_REGION" -> "Novig says your last location check is in a restricted state (Novig code $code)."
            status == 451 -> "Novig needs its location check: open the Novig app for a moment so it can geolocate, then try again" + (code?.let { " (Novig code $it)." } ?: ".")
            status == 503 && code == "GEOLOCATION_SCREENING_UNAVAILABLE" -> "Novig's location screening is down for the moment. Try again in a few minutes."
            // docs.novig.com/api/errors (read 2026-10-02): "A 423 doesn't clear on its own. Contact support." Four codes lock one bet's market, game,
            // league or player; the rest the account or the whole exchange.
            status == 423 && code in BET_LOCK_CODES ->
                "Novig has locked this ${BET_LOCK_WHAT.getValue(code!!)} (Novig code $code): this bet can't be placed now; others still can."
            status == 423 && code == "SYSTEM_LOCKED" -> "Novig has halted trading on the whole exchange (Novig code $code). Nothing can be bet until it reopens; Novig support can say when."
            status == 423 && code == "SELF_EXCLUDED" -> "Novig says this account is self-excluded (Novig code $code): it can't bet until Novig support lifts it."
            status == 423 && code != null -> "Novig has locked the account for betting (Novig code $code). It doesn't clear on its own: contact Novig support and quote $code."
            status == 423 -> "Novig reports the account is locked, self-excluded, or trading is halted (no code given). Contact Novig support."
            status == 401 && serverMessage?.contains("timestamp") == true -> "The phone's clock is off by more than 30 seconds. Turn on automatic date & time."
            status == 401 && serverMessage?.contains("not found") == true -> "Novig doesn't know that key ID. Check it was copied exactly, and that the key is a production (not QA) key."
            status == 401 && serverMessage?.contains("revoked") == true -> "That key was revoked. Create a new one on novig.com → Profile → Settings → Novig API."
            status == 401 -> "Novig rejected the signature. Make sure the key ID and the key file are from the same key."
            status == 403 && serverMessage?.contains("scope") == true -> "This key's scope can't do that. The scanner needs a trading or trading::read key; setup creates one."
            status == 403 && serverMessage?.contains("VPN") == true -> "Keys can't be created over a VPN or proxy. Turn it off and try again."
            status == 403 && serverMessage?.contains("KYC") == true -> "Novig needs your identity check (KYC) finished first."
            status == 403 -> "Novig's edge refused the request (usually too many requests). Wait a minute."
            status == 409 -> "Novig already has that key or limit (one management key, one trading key per subaccount)."
            status == 422 -> "Novig refused the order: the Vigilant subaccount doesn't have enough money for it, or a position limit stopped it" + (serverMessage?.let { " ($it)." } ?: ".")
            status == 404 -> serverMessage ?: "Novig doesn't know that market or order (it may have closed)."
            status == 429 -> "Novig asked us to slow down. Try again in a few seconds."
            code == "SIGNING_FAILED" -> serverMessage ?: "The Novig key on this phone can't sign. Connect it again in Settings."
            else -> serverMessage ?: "Novig returned HTTP $status."
        }

    /** A 423 that locks this one bet's market, game, league or player, not the account ([BET_LOCK_CODES]). */
    val betLocked: Boolean get() = status == 423 && code in BET_LOCK_CODES

    companion object {
        /** The 451 codes about the request's network rather than the key holder's device. */
        val NETWORK_CODES = setOf("ANONYMIZED_NETWORK", "RESTRICTED_NETWORK_REGION")

        /** The 423 codes that lock one bet's market, game (event), league or competitor (docs.novig.com/api/errors). */
        val BET_LOCK_WHAT = mapOf("MARKET_LOCKED" to "market", "EVENT_LOCKED" to "game", "LEAGUE_LOCKED" to "league", "COMPETITOR_LOCKED" to "player or team")
        val BET_LOCK_CODES = BET_LOCK_WHAT.keys
    }
}

@Serializable
data class KeyCreated(val keyId: String, val algorithm: String? = null, val fingerprint: String? = null)

@Serializable
data class SubaccountRow(val keyId: String, val label: String? = null)

@Serializable
private data class ErrorBody(val code: String? = null, val message: String? = null)

/**
 * Novig's signed routes (NOVIG_API.md §2–4). Every request, GETs included, carries the three
 * NOVIG-V3 headers. The body is serialized once to bytes, and those exact bytes are both hashed
 * and sent; re-serializing after signing is the classic way to fail verification.
 */
class NovigSignedClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val key: NovigSigningKey,
    private val baseUrl: String = "https://api.novig.com",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val jsonType = "application/json".toMediaType()

    /** `POST /v3/echo`: a 200 proves host, key, clock and signature are all right. Costs 0 tokens. */
    suspend fun echo(): String = call("POST", "/v3/echo", body = """{"hello":"vigilant"}""")

    suspend fun listSubaccounts(): List<SubaccountRow> =
        json.decodeFromString(ListSerializer(SubaccountRow.serializer()), call("GET", "/v3/account/subaccounts"))

    /** Opens a subaccount and registers its trading key in one call. Needs the management key. */
    suspend fun openSubaccount(label: String, publicKeyPem: String, algorithm: NovigKeyAlgorithm): KeyCreated =
        json.decodeFromString(
            KeyCreated.serializer(),
            call("POST", "/v3/account/subaccounts", body = body(mapOf("label" to label, "publicKey" to publicKeyPem, "algorithm" to algorithm.apiName))),
        )

    /** Adds a `trading::read` (or replacement `trading`) key to a subaccount. Needs the management key. */
    suspend fun createSubaccountKey(tradingKeyId: String, name: String, publicKeyPem: String, algorithm: NovigKeyAlgorithm, scope: String): KeyCreated =
        json.decodeFromString(
            KeyCreated.serializer(),
            call(
                "POST", "/v3/account/subaccounts/$tradingKeyId/keys",
                body = body(mapOf("name" to name, "publicKey" to publicKeyPem, "algorithm" to algorithm.apiName, "scope" to scope)),
            ),
        )

    /** A signed request, for callers (the websocket upgrade) that build their own call. */
    fun signedRequest(verb: String, path: String, rawQuery: String? = null, body: String? = null): Request {
        val bytes = body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val headers = NovigV3.headers(key, clock(), verb, path, rawQuery, bytes)
        val url = baseUrl + path + (rawQuery?.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: "")
        val requestBody = when {
            body != null -> bytes.toRequestBody(jsonType)
            verb == "GET" || verb == "DELETE" -> null
            else -> ByteArray(0).toRequestBody(jsonType)
        }
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        // Always declared: without it Novig hashes zero bytes instead of the body (NOVIG_API.md §3).
        builder.header("Content-Type", "application/json")
        return builder.method(verb, requestBody).build()
    }

    /** One signed call; the reply's body, or a [NovigApiException] with Novig's own code. */
    suspend fun call(verb: String, path: String, rawQuery: String? = null, body: String? = null): String {
        http.newCall(signedRequest(verb, path, rawQuery, body)).await().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val err = runCatching { json.decodeFromString(ErrorBody.serializer(), text) }.getOrNull()
                throw NovigApiException(response.code, err?.code, err?.message ?: text.take(120).takeIf { !it.trimStart().startsWith("<") })
            }
            return text
        }
    }

    private fun body(fields: Map<String, String>): String =
        json.encodeToString(JsonObject.serializer(), JsonObject(fields.mapValues { JsonPrimitive(it.value) }))
}
