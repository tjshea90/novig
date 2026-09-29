package com.tjshea.vigilant.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The +EV alert's "✓ Placed" and the confirmation's "Undo" buttons ([EvAlerts.handle]): they run here, in the
 * background, so a bet is tracked from the notification shade without opening Vigilant. The work lives as long as the
 * process ([AppContainer.appScope]); [goAsync] keeps the receiver alive until it's written.
 */
class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? VigilantApp ?: return
        val alert = EvAlerts.alertOf(intent) ?: return
        val pending = runCatching { goAsync() }.getOrNull()
        app.container.appScope.launch {
            try {
                EvAlerts.handle(app, app.container, intent.action, alert)
            } finally {
                pending?.finish()
            }
            // After the receiver is done (a catalog read can take seconds): a CNO bet's Novig market, so scans follow its line.
            if (intent.action == EvAlerts.ACTION_PLACED && AppBook.isNovig && alert.isCno) {
                com.tjshea.vigilant.data.alerts.AlertPlacement.attachMarket(app.container.tracker, alert.key) { row ->
                    withContext(Dispatchers.IO) { app.container.betFinder.find(row) }
                }
            }
        }
    }
}
