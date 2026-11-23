package com.save.finanzas

import com.save.finanzas.domain.*
import com.save.finanzas.update.compareVersions
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class FinanceTest {
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)

    private fun mv(
        id: String, kind: Kind, amount: Long, nature: Nature = Nature.VARIABLE,
        month: String = "2026-11", account: Account = Account.BANK, paid: Set<String> = emptySet(),
        fundId: String? = null, to: Account? = null, end: String? = null,
    ) = Movement(id = id, kind = kind, amount = amount, account = account, toAccount = to, nature = nature, month = month, endMonth = end, paidMonths = paid, fundId = fundId)

    @Test
    fun calculator() {
        assertEquals(155000L, Calc.evalCents("1200+350"))
        assertEquals(110000L, Calc.evalCents("1200−100"))
        assertEquals(70000L, Calc.evalCents("100+200×3"))
        assertEquals(3333L, Calc.evalCents("100÷3"))
        assertEquals(1250L, Calc.evalCents("12.5"))
        assertEquals(50L, Calc.evalCents(".5"))
        assertEquals(1000L, Calc.evalCents("10+"))
        assertNull(Calc.evalCents("5÷0"))
        assertNull(Calc.evalCents("1..2"))
        assertNull(Calc.evalCents(""))
    }

    @Test
    fun money() {
        assertEquals("$1,234.56", formatMoney(123456))
        assertEquals("$1,200", formatMoney(120000))
        assertEquals("−$5.05", formatMoney(-505))
        assertEquals("+$0.10", formatMoney(10, signed = true))
        assertEquals("1234.5", centsToInput(123450))
    }

    @Test
    fun monthFlowAndBalances() {
        val data = AppData(
            movements = listOf(
                mv("salario", Kind.INCOME, 2000000, Nature.FIXED, paid = setOf("2026-11")),
                mv("renta", Kind.EXPENSE, 800000, Nature.FIXED),               // pendiente
                mv("extra", Kind.INCOME, 50000, account = Account.CASH),
                mv("super", Kind.EXPENSE, 120000),
                mv("tacos", Kind.EXPENSE, 20000, account = Account.CASH),
                mv("ahorro", Kind.SAVE, 100000, fundId = "f1"),
                mv("cajero", Kind.TRANSFER, 30000, to = Account.CASH),
            ),
            funds = listOf(Fund("f1", "Emergencia", FundType.EMERGENCY)),
            settings = Settings(initialBank = 100000, budgets = mapOf("2026-10" to 1000000)),
        ).migrated()
        val s = summarize(data, nov)
        assertEquals(2050000L, s.real.income)
        assertEquals(140000L, s.real.expense)
        assertEquals(100000L, s.real.saved)
        assertEquals(1810000L, s.real.flow)
        assertEquals(940000L, s.estimated.expense)
        assertEquals(1010000L, s.estimated.flow)
        assertEquals(2000000L, s.fixedIncome)
        assertEquals(800000L, s.fixedExpense)
        assertEquals(1000000L, s.budget) // hereda el de octubre
        assertEquals(860000L, s.budgetLeft)
        assertEquals(1, s.fixed.count { it.pending })

        // Banco: 1000 + 20000 − 1200 − 1000 − 300 = 18500
        assertEquals(1850000L, s.balances.bank)
        // Efectivo: 500 − 200 + 300 = 600
        assertEquals(60000L, s.balances.cash)
        assertEquals(100000L, s.balances.funds["f1"])

        // Saldo inicial + flujo = saldo final
        assertEquals(100000L, s.opening)
        assertEquals(s.balances.available, s.closing)
        assertEquals(1910000L, s.closing)

        // Diciembre: el salario fijo aparece pero no está confirmado
        val d = summarize(data, dec)
        assertEquals(0L, d.real.income)
        assertEquals(2000000L, d.estimated.income)
        assertTrue(d.variable.isEmpty())
        assertEquals(s.balances, d.balances)
        // Lo que sobró en noviembre es el saldo inicial de diciembre
        assertEquals(s.closing, d.opening)
        assertEquals(d.opening + 2000000L - 800000L, d.closingEstimated)
    }

    @Test
    fun fixedEndMonth() {
        val m = mv("gym", Kind.EXPENSE, 50000, Nature.FIXED, month = "2026-09", end = "2026-11",
            paid = setOf("2026-09", "2026-10", "2026-12"))
        assertTrue(m.appliesTo(nov))
        assertFalse(m.appliesTo(dec))
        assertFalse(m.appliesTo(YearMonth.of(2026, 8)))
        // diciembre está fuera de rango, no cuenta
        assertEquals(-100000L, balances(AppData(movements = listOf(m)).migrated(), null).bank)
    }

    @Test
    fun multipleAccountsAndTags() {
        val data = AppData(
            version = DATA_VERSION,
            accounts = listOf(
                MoneyAccount("bbva", "BBVA", Account.BANK, 500000),
                MoneyAccount("revolut", "Revolut", Account.BANK),
                MoneyAccount("cash", "Efectivo", Account.CASH, 10000),
            ),
            tags = listOf(Tag("comida", "Comida"), Tag("casa", "Casa", color = 1)),
            movements = listOf(
                Movement("1", Kind.EXPENSE, 30000, accountId = "bbva", tagId = "comida", month = "2026-11"),
                Movement("2", Kind.EXPENSE, 10000, accountId = "cash", tagId = "comida", month = "2026-11"),
                Movement("3", Kind.EXPENSE, 60000, accountId = "revolut", tagId = "casa", month = "2026-11"),
                Movement("4", Kind.EXPENSE, 5000, accountId = "cash", month = "2026-11"),
                Movement("5", Kind.TRANSFER, 200000, accountId = "bbva", toAccountId = "revolut", month = "2026-11"),
            ),
        )
        val b = balances(data, nov)
        assertEquals(270000L, b.accounts["bbva"])
        assertEquals(140000L, b.accounts["revolut"])
        assertEquals(-5000L, b.accounts["cash"])
        assertEquals(410000L, b.bank)
        assertEquals(-5000L, b.cash)

        val parts = breakdown(data, nov, Kind.EXPENSE)
        assertEquals(listOf(Slice("casa", 60000), Slice("comida", 40000), Slice(null, 5000)), parts)
    }

    @Test
    fun migrationFromV1() {
        val old = AppData(
            version = 1,
            movements = listOf(
                Movement("a", Kind.INCOME, 1000, account = Account.CASH, month = "2026-11"),
                Movement("b", Kind.TRANSFER, 500, account = Account.BANK, toAccount = Account.CASH, month = "2026-11"),
            ),
            settings = Settings(initialBank = 7000, initialCash = 300),
        ).migrated()
        assertEquals(DATA_VERSION, old.version)
        assertEquals(listOf("bank", "cash"), old.accounts.map { it.id })
        assertEquals(7000L, old.accounts[0].initial)
        assertEquals("cash", old.movements[0].accountId)
        assertEquals("cash", old.movements[1].toAccountId)
        assertTrue(old.tags.isNotEmpty())
        assertTrue(old.tags.any { it.name == "Rendimientos" })
        val b = balances(old, null)
        assertEquals(6500L, b.bank)
        assertEquals(1800L, b.cash)
        // Volver a migrar no cambia nada
        assertEquals(old, old.migrated())
    }

    @Test
    fun fundInitialAndDirectDeposit() {
        val data = AppData(
            version = DATA_VERSION,
            accounts = listOf(MoneyAccount("bbva", "BBVA", Account.BANK, 100000)),
            funds = listOf(Fund("rev", "Revolut ahorro", initial = 500000)),
            tags = listOf(Tag("rend", "Rendimientos", Kind.INCOME)),
            movements = listOf(
                // Traspaso: sale de BBVA y entra al fondo
                Movement("1", Kind.SAVE, 20000, accountId = "bbva", fundId = "rev", month = "2026-11"),
                // Rendimiento directo al fondo
                Movement("2", Kind.SAVE, 1500, accountId = "", fundId = "rev", tagId = "rend", month = "2026-11"),
            ),
        )
        val s = summarize(data, nov)
        assertEquals(521500L, s.balances.funds["rev"])
        assertEquals(80000L, s.balances.bank)
        assertEquals(1500L, s.real.income)
        assertEquals(21500L, s.real.saved)
        // El flujo solo refleja lo que salió de BBVA; el saldo inicial del fondo no lo toca
        assertEquals(-20000L, s.real.flow)
        assertEquals(100000L, s.opening)
        assertEquals(s.balances.available, s.closing)
        assertEquals(listOf(Slice("rend", 1500)), breakdown(data, nov, Kind.INCOME))
    }

    @Test
    fun dueDatesAndReminders() {
        val netflix = Movement("n", Kind.EXPENSE, 19900, accountId = "bank", nature = Nature.FIXED, month = "2026-10", day = 15)
        val renta = Movement("r", Kind.EXPENSE, 800000, accountId = "bank", nature = Nature.FIXED, month = "2026-10", day = 31,
            paidMonths = setOf("2026-10"))
        val sueldo = Movement("s", Kind.INCOME, 2000000, accountId = "bank", nature = Nature.FIXED, month = "2026-11", day = 24)
        val callado = netflix.copy(id = "x", remind = false)
        val base = AppData(version = DATA_VERSION, movements = listOf(netflix, renta, sueldo, callado))

        // El 31 se ajusta al último día del mes
        assertEquals(java.time.LocalDate.of(2027, 2, 28), renta.dueDate(YearMonth.of(2027, 2)))

        val today = java.time.LocalDate.of(2026, 11, 15)
        val r = reminders(base, today)
        // Netflix de noviembre vence hoy; Netflix de octubre quedó vencido; renta de octubre está pagada
        assertEquals(listOf("n" to DueStatus.TODAY, "n" to DueStatus.OVERDUE), r.map { it.movement.id to it.status })
        assertEquals(YearMonth.of(2026, 11), r[0].month)

        // Al marcar el de octubre como pagado ya no se recuerda
        val paid = base.copy(movements = listOf(netflix.copy(paidMonths = setOf("2026-10")), renta, sueldo))
        assertEquals(1, reminders(paid, today).size)

        // Un día antes solo si se pidió
        val tomorrow = java.time.LocalDate.of(2026, 11, 23)
        assertTrue(reminders(paid, tomorrow).none { it.status == DueStatus.TOMORROW })
        val early = paid.copy(settings = Settings(remindDayBefore = true))
        assertEquals("s", reminders(early, tomorrow).first { it.status == DueStatus.TOMORROW }.movement.id)

        // Recordatorios apagados
        assertTrue(reminders(base.copy(settings = Settings(reminders = false)), today).isEmpty())
    }

    @Test
    fun versions() {
        assertTrue(compareVersions("v1.2.0", "1.1.9") > 0)
        assertTrue(compareVersions("1.0.0", "1.0.0-dev") > 0)
        assertTrue(compareVersions("1.10.0", "1.9.0") > 0)
        assertEquals(0, compareVersions("v1.0", "1.0.0"))
        assertTrue(compareVersions("0.0.0-dev", "1.0.0") < 0)
    }
}
