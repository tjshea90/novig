package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.ProviderKeys
import com.tjshea.vigilant.data.keys.ApiProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    providerKeys: List<ProviderKeys>,
    onAddKey: (ApiProvider, String) -> Unit,
    onRemoveKey: (ApiProvider, String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            items(providerKeys) { entry ->
                ProviderKeysSection(
                    entry = entry,
                    onAddKey = { key -> onAddKey(entry.provider, key) },
                    onRemoveKey = { key -> onRemoveKey(entry.provider, key) },
                )
            }
        }
    }
}

@Composable
private fun ProviderKeysSection(
    entry: ProviderKeys,
    onAddKey: (String) -> Unit,
    onRemoveKey: (String) -> Unit,
) {
    var newKeyText by remember(entry.provider) { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(entry.provider.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                if (entry.keys.isEmpty()) "No keys yet — running on sample data for this leg." else "${entry.keys.size} key(s) — tried in this order, auto-switches when one runs out.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            entry.keys.forEach { key ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        maskKey(key),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    IconButton(onClick = { onRemoveKey(key) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove key")
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                OutlinedTextField(
                    value = newKeyText,
                    onValueChange = { newKeyText = it },
                    label = { Text("Add a key") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        onAddKey(newKeyText)
                        newKeyText = ""
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text("Add")
                }
            }
        }
    }
}

private fun maskKey(key: String): String {
    if (key.length <= 8) return "••••"
    return "${key.take(4)}••••${key.takeLast(4)}"
}
