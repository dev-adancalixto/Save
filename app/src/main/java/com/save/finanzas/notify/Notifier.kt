package com.save.finanzas.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.save.finanzas.MainActivity
import com.save.finanzas.R
import com.save.finanzas.domain.*
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Notificaciones de pagos e ingresos fijos. */
object Notifier {
    const val CHANNEL = "pagos"
    private const val OVERDUE_ID = 1
    private const val PREFS = "reminders"
    /** Horas a partir de las que se avisa: mañana y tarde. */
    private val SLOTS = listOf(LocalTime.of(9, 0) to "am", LocalTime.of(19, 0) to "pm")

    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork("recordatorios", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Pagos e ingresos fijos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Recordatorios el día de pago de tus fijos"
                }
            )
        }
    }

    fun notifId(movementId: String, month: String) = ("$movementId|$month").hashCode() and 0x7fffffff or 0x100

    /** Revisa qué toca recordar en este momento y publica las notificaciones que falten. */
    fun run(context: Context, data: AppData, now: java.time.LocalDateTime = java.time.LocalDateTime.now()) {
        if (!canNotify(context)) return
        val slot = SLOTS.lastOrNull { !now.toLocalTime().isBefore(it.first) }?.second ?: return
        val today = now.toLocalDate()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sent = prefs.getStringSet("sent", emptySet())!!.filter { it.startsWith("$today|") }.toMutableSet()
        ensureChannel(context)
        val cur = data.settings.currency
        val tags = data.tags.associateBy { it.id }
        val funds = data.funds.associateBy { it.id }
        fun name(m: Movement) = m.note.ifBlank {
            when (m.kind) {
                Kind.SAVE, Kind.WITHDRAW -> m.fundId?.let { funds[it]?.name } ?: "tu fondo"
                else -> m.tagId?.let { tags[it]?.name } ?: if (m.kind == Kind.INCOME) "Ingreso fijo" else "Gasto fijo"
            }
        }

        val list = reminders(data, today)
        val nm = NotificationManagerCompat.from(context)

        // Vencen hoy (mañana y tarde) o mañana (solo por la mañana)
        for (r in list.filter { it.status == DueStatus.TODAY || it.status == DueStatus.TOMORROW }) {
            if (r.status == DueStatus.TOMORROW && slot != "am") continue
            val key = "$today|${r.movement.id}|${r.month}|${r.status}|$slot"
            if (key in sent) continue
            val m = r.movement
            val n = name(m)
            val title = when (m.kind) {
                Kind.INCOME -> "¿Ya recibiste $n?"
                Kind.SAVE -> "¿Ya apartaste para $n?"
                else -> "¿Ya pagaste $n?"
            }
            val whenTxt = if (r.status == DueStatus.TODAY) "hoy" else "mañana"
            val body = "${formatMoney(m.amount, cur)} · ${if (m.kind == Kind.INCOME) "llega" else "vence"} $whenTxt"
            post(context, nm, notifId(m.id, r.month.key()), title, body, paidAction(context, m, r.month.key()))
            sent += key
        }

        // Vencidos: un resumen al día, por la mañana
        val overdue = list.filter { it.status == DueStatus.OVERDUE }
        val okey = "$today|overdue"
        if (overdue.isNotEmpty() && slot == "am" && okey !in sent) {
            val names = overdue.joinToString(", ") { name(it.movement) }
            val title = if (overdue.size == 1) "Pendiente vencido: ${name(overdue[0].movement)}" else "Tienes ${overdue.size} fijos vencidos"
            val action = if (overdue.size == 1) paidAction(context, overdue[0].movement, overdue[0].month.key(), OVERDUE_ID) else null
            post(context, nm, OVERDUE_ID, title, if (overdue.size == 1) formatMoney(overdue[0].movement.amount, cur) else names, action)
            sent += okey
        }
        prefs.edit().putStringSet("sent", sent).apply()
    }

    private fun paidAction(context: Context, m: Movement, month: String, notif: Int = notifId(m.id, month)): NotificationCompat.Action {
        val i = Intent(context, PaidReceiver::class.java)
            .putExtra(PaidReceiver.EXTRA_ID, m.id)
            .putExtra(PaidReceiver.EXTRA_MONTH, month)
            .putExtra(PaidReceiver.EXTRA_NOTIF, notif)
        val pi = PendingIntent.getBroadcast(context, notif, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val label = if (m.kind == Kind.INCOME) "Ya lo recibí" else "Ya lo pagué"
        return NotificationCompat.Action(0, label, pi)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun post(context: Context, nm: NotificationManagerCompat, id: Int, title: String, body: String, action: NotificationCompat.Action?) {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (action != null) b.addAction(action)
        nm.notify(id, b.build())
    }
}
