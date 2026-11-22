package com.save.finanzas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.save.finanzas.domain.*

@Composable
fun FundsScreen(
    data: AppData,
    contentPadding: PaddingValues,
    onDeposit: (Fund) -> Unit,
    onWithdraw: (Fund) -> Unit,
    onSaveFund: (Fund) -> Unit,
    onDeleteFund: (Fund) -> Unit,
    onSaveAccount: (MoneyAccount) -> Unit,
    onDeleteAccount: (MoneyAccount) -> Unit,
    canDeleteAccount: (MoneyAccount) -> Boolean,
    newId: () -> String,
) {
    val cur = data.settings.currency
    val bal = remember(data) { balances(data, null) }
    var editing by remember { mutableStateOf<Fund?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<MoneyAccount?>(null) }
    var creatingAccount by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SectionLabel("Todo tu dinero")
                Spacer(Modifier.height(6.dp))
                Text(formatMoney(bal.total, cur), style = MaterialTheme.typography.displayMedium)
                Text(
                    "Banco ${formatMoney(bal.bank, cur)} · Efectivo ${formatMoney(bal.cash, cur)} · Fondos ${formatMoney(bal.fundsTotal, cur)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionLabel("Cuentas", Modifier.padding(top = 8.dp)) }
        items(data.accounts, key = { "a" + it.id }) { a ->
            val b = bal.accounts[a.id] ?: 0
            Tile(Modifier.fillMaxWidth(), onClick = { editingAccount = a }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.titleMedium)
                        Text(a.type.label(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatMoney(b, cur), style = MaterialTheme.typography.headlineSmall, color = if (b < 0) Tone.expense else androidx.compose.ui.graphics.Color.Unspecified)
                }
            }
        }
        item {
            OutlinedButton(onClick = { creatingAccount = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Nueva cuenta") }
        }

        item { SectionLabel("Fondos", Modifier.padding(top = 16.dp)) }
        if (data.funds.isEmpty()) {
            item {
                Text(
                    "Crea fondos para separar tu ahorro y tu colchón de emergencia del dinero del día a día.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(data.funds, key = { it.id }) { f ->
            val b = bal.funds[f.id] ?: 0
            Tile(Modifier.fillMaxWidth(), onClick = { editing = f }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.titleMedium)
                        Text(f.type.label(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatMoney(b, cur), style = MaterialTheme.typography.headlineSmall)
                }
                if (f.goal > 0) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (b.toFloat() / f.goal).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Tone.save,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Meta ${formatMoney(f.goal, cur)} · ${(b * 100 / f.goal).coerceAtLeast(0)}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onDeposit(f) }, modifier = Modifier.weight(1f)) { Text("Aportar") }
                    OutlinedButton(onClick = { onWithdraw(f) }, modifier = Modifier.weight(1f)) { Text("Retirar") }
                }
            }
        }
        item {
            OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Nuevo fondo") }
        }
    }

    if (creatingAccount || editingAccount != null) {
        val ea = editingAccount
        AccountDialog(
            initial = ea,
            symbol = cur,
            canDelete = ea != null && canDeleteAccount(ea),
            onDismiss = { creatingAccount = false; editingAccount = null },
            onSave = { name, type, initial ->
                onSaveAccount((ea ?: MoneyAccount(newId(), name)).copy(name = name, type = type, initial = initial))
                creatingAccount = false; editingAccount = null
            },
            onDelete = { if (ea != null) onDeleteAccount(ea); editingAccount = null },
        )
    }

    if (creating || editing != null) {
        FundDialog(
            initial = editing,
            symbol = cur,
            onDismiss = { creating = false; editing = null },
            onSave = { name, type, goal ->
                onSaveFund((editing ?: Fund(newId(), name)).copy(name = name, type = type, goal = goal))
                creating = false; editing = null
            },
            onDelete = editing?.let { f -> { onDeleteFund(f); editing = null } },
        )
    }
}

@Composable
private fun FundDialog(
    initial: Fund?,
    symbol: String,
    onDismiss: () -> Unit,
    onSave: (String, FundType, Long) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: FundType.SAVINGS) }
    var goal by remember { mutableStateOf(centsToInput(initial?.goal ?: 0)) }
    var askDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo fondo" else "Editar fondo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Segmented(listOf(FundType.SAVINGS to "Ahorro", FundType.EMERGENCY to "Emergencia"), type, { type = it })
                MoneyField(goal, { goal = it }, "Meta (opcional)", symbol)
                if (askDelete) {
                    Text(
                        "Se eliminarán también sus aportes y retiros. ¿Continuar?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), type, Calc.evalCents(goal) ?: 0) },
                enabled = name.isNotBlank(),
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { if (askDelete) onDelete() else askDelete = true }) {
                        Text(if (askDelete) "Sí, eliminar" else "Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun AccountDialog(
    initial: MoneyAccount?,
    symbol: String,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Account, Long) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: Account.BANK) }
    var start by remember { mutableStateOf(centsToInput(initial?.initial ?: 0)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nueva cuenta" else "Editar cuenta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre (ej. BBVA, Revolut)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Segmented(listOf(Account.BANK to "Banco", Account.CASH to "Efectivo"), type, { type = it })
                MoneyField(start, { start = it }, "Saldo al empezar", symbol)
                Text(
                    "Lo que tenía esta cuenta antes de registrar movimientos.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (initial != null && !canDelete) {
                    Text(
                        "Para eliminarla no debe tener movimientos y debe quedar al menos otra cuenta.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), type, Calc.evalCents(start) ?: 0) }, enabled = name.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (initial != null && canDelete) {
                    TextButton(onClick = onDelete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
