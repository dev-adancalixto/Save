package com.save.finanzas.data

import com.save.finanzas.domain.AppData
import kotlinx.serialization.json.Json
import java.io.File

/** Guarda todo en un archivo JSON local (privado de la app). */
class Store(dir: File) {
    private val file = File(dir, "data.json")
    private val tmp = File(dir, "data.json.tmp")

    fun load(): AppData = runCatching {
        if (file.exists()) json.decodeFromString<AppData>(file.readText()) else AppData()
    }.getOrElse { AppData() }

    fun save(data: AppData) = synchronized(lock) {
        tmp.writeText(json.encodeToString(AppData.serializer(), data))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        Unit
    }

    companion object {
        /** Un solo candado para la app, el worker y el receptor de notificaciones. */
        val lock = Any()

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

        fun encode(data: AppData): String = json.encodeToString(AppData.serializer(), data)
        fun decode(text: String): AppData = json.decodeFromString(AppData.serializer(), text)
    }
}
