package com.save.finanzas.domain

import kotlinx.serialization.Serializable

/** Tipo de movimiento. */
@Serializable
enum class Kind {
    INCOME,     // Ingreso: entra dinero a banco o efectivo
    EXPENSE,    // Gasto: sale dinero de banco o efectivo
    SAVE,       // Aporte a un fondo (sale de banco/efectivo, entra al fondo)
    WITHDRAW,   // Retiro de un fondo (sale del fondo, entra a banco/efectivo)
    TRANSFER,   // Mover entre banco y efectivo (ej. retiro en cajero)
}

/** Fijo = se repite cada mes. Variable = solo ocurre en su mes. */
@Serializable
enum class Nature { FIXED, VARIABLE }

/** Tipo de cuenta. */
@Serializable
enum class Account { BANK, CASH }

/** Una cuenta concreta: BBVA, Revolut, Stori, Efectivo… */
@Serializable
data class MoneyAccount(
    val id: String,
    val name: String,
    val type: Account = Account.BANK,
    /** Lo que tenía la cuenta antes de empezar a registrar (centavos). */
    val initial: Long = 0,
)

/** Etiqueta para agrupar ingresos o gastos (Comida, Transporte…). */
@Serializable
data class Tag(
    val id: String,
    val name: String,
    /** INCOME o EXPENSE. */
    val kind: Kind = Kind.EXPENSE,
    /** Posición en la paleta de colores (0..7). */
    val color: Int = 0,
)

@Serializable
enum class FundType { SAVINGS, EMERGENCY }

/**
 * Montos en centavos para evitar errores de redondeo.
 * Los meses se guardan como "yyyy-MM".
 */
@Serializable
data class Movement(
    val id: String,
    val kind: Kind,
    val amount: Long,
    /** Cuenta de donde sale / a donde entra el dinero. */
    val accountId: String = "",
    /** Solo para TRANSFER: cuenta destino. */
    val toAccountId: String? = null,
    val tagId: String? = null,
    /** Obsoleto (v1): tipo de cuenta antes de existir cuentas múltiples. */
    val account: Account = Account.BANK,
    /** Obsoleto (v1). */
    val toAccount: Account? = null,
    val nature: Nature = Nature.VARIABLE,
    /** Mes del movimiento variable, o mes de inicio de un fijo. */
    val month: String,
    val day: Int = 1,
    /** Fijos: último mes en que aplica (inclusive). null = indefinido. */
    val endMonth: String? = null,
    /** Fijos: meses en que ya se confirmó (pagado / recibido). */
    val paidMonths: Set<String> = emptySet(),
    val fundId: String? = null,
    val note: String = "",
    val createdAt: Long = 0,
    /** Fijos: avisar con una notificación el día de pago. */
    val remind: Boolean = true,
)

@Serializable
data class Fund(
    val id: String,
    val name: String,
    val type: FundType = FundType.SAVINGS,
    /** Meta en centavos; 0 = sin meta. */
    val goal: Long = 0,
    /** Lo que ya tenía ahorrado antes de registrar (no afecta cuentas ni flujo). */
    val initial: Long = 0,
)

@Serializable
data class Settings(
    val currency: String = "$",
    /** Obsoleto (v1): ahora cada cuenta tiene su saldo inicial. */
    val initialBank: Long = 0,
    /** Obsoleto (v1). */
    val initialCash: Long = 0,
    /** Presupuesto de gastos por mes ("yyyy-MM" -> centavos). Aplica también a los meses siguientes. */
    val budgets: Map<String, Long> = emptyMap(),
    /** Notificaciones de pagos e ingresos fijos. */
    val reminders: Boolean = true,
    /** Avisar también un día antes. */
    val remindDayBefore: Boolean = false,
)

@Serializable
data class AppData(
    val version: Int = 1,
    val movements: List<Movement> = emptyList(),
    val funds: List<Fund> = emptyList(),
    val settings: Settings = Settings(),
    val accounts: List<MoneyAccount> = emptyList(),
    val tags: List<Tag> = emptyList(),
)

const val DATA_VERSION = 3
const val PALETTE_SIZE = 8

private val defaultExpenseTags = listOf(
    "Comida", "Transporte", "Vivienda", "Servicios", "Salud", "Entretenimiento", "Compras", "Otros",
)
private val defaultIncomeTags = listOf("Sueldo", "Extra")

/** Aporte que entra directo al fondo sin salir de ninguna cuenta (rendimientos, reembolsos). */
val Movement.isDirect: Boolean get() = kind == Kind.SAVE && accountId.isEmpty()

/** Actualiza datos de versiones anteriores al formato actual. */
fun AppData.migrated(): AppData {
    if (version >= DATA_VERSION) return this
    val v2 = if (version >= 2) this else toV2()
    // v3: etiqueta de ingreso para rendimientos
    val tags3 = if (v2.tags.any { it.kind == Kind.INCOME && it.name.equals("Rendimientos", true) }) v2.tags
    else v2.tags + Tag("t-rend", "Rendimientos", Kind.INCOME, v2.tags.count { it.kind == Kind.INCOME } % PALETTE_SIZE)
    return v2.copy(version = DATA_VERSION, tags = tags3)
}

private fun AppData.toV2(): AppData {
    val accs = accounts.ifEmpty {
        listOf(
            MoneyAccount("bank", "Banco", Account.BANK, settings.initialBank),
            MoneyAccount("cash", "Efectivo", Account.CASH, settings.initialCash),
        )
    }
    fun legacyId(a: Account) = if (a == Account.BANK) "bank" else "cash"
    val mvs = movements.map { m ->
        m.copy(
            accountId = m.accountId.ifEmpty { legacyId(m.account) },
            toAccountId = m.toAccountId ?: if (m.kind == Kind.TRANSFER) legacyId(m.toAccount ?: Account.CASH) else null,
        )
    }
    val tg = tags.ifEmpty {
        defaultExpenseTags.mapIndexed { i, n -> Tag("t-e$i", n, Kind.EXPENSE, i % PALETTE_SIZE) } +
            defaultIncomeTags.mapIndexed { i, n -> Tag("t-i$i", n, Kind.INCOME, i % PALETTE_SIZE) }
    }
    return copy(
        version = 2,
        movements = mvs,
        accounts = accs,
        tags = tg,
        settings = settings.copy(initialBank = 0, initialCash = 0),
    )
}
