package com.save.finanzas.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.save.finanzas.data.Store
import com.save.finanzas.domain.migrated

/** Botón "Ya lo pagué / Ya lo recibí" de la notificación: marca el fijo como confirmado ese mes. */
class PaidReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val month = intent.getStringExtra(EXTRA_MONTH) ?: return
        val store = Store(context.filesDir)
        synchronized(Store.lock) {
            val d = store.load().migrated()
            val updated = d.copy(movements = d.movements.map {
                if (it.id == id) it.copy(paidMonths = it.paidMonths + month) else it
            })
            store.save(updated)
        }
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIF, 0))
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_MONTH = "month"
        const val EXTRA_NOTIF = "notif"
    }
}
