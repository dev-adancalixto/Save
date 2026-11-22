package com.save.finanzas.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/** 123456 -> "$1,234.56"; 120000 -> "$1,200". */
fun formatMoney(cents: Long, symbol: String = "$", signed: Boolean = false): String {
    val a = abs(cents)
    val whole = (a / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    val dec = a % 100
    val body = if (dec == 0L) whole else "$whole.${dec.toString().padStart(2, '0')}"
    val sign = when {
        cents < 0 -> "−"
        signed && cents > 0 -> "+"
        else -> ""
    }
    return "$sign$symbol$body"
}

/** Texto para editar: 123456 -> "1234.56". */
fun centsToInput(cents: Long): String {
    if (cents == 0L) return ""
    return BigDecimal(cents).movePointLeft(2).stripTrailingZeros().toPlainString()
}

/**
 * Evaluador de la calculadora: números con + − × ÷ (y también + - * /),
 * con precedencia normal. Devuelve centavos o null si la expresión no es válida.
 */
object Calc {
    private val ops = setOf('+', '-', '*', '/', '−', '×', '÷')

    fun evalCents(expr: String): Long? {
        val v = eval(expr) ?: return null
        return v.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()
    }

    fun eval(expr: String): BigDecimal? {
        val s = expr.replace(" ", "").replace(",", "")
            .replace('−', '-').replace('×', '*').replace('÷', '/')
            .trimEnd('+', '-', '*', '/')
        if (s.isEmpty()) return null
        // Tokenizar
        val nums = mutableListOf<BigDecimal>()
        val opsList = mutableListOf<Char>()
        var i = 0
        var expectNumber = true
        while (i < s.length) {
            val c = s[i]
            if (expectNumber) {
                var j = i
                if (j < s.length && s[j] == '-') j++ // número negativo
                val start = j
                while (j < s.length && (s[j].isDigit() || s[j] == '.')) j++
                val raw = s.substring(start, j)
                if (raw.isEmpty() || raw == "." || raw.count { it == '.' } > 1) return null
                val n = BigDecimal(if (raw.startsWith(".")) "0$raw" else raw)
                nums += if (s[i] == '-') n.negate() else n
                i = j
                expectNumber = false
            } else {
                if (c !in ops) return null
                opsList += c
                i++
                expectNumber = true
            }
        }
        if (expectNumber) return null
        // Primero × ÷
        val n2 = mutableListOf(nums[0])
        val o2 = mutableListOf<Char>()
        for (k in opsList.indices) {
            val op = opsList[k]
            val next = nums[k + 1]
            when (op) {
                '*' -> n2[n2.lastIndex] = n2.last().multiply(next)
                '/' -> {
                    if (next.signum() == 0) return null
                    n2[n2.lastIndex] = n2.last().divide(next, 10, RoundingMode.HALF_UP)
                }
                else -> { o2 += op; n2 += next }
            }
        }
        var acc = n2[0]
        for (k in o2.indices) acc = if (o2[k] == '+') acc.add(n2[k + 1]) else acc.subtract(n2[k + 1])
        return acc
    }

    fun isOp(c: Char) = c in ops
}
