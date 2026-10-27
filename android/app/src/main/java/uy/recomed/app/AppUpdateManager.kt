package uy.recomed.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String
)

internal sealed interface AppDownloadStatus {
    data object Pending : AppDownloadStatus
    data class Complete(val file: File) : AppDownloadStatus
    data class Failed(val message: String) : AppDownloadStatus
}

internal object AppUpdateManager {
    const val PREFS_NAME = "recomed_prefs"
    const val KEY_DOWNLOAD_ID = "app_update_download_id"
    const val KEY_PENDING_APK = "app_update_pending_apk"
    const val KEY_PENDING_VERSION = "app_update_pending_version"
    const val KEY_AWAITING_INSTALL_PERMISSION = "app_update_awaiting_install_permission"
    const val KEY_LAST_CHECKED = "app_update_last_checked"

    private const val VERSION_URL = "https://spenkzero.github.io/RecoMed/version.json"
    private const val TRUSTED_HOST = "spenkzero.github.io"
    private const val UPDATE_FILE_PREFIX = "recomed-update-"

    fun fetchAvailableUpdate(context: Context): AppUpdate? {
        val connection = (URL(VERSION_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("El servidor respondió ${connection.responseCode}")
            }
            val metadata = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(metadata)
            val versionCode = json.getInt("versionCode")
            val versionName = json.getString("versionName").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("La versión publicada no es válida")
            val apkUrl = URL(URL(VERSION_URL), json.getString("apkUrl"))
            require(apkUrl.protocol == "https" && apkUrl.host == TRUSTED_HOST) {
                "La dirección de descarga no pertenece al sitio oficial"
            }
            val installedVersion = PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(context.packageName, 0)
            )
            if (versionCode <= installedVersion) return null
            return AppUpdate(versionCode, versionName, apkUrl.toString())
        } finally {
            connection.disconnect()
        }
    }

    fun enqueueDownload(context: Context, update: AppUpdate): Long {
        val apkUri = Uri.parse(update.apkUrl)
        require(apkUri.scheme == "https" && apkUri.host == TRUSTED_HOST) {
            "La dirección de descarga no pertenece al sitio oficial"
        }
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("El almacenamiento de descargas no está disponible")
        val apkFile = File(directory, "$UPDATE_FILE_PREFIX${update.versionCode}.apk")
        if (apkFile.exists() && !apkFile.delete()) {
            throw IllegalStateException("No se pudo reemplazar una descarga anterior")
        }
        val request = DownloadManager.Request(apkUri)
            .setTitle("Actualización de RecoMed ${update.versionName}")
            .setDescription("Descargando la actualización")
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                apkFile.name
            )
        val downloadManager = context.getSystemService(DownloadManager::class.java)
            ?: throw IllegalStateException("El gestor de descargas de Android no está disponible")
        return downloadManager.enqueue(request)
    }

    fun getDownloadStatus(context: Context, downloadId: Long): AppDownloadStatus {
        val manager = context.getSystemService(DownloadManager::class.java)
            ?: return AppDownloadStatus.Failed("El gestor de descargas de Android no está disponible")
        val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
            ?: return AppDownloadStatus.Failed("No se encontró la descarga")
        cursor.use {
            if (!it.moveToFirst()) return AppDownloadStatus.Failed("No se encontró la descarga")
            return when (it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED -> AppDownloadStatus.Pending
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val localUri = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    val file = localUri?.let(Uri::parse)?.path?.let(::File)
                    if (file != null && file.isFile && file.length() > 0) {
                        AppDownloadStatus.Complete(file)
                    } else {
                        AppDownloadStatus.Failed("No se pudo encontrar el APK descargado")
                    }
                }
                else -> {
                    val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    AppDownloadStatus.Failed("Falló la descarga (código $reason)")
                }
            }
        }
    }

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
}
