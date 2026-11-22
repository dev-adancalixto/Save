package com.save.finanzas.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.save.finanzas.domain.Kind
import kotlinx.coroutines.launch

@Composable
fun SaveApp(vm: AppViewModel = viewModel()) {
    val data by vm.data.collectAsState()
    val month by vm.month.collectAsState()
    val update by vm.update.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<SheetRequest?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val message: (String) -> Unit = { m -> scope.launch { snackbar.showSnackbar(m) } }

    LaunchedEffect(Unit) { vm.checkForUpdates(silent = true) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                listOf(
                    Triple("Mes", Icons.Outlined.CalendarMonth, 0),
                    Triple("Cuentas", Icons.Outlined.AccountBalanceWallet, 1),
                    Triple("Ajustes", Icons.Outlined.Settings, 2),
                ).forEach { (label, icon, i) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, label) },
                        label = { Text(label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(
                    onClick = { sheet = SheetRequest() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Default.Add, "Agregar") }
            }
        },
    ) { padding ->
        when (tab) {
            0 -> MonthScreen(
                data = data,
                month = month,
                update = update,
                contentPadding = padding,
                onPrev = vm::prevMonth,
                onNext = vm::nextMonth,
                onToday = vm::thisMonth,
                onOpen = { sheet = SheetRequest(existing = it) },
                onTogglePaid = { vm.togglePaid(it.id, month) },
                onSetBudget = { vm.setBudget(month, it) },
                onUpdateClick = { tab = 2 },
            )
            1 -> FundsScreen(
                data = data,
                contentPadding = padding,
                onDeposit = { sheet = SheetRequest(kind = Kind.SAVE, fundId = it.id) },
                onWithdraw = { sheet = SheetRequest(kind = Kind.WITHDRAW, fundId = it.id) },
                onSaveFund = vm::upsertFund,
                onDeleteFund = { vm.deleteFund(it.id) },
                onSaveAccount = vm::upsertAccount,
                onDeleteAccount = { vm.deleteAccount(it.id) },
                canDeleteAccount = { vm.canDeleteAccount(it.id) },
                newId = vm::newId,
            )
            else -> SettingsScreen(
                data = data,
                version = vm.currentVersion,
                update = update,
                contentPadding = padding,
                onSaveSettings = vm::setSettings,
                onCheckUpdate = { vm.checkForUpdates() },
                onDownload = vm::downloadUpdate,
                onExport = vm::exportTo,
                onImport = vm::importFrom,
                onMessage = message,
                onCreateTag = { name, kind -> vm.createTag(name, kind) },
                onRenameTag = vm::upsertTag,
                onDeleteTag = { vm.deleteTag(it.id) },
            )
        }
    }

    sheet?.let { req ->
        MovementSheet(
            request = req,
            month = month,
            funds = data.funds,
            accounts = data.accounts,
            tags = data.tags,
            defaultAccountId = vm.defaultAccountId(),
            onCreateTag = vm::createTag,
            symbol = data.settings.currency,
            newId = vm::newId,
            defaultDay = vm.defaultDay(month),
            onDismiss = { sheet = null },
            onSave = {
                vm.upsert(it)
                sheet = null
                if (tab == 1) message("Listo — revisa el mes para verlo")
            },
            onDelete = { vm.delete(it.id); sheet = null },
            onEndFixed = { vm.endFixedFrom(it.id, month); sheet = null },
        )
    }
}

