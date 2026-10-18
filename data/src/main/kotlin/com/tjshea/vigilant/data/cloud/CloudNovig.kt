package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.PemSigningKey
import com.tjshea.vigilant.data.novig.stream.NovigStream
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * The GitHub engine's Novig connection (Tj, 2026-10-15: "allow me to send my novig key and boltodds key to GitHub for rapid scanning"). With the engine's own `trading::read` key ([CloudNovigKey]; the two secrets
 * [CloudKeys.NOVIG]) the engine reads Novig's books through the signed route (the key's own 14 a second, instead of the public routes' ~2 from a runner) and keeps the key's websocket, exactly as the phone does with
 * its own key ([com.tjshea.vigilant.app.AppContainer.useConnection]). Without the secrets nothing changes: public routes only.
 *
 * Read only by construction: this class builds a signer and hands it to [NovigPublicClient], whose methods only read. No order, no balance, no management call is possible from here, and a `trading::read` key
 * would be refused if one were tried.
 *
 * The phone's Novig switches are mirrored ([configure], each scan): [com.tjshea.vigilant.data.scanner.ApiGates.novigSocket] (off = every read by request), `novigBbo`, `novigKeepOpen`, `novigCompress`.
 * The orders channel is never asked for. A refusal of the runner's address (451 `ANONYMIZED_NETWORK`: GitHub's runners are cloud addresses, "a data-center address is fine" per Novig's docs but that is a verdict the
 * docs make and no runner has yet confirmed) puts the reads back on the public routes for the client's usual stand-down, and [route] says so.
 */
class CloudNovig(
    private val novig: NovigPublicClient,
    private val http: OkHttpClient,
    private val json: Json,
    private val scope: CoroutineScope,
    keyId: String,
    pem: String,
    private val log: (String) -> Unit = {},
    private val baseUrl: String = "https://api.novig.com",
    private val wsUrl: String = "wss://api.novig.com/v3/ws",
) {
    private val signer: NovigSignedClient? = runCatching { NovigSignedClient(http, json, PemSigningKey(keyId.trim(), pem), baseUrl) }.getOrNull()
    private var stream: NovigStream? = null

    /** Why the key cannot be used at all (never key text), or null. */
    val problem: String? = if (signer == null) "the Novig key sent to GitHub could not be read (it must be a PKCS#8 PEM of a P-256 or Ed25519 key)" else null

    /** Sets the client up for a scan under [s]: the key route on, and the stream as the phone's own switches say. Safe to call every scan. */
    @Synchronized
    fun configure(s: ScanSettings) {
        val sg = signer ?: return
        novig.keyed = sg
        val g = s.gates
        if (!g.novigSocket) {
            if (stream != null) { stream?.close(); stream = null; novig.stream = null; log("Novig stream closed (the socket switch is off): every read is by request") }
            return
        }
        val st = stream ?: NovigStream(http, sg, scope, wsUrl = wsUrl, onEvent = { warn, text -> log((if (warn) "Novig stream WARN: " else "Novig stream: ") + text.take(200)) }).also { stream = it; novig.stream = it }
        st.configureFeed(g.novigBbo, g.novigKeepOpen, false)
        st.configureCompression(g.novigCompress)
    }

    /** Closes the stream (the job is ending). */
    @Synchronized
    fun close() { stream?.close(); stream = null; novig.stream = null }

    /**
     * One cheap signed read that learns Novig's verdict on this machine's address BEFORE any odds API is asked (Tj, 2026-10-18, TCL116: "If a novig address is refused, all scanning across all apis should
     * immediately be stopped completely. They should only scan fully when a GitHub runner address is accepted by novig"). true = accepted, false = refused for the ADDRESS, null = unknown (no key sent: the
     * public routes carry on as before). The read goes through the keyed catalog, whose refusal is what [addressRefused] reports.
     */
    suspend fun probe(): Boolean? {
        val sg = signer ?: return null
        novig.keyed = sg
        runCatching { novig.events(listOf("NFL"), listOf(com.tjshea.vigilant.data.novig.NovigEvent.STATUS_PREGAME)) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        return !addressRefused(System.currentTimeMillis())
    }

    /** True while the key route stands down because Novig refused this runner's ADDRESS (not the device check): [AddressWatch] then draws another runner. */
    fun addressRefused(now: Long): Boolean = signer != null && AddressWatch.isAddressRefusal(novig.keyDown(now))

    /** The verdict on THIS machine's internet address, in words, for the dashboard (Tj, 2026-10-17: "make it so the android app reads GitHub runner whether its address is accepted or not"). */
    fun addressWords(now: Long, swaps: Int = 0): String {
        if (signer == null) return "not tested (no Novig key on GitHub: public routes only)"
        val why = novig.keyDown(now)
        val code = AddressWatch.ADDRESS_CODES.firstOrNull { why?.contains(it) == true }
        return if (code != null) "REFUSED by Novig ($code): this GitHub machine's location is not served" + if (swaps > 0) "; $swaps swap${if (swaps == 1) "" else "s"} to a new machine tried in a row" else "; a swap to a new machine is due"
        else if (why != null) "accepted; the key was refused for another reason ($why)"
        else "accepted by Novig"
    }

    /** One line for the status file and the Cloud page: how this scan's Novig reads are going out. */
    fun route(now: Long): String = route(novig, signer != null, stream != null, problem, now)

    companion object {
        /** [route] for an engine with no key at all: the public routes. */
        const val PUBLIC_ONLY = "public routes only (no Novig key was sent to GitHub: about 2 prices a second)"

        fun route(novig: NovigPublicClient, hasKey: Boolean, streaming: Boolean, problem: String?, now: Long): String = when {
            problem != null -> "public routes: $problem"
            !hasKey -> PUBLIC_ONLY
            else -> novig.keyDown(now)?.let { "public routes: the read key was refused ($it); it is tried again shortly" }
                ?: ("Novig read-only key active" + (novig.limits?.let { ": ${it.readPerSec.toInt()} reads a second" } ?: "") + if (streaming) ", websocket on" else ", websocket off (reads by request)")
        }
    }
}
