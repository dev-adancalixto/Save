package com.save.finanzas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.save.finanzas.domain.*
import java.time.YearMonth

/** Lo que se va a crear o editar. */
data class SheetRequest(
    val existing: Movement? = null,
    val kind: Kind = Kind.EXPENSE,
    val fundId: String? = null,
)

private enum class Mode { INCOME, EXPENSE, FUND, TRANSFER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementSheet(
    request: SheetRequest,
    month: YearMonth,
    funds: List<Fund>,
    accounts: List<MoneyAccount>,
    tags: List<Tag>,
    defaultAccountId: String,
    onCreateTag: (String, Kind) -> Tag,
    symbol: String,
    newId: () -> String,
    defaultDay: Int,
    onDismiss: () -> Unit,
    onSave: (Movement) -> Unit,
    onDelete: (Movement) -> Unit,
    onEndFixed: (Movement) -> Unit,
) {
    val ex = request.existing
    val startKind = ex?.kind ?: request.kind
    var mode by remember {
        mutableStateOf(
            when (startKind) {
                Kind.INCOME -> Mode.INCOME
                Kind.EXPENSE -> Mode.EXPENSE
                Kind.SAVE, Kind.WITHDRAW -> Mode.FUND
                Kind.TRANSFER -> Mode.TRANSFER
            }
        )
    }
    var withdraw by remember { mutableStateOf(startKind == Kind.WITHDRAW) }
    var direct by remember { mutableStateOf(ex?.isDirect ?: false) }
    var expr by remember { mutableStateOf(ex?.let { centsToInput(it.amount) } ?: "") }
    var nature by remember { mutableStateOf(ex?.nature ?: Nature.VARIABLE) }
    var accountId by remember { mutableStateOf(ex?.accountId ?: defaultAccountId) }
    var toAccountId by remember {
        mutableStateOf(ex?.toAccountId ?: accounts.firstOrNull { it.id != (ex?.accountId ?: defaultAccountId) }?.id)
    }
    var tagId by remember { mutableStateOf(ex?.tagId) }
    var newTag by remember { mutableStateOf(false) }
    var fundId by remember { mutableStateOf(ex?.fundId ?: request.fundId ?: funds.firstOrNull()?.id) }
    var note by remember { mutableStateOf(ex?.note ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    val cents = Calc.evalCents(expr)
    val kind = when (mode) {
        Mode.INCOME -> Kind.INCOME
        Mode.EXPENSE -> Kind.EXPENSE
        Mode.FUND -> if (withdraw) Kind.WITHDRAW else Kind.SAVE
        Mode.TRANSFER -> Kind.TRANSFER
    }
    val allowsFixed = kind == Kind.INCOME || kind == Kind.EXPENSE || kind == Kind.SAVE
    val isDirect = kind == Kind.SAVE && direct
    val allowsTag = kind == Kind.INCOME || kind == Kind.EXPENSE || isDirect
    val tagKind = if (isDirect) Kind.INCOME else kind
    val kindTags = tags.filter { it.kind == tagKind }
    val effTag = tagId?.takeIf { allowsTag && kindTags.any { t -> t.id == it } }
    val valid = cents != null && cents > 0 &&
        (isDirect || accounts.any { it.id == accountId }) &&
        (mode != Mode.FUND || fundId != null) &&
        (mode != Mode.TRANSFER || (toAccountId != null && toAccountId != accountId))

    fun save() {
        if (!valid) return
        val effNature = if (allowsFixed) nature else Nature.VARIABLE
        val base = ex ?: Movement(id = newId(), kind = kind, amount = 0, month = month.key(), day = defaultDay)
        onSave(
            base.copy(
                kind = kind,
                amount = cents!!,
                accountId = if (isDirect) "" else accountId,
                toAccountId = if (kind == Kind.TRANSFER) toAccountId else null,
                tagId = effTag,
                nature = effNature,
                fundId = if (mode == Mode.FUND) fundId else null,
                note = note.trim(),
                // Al convertir variable → fijo empieza en el mes del movimiento
                endMonth = if (effNature == Nature.FIXED) base.endMonth else null,
                paidMonths = if (effNature == Nature.FIXED) base.paidMonths else emptySet(),
            )
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Segmented(
                listOf(Mode.INCOME to "Ingreso", Mode.EXPENSE to "Gasto", Mode.FUND to "Fondo", Mode.TRANSFER to "Mover"),
                mode, { mode = it },
            )

            // Pantalla de la calculadora
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.End) {
                val hasOps = expr.any { Calc.isOp(it) }
                Text(
                    if (hasOps) expr else " ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    when {
                        expr.isEmpty() -> formatMoney(0, symbol)
                        hasOps -> cents?.let { formatMoney(it, symbol) } ?: "—"
                        else -> "$symbol$expr"
                    },
                    style = MaterialTheme.typography.displayMedium,
                    color = kindColor(kind),
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when (mode) {
                Mode.FUND -> {
                    Segmented(listOf(false to "Aportar", true to "Retirar"), withdraw, { withdraw = it })
                    if (!withdraw) {
                        Segmented(listOf(false to "Desde una cuenta", true to "Ingreso directo"), direct, { direct = it })
                        Text(
                            if (direct) "Dinero que entra directo al fondo (rendimientos, reembolsos). Cuenta como ingreso y como ahorro, así que no cambia tu flujo."
                            else "Traspaso: sale de una de tus cuentas y entra al fondo.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (funds.isEmpty()) {
                        Text(
                            "Primero crea un fondo en la pestaña Fondos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            funds.forEach { f ->
                                FilterChip(selected = fundId == f.id, onClick = { fundId = f.id }, label = { Text(f.name) })
                            }
                        }
                    }
                }
                else -> Unit
            }

            // Etiqueta
            if (allowsTag) {
                ChipRow {
                    kindTags.forEach { t ->
                        FilterChip(
                            selected = effTag == t.id,
                            onClick = { tagId = if (effTag == t.id) null else t.id },
                            label = { Text(t.name) },
                            leadingIcon = {
                                Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tagColor(t.color)))
                            },
                        )
                    }
                    AssistChip(onClick = { newTag = true }, label = { Text("+ Etiqueta") })
                }
            }

            // Cuenta
            if (mode == Mode.TRANSFER) {
                AccountChips("Desde", accounts, accountId) { accountId = it; if (toAccountId == it) toAccountId = accounts.firstOrNull { a -> a.id != it }?.id }
                AccountChips("Hacia", accounts.filter { it.id != accountId }, toAccountId) { toAccountId = it }
                if (accounts.size < 2) {
                    Text("Agrega otra cuenta en la pestaña Cuentas para mover dinero.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (!isDirect) {
                AccountChips(
                    when (kind) { Kind.SAVE -> "Desde"; Kind.WITHDRAW -> "Hacia"; Kind.INCOME -> "Entra a"; else -> "Sale de" },
                    accounts, accountId,
                ) { accountId = it }
            }
            if (allowsFixed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Se repite cada mes", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = nature == Nature.FIXED, onCheckedChange = { nature = if (it) Nature.FIXED else Nature.VARIABLE })
                }
            }
            if (allowsFixed && nature == Nature.FIXED) {
                Text(
                    "Fijo: aparece cada mes como pendiente; márcalo con ✓ cuando se pague o se reciba.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("Descripción (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Keypad(
                onKey = { k -> expr = pressKey(expr, k) },
                onBackspace = { expr = expr.dropLast(1) },
                onClear = { expr = "" },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ex != null) {
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.height(52.dp)) { Text("Eliminar") }
                }
                Button(
                    onClick = { save() },
                    enabled = valid,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(if (ex == null) "Agregar" else "Guardar", fontSize = 16.sp) }
            }
        }
    }

    if (newTag) {
        NameDialog(
            title = "Nueva etiqueta de ${if (tagKind == Kind.INCOME) "ingreso" else "gasto"}",
            onDismiss = { newTag = false },
            onSave = { name -> tagId = onCreateTag(name, tagKind).id; newTag = false },
        )
    }

    if (confirmDelete && ex != null) {
        if (ex.nature == Nature.FIXED) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Eliminar fijo") },
                text = { Text("¿Quitarlo a partir de ${monthTitle(month)} (conserva los meses anteriores) o eliminarlo de todos los meses?") },
                confirmButton = { TextButton(onClick = { onEndFixed(ex) }) { Text("Desde este mes") } },
                dismissButton = { TextButton(onClick = { onDelete(ex) }) { Text("Todos") } },
            )
        } else {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("¿Eliminar movimiento?") },
                confirmButton = { TextButton(onClick = { onDelete(ex) }) { Text("Eliminar") } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
            )
        }
    }
}

