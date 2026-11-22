package com.save.finanzas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
