package com.tjshea.vigilant.data.cno

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * How Vigilant talks to CrazyNinjaOdds over a phone's network (Tj, 2026-09-27: "sometimes it says
 * unable to resolve cno sometimes it says timeout"; RESEARCH.md §20.2). CNO is one IIS server on
 * shared hosting (Winhost), no CDN: nothing in front of it retries or caches for us.
 */
object CnoNetwork {

    /** Connections idle longer than this are closed, not reused (see [client]). */
    const val KEEP_ALIVE_SECONDS = 20L

    /** A read that hasn't answered in this long is given up (and retried once on a fresh connection). */
    const val READ_TIMEOUT_SECONDS = 12L

    /**
     * The client CNO's requests use, built on the app's shared one (same dispatcher and TLS
     * setup) with its own connection pool: a phone that slept or changed networks leaves a dead
     * pooled connection behind, and a request on it hangs until it times out ("timeout"). Closing
     * connections after [KEEP_ALIVE_SECONDS] idle means a request after a pause starts clean;
     * the list's own reads (every few seconds) keep one warm while the scanner is on screen.
     * DNS remembers CNO's last address for when the phone's DNS fails ("unable to resolve").
     */
    fun client(base: OkHttpClient, online: () -> Boolean = { true }, dns: Dns = RememberingDns(fallback = DnsOverHttps(base), online = online)): OkHttpClient = base.newBuilder()
        .connectionPool(ConnectionPool(2, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS))
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .dns(dns)
        .build()
}

/**
 * DNS that doesn't give up (Tj, 2026-09-27: "unable to resolve cno"). The phone's own DNS first
 * (fast, cached by Android); when it fails (a network switch, a VPN reconnecting, a flaky mobile
 * resolver), [fallback] (DNS over HTTPS to Cloudflare or Google, which doesn't depend on the
 * phone's resolver); when that fails too, the last good answer, kept for [keepMs]. CNO's record
 * changes rarely (TTL one hour).
 */
class RememberingDns(
    private val system: Dns = Dns.SYSTEM,
    private val fallback: Dns? = null,
    private val keepMs: Long = 24 * 60 * 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
    /** The phone has a network: with none, [fallback] can't reach its resolvers either, so it isn't asked (Tj's v0.52.0 file: 50 such calls, all failed). */
    private val online: () -> Boolean = { true },
) : Dns {
    private class Known(val addresses: List<InetAddress>, val atMs: Long)

    private val known = ConcurrentHashMap<String, Known>()

    override fun lookup(hostname: String): List<InetAddress> {
        val first = try {
            system.lookup(hostname)
        } catch (e: UnknownHostException) {
            val second = fallback?.takeIf { runCatching { online() }.getOrDefault(true) }?.let { runCatching { it.lookup(hostname) }.getOrNull() }
            if (second.isNullOrEmpty()) {
                return known[hostname]?.takeIf { clock() - it.atMs < keepMs }?.addresses ?: throw e
            }
            second
        }
        if (first.isNotEmpty()) known[hostname] = Known(first, clock())
        return first
    }
}

/**
 * DNS over HTTPS (Cloudflare's 1.1.1.1, then Google's 8.8.8.8), for when the phone's own DNS
 * fails. The resolvers are reached at their fixed addresses, so asking them needs no DNS itself;
 * TLS still checks their certificates by name. Answers are kept for their TTL (at most an hour).
 * Only the host name being looked up is sent (crazyninjaodds.com), nothing about the user.
 */
class DnsOverHttps(
    base: OkHttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
) : Dns {
    private val http: OkHttpClient = base.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                RESOLVERS[hostname]?.map { InetAddress.getByName(it) } ?: Dns.SYSTEM.lookup(hostname)
        })
        .build()

    private class Answer(val addresses: List<InetAddress>, val untilMs: Long)

    private val answers = ConcurrentHashMap<String, Answer>()

    override fun lookup(hostname: String): List<InetAddress> {
        answers[hostname]?.takeIf { it.untilMs > clock() }?.let { return it.addresses }
        for (url in listOf("https://cloudflare-dns.com/dns-query", "https://dns.google/resolve")) {
            val found = runCatching { ask(url, hostname) }.getOrNull() ?: continue
            if (found.first.isNotEmpty()) {
                answers[hostname] = Answer(found.first, clock() + found.second.coerceIn(30, 3_600) * 1000L)
                return found.first
            }
        }
        throw UnknownHostException("$hostname: DNS over HTTPS didn't answer either")
    }

    /** The A records for [host] from one resolver's JSON API, and their TTL in seconds. */
    private fun ask(url: String, host: String): Pair<List<InetAddress>, Int>? {
        val request = okhttp3.Request.Builder()
            .url("$url?name=$host&type=A")
            .header("Accept", "application/dns-json")
            .build()
        val body = http.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return null
        return parse(body)
    }

    companion object {
        /** The resolvers' own fixed addresses. */
        private val RESOLVERS = mapOf(
            "cloudflare-dns.com" to listOf("1.1.1.1", "1.0.0.1"),
            "dns.google" to listOf("8.8.8.8", "8.8.4.4"),
        )

        /** A DNS JSON reply (RFC 8427 style, both resolvers): the A records and the lowest TTL. */
        fun parse(body: String): Pair<List<InetAddress>, Int>? = runCatching {
            val root = kotlinx.serialization.json.Json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject
            val answers = (root["Answer"] as? kotlinx.serialization.json.JsonArray).orEmpty()
                .mapNotNull { it as? kotlinx.serialization.json.JsonObject }
                .filter { (it["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "1" }
            val ips = answers.mapNotNull { a ->
                (a["data"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { IPV4.matches(it) }?.let { InetAddress.getByName(it) }
            }
            val ttl = answers.mapNotNull { (it["TTL"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() }.minOrNull() ?: 300
            ips to ttl
        }.getOrNull()

        private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    }
}

/**
 * One pace for every request to CNO, whichever part of the app makes it (the list, the green-check
 * books, the bet links): never two closer than [minGapMs], so the app's parts together never hit
 * CNO's one small server in bursts (Tj, 2026-09-27: "I think cno is restricting or slowing me
 * down"; CNO's robots.txt asks for 30 s between a crawler's pages, and the app reads far more
 * often than that while the scanner is on screen).
 */
class CnoPace(
    private val minGapMs: Long = MIN_GAP_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var lastMs = Long.MIN_VALUE / 2

    /** Waits until a request may go, then books the slot. */
    suspend fun await() {
        mutex.withLock {
            val wait = lastMs + minGapMs - clock()
            if (wait > 0) delay(wait)
            lastMs = clock()
        }
    }

    companion object {
        const val MIN_GAP_MS = 1_000L
    }
}
