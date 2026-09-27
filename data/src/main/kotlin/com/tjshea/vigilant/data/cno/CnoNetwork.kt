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
    fun client(base: OkHttpClient, dns: Dns = RememberingDns()): OkHttpClient = base.newBuilder()
        .connectionPool(ConnectionPool(2, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS))
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .dns(dns)
        .build()
}

/**
 * The system's DNS, with the last good answer per host kept for [keepMs]: when the phone's DNS
 * fails for a moment (a network switch, a VPN reconnecting, a flaky mobile resolver), the host is
 * still at the address it was at a minute ago. CNO's record changes rarely (TTL one hour).
 */
class RememberingDns(
    private val system: Dns = Dns.SYSTEM,
    private val keepMs: Long = 24 * 60 * 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) : Dns {
    private class Known(val addresses: List<InetAddress>, val atMs: Long)

    private val known = ConcurrentHashMap<String, Known>()

    override fun lookup(hostname: String): List<InetAddress> = try {
        system.lookup(hostname).also { if (it.isNotEmpty()) known[hostname] = Known(it, clock()) }
    } catch (e: UnknownHostException) {
        known[hostname]?.takeIf { clock() - it.atMs < keepMs }?.addresses ?: throw e
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
