package com.save.finanzas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.save.finanzas.domain.*
import java.time.YearMonth

@Composable
fun MonthScreen(
    data: AppData,
    month: YearMonth,
    update: UpdateState,
    contentPadding: PaddingValues,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onOpen: (Movement) -> Unit,
    onTogglePaid: (Movement) -> Unit,
    onSetBudget: (Long?) -> Unit,
    onUpdateClick: () -> Unit,
) {
    val s = remember(data, month) { summarize(data, month) }
    val cur = data.settings.currency
    val look = remember(data) { Lookup.of(data) }
    var editBudget by remember { mutableStateOf(false) }
    var chartKind by remember { mutableStateOf(Kind.EXPENSE) }
    val slices = remember(data, month, chartKind) { breakdown(data, month, chartKind) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mes anterior") }
                Text(
                    monthTitle(month),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).clickable(onClick = onToday),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mes siguiente") }
            }
        }

        if (update is UpdateState.Available || update is UpdateState.Ready) {
            item {
                val v = when (update) {
                    is UpdateState.Available -> update.release.version
                    is UpdateState.Ready -> update.release.version
                    else -> ""
                }
                Tile(Modifier.fillMaxWidth(), onClick = onUpdateClick) {
                    Text("Nueva versión $v disponible", style = MaterialTheme.typography.titleMedium)
                    Text("Toca para actualizar", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Flujo del mes
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SectionLabel("Flujo del mes")
                Spacer(Modifier.height(6.dp))
                Text(
                    formatMoney(s.real.flow, cur),
                    style = MaterialTheme.typography.displayMedium,
                    color = if (s.real.flow < 0) Tone.expense else MaterialTheme.colorScheme.onBackground,
                )
                if (s.estimated != s.real) {
                    Text(
                        "Estimado al cierre: ${formatMoney(s.estimated.flow, cur)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Saldo inicial + flujo = saldo final (pasa al siguiente mes)
        item {
            Tile(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CarryCell("Saldo inicial", formatMoney(s.opening, cur), Modifier.weight(1f))
                    Text("+", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CarryCell("Flujo", formatMoney(s.real.flow, cur), Modifier.weight(1f))
                    Text("=", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CarryCell("Saldo final", formatMoney(s.closing, cur), Modifier.weight(1f), strong = true)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        append("El saldo final pasa como saldo inicial de ")
                        append(monthTitle(month.plusMonths(1)).substringBefore(' '))
                        if (s.closingEstimated != s.closing) append(" · estimado ${formatMoney(s.closingEstimated, cur)}")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    "Ingresos", formatMoney(s.real.income, cur), Modifier.weight(1f),
                    sub = estSub(s.estimated.income, s.real.income, cur), color = Tone.income,
                )
                StatTile(
                    "Gastos", formatMoney(s.real.expense, cur), Modifier.weight(1f),
                    sub = estSub(s.estimated.expense, s.real.expense, cur), color = Tone.expense,
                )
                StatTile(
                    "Ahorro", formatMoney(s.real.saved, cur), Modifier.weight(1f),
                    sub = estSub(s.estimated.saved, s.real.saved, cur), color = Tone.save,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Fijos", "${formatMoney(s.fixedIncome, cur)} / ${formatMoney(s.fixedExpense, cur)}", Modifier.weight(1f), sub = "ingreso / gasto")
                StatTile("Variables", "${formatMoney(s.variableIncome, cur)} / ${formatMoney(s.variableExpense, cur)}", Modifier.weight(1f), sub = "ingreso / gasto")
            }
        }

        // Presupuesto
        item {
            Tile(Modifier.fillMaxWidth(), onClick = { editBudget = true }) {
                if (s.budget == null) {
                    Text("Definir presupuesto mensual", style = MaterialTheme.typography.titleMedium)
                    Text("Un límite de gastos para ver cuánto te queda", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val left = s.budgetLeft ?: 0
                    val ratio = (s.real.expense.toFloat() / s.budget).coerceIn(0f, 1f)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            SectionLabel("Presupuesto")
                            Text(
                                if (left >= 0) "Te quedan ${formatMoney(left, cur)}" else "Excedido por ${formatMoney(-left, cur)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (left < 0) Tone.expense else Color.Unspecified,
                            )
                        }
                        Text("de ${formatMoney(s.budget, cur)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (left < 0) Tone.expense else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        drawStopIndicator = {},
                    )
                    val estLeft = s.budgetLeftEstimated
                    if (estLeft != null && estLeft != left) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Con fijos pendientes: ${formatMoney(estLeft, cur)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Gráfica por etiqueta
        item {
            Tile(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(if (chartKind == Kind.EXPENSE) "En qué se va tu dinero" else "De dónde viene", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Segmented(listOf(Kind.EXPENSE to "Gastos", Kind.INCOME to "Ingresos"), chartKind, { chartKind = it })
                Spacer(Modifier.height(14.dp))
                TagBreakdown(slices, look, cur)
            }
        }

        // Saldos por cuenta
        item {
            SectionLabel("Dónde está tu dinero", Modifier.padding(top = 8.dp))
        }
        item {
            Tile(Modifier.fillMaxWidth()) {
                data.accounts.forEach { a ->
                    BalanceLine(a.name, a.type.label(), s.balances.accounts[a.id] ?: 0, cur)
                }
                if (data.funds.isNotEmpty()) {
                    BalanceLine("Fondos", "ahorro y emergencia", s.balances.fundsTotal, cur)
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Total", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Banco ${formatMoney(s.balances.bank, cur)} · Efectivo ${formatMoney(s.balances.cash, cur)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(formatMoney(s.balances.total, cur), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (s.fixed.isNotEmpty()) {
            item { SectionLabel("Fijos del mes", Modifier.padding(top = 12.dp)) }
            items(s.fixed, key = { "f" + it.movement.id }) { e ->
                MovementRow(e, cur, look, onClick = { onOpen(e.movement) }, onToggle = { onTogglePaid(e.movement) })
            }
        }

        item { SectionLabel("Movimientos", Modifier.padding(top = 12.dp)) }
        if (s.variable.isEmpty()) {
            item {
                Text(
                    "Sin movimientos este mes. Toca + para agregar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            items(s.variable, key = { "v" + it.movement.id }) { e ->
                MovementRow(e, cur, look, onClick = { onOpen(e.movement) }, onToggle = null)
            }
        }
    }

    if (editBudget) {
        BudgetDialog(
            initial = s.budget ?: 0,
            symbol = cur,
            onDismiss = { editBudget = false },
            onSave = { onSetBudget(it); editBudget = false },
        )
    }
}

private fun estSub(est: Long, real: Long, cur: String) =
    if (est != real) "est. ${formatMoney(est, cur)}" else null

@Composable
fun MovementRow(
    e: Entry,
    cur: String,
    look: Lookup,
    onClick: () -> Unit,
    onToggle: (() -> Unit)?,
) {
    val m = e.movement
    val color = kindColor(m.kind)
    val tag = look.tag(m.tagId)
    val title = m.note.ifBlank {
        when (m.kind) {
            Kind.SAVE, Kind.WITHDRAW -> look.fund(m.fundId)
            else -> tag?.name ?: m.kind.label()
        }
    }
    val detail = buildList {
        when (m.kind) {
            Kind.TRANSFER -> add("${look.account(m.accountId)} → ${look.account(m.toAccountId)}")
            Kind.SAVE -> add("${look.account(m.accountId)} → ${look.fund(m.fundId)}")
            Kind.WITHDRAW -> add("${look.fund(m.fundId)} → ${look.account(m.accountId)}")
            else -> {
                if (tag != null && m.note.isNotBlank()) add(tag.name)
                add(look.account(m.accountId))
            }
        }
        if (m.nature == Nature.VARIABLE) add("día ${m.day}")
        if (e.pending) add("pendiente")
    }.joinToString(" · ")

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onToggle != null) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (e.pending) MaterialTheme.colorScheme.surfaceContainerHigh else color)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                if (!e.pending) Icon(Icons.Default.Check, "Confirmado", tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(16.dp))
            }
        } else {
            val dot = tag?.let { tagColor(it.color) } ?: color
            Box(Modifier.padding(horizontal = 9.dp).size(8.dp).clip(CircleShape).background(dot))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val signed = m.kind.sign() * m.amount
        Text(
            if (m.kind == Kind.TRANSFER) formatMoney(m.amount, cur) else formatMoney(signed, cur, signed = true),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (e.pending) MaterialTheme.colorScheme.onSurfaceVariant else color,
        )
    }
}

@Composable
private fun CarryCell(label: String, value: String, modifier: Modifier = Modifier, strong: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun BalanceLine(name: String, sub: String, amount: Long, cur: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatMoney(amount, cur), style = MaterialTheme.typography.titleMedium, color = if (amount < 0) Tone.expense else Color.Unspecified)
    }
}

private const val OTHER = "__otros"

/** Barra 100% apilada + tabla con nombre, monto y porcentaje por etiqueta. */
@Composable
fun TagBreakdown(raw: List<Slice>, look: Lookup, cur: String) {
    val total = raw.sumOf { it.amount }
    if (total <= 0) {
        Text(
            "Aún no hay movimientos este mes. Ponle etiqueta a cada uno para ver aquí el reparto.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    // Más de 8 grupos: los menores se juntan en "Otras"
    val slices = if (raw.size <= 8) raw else raw.take(7) + Slice(OTHER, raw.drop(7).sumOf { it.amount })
    val neutral = MaterialTheme.colorScheme.outline
    @Composable
    fun colorOf(sl: Slice): Color = when (sl.tagId) {
        null, OTHER -> neutral
        else -> look.tag(sl.tagId)?.let { tagColor(it.color) } ?: neutral
    }
    fun nameOf(sl: Slice): String = when (sl.tagId) {
        null -> "Sin etiqueta"
        OTHER -> "Otras"
        else -> look.tag(sl.tagId)?.name ?: "Sin etiqueta"
    }

    Row(
        Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        slices.forEach { sl ->
            Box(
                Modifier
                    .weight(sl.amount.toFloat().coerceAtLeast(total * 0.004f))
                    .fillMaxHeight()
                    .background(colorOf(sl)),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    slices.forEach { sl ->
        val pct = sl.amount * 100.0 / total
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(colorOf(sl)))
            Spacer(Modifier.width(10.dp))
            Text(nameOf(sl), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatMoney(sl.amount, cur), style = MaterialTheme.typography.bodyMedium)
            Text(
                if (pct < 1) "<1%" else "${Math.round(pct)}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.width(52.dp),
            )
        }
    }
}

@Composable
fun BudgetDialog(initial: Long, symbol: String, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    var text by remember { mutableStateOf(centsToInput(initial)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Presupuesto mensual") },
        text = {
            Column {
                Text(
                    "Límite de gastos para este mes y los siguientes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                MoneyField(text, { text = it }, "Monto", symbol)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(Calc.evalCents(text)) }) { Text("Guardar") } },
        dismissButton = {
            Row {
                if (initial > 0) TextButton(onClick = { onSave(null) }) { Text("Quitar") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
