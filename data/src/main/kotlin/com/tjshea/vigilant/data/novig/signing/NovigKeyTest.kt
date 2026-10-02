package com.tjshea.vigilant.data.novig.signing

/**
 * What Settings › Betting & Novig account › Test key says (Tj, 2026-09-28: "The app is telling me I have a proxy or vpn when I test
 * the novig key, but I don't"). Novig's `ANONYMIZED_NETWORK` and `RESTRICTED_NETWORK_REGION` judge the internet
 * address a request comes from, not the phone (docs.novig.com/api/errors), so the test says which connection it used,
 * whether this phone really has a VPN up, and, when Novig blamed the address, what it says over the other connection.
 * Pure: the app does the network work and hands the outcomes in.
 */
object NovigKeyTest {

    /** One try of the key: over [network] ("Wi-Fi", "mobile data"; null = unknown), accepted when [error] is null. */
    data class Attempt(val network: String?, val error: Throwable? = null) {
        val ok: Boolean get() = error == null
        val refusal: NovigApiException? get() = error as? NovigApiException
    }

    /** The test's outcome: [message] when the key works where the phone is now, else [error]. */
    data class Report(val message: String?, val error: String?)

    /** Whether a failed [first] try is worth repeating over the phone's other connection. */
    fun worthOtherNetwork(first: Attempt): Boolean = first.refusal?.networkRefusal == true

    fun report(first: Attempt, vpnOnPhone: Boolean, other: Attempt? = null): Report {
        val over = first.network?.let { " over $it" }.orEmpty()
        if (first.ok) {
            return Report(
                "Novig accepted the key$over (signature, clock and network all OK)." +
                    if (vpnOnPhone) " A VPN is up on this phone, and Novig accepted its address." else "",
                null,
            )
        }
        val refusal = first.refusal
        if (refusal == null || !refusal.networkRefusal) {
            return Report(null, (refusal?.advice ?: first.error?.message ?: "The test failed.") + over.let { if (it.isEmpty()) "" else " (Tested$it.)" })
        }
        val where = first.network ?: "this connection"
        val out = StringBuilder(refusal.advice).append(" Tested over ").append(where).append('.')
        if (vpnOnPhone) {
            out.append(" A VPN is up on this phone right now (the key icon in the status bar): ad blockers, security and ")
                .append("\"private DNS\" apps often run one. Turn it off, or leave Vigilant out of it, and test again.")
        } else {
            out.append(" No VPN is running on this phone.")
        }
        when {
            other == null -> out.append(" The other connection wasn't available to compare.")
            other.ok -> out.append(" Over ").append(other.network ?: "the other connection")
                .append(" the key works: Novig lists only the ").append(where).append(" address. Scans use the key whenever the phone is on ")
                .append(other.network ?: "that connection").append(", and Novig's public prices (slower) on ").append(where).append('.')
            else -> out.append(" Over ").append(other.network ?: "the other connection").append(" Novig refused it too")
                .append(other.refusal?.code?.let { " ($it)" } ?: other.error?.message?.let { " ($it)" } ?: "").append('.')
        }
        return Report(null, out.toString())
    }
}
