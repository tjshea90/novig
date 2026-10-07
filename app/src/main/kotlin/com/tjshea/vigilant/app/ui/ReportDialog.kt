package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.ReportUi

/** What Settings' report buttons do (Tj, 2026-09-29): show the Diagnostics page, run the Grading check, close the report, say it was copied. */
data class ReportActions(
    val onDiagnostics: () -> Unit = {},
    val onGradingCheck: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onCopied: () -> Unit = {},
    /** Make the diagnostics file and open Android's share sheet (Tj, 2026-10-02): "Share diagnostics with Claude". */
    val onShare: () -> Unit = {},
    /** Make the scan study's file and open the share sheet (Tj, 2026-10-03), and read the line that says what's logged when the page opens. */
    val onShareStudy: () -> Unit = {},
    val onStudyShown: () -> Unit = {},
    /** The live burst recorder (Tj, 2026-10-06): make its study file and share it, and read the line that says what it is doing. */
    val onShareBurst: () -> Unit = {},
    val onBurstShown: () -> Unit = {},
    /** The live feed test (Tj, 2026-10-07): make its file and share it, and read the line that says what it is doing. */
    val onShareFeedRace: () -> Unit = {},
    val onFeedRaceShown: () -> Unit = {},
)

/** A report in a dialog: the text to select or copy, and Copy / Close. */
@Composable
fun ReportDialog(report: ReportUi, actions: ReportActions) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = actions.onDismiss,
        title = { Text(report.title) },
        text = { ReportContent(report) },
        confirmButton = {
            Row {
                // Only the Diagnostics report is the file Claude works from.
                if (report.title == "Diagnostics") TextButton(onClick = actions.onShare, enabled = !report.busy, modifier = Modifier.testTag("reportShare")) { Text("Share with Claude") }
                TextButton(onClick = { clipboard.setText(AnnotatedString(report.text)); actions.onCopied() }, enabled = !report.busy) { Text("Copy") }
            }
        },
        dismissButton = { TextButton(onClick = actions.onDismiss) { Text("Close") } },
    )
}

/** The report's text, scrollable, in a fixed-width font so its columns line up. */
@Composable
fun ReportContent(report: ReportUi, modifier: Modifier = Modifier) {
    Column(modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).testTag("reportText"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (report.busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(report.text, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            SelectionContainer { Text(report.text, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace) }
        }
    }
}
