package com.pulsemessenger.android.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.pulsemessenger.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
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
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .callTimeout(10, TimeUnit.MINUTES)
        .build()
    private val gson = Gson()

    suspend fun checkForUpdate(currentVersion: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BuildConfig.BASE_URL.trimEnd('/') + "/api/app-update/android")
            .build()

        val response = client.newCall(request).execute()

        response.use {
            val body = it.body?.string().orEmpty()

            Log.d(
                "APP_UPDATE",
                "status=${it.code} currentVersion=$currentVersion body=$body"
            )

            if (!it.isSuccessful) return@withContext null

            val payload = gson.fromJson(body, BackendUpdateResponse::class.java)
                ?: return@withContext null

            Log.d(
                "APP_UPDATE",
                "enabled=${payload.enabled} remote=${payload.version} local=$currentVersion downloadUrl=${payload.downloadUrl}"
            )

            if (!payload.enabled) return@withContext null

            val remoteVersion = normalizeVersion(payload.version)
            val localVersion = normalizeVersion(currentVersion)

            if (remoteVersion.isBlank() || remoteVersion == localVersion) {
                return@withContext null
            }

            val downloadUrl = payload.downloadUrl
                .takeIf { url -> url.isNotBlank() }
                ?: return@withContext null

            val absoluteUrl = if (
                downloadUrl.startsWith("http://") ||
                downloadUrl.startsWith("https://")
            ) {
                downloadUrl
            } else {
                BuildConfig.BASE_URL.trimEnd('/') + "/" + downloadUrl.trimStart('/')
            }

            AppUpdateInfo(
                version = remoteVersion,
                notes = payload.notes,
                apkUrl = absoluteUrl,
                fileName = payload.fileName.ifBlank { "zhuravlik-$remoteVersion.apk" },
            )
        }
    }

    suspend fun downloadApk(
        info: AppUpdateInfo,
        onProgress: (Float?) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(info.apkUrl)
            .build()

        val response = client.newCall(request).execute()

        response.use {
            Log.d("APP_UPDATE", "download status=${it.code} url=${info.apkUrl}")

            if (!it.isSuccessful) return@withContext null

            val body = it.body ?: return@withContext null

            val dir = File(context.cacheDir, "updates").apply {
                mkdirs()
            }

            val safeFileName = info.fileName
                .ifBlank { "zhuravlik-${info.version}.apk" }
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")

            val file = File(dir, safeFileName)
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

                        val progress = total?.let { totalBytes ->
                            downloaded.toFloat() / totalBytes.toFloat()
                        }

                        withContext(Dispatchers.Main) {
                            onProgress(progress)
                        }
                    }

                    output.flush()
                }
            }

            withContext(Dispatchers.Main) {
                onProgress(1f)
            }

            Log.d("APP_UPDATE", "apk saved=${file.absolutePath} size=${file.length()}")

            file
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