package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.NovigUi
import com.tjshea.vigilant.app.ScanStatus
import com.tjshea.vigilant.data.novig.signing.ManagementKey
import com.tjshea.vigilant.data.scanner.ScanTiming

/**
 * Settings → Novig API. Not connected: the one-time setup (management key ID + its .pem file, saved on this phone once Novig accepts it).
 * Connected: what the key does for scans, test, disconnect.
 */
@Composable
fun NovigKeySection(
    novig: NovigUi,
    /** [typed]: the management key Tj just entered (saved once Novig accepts it); null = the one saved on this phone. */
    onConnect: (typed: ManagementKey?) -> Unit,
    onTest: () -> Unit,
    onDisconnect: () -> Unit,
    /** The last scan, for how its prices came in. */
    lastScan: ScanStatus? = null,
    /** Deletes the saved management key from this phone. */
    onForgetKey: () -> Unit = {},
) {
    val conn = novig.connection
    if (conn == null) {
        SetupForm(novig, onConnect, onForgetKey)
    } else {
        Text(
            "Connected · read-only key ••••${conn.readKeyId.takeLast(4)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Scans open Novig's live feed with this key: once the other books' odds are in (30 seconds at most), up to " +
                "2,000 lines the scan hasn't read yet arrive at once and then update themselves, with no request per price. " +
                "Until then, and for anything the feed doesn't cover, prices are read through the key's own rate limit (16 a second) " +
                "instead of the public one your phone's network shares. If Novig refuses the key (its network screen " +
                "flags the connection's address, or a location check is due), the scan falls back to public prices and says why.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        lastScan?.takeIf { it.scannedAtMs != null }?.let { s ->
            // Where the last scan's time went (Tj, 2026-09-28: "now it is reading the API very slow").
            val line = s.timing?.let { ScanTiming.text(it, s.booksFetched, s.booksViaKey, s.booksViaPush, s.keyReadPerSec) }
                ?: "Last scan: ${s.booksViaPush} of ${s.booksFetched} Novig prices came by live feed, ${s.booksViaKey} through the key's rate limit."
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp).testTag("scanTiming"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onTest, enabled = !novig.busy) { Text("Test key") }
            TextButton(onClick = onDisconnect, enabled = !novig.busy) { Text("Disconnect") }
        }
        Status(novig)
        Text(
            "The read-only key can't place bets or move money. Its private half was generated in this " +
                "phone's secure hardware and can't be copied off it. Novig refuses keyed requests from addresses its " +
                "screen lists as a VPN or proxy (now and then a Wi-Fi's or carrier's shared address: Test key tries your " +
                "other connection), and needs the Novig app opened every few days to confirm your location. After the key is " +
                "accepted, Test key also times the live feed, the key's limits and book reads (about a minute) and prints the numbers.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SetupForm(novig: NovigUi, onConnect: (ManagementKey?) -> Unit, onForgetKey: () -> Unit) {
    val key = remember { ManagementKeyState() }
    val saved = novig.managementKey
    Text(
        "Optional. Public prices already work with no key. Connecting your Novig API key lets scans read " +
            "prices under your key's own rate limit, so big scans are faster and never throttled by a shared network.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!key.usesSaved(saved)) {
        Text(
            "1. VPN off. On novig.com: Profile → Settings → Novig API → Create Key.\n" +
                "2. Save the downloaded novig-api-key-….pem file and copy the key ID shown.\n" +
                "3. Enter both below, once. Vigilant makes its own read-only key from it and saves yours on this phone for betting and moving money.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
    ManagementKeyBlock(saved, key, novig.busy, onSave = null, onForget = onForgetKey)
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = {
                onConnect(if (key.usesSaved(saved)) null else key.typed())
                key.clearSecret() // the typed key never lingers in the UI state: it's saved (sealed) once Novig accepts it
                key.replacing = false
            },
            enabled = !novig.busy && (key.usesSaved(saved) || key.ready),
            modifier = Modifier.testTag("novigConnect"),
        ) { Text("Connect") }
        if (novig.busy) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
    }
    Status(novig)
}

@Composable
private fun Status(novig: NovigUi) {
    novig.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
    novig.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Edge.colors.negative, modifier = Modifier.padding(top = 6.dp)) }
}
