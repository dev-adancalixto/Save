package com.save.finanzas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.save.finanzas.domain.*
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

val Spanish: Locale = Locale.forLanguageTag("es-MX")

fun monthTitle(m: YearMonth): String =
    m.format(DateTimeFormatter.ofPattern("MMMM yyyy", Spanish)).replaceFirstChar { it.titlecase(Spanish) }

fun Account.label() = if (this == Account.BANK) "Banco" else "Efectivo"
fun FundType.label() = if (this == FundType.SAVINGS) "Ahorro" else "Emergencia"

fun Kind.label() = when (this) {
    Kind.INCOME -> "Ingreso"
    Kind.EXPENSE -> "Gasto"
    Kind.SAVE -> "Aporte a fondo"
    Kind.WITHDRAW -> "Retiro de fondo"
    Kind.TRANSFER -> "Movimiento"
}

@Composable
fun kindColor(kind: Kind): Color = when (kind) {
    Kind.INCOME, Kind.WITHDRAW -> Tone.income
    Kind.EXPENSE -> Tone.expense
    Kind.SAVE -> Tone.save
    Kind.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Signo con el que el movimiento afecta tu dinero disponible. */
fun Kind.sign(): Int = when (this) {
    Kind.INCOME, Kind.WITHDRAW -> 1
    Kind.EXPENSE, Kind.SAVE -> -1
    Kind.TRANSFER -> 0
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Spanish),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun Tile(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, color: Color = Color.Unspecified, onClick: (() -> Unit)? = null) {
    Tile(modifier, onClick) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
        if (sub != null) {
            Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Campo de monto simple (acepta operaciones como 100+50). */
@Composable
fun MoneyField(value: String, onChange: (String) -> Unit, label: String, symbol: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onChange(s.filter { it.isDigit() || it == '.' || Calc.isOp(it) }) },
        label = { Text(label) },
        prefix = { Text(symbol) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Paleta categórica validada (orden fijo; el color sigue a la etiqueta, no a su posición). */
private val TagLight = listOf(
    0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100, 0xFFE87BA4, 0xFF008300, 0xFF6250D6, 0xFFE34948,
).map { Color(it) }
private val TagDark = listOf(
    0xFF3987E5, 0xFFD95926, 0xFF199E70, 0xFFC98500, 0xFFD55181, 0xFF0CA30C, 0xFF9085E9, 0xFFE66767,
).map { Color(it) }

@Composable
fun tagColor(slot: Int): Color {
    val p = if (androidx.compose.foundation.isSystemInDarkTheme()) TagDark else TagLight
    return p[Math.floorMod(slot, p.size)]
}

/** Nombres para mostrar cuentas, fondos y etiquetas. */
data class Lookup(
    val accounts: Map<String, MoneyAccount>,
    val funds: Map<String, Fund>,
    val tags: Map<String, Tag>,
) {
    fun account(id: String?) = id?.let { accounts[it] }?.name ?: "Cuenta"
    fun fund(id: String?) = id?.let { funds[it] }?.name ?: "Fondo"
    fun tag(id: String?): Tag? = id?.let { tags[it] }

    companion object {
        fun of(d: AppData) = Lookup(
            d.accounts.associateBy { it.id },
            d.funds.associateBy { it.id },
            d.tags.associateBy { it.id },
        )
    }
}

/** Fila de chips con scroll horizontal. */
@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Diálogo simple para escribir un nombre. */
@Composable
fun NameDialog(title: String, initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
