package com.tjshea.vigilant.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import com.tjshea.vigilant.app.ui.Format
import kotlinx.coroutines.CancellationException

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

    var last: Reading?
        get() = _flow.value
        private set(value) {
            _flow.value = value
        }

    val isSetUp: Boolean get() = setUp()

    /** A balance just read elsewhere (Novig's answer): kept as the latest. */
    fun record(dollars: Double, at: Long = clock()) {
        if (!dollars.isFinite()) return
        val l = last
        if (l != null && l.atMs > at) return
        last = Reading(dollars, at)
        prefs?.edit()?.putString(KEY_DOLLARS, dollars.toString())?.putLong(KEY_AT, at)?.apply()
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
}

/**
 * Every notification Vigilant posts carries the wallet's balance in its header line (Android's sub-text: shown collapsed and expanded, on the lock
 * screen, silent or not). `WalletNotificationsTest` checks that no notification is built without it.
 */
fun NotificationCompat.Builder.withWallet(context: Context, now: Long = System.currentTimeMillis()): NotificationCompat.Builder {
    val wallet = (context.applicationContext as? VigilantApp)?.container?.wallet ?: return this
    return setSubText(WalletNote.line(wallet.last, wallet.isSetUp, now))
}
