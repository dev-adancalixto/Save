package com.save.finanzas.domain

import java.time.LocalDate
import java.time.YearMonth

/** Fecha de pago de un fijo en un mes (el día 31 se ajusta al último día del mes). */
fun Movement.dueDate(m: YearMonth): LocalDate = m.atDay(day.coerceIn(1, m.lengthOfMonth()))

enum class DueStatus { PAID, UPCOMING, TOMORROW, TODAY, OVERDUE }

/** Estado de un fijo en un mes respecto a `today`. */
fun Movement.dueStatus(m: YearMonth, today: LocalDate): DueStatus {
    if (isRealIn(m)) return DueStatus.PAID
    val due = dueDate(m)
    return when {
        due.isBefore(today) -> DueStatus.OVERDUE
        due == today -> DueStatus.TODAY
        due == today.plusDays(1) -> DueStatus.TOMORROW
        else -> DueStatus.UPCOMING
    }
}

data class Reminder(val movement: Movement, val month: YearMonth, val status: DueStatus)

/**
 * Fijos pendientes que hay que recordar hoy: los que vencen hoy, los vencidos del mes
 * (y del mes anterior, por si el pago era a fin de mes) y, si se pidió, los de mañana.
 */
fun reminders(data: AppData, today: LocalDate): List<Reminder> {
    if (!data.settings.reminders) return emptyList()
    val cur = YearMonth.from(today)
    val months = listOf(cur.minusMonths(1), cur, cur.plusMonths(1))
    return data.movements
        .filter { it.nature == Nature.FIXED && it.remind && it.kind != Kind.TRANSFER }
        .flatMap { mv ->
            months.filter { mv.appliesTo(it) }.map { m -> Reminder(mv, m, mv.dueStatus(m, today)) }
        }
        .filter {
            when (it.status) {
                DueStatus.TODAY, DueStatus.OVERDUE -> true
                DueStatus.TOMORROW -> data.settings.remindDayBefore
                else -> false
            }
        }
        .sortedWith(compareBy({ it.status.ordinal }, { it.movement.dueDate(it.month) }))
}
