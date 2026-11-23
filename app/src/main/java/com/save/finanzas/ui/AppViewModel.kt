package com.save.finanzas.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.save.finanzas.BuildConfig
import com.save.finanzas.data.Store
import com.save.finanzas.domain.*
import com.save.finanzas.notify.Notifier
import com.save.finanzas.update.Release
import com.save.finanzas.update.Updater
import com.save.finanzas.update.compareVersions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Ready(val release: Release, val file: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app.filesDir)
    private val saveLock = Mutex()

    private val _data = MutableStateFlow(store.load().let { raw ->
        raw.migrated().also { if (it != raw) store.save(it) }
    })
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME

    private val edits = java.util.concurrent.atomic.AtomicInteger(0)

    private fun edit(f: (AppData) -> AppData) {
        edits.incrementAndGet()
        _data.value = f(_data.value)
        viewModelScope.launch(Dispatchers.IO) { saveLock.withLock { store.save(_data.value) } }
    }

    /** Vuelve a leer el archivo (lo pudo cambiar una notificación). Nunca pisa ediciones recientes. */
    fun reload() {
        val stamp = edits.get()
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.withLock {
                val fresh = store.load().migrated()
                if (edits.get() == stamp && fresh != _data.value) _data.value = fresh
            }
        }
    }

    fun canNotify(): Boolean = Notifier.canNotify(getApplication())

    // ---- Mes ----
    fun prevMonth() { _month.value = _month.value.minusMonths(1) }
    fun nextMonth() { _month.value = _month.value.plusMonths(1) }
    fun thisMonth() { _month.value = YearMonth.now() }

    // ---- Movimientos ----
    fun newId(): String = UUID.randomUUID().toString()

    fun defaultDay(m: YearMonth): Int {
        val today = LocalDate.now()
        return if (YearMonth.from(today) == m) today.dayOfMonth else 1
    }

    fun upsert(mv: Movement) = edit { d ->
        val exists = d.movements.any { it.id == mv.id }
        val stamped = if (mv.createdAt == 0L) mv.copy(createdAt = System.currentTimeMillis()) else mv
        d.copy(movements = if (exists) d.movements.map { if (it.id == mv.id) stamped else it } else d.movements + stamped)
    }

    fun delete(id: String) = edit { d -> d.copy(movements = d.movements.filterNot { it.id == id }) }

    /** Fijo: deja de aplicar a partir de `from` (los meses anteriores se conservan). */
    fun endFixedFrom(id: String, from: YearMonth) = edit { d ->
        d.copy(movements = d.movements.mapNotNull { mv ->
            if (mv.id != id) return@mapNotNull mv
            val last = from.minusMonths(1)
            if (last.isBefore(monthOf(mv.month))) null
            else mv.copy(endMonth = last.key(), paidMonths = mv.paidMonths.filter { it <= last.key() }.toSet())
        })
    }

    fun togglePaid(id: String, m: YearMonth) = edit { d ->
        d.copy(movements = d.movements.map { mv ->
            if (mv.id != id) mv
            else if (m.key() in mv.paidMonths) mv.copy(paidMonths = mv.paidMonths - m.key())
            else mv.copy(paidMonths = mv.paidMonths + m.key())
        })
    }

    // ---- Fondos ----
    fun upsertFund(f: Fund) = edit { d ->
        val exists = d.funds.any { it.id == f.id }
        d.copy(funds = if (exists) d.funds.map { if (it.id == f.id) f else it } else d.funds + f)
    }

    fun deleteFund(id: String) = edit { d ->
        d.copy(funds = d.funds.filterNot { it.id == id }, movements = d.movements.filterNot { it.fundId == id })
    }

    // ---- Cuentas ----
    fun defaultAccountId(): String =
        (_data.value.accounts.firstOrNull { it.type == Account.BANK } ?: _data.value.accounts.firstOrNull())?.id ?: ""

    fun upsertAccount(a: MoneyAccount) = edit { d ->
        val exists = d.accounts.any { it.id == a.id }
        d.copy(accounts = if (exists) d.accounts.map { if (it.id == a.id) a else it } else d.accounts + a)
    }

    /** Solo se puede borrar una cuenta sin movimientos y que no sea la última. */
    fun canDeleteAccount(id: String): Boolean {
        val d = _data.value
        return d.accounts.size > 1 && d.movements.none { it.accountId == id || it.toAccountId == id }
    }

    fun deleteAccount(id: String) {
        if (!canDeleteAccount(id)) return
        edit { d -> d.copy(accounts = d.accounts.filterNot { it.id == id }) }
    }

    // ---- Etiquetas ----
    /** Crea una etiqueta con el color menos usado entre las de su tipo. */
    fun createTag(name: String, kind: Kind): Tag {
        val d = _data.value
        val used = d.tags.filter { it.kind == kind }.groupingBy { it.color }.eachCount()
        val slot = (0 until PALETTE_SIZE).minBy { used[it] ?: 0 }
        val t = Tag(newId(), name.trim(), kind, slot)
        edit { it.copy(tags = it.tags + t) }
        return t
    }

    fun upsertTag(t: Tag) = edit { d -> d.copy(tags = d.tags.map { if (it.id == t.id) t else it }) }

    fun deleteTag(id: String) = edit { d ->
        d.copy(
            tags = d.tags.filterNot { it.id == id },
            movements = d.movements.map { if (it.tagId == id) it.copy(tagId = null) else it },
        )
    }

    // ---- Ajustes ----
    fun setBudget(m: YearMonth, cents: Long?) = edit { d ->
        val b = d.settings.budgets.toMutableMap()
        b[m.key()] = cents ?: 0L
        d.copy(settings = d.settings.copy(budgets = b))
    }

    fun setSettings(s: Settings) = edit { it.copy(settings = s) }

    // ---- Respaldo ----
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(Store.encode(_data.value).toByteArray())
        } ?: error("No se pudo abrir el archivo")
    }

    suspend fun importFrom(uri: Uri) {
        val text = withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error("No se pudo leer el archivo")
        }
        val parsed = Store.decode(text).migrated()
        edit { parsed }
    }

    // ---- Actualizaciones ----
    fun checkForUpdates(silent: Boolean = false) {
        if (_update.value is UpdateState.Checking || _update.value is UpdateState.Downloading) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            _update.value = try {
                val r = Updater.latest(BuildConfig.UPDATE_REPO)
                if (compareVersions(r.version, currentVersion) > 0) UpdateState.Available(r) else UpdateState.UpToDate
            } catch (e: Exception) {
                if (silent) UpdateState.Idle else UpdateState.Error(e.message ?: "Sin conexión")
            }
        }
    }

    fun downloadUpdate(release: Release) {
        val url = release.apkUrl ?: run {
            _update.value = UpdateState.Error("La release no incluye un APK")
            return
        }
        _update.value = UpdateState.Downloading(release, 0f)
        viewModelScope.launch {
            _update.value = try {
                val f = Updater.download(getApplication(), url) { p ->
                    _update.value = UpdateState.Downloading(release, p)
                }
                UpdateState.Ready(release, f)
            } catch (e: Exception) {
                UpdateState.Error(e.message ?: "Error al descargar")
            }
        }
    }

    fun dismissUpdate() { _update.value = UpdateState.Idle }
}
