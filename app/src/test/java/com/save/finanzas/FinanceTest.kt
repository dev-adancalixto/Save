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
    ) = Movement(id, kind, amount, account, to, nature, month, endMonth = end, paidMonths = paid, fundId = fundId)

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
        )
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

        // Diciembre: el salario fijo aparece pero no está confirmado
        val d = summarize(data, dec)
        assertEquals(0L, d.real.income)
        assertEquals(2000000L, d.estimated.income)
        assertTrue(d.variable.isEmpty())
        assertEquals(s.balances, d.balances)
    }

    @Test
    fun fixedEndMonth() {
        val m = mv("gym", Kind.EXPENSE, 50000, Nature.FIXED, month = "2026-09", end = "2026-11",
            paid = setOf("2026-09", "2026-10", "2026-12"))
        assertTrue(m.appliesTo(nov))
        assertFalse(m.appliesTo(dec))
        assertFalse(m.appliesTo(YearMonth.of(2026, 8)))
        // diciembre está fuera de rango, no cuenta
        assertEquals(-100000L, balances(AppData(movements = listOf(m)), null).bank)
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
