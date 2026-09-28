package com.tjshea.vigilant.data.novig.signing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Tj, 2026-09-28: "The app is telling me I have a proxy or vpn when I test the novig key, but I don't." Novig's 451
 * `ANONYMIZED_NETWORK` is its verdict on the internet address a request came from (docs.novig.com/api/errors), not on
 * the phone: the app said "Turn the VPN off" to someone with no VPN.
 */
class NovigKeyTestTest {

    private val anonymized = NovigApiException(451, "ANONYMIZED_NETWORK", "request from an anonymized network")
    private val region = NovigApiException(451, "RESTRICTED_NETWORK_REGION", null)

    @Test
    fun `each 451 code says what Novig actually judged, with its code`() {
        assertTrue(anonymized.networkRefusal)
        assertTrue(region.networkRefusal)
        assertTrue(anonymized.advice.contains("ANONYMIZED_NETWORK"))
        assertTrue(anonymized.advice.contains("judges the address, not the phone"))
        assertFalse("no longer tells Tj to turn off a VPN he doesn't have", anonymized.advice.contains("Turn the VPN off"))
        // RESTRICTED_NETWORK_REGION used to read as "open the Novig app", which can't fix an address.
        assertTrue(region.advice.contains("other connection"))
        assertFalse(region.advice.contains("open the Novig app"))
        for (device in listOf("GEOLOCATION_NOT_FOUND", "GEOLOCATION_FAILED", "INVALID_GEOLOCATION_REGION", "GEOLOCATION_EXPIRED")) {
            val e = NovigApiException(451, device, null)
            assertFalse(e.networkRefusal)
            assertTrue(device, e.advice.contains("open the Novig app") && e.advice.contains(device))
        }
        assertTrue(NovigApiException(503, "GEOLOCATION_SCREENING_UNAVAILABLE", null).advice.contains("down for the moment"))
        // A scan's banner is one line and points at the test.
        assertTrue(anonymized.brief.contains("Test key"))
    }

    @Test
    fun `accepted - says over which connection`() {
        val r = NovigKeyTest.report(NovigKeyTest.Attempt("Wi-Fi"), vpnOnPhone = false)
        assertEquals("Novig accepted the key over Wi-Fi (signature, clock and network all OK).", r.message)
        assertNull(r.error)
    }

    @Test
    fun `flagged on Wi-Fi with no VPN on the phone, fine on mobile data - says exactly that`() {
        val first = NovigKeyTest.Attempt("Wi-Fi", anonymized)
        assertTrue(NovigKeyTest.worthOtherNetwork(first))
        val r = NovigKeyTest.report(first, vpnOnPhone = false, other = NovigKeyTest.Attempt("mobile data"))
        assertNull(r.message)
        val e = r.error!!
        assertTrue(e, e.contains("Tested over Wi-Fi."))
        assertTrue(e, e.contains("No VPN is running on this phone."))
        assertTrue(e, e.contains("Over mobile data the key works: Novig lists only the Wi-Fi address."))
    }

    @Test
    fun `a VPN really up on the phone is named, and a refusal everywhere is reported`() {
        val r = NovigKeyTest.report(NovigKeyTest.Attempt("mobile data", anonymized), vpnOnPhone = true, other = NovigKeyTest.Attempt("Wi-Fi", anonymized))
        assertTrue(r.error!!.contains("A VPN is up on this phone right now"))
        assertTrue(r.error!!.contains("Over Wi-Fi Novig refused it too (ANONYMIZED_NETWORK)."))
        assertTrue(NovigKeyTest.report(NovigKeyTest.Attempt("Wi-Fi", anonymized), vpnOnPhone = false).error!!.contains("wasn't available to compare"))
    }

    @Test
    fun `other failures keep their own advice and aren't retried elsewhere`() {
        val clock = NovigKeyTest.Attempt("Wi-Fi", NovigApiException(401, "SIGNATURE_REJECTED", "novig-timestamp is too old"))
        assertFalse(NovigKeyTest.worthOtherNetwork(clock))
        assertTrue(NovigKeyTest.report(clock, vpnOnPhone = false).error!!.startsWith("The phone's clock is off"))
        val offline = NovigKeyTest.Attempt(null, IOException("timeout"))
        assertFalse(NovigKeyTest.worthOtherNetwork(offline))
        assertEquals("timeout", NovigKeyTest.report(offline, vpnOnPhone = false).error)
    }
}
