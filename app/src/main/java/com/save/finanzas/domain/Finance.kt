package com.save.finanzas.domain

import java.time.YearMonth

fun YearMonth.key(): String = toString() // yyyy-MM

fun monthOf(key: String): YearMonth = YearMonth.parse(key)

/** ¿El movimiento aparece en este mes? */
fun Movement.appliesTo(m: YearMonth): Boolean = when (nature) {
    Nature.VARIABLE -> month == m.key()
    Nature.FIXED -> {
        val start = monthOf(month)
        val end = endMonth?.let(::monthOf)
        !m.isBefore(start) && (end == null || !m.isAfter(end))
    }
}

/** ¿Ya ocurrió de verdad en este mes? Los variables siempre; los fijos cuando se confirman. */
fun Movement.isRealIn(m: YearMonth): Boolean =
    appliesTo(m) && (nature == Nature.VARIABLE || m.key() in paidMonths)

/** Meses (hasta `upTo` inclusive) en que el movimiento cuenta como real. */
private fun Movement.realMonthsUpTo(upTo: YearMonth?): List<YearMonth> = when (nature) {
    Nature.VARIABLE -> listOf(monthOf(month))
    Nature.FIXED -> paidMonths.map(::monthOf).filter { appliesTo(it) }
}.filter { upTo == null || !it.isAfter(upTo) }

data class Totals(val income: Long = 0, val expense: Long = 0, val saved: Long = 0) {
    /** Flujo = ingresos − gastos − lo que se mandó a fondos. */
    val flow: Long get() = income - expense - saved

    operator fun plus(m: Movement): Totals = when (m.kind) {
        Kind.INCOME -> copy(income = income + m.amount)
        Kind.EXPENSE -> copy(expense = expense + m.amount)
        Kind.SAVE -> copy(saved = saved + m.amount)
        Kind.WITHDRAW -> copy(saved = saved - m.amount)
        Kind.TRANSFER -> this
    }
}

data class Entry(val movement: Movement, val pending: Boolean)

data class Balances(
    /** Saldo por cuenta (id -> centavos). */
    val accounts: Map<String, Long>,
    val bank: Long,
    val cash: Long,
    val funds: Map<String, Long>,
) {
    val fundsTotal: Long get() = funds.values.sum()
    /** Dinero disponible (cuentas, sin fondos). */
    val available: Long get() = bank + cash
    val total: Long get() = available + fundsTotal
}

data class MonthSummary(
    val month: YearMonth,
    /** Incluye fijos pendientes: lo que esperas que pase en el mes. */
    val estimated: Totals,
    /** Solo lo que ya ocurrió. */
    val real: Totals,
    val fixedIncome: Long,
    val variableIncome: Long,
    val fixedExpense: Long,
    val variableExpense: Long,
    val budget: Long?,
    /** Saldo inicial del mes = lo disponible al cierre del mes anterior. */
    val opening: Long,
    /** Saldos acumulados al cierre del mes. */
    val balances: Balances,
    val fixed: List<Entry>,
    val variable: List<Entry>,
) {
    val budgetLeft: Long? get() = budget?.let { it - real.expense }
    val budgetLeftEstimated: Long? get() = budget?.let { it - estimated.expense }
    /** Saldo final = saldo inicial + flujo. Pasa como saldo inicial del siguiente mes. */
    val closing: Long get() = opening + real.flow
    val closingEstimated: Long get() = opening + estimated.flow
}

fun budgetFor(settings: Settings, m: YearMonth): Long? {
    val k = m.key()
    return settings.budgets.entries
        .filter { it.key <= k }
        .maxByOrNull { it.key }
        ?.value
        ?.takeIf { it > 0 }
}

/** Saldos acumulados (solo movimientos reales) hasta `upTo` inclusive; null = todo. */
fun balances(data: AppData, upTo: YearMonth?): Balances {
    val acc = data.accounts.associate { it.id to it.initial }.toMutableMap()
    val funds = data.funds.associate { it.id to 0L }.toMutableMap()

    fun add(id: String, v: Long) { acc[id] = (acc[id] ?: 0) + v }

    for (mv in data.movements) {
        val times = mv.realMonthsUpTo(upTo).size.toLong()
        if (times == 0L) continue
        val v = mv.amount * times
        when (mv.kind) {
            Kind.INCOME -> add(mv.accountId, v)
            Kind.EXPENSE -> add(mv.accountId, -v)
            Kind.SAVE -> {
                add(mv.accountId, -v)
                mv.fundId?.let { funds[it] = (funds[it] ?: 0) + v }
            }
            Kind.WITHDRAW -> {
                add(mv.accountId, v)
                mv.fundId?.let { funds[it] = (funds[it] ?: 0) - v }
            }
            Kind.TRANSFER -> {
                add(mv.accountId, -v)
                mv.toAccountId?.let { add(it, v) }
            }
        }
    }
    val types = data.accounts.associate { it.id to it.type }
    val bank = acc.filter { (types[it.key] ?: Account.BANK) == Account.BANK }.values.sum()
    val cash = acc.filter { types[it.key] == Account.CASH }.values.sum()
    return Balances(acc, bank, cash, funds)
}

data class Slice(val tagId: String?, val amount: Long)

/** Reparto por etiqueta de los ingresos o gastos del mes, de mayor a menor. */
fun breakdown(data: AppData, m: YearMonth, kind: Kind, includePending: Boolean = false): List<Slice> {
    val known = data.tags.map { it.id }.toSet()
    return data.movements
        .filter { it.kind == kind && (if (includePending) it.appliesTo(m) else it.isRealIn(m)) }
        .groupBy { it.tagId?.takeIf { id -> id in known } }
        .map { (tag, list) -> Slice(tag, list.sumOf { it.amount }) }
        .filter { it.amount > 0 }
        .sortedByDescending { it.amount }
}

fun summarize(data: AppData, m: YearMonth): MonthSummary {
    val inMonth = data.movements.filter { it.appliesTo(m) }
    var est = Totals()
    var real = Totals()
    var fi = 0L; var vi = 0L; var fe = 0L; var ve = 0L
    for (mv in inMonth) {
        est += mv
        if (mv.isRealIn(m)) real += mv
        when {
            mv.kind == Kind.INCOME && mv.nature == Nature.FIXED -> fi += mv.amount
            mv.kind == Kind.INCOME -> vi += mv.amount
            mv.kind == Kind.EXPENSE && mv.nature == Nature.FIXED -> fe += mv.amount
            mv.kind == Kind.EXPENSE -> ve += mv.amount
        }
    }
    val fixed = inMonth.filter { it.nature == Nature.FIXED }
        .sortedWith(compareBy({ it.kind.ordinal }, { it.day }, { it.createdAt }))
        .map { Entry(it, pending = !it.isRealIn(m)) }
    val variable = inMonth.filter { it.nature == Nature.VARIABLE }
        .sortedWith(compareByDescending<Movement> { it.day }.thenByDescending { it.createdAt })
        .map { Entry(it, pending = false) }

    return MonthSummary(
        month = m,
        estimated = est,
        real = real,
        fixedIncome = fi,
        variableIncome = vi,
        fixedExpense = fe,
        variableExpense = ve,
        budget = budgetFor(data.settings, m),
        opening = balances(data, m.minusMonths(1)).available,
        balances = balances(data, m),
        fixed = fixed,
        variable = variable,
    )
}
