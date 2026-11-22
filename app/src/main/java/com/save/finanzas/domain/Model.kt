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

@Serializable
enum class Account { BANK, CASH }

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
    val account: Account = Account.BANK,
    /** Solo para TRANSFER: cuenta destino. */
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
)

@Serializable
data class Fund(
    val id: String,
    val name: String,
    val type: FundType = FundType.SAVINGS,
    /** Meta en centavos; 0 = sin meta. */
    val goal: Long = 0,
)

@Serializable
data class Settings(
    val currency: String = "$",
    val initialBank: Long = 0,
    val initialCash: Long = 0,
    /** Presupuesto de gastos por mes ("yyyy-MM" -> centavos). Aplica también a los meses siguientes. */
    val budgets: Map<String, Long> = emptyMap(),
)

@Serializable
data class AppData(
    val version: Int = 1,
    val movements: List<Movement> = emptyList(),
    val funds: List<Fund> = emptyList(),
    val settings: Settings = Settings(),
)
