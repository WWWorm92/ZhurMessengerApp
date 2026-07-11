package com.pulsemessenger.android.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.pulsemessenger.android.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class BackendUpdateResponse(
    val enabled: Boolean = false,
    val version: String = "",
    val notes: String = "",
    val publishedAt: String = "",
    val downloadUrl: String = "",
    val fileName: String = "",
)

data class AppUpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val fileName: String,
)

class AppUpdateManager(
    private val context: Context,
) {
    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun checkForUpdate(currentVersion: String): AppUpdateInfo? {
        val request = Request.Builder()
            .url(BuildConfig.BASE_URL.trimEnd('/') + "/api/app-update/android")
            .build()

        val response = client.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) return null
            val body = it.body?.string().orEmpty()
            val payload = gson.fromJson(body, BackendUpdateResponse::class.java) ?: return null
            if (!payload.enabled) return null

            val remoteVersion = normalizeVersion(payload.version)
            val localVersion = normalizeVersion(currentVersion)
            if (remoteVersion.isBlank() || remoteVersion == localVersion) return null

            val downloadUrl = payload.downloadUrl.takeIf { url -> url.isNotBlank() } ?: return null
            val absoluteUrl = if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) {
                downloadUrl
            } else {
                BuildConfig.BASE_URL.trimEnd('/') + "/" + downloadUrl.trimStart('/')
            }

            return AppUpdateInfo(
                version = remoteVersion,
                notes = payload.notes,
                apkUrl = absoluteUrl,
                fileName = payload.fileName.ifBlank { "zhuravlik-$remoteVersion.apk" },
            )
        }
    }

    suspend fun downloadApk(info: AppUpdateInfo, onProgress: (Float?) -> Unit): File? {
        val request = Request.Builder().url(info.apkUrl).build()
        val response = client.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) return null
            val body = it.body ?: return null

            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, info.fileName)
            val total = body.contentLength().takeIf { len -> len > 0L }

            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(total?.let { downloaded.toFloat() / it.toFloat() })
                    }
                    output.flush()
                }
            }

            return file
        }
    }

    fun createInstallIntent(file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun normalizeVersion(value: String): String {
        return value.trim().removePrefix("v").removePrefix("V")
    }
}
