package com.tjshea.vigilant.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import com.tjshea.vigilant.app.ui.Format
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.updateAndGet

/**
 * The Vigilant wallet's balance as every notification shows it (Tj, 2026-10-02 21:51Z: "always include my vigilant wallet current balance in all vigilant
 * notifications whether push or silent, so I can always quickly see how much is in the wallet"). One reading for the whole app: every balance read
 * anywhere ([record]: auto-bet's before each pass, the Bet sheet's, a transfer's answer) keeps it current, and [fresh] reads again only when it's older
 * than [FRESH_MS] (each background cycle asks), so a burst of notifications costs no extra requests. The last reading is kept across restarts, so a
 * notification posted by a worker in a new process still has it.
 */
class WalletBalance(
    /** The subaccount's balance from Novig now; null when betting through the API isn't set up. May throw. */
    private val read: suspend () -> Double?,
    /** Whether betting through the API is set up (there is a wallet to show). */
    private val setUp: () -> Boolean,
    private val prefs: SharedPreferences?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Reading(val dollars: Double, val atMs: Long)

    private val _flow = kotlinx.coroutines.flow.MutableStateFlow(load())

    /** The latest reading as it changes: the wallet strip above the tabs shows it (Tj, 2026-10-03: "show it somewhere in the app at all times"). */
    val flow: kotlinx.coroutines.flow.StateFlow<Reading?> = _flow

    val last: Reading? get() = _flow.value

    val isSetUp: Boolean get() = setUp()

    /** A balance just read elsewhere (Novig's answer): kept as the latest. */
    fun record(dollars: Double, at: Long = clock()) {
        if (!dollars.isFinite()) return
        val next = Reading(dollars, at)
        // A newer reading already in (two reads racing) stays.
        val kept = _flow.updateAndGet { l -> if (l != null && l.atMs > at) l else next }
        if (kept === next) prefs?.edit()?.putString(KEY_DOLLARS, dollars.toString())?.putLong(KEY_AT, at)?.apply()
    }

    /** The balance no older than [maxAgeMs]: the last reading when it's that young, else read now (the last one stands if Novig doesn't answer). */
    suspend fun fresh(maxAgeMs: Long = FRESH_MS): Reading? {
        if (!setUp()) return last
        last?.takeIf { clock() - it.atMs < maxAgeMs }?.let { return it }
        val dollars = try {
            read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return last
        record(dollars)
        return last
    }

    private fun load(): Reading? {
        val p = prefs ?: return null
        val dollars = p.getString(KEY_DOLLARS, null)?.toDoubleOrNull() ?: return null
        return Reading(dollars, p.getLong(KEY_AT, 0L))
    }

    companion object {
        /** A reading this young is shown without another request. */
        const val FRESH_MS = 30_000L

        const val PREFS = "wallet"
        private const val KEY_DOLLARS = "dollars"
        private const val KEY_AT = "at"
    }
}

/**
 * Which notifications may be posted (Tj, 2026-10-09: "an option to turn all notifications off except notifications of actual money bet or bids filled"). [quiet] follows
 * `ScanSettings.quietNotifications` ([AppContainer.syncSgo]); a notification about money (a bet placed, a bid filled, the sample test) passes [allow] with `money = true`.
 */
object NotifyGate {
    @Volatile var quiet: Boolean = false
    fun allow(money: Boolean = false): Boolean = money || !quiet
}

/** The wallet line every notification carries ([withWallet]). Pure, so it's tested. */
object WalletNote {
    /** Past this, the line says how old the balance is. */
    const val SAY_AGE_AFTER_MS = 2 * 60_000L

    fun line(r: WalletBalance.Reading?, setUp: Boolean, now: Long): String = when {
        !setUp && r == null -> "Wallet: betting not set up"
        r == null -> "Wallet: not read yet"
        now - r.atMs < SAY_AGE_AFTER_MS -> "Wallet ${Format.money(r.dollars)}"
        else -> "Wallet ${Format.money(r.dollars)} (${Format.age(r.atMs, now)})"
    }

    /** "Wallet $123.08 · 8 bids up ($9.86)": the open bids on every notification, always ("No bids up" when none; Tj, 2026-10-09). */
    fun withBids(line: String, bids: Int, bidDollars: Double): String =
        line + " · " + if (bids <= 0) "no bids up" else "$bids bid${if (bids == 1) "" else "s"} up (${Format.money(bidDollars)})"
}

/**
 * Every notification Vigilant posts carries the wallet's balance in its header line (Android's sub-text: shown collapsed and expanded, on the lock
 * screen, silent or not). `WalletNotificationsTest` checks that no notification is built without it.
 */
fun NotificationCompat.Builder.withWallet(context: Context, now: Long = System.currentTimeMillis()): NotificationCompat.Builder {
    val container = (context.applicationContext as? VigilantApp)?.container ?: return this
    val wallet = container.wallet
    val up = runCatching { container.makerStore.flow.value.orEmpty().filter { it.resting } }.getOrDefault(emptyList())
    return setSubText(WalletNote.withBids(WalletNote.line(wallet.last, wallet.isSetUp, now), up.size, up.sumOf { it.restingDollars }))
}
