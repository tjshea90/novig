package com.tjshea.vigilant.app

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * Tj, 2026-09-28: "The app is telling me I have a proxy or vpn when I test the novig key, but I don't." Test key now
 * says which connection it used and whether a VPN is really up on the phone.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PhoneNetworksTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private fun active(vararg transports: Int) {
        val caps = ShadowNetworkCapabilities.newInstance()
        transports.forEach { shadowOf(caps).addTransportType(it) }
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, caps)
    }

    @Test
    fun `names the connection a request goes out on`() {
        active(NetworkCapabilities.TRANSPORT_WIFI)
        assertEquals("Wi-Fi", PhoneNetworks(context).current())
        active(NetworkCapabilities.TRANSPORT_CELLULAR)
        assertEquals("mobile data", PhoneNetworks(context).current())
    }

    @Test
    fun `a VPN is reported only when one is really up`() {
        active(NetworkCapabilities.TRANSPORT_WIFI)
        assertFalse(PhoneNetworks(context).vpnUp())
        active(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_VPN)
        assertTrue(PhoneNetworks(context).vpnUp())
        assertEquals("Wi-Fi", PhoneNetworks(context).current())
    }
}
