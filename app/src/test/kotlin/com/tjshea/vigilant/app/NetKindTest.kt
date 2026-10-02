package com.tjshea.vigilant.app

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

/** The connection figures name the network the phone was on ([NetKind]): Wi-Fi, mobile, anything else, or none. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NetKindTest {

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

    private fun on(vararg transports: Int): String? {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val caps = ShadowNetworkCapabilities.newInstance()
        transports.forEach { shadowOf(caps).addTransportType(it) }
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, caps)
        return NetKind.of(app)
    }

    @Test
    fun `Wi-Fi, mobile and anything else are told apart`() {
        assertEquals("Wi-Fi", on(NetworkCapabilities.TRANSPORT_WIFI))
        assertEquals("mobile", on(NetworkCapabilities.TRANSPORT_CELLULAR))
        assertEquals("Wi-Fi", on(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_CELLULAR))
        assertEquals("other", on(NetworkCapabilities.TRANSPORT_ETHERNET))
    }

    @Test
    fun `thermal states are named, and an unknown one says so`() {
        assertEquals(listOf("none", "light", "moderate", "severe", "critical", "emergency", "shutdown", "?"), (0..7).map { Diagnostics.thermalName(it) })
    }
}
