package com.tjshea.vigilant.data.cno

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

/** Reading CNO over a phone's network (Tj, 2026-09-27: "unable to resolve cno", "timeout"). */
@OptIn(ExperimentalCoroutinesApi::class)
class CnoNetworkTest {

    private val cnoIp = listOf(InetAddress.getByName("162.250.75.106"))

    private class FlakyDns(var fail: Boolean, val answer: List<InetAddress>) : Dns {
        override fun lookup(hostname: String): List<InetAddress> = if (fail) throw UnknownHostException(hostname) else answer
    }

    @Test
    fun `when the phone's DNS fails, DNS over HTTPS answers, then the last good address`() {
        val system = FlakyDns(fail = false, answer = cnoIp)
        val doh = FlakyDns(fail = false, answer = listOf(InetAddress.getByName("162.250.75.107")))
        val dns = RememberingDns(system, doh)
        assertEquals(cnoIp, dns.lookup("crazyninjaodds.com"))
        system.fail = true
        assertEquals("162.250.75.107", dns.lookup("crazyninjaodds.com").single().hostAddress) // DoH
        doh.fail = true
        assertEquals("162.250.75.107", dns.lookup("crazyninjaodds.com").single().hostAddress) // remembered
        val fresh = RememberingDns(system, doh)
        assertTrue(runCatching { fresh.lookup("crazyninjaodds.com") }.exceptionOrNull() is UnknownHostException) // nothing known yet
    }

    @Test
    fun `DNS over HTTPS replies are read the way Cloudflare and Google send them`() {
        val cloudflare = """{"Status":0,"TC":false,"RD":true,"RA":true,"AD":false,"CD":false,"Question":[{"name":"crazyninjaodds.com","type":1}],"Answer":[{"name":"crazyninjaodds.com","type":1,"TTL":1399,"data":"162.250.75.106"}]}"""
        val (ips, ttl) = DnsOverHttps.parse(cloudflare)!!
        assertEquals("162.250.75.106", ips.single().hostAddress)
        assertEquals(1399, ttl)
        // A CNAME before the A record, and no answer at all.
        val cname = """{"Status":0,"Answer":[{"name":"x.com.","type":5,"TTL":60,"data":"y.com."},{"name":"y.com.","type":1,"TTL":30,"data":"1.2.3.4"}]}"""
        assertEquals("1.2.3.4", DnsOverHttps.parse(cname)!!.first.single().hostAddress)
        assertTrue(DnsOverHttps.parse("""{"Status":3}""")!!.first.isEmpty())
    }

    @Test
    fun `a request on a dead connection is tried once more at once, and goes through`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setBody("<script>location.replace('novigapp://events/abc/cno');</script>"))
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        val client = CnoClient(OkHttpClient.Builder().retryOnConnectionFailure(false).build(), pace = CnoPace(minGapMs = 0))
        // By address, not "localhost": where that resolves to ::1 first (CI's runners), the first
        // try is refused at ::1 and never reaches the server's dropped connection.
        val url = server.url("/deeplink.aspx?line_id=1").newBuilder().host("127.0.0.1").build()
        val row = CnoRow(0.03, event = "A @ B", market = "M", bet = "X Over 1.5", odds = 110, book = "Novig", betUrl = url.toString())
        assertEquals("novigapp://events/abc/cno", client.novigLink(row))
        assertEquals(2, server.requestCount)
        server.shutdown()
    }

    @Test
    fun `every CNO request keeps its distance from the last, whichever part of the app makes it`() = runTest {
        val pace = CnoPace(minGapMs = 1_000L, clock = { currentTime })
        val times = mutableListOf<Long>()
        repeat(3) {
            pace.await()
            times += currentTime
        }
        assertEquals(listOf(0L, 1_000L, 2_000L), times)
    }

    @Test
    fun `network failures say which one it was - DNS, a timeout, no connection`() {
        assertEquals(
            "the phone couldn't look up its address: no signal, or a VPN reconnecting",
            CnoClient.why(java.net.UnknownHostException("Unable to resolve host \"crazyninjaodds.com\"")),
        )
        assertEquals("it didn't answer in time", CnoClient.why(java.net.SocketTimeoutException("timeout")))
        assertEquals("it didn't answer in time", CnoClient.why(java.io.InterruptedIOException("timeout")))
        assertEquals("no connection to it", CnoClient.why(java.net.ConnectException("Failed to connect")))
        assertEquals("stream was reset: CANCEL", CnoClient.why(java.io.IOException("stream was reset: CANCEL")))
    }
}
