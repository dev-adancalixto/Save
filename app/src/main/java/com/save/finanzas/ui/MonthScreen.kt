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
    val fundNames = remember(data.funds) { data.funds.associate { it.id to it.name } }
    var editBudget by remember { mutableStateOf(false) }

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

        // Saldos
        item {
            SectionLabel("Dónde está tu dinero", Modifier.padding(top = 8.dp))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Banco", formatMoney(s.balances.bank, cur), Modifier.weight(1f))
                StatTile("Efectivo", formatMoney(s.balances.cash, cur), Modifier.weight(1f))
                StatTile("Fondos", formatMoney(s.balances.fundsTotal, cur), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text("Total", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(formatMoney(s.balances.total, cur), style = MaterialTheme.typography.titleMedium)
            }
        }

        if (s.fixed.isNotEmpty()) {
            item { SectionLabel("Fijos del mes", Modifier.padding(top = 12.dp)) }
            items(s.fixed, key = { "f" + it.movement.id }) { e ->
                MovementRow(e, cur, fundNames, onClick = { onOpen(e.movement) }, onToggle = { onTogglePaid(e.movement) })
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
                MovementRow(e, cur, fundNames, onClick = { onOpen(e.movement) }, onToggle = null)
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
    fundNames: Map<String, String>,
    onClick: () -> Unit,
    onToggle: (() -> Unit)?,
) {
    val m = e.movement
    val color = kindColor(m.kind)
    val title = m.note.ifBlank {
        when (m.kind) {
            Kind.SAVE, Kind.WITHDRAW -> fundNames[m.fundId] ?: m.kind.label()
            else -> m.kind.label()
        }
    }
    val detail = buildList {
        when (m.kind) {
            Kind.TRANSFER -> add("${m.account.label()} → ${(m.toAccount ?: Account.CASH).label()}")
            Kind.SAVE -> { add("${m.account.label()} → ${fundNames[m.fundId] ?: "fondo"}") }
            Kind.WITHDRAW -> { add("${fundNames[m.fundId] ?: "fondo"} → ${m.account.label()}") }
            else -> add(m.account.label())
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
            Box(Modifier.padding(horizontal = 9.dp).size(8.dp).clip(CircleShape).background(color))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
