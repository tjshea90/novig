package com.tjshea.vigilant.data.novig

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test

class HttpSlotsProbe {
    @Test
    fun probe() {
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
        server.start()
        val http = OkHttpClient()
        val opened = java.util.concurrent.CountDownLatch(1)
        http.newWebSocket(Request.Builder().url(server.url("/ws")).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { opened.countDown() }
        })
        opened.await()
        Thread.sleep(200)
        println("PROBE running=${http.dispatcher.runningCallsCount()} perHost=${http.dispatcher.maxRequestsPerHost}")
        server.shutdown()
    }
}
