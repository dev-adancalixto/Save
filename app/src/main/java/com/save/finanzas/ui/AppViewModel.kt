package com.save.finanzas.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.save.finanzas.BuildConfig
import com.save.finanzas.data.Store
import com.save.finanzas.domain.*
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

    private val _data = MutableStateFlow(store.load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME

    private fun edit(f: (AppData) -> AppData) {
        _data.value = f(_data.value)
        viewModelScope.launch(Dispatchers.IO) { saveLock.withLock { store.save(_data.value) } }
    }

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
        val parsed = Store.decode(text)
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
