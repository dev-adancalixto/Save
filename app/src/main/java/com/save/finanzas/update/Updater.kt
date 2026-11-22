package com.save.finanzas.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GhAsset(
    val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
    val size: Long = 0,
)

@Serializable
data class GhRelease(
    @SerialName("tag_name") val tag: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GhAsset> = emptyList(),
)

data class Release(val version: String, val notes: String, val apkUrl: String?, val pageUrl: String, val size: Long)

/** Busca la última release en GitHub, descarga el APK y abre el instalador. */
object Updater {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(repo: String): Release = withContext(Dispatchers.IO) {
        val conn = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Save-App")
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        try {
            when (val code = conn.responseCode) {
                200 -> Unit
                404 -> throw IOException("No hay releases públicas (¿el repositorio es privado?)")
                else -> throw IOException("GitHub respondió $code")
            }
            val r = json.decodeFromString<GhRelease>(conn.inputStream.bufferedReader().use { it.readText() })
            val apk = r.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            Release(
                version = r.tag.removePrefix("v"),
                notes = r.body.orEmpty().trim(),
                apkUrl = apk?.url,
                pageUrl = r.htmlUrl,
                size = apk?.size ?: 0,
            )
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(context: Context, url: String, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "save-update.apk")
            var current = url
            var conn: HttpURLConnection
            var redirects = 0
            while (true) {
                conn = URL(current).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "Save-App")
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                val code = conn.responseCode
                if (code in 300..399 && redirects < 5) {
                    current = conn.getHeaderField("Location") ?: break
                    conn.disconnect()
                    redirects++
                    continue
                }
                if (code != 200) throw IOException("Descarga falló ($code)")
                break
            }
            val total = conn.contentLengthLong.takeIf { it > 0 }
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (total != null) onProgress(read.toFloat() / total)
                    }
                }
            }
            conn.disconnect()
            out
        }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }
}
