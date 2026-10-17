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
    /** Reset diagnostics and Clear scan study (Tj, 2026-10-10). */
    val onResetDiagnostics: () -> Unit = {},
    val onClearStudy: () -> Unit = {},
    /** Settings › Messages from Claude: mark everything read. */
    /** Make the scan study's file and open the share sheet (Tj, 2026-10-03), and read the line that says what's logged when the page opens. */
    val onShareStudy: () -> Unit = {},
    val onStudyShown: () -> Unit = {},
    /** The live burst recorder (Tj, 2026-10-06): make its study file and share it, and read the line that says what it is doing. */
    val onShareBurst: () -> Unit = {},
    val onBurstShown: () -> Unit = {},
    /** Settings › Pinnodds live is on screen: refresh its status line. */
    val onPinnShown: () -> Unit = {},
    /** Settings › Live bids is open: refresh the desk's status (asked every second or two). */
    val onLiveBidShown: () -> Unit = {},
    val onTestPinnKey: () -> Unit = {},
    /** SportsGameOdds Pro: test the key (and, with true, share the sample it saved). */
    val onTestSgoKey: (Boolean) -> Unit = {},
    /** OddsPapi: test the key (and, with true, share the sample it saved). */
    val onTestOpKey: (Boolean) -> Unit = {},
    /** Unabated (Free) and OddsPapi Free (v4): test the key (and, with true, share the sample it saved). */
    /** Settings › APIs: test one API's keys, test every API that is on (plus a one-league read through each source in play), refresh the usage meters from the APIs' own prompts. */
    val onTestApi: (com.tjshea.vigilant.app.ApiEntry) -> Unit = {},
    val onTestActiveApis: () -> Unit = {},
    val onRefreshUsage: () -> Unit = {},
    val onTestUnabatedKey: (Boolean) -> Unit = {},
    /** BoltOdds Pro: test the key (and, with true, share the sample it saved). */
    val onTestBoltKey: (Boolean) -> Unit = {},
    /** Kalshi (Settings › Kalshi): save the Key ID and private key, remove them, test them. */
    val onSaveKalshiKey: (String, String) -> Unit = { _, _ -> },
    val onRemoveKalshiKey: () -> Unit = {},
    val onTestKalshiKey: () -> Unit = {},
    /** Cloud scan (Settings › Cloud scan): save the GitHub token, remove it, test it, start the engine, save the bets/bids/history to GitHub now. */
    val onSaveCloudToken: (String) -> Unit = {},
    val onRemoveCloudToken: () -> Unit = {},
    val onTestCloud: () -> Unit = {},
    val onStartCloud: () -> Unit = {},
    val onRunCloudOutsideHours: () -> Unit = {},
    val onStopCloud: () -> Unit = {},
    val onSaveCloudNow: () -> Unit = {},
    val onSendCloudKeys: () -> Unit = {},
    val onSendNovigKey: () -> Unit = {},
    val onRemoveNovigKey: () -> Unit = {},
    val onSendTradeKey: () -> Unit = {},
    val onRemoveTradeKey: () -> Unit = {},
    val onTestOddsPapiFreeKey: (Boolean) -> Unit = {},
    /** The live feed test (Tj, 2026-10-07): make its file and share it, and read the line that says what it is doing. */
    val onShareFeedRace: () -> Unit = {},
    val onShareResearch: () -> Unit = {},
    val onShareResearchAll: () -> Unit = {},
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
