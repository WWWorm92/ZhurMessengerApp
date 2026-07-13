package com.pulsemessenger.android.ui

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

fun createCameraImageUri(context: Context): Uri {
    val directory = File(context.cacheDir, "camera")
    directory.mkdirs()

    val file = File.createTempFile(
        "camera_${System.currentTimeMillis()}_",
        ".jpg",
        directory
    )

    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}