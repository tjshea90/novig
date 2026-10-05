package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.data.scanner.ScanSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The kill switch bar's words, free of Compose so they're tested. */
object KillBarText {
    const val STOP = "STOP ALL"
    const val RESUME = "RESUME"
    const val STOP_DESCRIPTION = "Stop everything: all scanning, auto-betting, auto-bidding and background scan, until you resume"
    const val RESUME_DESCRIPTION = "Resume everything that was running before the stop"

    /** What is switched on and would run now (what STOP is about to stop), in words; empty when only the scanner itself is idle. */
    fun running(s: ScanSettings): List<String> = listOfNotNull(
        "auto-bet".takeIf { s.autoBet && s.autoBetHalted == null },
        "bids".takeIf { s.maker },
        "auto-lock".takeIf { s.autoLock },
        "background scan".takeIf { s.autoScan != com.tjshea.vigilant.data.scanner.AutoScanMode.OFF },
    )

    /** The bar while running: what would stop ("Running: auto-bet · bids · background scan"), or just "All scanning on" when only the scanners are on. */
    fun runningLine(s: ScanSettings): String {
        if (s.pausedByHand) return "Scanning paused" + running(s).takeIf { it.isNotEmpty() }?.let { " · ${it.joinToString(" · ")} waiting" }.orEmpty()
        val on = running(s)
        return if (on.isEmpty()) "Scanners on" else "Running: ${on.joinToString(" · ")}"
    }

    /** The bar once pressed: what is stopped, since when ("Stopped 3:42 PM"), and that it stays so until Resume. */
    fun stoppedLine(s: ScanSettings, zone: TimeZone = TimeZone.getDefault()): String {
        val at = s.killedAtMs?.let { " " + SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = zone }.format(Date(it)) }.orEmpty()
        return "STOPPED$at: nothing scans, bets or bids until you resume"
    }

    /** The words after Resume, listing what runs again (the switches Tj had on). */
    fun resumedList(s: ScanSettings): String {
        val on = listOf("scanning") + running(s)
        return on.joinToString(", ")
    }
}

/**
 * The kill switch on every tab (Tj, 2026-10-05: "a stop button kill switch in the app visible everywhere that immediately stops all scanning, all auto
 * betting, all auto bidding, and all background scan. If I press this, everything remains off, even if I close the app and open it again, until I press
 * resume"): a slim bar above the tab bar. Running, it shows what is on and a red STOP ALL; stopped, it turns solid red and shows RESUME. One tap each:
 * a stop that asks first isn't a stop. It reads the settings it is handed and nothing else.
 */
@Composable
fun KillBar(settings: ScanSettings, onKill: () -> Unit, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val killed = settings.killed
    Surface(
        modifier.fillMaxWidth().testTag("killBar"),
        color = if (killed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (killed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp).height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (killed) KillBarText.stoppedLine(settings) else KillBarText.runningLine(settings),
                Modifier.weight(1f).testTag("killStatus"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (killed) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (killed) {
                Button(
                    onClick = onResume,
                    modifier = Modifier.height(28.dp).testTag("killResume").semantics { contentDescription = KillBarText.RESUME_DESCRIPTION },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onError, contentColor = MaterialTheme.colorScheme.error),
                ) { Text(KillBarText.RESUME, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1) }
            } else {
                Button(
                    onClick = onKill,
                    modifier = Modifier.height(28.dp).testTag("killStop").semantics { contentDescription = KillBarText.STOP_DESCRIPTION },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text(KillBarText.STOP, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1) }
            }
        }
    }
}
