package com.tjshea.vigilant.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.NovigUi

/**
 * Settings → Novig API. Not connected: the one-time setup (management key ID + its .pem file).
 * Connected: what the key does for scans, test, disconnect.
 */
@Composable
fun NovigKeySection(
    novig: NovigUi,
    onConnect: (keyId: String, pem: String) -> Unit,
    onTest: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val conn = novig.connection
    if (conn == null) {
        SetupForm(novig, onConnect)
    } else {
        Text(
            "Connected · read-only key ••••${conn.readKeyId.takeLast(4)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Scans read Novig's prices through this key's own rate limit (64 at once, 16 a second) " +
                "instead of the public one your phone's network shares. If Novig refuses the key " +
                "(VPN on, location check due), the scan falls back to public prices and says why.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onTest, enabled = !novig.busy) { Text("Test key") }
            TextButton(onClick = onDisconnect, enabled = !novig.busy) { Text("Disconnect") }
        }
        Status(novig)
        Text(
            "The read-only key can't place bets or move money. Its private half was generated in this " +
                "phone's secure hardware and can't be copied off it. Novig refuses keyed requests over a VPN, " +
                "and needs the Novig app opened every few days to confirm your location.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SetupForm(novig: NovigUi, onConnect: (String, String) -> Unit) {
    val context = LocalContext.current
    var keyId by remember { mutableStateOf("") }
    var pem by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
                .getOrNull()?.takeIf { it.contains("PRIVATE KEY") }
                ?.let {
                    pem = it
                    fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "key file"
                }
        }
    }

    Text(
        "Optional. Public prices already work with no key. Connecting your Novig API key lets scans read " +
            "prices under your key's own rate limit, so big scans are faster and never throttled by a shared network.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "1. VPN off. On novig.com: Profile → Settings → Novig API → Create Key.\n" +
            "2. Save the downloaded novig-api-key-….pem file and copy the key ID shown.\n" +
            "3. Enter both below. Vigilant makes its own read-only key and forgets yours.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 6.dp),
    )
    OutlinedTextField(
        value = keyId,
        onValueChange = { keyId = it.trim() },
        label = { Text("Management key ID") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text(if (fileName == null) "Choose .pem file" else "Change file") }
        Text(fileName?.let { "✓ $it" } ?: "or paste it below", style = MaterialTheme.typography.bodySmall)
    }
    if (fileName == null) {
        OutlinedTextField(
            value = pem,
            onValueChange = { pem = it },
            label = { Text("…or paste the key text") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            maxLines = 3,
        )
    }
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = {
                onConnect(keyId, pem)
                pem = "" // the management key never lingers in the UI state
                fileName = null
            },
            enabled = !novig.busy && keyId.length >= 8 && pem.contains("PRIVATE KEY"),
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
