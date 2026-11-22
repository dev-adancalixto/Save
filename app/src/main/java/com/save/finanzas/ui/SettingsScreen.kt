package com.save.finanzas.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.save.finanzas.domain.*
import com.save.finanzas.update.Updater
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen(
    data: AppData,
    version: String,
    update: UpdateState,
    contentPadding: PaddingValues,
    onSaveSettings: (Settings) -> Unit,
    onCheckUpdate: () -> Unit,
    onDownload: (com.save.finanzas.update.Release) -> Unit,
    onExport: suspend (Uri) -> Unit,
    onImport: suspend (Uri) -> Unit,
    onMessage: (String) -> Unit,
) {
    val s = data.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currency by remember(s.currency) { mutableStateOf(s.currency) }
    var bank by remember(s.initialBank) { mutableStateOf(centsToInput(s.initialBank)) }
    var cash by remember(s.initialCash) { mutableStateOf(centsToInput(s.initialCash)) }
    var confirmImport by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { onExport(uri) }
                .onSuccess { onMessage("Respaldo guardado") }
                .onFailure { onMessage("No se pudo guardar: ${it.message}") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport = uri
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall)

        SectionLabel("General", Modifier.padding(top = 8.dp))
        OutlinedTextField(
            value = currency,
            onValueChange = { currency = it.take(4) },
            label = { Text("Símbolo de moneda") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionLabel("Saldo inicial", Modifier.padding(top = 8.dp))
        Text(
            "Lo que ya tenías antes de empezar a registrar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MoneyField(bank, { bank = it }, "En banco", currency)
        MoneyField(cash, { cash = it }, "En efectivo", currency)

        Button(
            onClick = {
                onSaveSettings(
                    s.copy(
                        currency = currency.ifBlank { "$" },
                        initialBank = Calc.evalCents(bank) ?: 0,
                        initialCash = Calc.evalCents(cash) ?: 0,
                    )
                )
                onMessage("Ajustes guardados")
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Guardar ajustes") }

        SectionLabel("Respaldo", Modifier.padding(top = 16.dp))
        Text(
            "Tus datos viven solo en este teléfono. Exporta un respaldo antes de cambiar de equipo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportLauncher.launch("save-respaldo-${LocalDate.now()}.json") }, modifier = Modifier.weight(1f)) { Text("Exportar") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.weight(1f)) { Text("Importar") }
        }

        SectionLabel("Actualizaciones", Modifier.padding(top = 16.dp))
        Text("Versión instalada: $version", style = MaterialTheme.typography.bodyMedium)
        when (update) {
            UpdateState.Idle -> OutlinedButton(onClick = onCheckUpdate, modifier = Modifier.fillMaxWidth()) { Text("Buscar actualizaciones") }
            UpdateState.Checking -> LinearProgressIndicator(Modifier.fillMaxWidth())
            UpdateState.UpToDate -> {
                Text("Estás en la última versión.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onCheckUpdate, modifier = Modifier.fillMaxWidth()) { Text("Buscar de nuevo") }
            }
            is UpdateState.Error -> {
                Text(update.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onCheckUpdate, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
            }
            is UpdateState.Available -> {
                Text("Disponible: versión ${update.release.version}", style = MaterialTheme.typography.titleMedium)
                if (update.release.notes.isNotBlank()) {
                    Text(update.release.notes.take(600), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { onDownload(update.release) }, modifier = Modifier.fillMaxWidth()) { Text("Descargar e instalar") }
            }
            is UpdateState.Downloading -> {
                Text("Descargando ${update.release.version}…", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { update.progress }, modifier = Modifier.fillMaxWidth())
            }
            is UpdateState.Ready -> {
                Text("Lista para instalar: ${update.release.version}", style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = {
                        if (Updater.canInstall(context)) Updater.install(context, update.file)
                        else {
                            onMessage("Permite instalar apps desde Save y vuelve a tocar Instalar")
                            Updater.openInstallPermission(context)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Instalar") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("¿Importar respaldo?") },
            text = { Text("Se reemplazarán todos los datos actuales por los del archivo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = null
                    scope.launch {
                        runCatching { onImport(uri) }
                            .onSuccess { onMessage("Respaldo importado") }
                            .onFailure { onMessage("Archivo no válido") }
                    }
                }) { Text("Importar") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Cancelar") } },
        )
    }
}
