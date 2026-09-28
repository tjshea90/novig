package com.tjshea.vigilant.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import kotlin.coroutines.resume

/**
 * The phone's connections, for Settings › Novig API › Test key (Tj, 2026-09-28: "The app is telling me I have a proxy
 * or vpn when I test the novig key, but I don't"). Novig judges the internet address a request comes from, so the
 * test says which connection it went out on, whether an app really has a VPN up, and can try the other connection.
 */
class PhoneNetworks(context: Context) {
    private val cm: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

    /** "Wi-Fi", "mobile data" or "Ethernet": what requests go out on now. Null when unknown or offline. */
    fun current(): String? = cm?.activeNetwork?.let { name(it) }

    /** An app on this phone has a VPN up: requests leave through it, from its address. */
    fun vpnUp(): Boolean = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

    private fun name(network: Network): String? {
        val caps = cm?.getNetworkCapabilities(network) ?: return null
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> MOBILE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> null
        }
    }

    /**
     * Runs [block] over the other connection (mobile data when on Wi-Fi, Wi-Fi when on mobile data), asked for and held
     * only while [block] runs. Null when there's no other connection within [timeoutMs].
     */
    suspend fun <T> onOther(timeoutMs: Long = 8_000, block: suspend (name: String, network: Network) -> T): T? {
        val cm = cm ?: return null
        val (transport, other) = when (current()) {
            WIFI, "Ethernet" -> NetworkCapabilities.TRANSPORT_CELLULAR to MOBILE
            MOBILE -> NetworkCapabilities.TRANSPORT_WIFI to WIFI
            else -> return null
        }
        val request = NetworkRequest.Builder().addTransportType(transport).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        var callback: ConnectivityManager.NetworkCallback? = null
        try {
            val network = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Network> { cont ->
                    val cb = object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            if (cont.isActive) cont.resume(network)
                        }
                    }
                    callback = cb
                    runCatching { cm.requestNetwork(request, cb) }.onFailure { if (cont.isActive) cont.cancel(it) }
                }
            } ?: return null
            return block(other, network)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        } finally {
            callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        }
    }

    companion object {
        const val WIFI = "Wi-Fi"
        const val MOBILE = "mobile data"

        /** [base], sending everything over [network] only (its own sockets, name lookups and connection pool). */
        fun bound(base: OkHttpClient, network: Network): OkHttpClient = base.newBuilder()
            .socketFactory(network.socketFactory)
            .dns(Dns { host -> network.getAllByName(host).toList() })
            .connectionPool(ConnectionPool())
            .build()
    }
}
