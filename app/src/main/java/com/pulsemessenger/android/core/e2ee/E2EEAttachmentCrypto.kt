package com.pulsemessenger.android.core.e2ee

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class E2EEAttachmentCrypto(
    private val context: Context,
) {
    data class PreparedEncryptedAttachment(
        val part: MultipartBody.Part,
        val tempFile: File,
        val originalFileName: String,
        val originalSize: Long?,
        val originalMimeType: String,
        val fileKeyBase64: String,
        val fileIvBase64: String,
    )

    data class DecryptedAttachment(
        val uri: Uri,
        val file: File,
        val fileName: String,
        val mimeType: String,
        val kind: String,
    )

    fun prepare(uri: Uri): PreparedEncryptedAttachment {
        val originalFileName = queryDisplayName(uri).ifBlank { "attachment" }
        val originalSize = querySize(uri)
        val mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }
        val fileKey = randomBytes(32)
        val fileIv = randomBytes(12)

        val directory = File(context.cacheDir, "e2ee_uploads")
        directory.mkdirs()
        val encryptedFile = File(directory, "encrypted-${UUID.randomUUID()}.bin")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(fileKey, "AES"),
            GCMParameterSpec(128, fileIv),
        )

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open attachment" }
            FileOutputStream(encryptedFile).use { fileOutput ->
                CipherOutputStream(fileOutput, cipher).use { cipherOutput ->
                    input.copyTo(cipherOutput)
                }
            }
        }

        val body = encryptedFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData(
            name = "file",
            filename = encryptedFile.name,
            body = body,
        )

        return PreparedEncryptedAttachment(
            part = part,
            tempFile = encryptedFile,
            originalFileName = originalFileName,
            originalSize = originalSize,
            originalMimeType = mimeType,
            fileKeyBase64 = b64(fileKey),
            fileIvBase64 = b64(fileIv),
        )
    }


    fun decryptToCache(
        encryptedFile: File,
        originalFileName: String,
        originalMimeType: String,
        kind: String,
        fileKeyBase64: String,
        fileIvBase64: String,
        cacheKey: String? = null,
    ): DecryptedAttachment {
        val safeName = sanitizeFileName(originalFileName.ifBlank { "attachment" })
        val outputFile = decryptedOutputFile(safeName, cacheKey)

        cachedDecryptedAttachment(
            originalFileName = safeName,
            originalMimeType = originalMimeType,
            kind = kind,
            cacheKey = cacheKey,
        )?.let { cached -> return cached }

        val key = b64decode(fileKeyBase64)
        val iv = b64decode(fileIvBase64)
        val tempOutputFile = File(outputFile.parentFile, "${outputFile.name}.part-${UUID.randomUUID()}")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, iv),
        )

        try {
            FileInputStream(encryptedFile).use { input ->
                CipherInputStream(input, cipher).use { cipherInput ->
                    FileOutputStream(tempOutputFile).use { output ->
                        cipherInput.copyTo(output)
                    }
                }
            }

            if (outputFile.exists()) {
                outputFile.delete()
            }
            if (!tempOutputFile.renameTo(outputFile)) {
                tempOutputFile.copyTo(outputFile, overwrite = true)
            }
        } finally {
            tempOutputFile.delete()
        }

        return makeDecryptedAttachment(
            file = outputFile,
            originalFileName = safeName,
            originalMimeType = originalMimeType,
            kind = kind,
        )
    }

    fun cachedDecryptedAttachment(
        originalFileName: String,
        originalMimeType: String,
        kind: String,
        cacheKey: String? = null,
    ): DecryptedAttachment? {
        val safeName = sanitizeFileName(originalFileName.ifBlank { "attachment" })
        val file = decryptedOutputFile(safeName, cacheKey)

        if (!file.isFile || file.length() <= 0L) {
            return null
        }

        return makeDecryptedAttachment(
            file = file,
            originalFileName = safeName,
            originalMimeType = originalMimeType,
            kind = kind,
        )
    }

    private fun decryptedOutputFile(safeName: String, cacheKey: String?): File {
        val directory = File(context.cacheDir, "e2ee_decrypted")
        directory.mkdirs()

        val safeKey = sanitizeCacheKey(cacheKey.orEmpty())
        val fileName = if (safeKey.isBlank()) {
            "${System.currentTimeMillis()}-${UUID.randomUUID()}-$safeName"
        } else {
            "$safeKey-$safeName"
        }

        return File(directory, fileName)
    }

    private fun makeDecryptedAttachment(
        file: File,
        originalFileName: String,
        originalMimeType: String,
        kind: String,
    ): DecryptedAttachment {
        val safeName = sanitizeFileName(originalFileName.ifBlank { file.name })
        val mime = originalMimeType.ifBlank { guessMimeType(safeName) }
            .ifBlank { "application/octet-stream" }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        return DecryptedAttachment(
            uri = uri,
            file = file,
            fileName = safeName,
            mimeType = mime,
            kind = kind,
        )
    }

    private fun queryDisplayName(uri: Uri): String {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index).orEmpty() else ""
                } else {
                    ""
                }
            }
            .orEmpty()
    }

    private fun querySize(uri: Uri): Long? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
                } else {
                    null
                }
            }
    }


    private fun b64decode(value: String): ByteArray {
        return Base64.decode(value, Base64.NO_WRAP)
    }

    private fun sanitizeFileName(value: String): String {
        return value
            .replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ._ -]+"), "_")
            .replace(Regex("_+"), "_")
            .trim()
            .take(140)
            .ifBlank { "attachment" }
    }

    private fun sanitizeCacheKey(value: String): String {
        return value
            .replace(Regex("[^a-zA-Z0-9._-]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_', '-', '.')
            .take(120)
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isBlank()) return ""
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext).orEmpty()
    }

    private fun randomBytes(size: Int): ByteArray {
        return ByteArray(size).also { SecureRandom().nextBytes(it) }
    }

    private fun b64(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