private fun pressKey(expr: String, k: Char): String {
    if (Calc.isOp(k)) {
        if (expr.isEmpty()) return expr
        return if (Calc.isOp(expr.last())) expr.dropLast(1) + k else expr + k
    }
    val lastNumber = expr.takeLastWhile { !Calc.isOp(it) }
    if (k == '.') {
        if ('.' in lastNumber) return expr
        return expr + if (lastNumber.isEmpty()) "0." else "."
    }
    // Máximo 2 decimales y sin ceros a la izquierda
    if ('.' in lastNumber && lastNumber.substringAfter('.').length >= 2) return expr
    if (lastNumber == "0") return expr.dropLast(1) + k
    if (expr.length >= 40) return expr
    return expr + k
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Keypad(onKey: (Char) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit) {
    val rows = listOf(
        listOf('7', '8', '9', '÷'),
        listOf('4', '5', '6', '×'),
        listOf('1', '2', '3', '−'),
        listOf('.', '0', '<', '+'),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val isOp = Calc.isOp(k)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isOp) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer)
                            .combinedClickable(
                                onClick = { if (k == '<') onBackspace() else onKey(k) },
                                onLongClick = if (k == '<') onClear else null,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == '<') {
                            Icon(Icons.AutoMirrored.Filled.Backspace, "Borrar (mantén para limpiar)")
                        } else {
                            Text(k.toString(), fontSize = 22.sp, fontWeight = if (isOp) FontWeight.Medium else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountChips(label: String, accounts: List<MoneyAccount>, selected: String?, onSelect: (String) -> Unit) {
    ChipRow {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        accounts.forEach { a ->
            FilterChip(selected = selected == a.id, onClick = { onSelect(a.id) }, label = { Text(a.name) })
        }
    }
}
