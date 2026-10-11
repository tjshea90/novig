package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.novig.signing.NovigApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gets auto-bet going again by itself after an order whose answer was lost (Tj, 2026-10-11, the Auto-bet tab's "Vigilant stopped while an order for Austin Hooper Over 7.5 ($1.47) was being
 * placed, so nothing says whether Novig filled it").
 *
 * Why it happens: before an order can be sent, [AutoBettor] saves a halt saying so ([AutoBettor.inFlightNote]) and clears it when the answer comes back. If the process dies in between (the
 * app was updated or reinstalled, Android ended it, a crash, the phone restarted) the halt is still there on the next start, and with it nothing that says whether Novig took the order.
 * Before this, only Tj could clear it (look at Novig, Sync with Novig's fills, Resume).
 *
 * What this does, every [CHECK_MS]: once the halt is a lost-order one, no order of ours is on its way and [SETTLE_MS] has passed (an auto-bet is a fill-or-kill/IOC order that is over in
 * seconds), it does the check Tj would: reads Novig's fills and adds any the Tracker lacks ([com.tjshea.vigilant.data.tracker.ApiBetSync], so a fill is on record and the bet cannot be placed
 * twice), and if that read worked it clears the halt, so the next cycle bets again. If Novig cannot be read it tries again every few seconds; after [GIVE_UP_MS] without success it says so
 * in a notification, once, with the reason, and carries on trying (the halt then clears itself the moment Novig answers).
 */
class AutoBetRecovery(
    private val c: AppContainer,
    private val scope: CoroutineScope,
    private val app: android.app.Application,
    private val clock: () -> Long = System::currentTimeMillis,
    private val notify: (title: String, text: String) -> Unit = { title, text -> AutoBetNotes.stopped(app, title, text) },
) {
    @Volatile private var seenHalt: String? = null
    @Volatile private var seenAtMs = 0L
    @Volatile private var notified = false

    /** What the last attempt found (for Diagnostics and tests). */
    @Volatile var lastNote: String? = null
        private set

    fun start() {
        scope.launch {
            while (isActive) {
                runCatching { step() }.onFailure { if (it is CancellationException) throw it; lastNote = "recovery error: ${it.message ?: it.javaClass.simpleName}" }
                delay(CHECK_MS)
            }
        }
    }

    /** One look at the halt; true when it cleared one. Public for tests. */
    suspend fun step(): Boolean {
        val now = clock()
        val s = c.currentSettings()
        val halt = s.autoBetHalted
        if (halt == null || !AutoBettor.isLostOrderHalt(halt) || s.killed) { seenHalt = null; notified = false; return false }
        if (halt != seenHalt) { seenHalt = halt; seenAtMs = now; notified = false }
        // An order of ours is on its way right now: its own answer clears the halt.
        if (c.autoBet.sending) return false
        if (now - seenAtMs < SETTLE_MS) { lastNote = "waiting for the order to end before checking Novig"; return false }
        val sync = c.apiSync
        if (sync == null) {
            lastNote = "betting through Novig's API isn't set up"
            giveUpNote(now, "Betting through Novig's API isn't connected on this phone, so the lost order can't be checked.")
            return false
        }
        val report = try {
            withContext(Dispatchers.IO) { sync.run(sinceMs = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val why = (e as? NovigApiException)?.advice ?: e.message ?: e.javaClass.simpleName
            lastNote = "couldn't read Novig's fills: $why"
            giveUpNote(now, "Novig's fills can't be read ($why), so Vigilant can't tell whether the order for the halted bet went through.")
            return false
        }
        if (report.added > 0) withContext(Dispatchers.IO) { runCatching { c.tracker.tagBids(c.makerStore.all().mapNotNullTo(HashSet()) { it.orderId }) } }
        // Cleared only if the halt is still the one we looked at (Tj may have resumed, or another order halted it, meanwhile).
        val cleared = withContext(kotlinx.coroutines.NonCancellable) {
            runCatching { c.settingsStore.update { if (it.autoBetHalted == halt) it.copy(autoBetHalted = null) else it } }.getOrNull()?.autoBetHalted == null
        }
        if (!cleared) return false
        lastNote = "cleared at ${clock()}: Novig's fills read, ${report.added} added to the Tracker"
        runCatching { c.eventLog.info("AUTOBET", "auto-bet resumed by itself after a lost order: ${report.added} fill(s) from Novig added to the Tracker") }
        seenHalt = null
        notified = false
        return true
    }

    private fun giveUpNote(now: Long, problem: String) {
        if (notified || now - seenAtMs < GIVE_UP_MS) return
        notified = true
        runCatching {
            notify(
                "Auto-bet is stopped",
                "$problem Auto-bet places nothing until it can check, and Vigilant keeps trying every ${CHECK_MS / 1000} seconds: it starts again by itself the moment Novig answers. " +
                    "If it does not, look at Novig for that bet, then Settings › Auto-bet › Resume auto-bet.",
            )
        }
    }

    companion object {
        const val CHECK_MS = 5_000L

        /** An auto-bet order is over within seconds; the check waits this long after the halt first shows. */
        const val SETTLE_MS = 45_000L

        /** After this long without a successful check the notification goes out (once). */
        const val GIVE_UP_MS = 3 * 60_000L
    }
}
