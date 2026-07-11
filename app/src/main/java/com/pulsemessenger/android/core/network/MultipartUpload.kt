package com.pulsemessenger.android.core.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

private fun guessImageMimeType(fileName: String): String {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        else -> "image/jpeg"
    }
}

fun createImagePart(context: Context, uri: Uri, partName: String = "image"): MultipartBody.Part? {
    val resolver = context.contentResolver
    val fileName = queryDisplayName(context, uri) ?: "image.jpg"
    val mimeType = resolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: guessImageMimeType(fileName)
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
    return MultipartBody.Part.createFormData(partName, fileName, requestBody)
}

fun createFilePart(context: Context, uri: Uri, partName: String = "file"): MultipartBody.Part? {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri) ?: "application/octet-stream"
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val fileName = queryDisplayName(context, uri) ?: "file"
    val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
    return MultipartBody.Part.createFormData(partName, fileName, requestBody)
}

fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            cursor.getString(index)
        } else {
            null
        }
    }
}

fun queryFileSize(context: Context, uri: Uri): Long? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
            cursor.getLong(index)
        } else {
            null
        }
    }
}
