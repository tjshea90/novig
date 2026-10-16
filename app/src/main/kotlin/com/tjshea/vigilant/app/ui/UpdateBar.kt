package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.AppUpdater
import com.tjshea.vigilant.app.BuildConfig
import com.tjshea.vigilant.app.VigilantApp
import kotlinx.coroutines.launch

/** "Vigilant 0.113.0 is ready · Install update" above the tab bar on every screen while a newer release is on GitHub (Tj, 2026-10-16). */
@Composable
fun UpdateBar() {
    val ctx = LocalContext.current
    val updater = (ctx.applicationContext as VigilantApp).container.updater
    val st by updater.state.collectAsState()
    UpdateBarContent(
        st, onInstall = { (ctx.applicationContext as VigilantApp).container.appScope.launch { updater.install() } },
        onAllow = { runCatching { ctx.startActivity(updater.permissionIntent()) } },
    )
}

@Composable
fun UpdateBarContent(st: AppUpdater.State, onInstall: () -> Unit, onAllow: () -> Unit, modifier: Modifier = Modifier) {
    val latest = st.latest
    if (!st.available && !st.installing && st.progress == null) return
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier.fillMaxWidth().testTag("updateBar")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Vigilant ${latest?.version ?: "update"} is ready (you have ${BuildConfig.VERSION_NAME})",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f),
                )
                when {
                    st.installing || st.progress != null -> Unit
                    st.needsPermission -> OutlinedButton(onClick = onAllow, modifier = Modifier.testTag("updateAllow")) { Text("Allow installs") }
                    else -> Button(onClick = onInstall, modifier = Modifier.testTag("updateInstall")) { Text("Install update") }
                }
            }
            st.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            st.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
    }
}
