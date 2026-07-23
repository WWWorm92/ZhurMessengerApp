package com.pulsemessenger.android.core.e2ee

import android.content.Context
import android.util.Base64
import android.util.Log
import com.pulsemessenger.android.core.network.DmMessageDto
import com.pulsemessenger.android.core.network.E2EEPreKeyBundleDto
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class E2EEMessageCrypto(
    context: Context,
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val keyManager = E2EEKeyManager(context, networkProvider, sessionStore)
    private val secureRandom = SecureRandom()

    data class EncryptedDmMessage(
        val encryptedPayload: String,
        val encryptedHeader: String,
        val recipientDeviceId: Long?,
    )

    suspend fun encryptDmText(peerUserId: Long, plaintext: String): Result<EncryptedDmMessage> = withContext(Dispatchers.IO) {
        try {
            val normalized = plaintext.trim()
            if (normalized.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Empty message"))
            }

            val registration = keyManager.ensureRegistered().getOrThrow()

            val token = sessionStore.currentToken().trim()
            if (token.isBlank()) {
                return@withContext Result.failure(IllegalStateException("No session token"))
            }

            val response = networkProvider.api.e2eePreKeyBundle("Bearer $token", peerUserId)
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("E2EE keys unavailable for recipient"))
            }

            val recipient = response.body()?.bundle
                ?: return@withContext Result.failure(IllegalStateException("Recipient has no E2EE keys"))

            val self = keyManager.localPublicBundleForSelf()
            val selfDeviceId = registration.deviceId ?: keyManager.currentDeviceId()
            val messageKey = randomBytes(32)
            val messageIv = randomBytes(GCM_IV_BYTES)
            val ciphertext = aesGcmEncrypt(
                key = messageKey,
                iv = messageIv,
                plaintext = normalized.toByteArray(Charsets.UTF_8),
            )

            val envelopes = JSONArray()
            envelopes.put(wrapMessageKeyForRecipient("recipient", recipient, messageKey))
            if (selfDeviceId != null) {
                envelopes.put(wrapMessageKeyForSelf(selfDeviceId, self, messageKey))
            }

            val header = JSONObject()
                .put("v", 1)
                .put("alg", ALG)
                .put("messageIv", b64(messageIv))
                .put("envelopes", envelopes)

            Result.success(
                EncryptedDmMessage(
                    encryptedPayload = b64(ciphertext),
                    encryptedHeader = header.toString(),
                    recipientDeviceId = recipient.deviceId,
                )
            )
        } catch (error: Throwable) {
            Log.w("E2EE", "encrypt failed: ${error.message}")
            Result.failure(IllegalStateException(error.message ?: "Failed to encrypt message"))
        }
    }

    suspend fun encryptPushPreview(peerUserId: Long, plaintext: String): Result<EncryptedDmMessage> {
        return encryptDmText(peerUserId, plaintext.take(240))
    }

    suspend fun hasPeerE2EEKeys(peerUserId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = sessionStore.currentToken().trim()
            if (token.isBlank()) {
                return@withContext false
            }

            val response = networkProvider.api.e2eePeerStatus("Bearer $token", peerUserId)
            response.isSuccessful && response.body()?.hasDeviceKeys == true
        } catch (error: Throwable) {
            Log.w("E2EE", "peer status check failed: ${error.message}")
            false
        }
    }

    fun tryDecryptDmMessage(message: DmMessageDto): DmMessageDto {
        if (message.encryptionVersion <= 0 || message.encryptedPayload.isBlank() || message.encryptedHeader.isBlank()) {
            return message
        }

        return runCatching {
            val plaintext = decryptPayload(
                encryptedPayload = message.encryptedPayload,
                encryptedHeader = message.encryptedHeader,
            )
            val attachmentMessage = tryParseEncryptedAttachmentMessage(message, plaintext)
            attachmentMessage ?: message.copy(content = plaintext, type = "text")
        }.getOrElse { error ->
            Log.w("E2EE", "decrypt failed message=${message.id}: ${error.message}")
            message.copy(
                content = "Сообщение недоступно на этом устройстве",
                type = "text",
            )
        }
    }

    fun tryDecryptPushPreview(encryptedPayload: String, encryptedHeader: String): String? {
        if (encryptedPayload.isBlank() || encryptedHeader.isBlank()) {
            return null
        }

        return runCatching {
            decryptPayload(
                encryptedPayload = encryptedPayload,
                encryptedHeader = encryptedHeader,
            ).trim().takeIf { it.isNotBlank() }
        }.getOrElse { error ->
            Log.w("E2EE", "push preview decrypt failed: ${error.message}")
            null
        }
    }


    private fun tryParseEncryptedAttachmentMessage(message: DmMessageDto, plaintext: String): DmMessageDto? {
        return runCatching {
            val root = JSONObject(plaintext)
            if (root.optString("kind") != "attachment") {
                return@runCatching null
            }

            val attachment = root.optJSONObject("attachment") ?: return@runCatching null
            val attachmentKind = attachment.optString("kind", "file")
            val originalFileName = attachment.optString("fileName", "")
            val fileSize = attachment.optLong("fileSize", 0L).takeIf { it > 0L }
            val encryptedUrl = attachment.optString("url", "")
            val caption = root.optString("caption", "")
            val preview = attachment.optJSONObject("preview")
            val fallbackFileName = if (attachmentKind == "image") "Изображение" else "Файл"

            message.copy(
                content = caption,
                type = "file",
                imageUrl = "",
                fileUrl = encryptedUrl,
                fileName = originalFileName.ifBlank { fallbackFileName },
                fileSize = fileSize,
                encryptedAttachmentUrl = encryptedUrl,
                encryptedAttachmentFileName = originalFileName,
                encryptedAttachmentFileSize = fileSize,
                encryptedAttachmentMimeType = attachment.optString("mimeType", "application/octet-stream"),
                encryptedAttachmentKey = attachment.optString("key", ""),
                encryptedAttachmentIv = attachment.optString("iv", ""),
                encryptedAttachmentKind = attachmentKind,
                encryptedAttachmentPreviewMimeType = preview?.optString("mimeType", "").orEmpty(),
                encryptedAttachmentPreviewData = preview?.optString("data", "").orEmpty(),
            )
        }.getOrNull()
    }

    private fun decryptPayload(encryptedPayload: String, encryptedHeader: String): String {
        keyManager.localPublicBundleForSelf()

        val header = JSONObject(encryptedHeader)
        if (header.optInt("v", 0) != 1) {
            throw IllegalStateException("Unsupported E2EE version")
        }

        val messageIv = b64decode(header.getString("messageIv"))
        val envelopes = header.getJSONArray("envelopes")
        var lastError: Throwable? = null

        for (i in 0 until envelopes.length()) {
            val envelope = envelopes.optJSONObject(i) ?: continue
            try {
                val wrappedKey = unwrapMessageKey(envelope)
                val plaintext = aesGcmDecrypt(
                    key = wrappedKey,
                    iv = messageIv,
                    ciphertext = b64decode(encryptedPayload),
                )
                return String(plaintext, Charsets.UTF_8)
            } catch (error: Throwable) {
                lastError = error
            }
        }

        throw IllegalStateException(lastError?.message ?: "No matching E2EE envelope")
    }

    private fun wrapMessageKeyForRecipient(
        kind: String,
        bundle: E2EEPreKeyBundleDto,
        messageKey: ByteArray,
    ): JSONObject {
        val oneTimePreKey = bundle.oneTimePreKey
        val targetPublic = oneTimePreKey?.preKeyPublic?.takeIf { it.isNotBlank() }
            ?: bundle.signedPreKeyPublic
        val oneTimePreKeyId = oneTimePreKey?.preKeyId ?: 0

        return wrapMessageKey(
            kind = kind,
            targetDeviceId = bundle.deviceId,
            signedPreKeyId = bundle.signedPreKeyId,
            oneTimePreKeyId = oneTimePreKeyId,
            targetPublicKeyBase64 = targetPublic,
            messageKey = messageKey,
        )
    }

    private fun wrapMessageKeyForSelf(
        selfDeviceId: Long,
        self: E2EELocalPublicBundle,
        messageKey: ByteArray,
    ): JSONObject {
        return wrapMessageKey(
            kind = "self",
            targetDeviceId = selfDeviceId,
            signedPreKeyId = self.signedPreKeyId,
            oneTimePreKeyId = 0,
            targetPublicKeyBase64 = self.signedPreKeyPublic,
            messageKey = messageKey,
        )
    }

    private fun wrapMessageKey(
        kind: String,
        targetDeviceId: Long,
        signedPreKeyId: Int,
        oneTimePreKeyId: Int,
        targetPublicKeyBase64: String,
        messageKey: ByteArray,
    ): JSONObject {
        val ephemeral = generateEphemeralKeyPair()
        val sharedSecret = ecdh(ephemeral.private.encoded, targetPublicKeyBase64)
        val wrapKey = hkdfSha256(
            inputKeyMaterial = sharedSecret,
            salt = HKDF_SALT,
            info = "$ALG:key-wrap:$targetDeviceId:$signedPreKeyId:$oneTimePreKeyId".toByteArray(Charsets.UTF_8),
            length = 32,
        )
        val keyIv = randomBytes(GCM_IV_BYTES)
        val wrapped = aesGcmEncrypt(wrapKey, keyIv, messageKey)

        return JSONObject()
            .put("kind", kind)
            .put("targetDeviceId", targetDeviceId)
            .put("signedPreKeyId", signedPreKeyId)
            .put("oneTimePreKeyId", oneTimePreKeyId)
            .put("ephemeralPublic", b64(ephemeral.public.encoded))
            .put("keyIv", b64(keyIv))
            .put("keyCipher", b64(wrapped))
    }

    private fun unwrapMessageKey(envelope: JSONObject): ByteArray {
        val signedPreKeyId = envelope.optInt("signedPreKeyId", 0)
        val oneTimePreKeyId = envelope.optInt("oneTimePreKeyId", 0)
        val privateKeyBase64 = if (oneTimePreKeyId > 0) {
            keyManager.oneTimePreKeyPrivateBase64(oneTimePreKeyId)
        } else {
            keyManager.signedPreKeyPrivateBase64()
        }

        if (privateKeyBase64.isBlank()) {
            throw IllegalStateException("Matching private key not found")
        }

        val targetDeviceId = envelope.optLong("targetDeviceId", 0L)
        val sharedSecret = ecdh(
            privateKeyBase64 = privateKeyBase64,
            publicKeyBase64 = envelope.getString("ephemeralPublic"),
        )
        val wrapKey = hkdfSha256(
            inputKeyMaterial = sharedSecret,
            salt = HKDF_SALT,
            info = "$ALG:key-wrap:$targetDeviceId:$signedPreKeyId:$oneTimePreKeyId".toByteArray(Charsets.UTF_8),
            length = 32,
        )

        return aesGcmDecrypt(
            key = wrapKey,
            iv = b64decode(envelope.getString("keyIv")),
            ciphertext = b64decode(envelope.getString("keyCipher")),
        )
    }

    private fun generateEphemeralKeyPair(): java.security.KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        return generator.generateKeyPair()
    }

    private fun ecdh(privateKeyBase64: String, publicKeyBase64: String): ByteArray {
        val keyFactory = KeyFactory.getInstance("EC")
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(b64decode(privateKeyBase64)))
        val publicKey = decodePublicKey(publicKeyBase64)
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(publicKey, true)
        return agreement.generateSecret()
    }

    private fun ecdh(privateKeyEncoded: ByteArray, publicKeyBase64: String): ByteArray {
        val keyFactory = KeyFactory.getInstance("EC")
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyEncoded))
        val publicKey = decodePublicKey(publicKeyBase64)
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(publicKey, true)
        return agreement.generateSecret()
    }

    private fun decodePublicKey(publicKeyBase64: String): PublicKey {
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(X509EncodedKeySpec(b64decode(publicKeyBase64)))
    }

    private fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(plaintext)
    }

    private fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(inputKeyMaterial)

        val result = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1

        while (offset < length) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(previous)
            mac.update(info)
            mac.update(byteArrayOf(counter.toByte()))
            previous = mac.doFinal()

            val copyLength = minOf(previous.size, length - offset)
            System.arraycopy(previous, 0, result, offset, copyLength)
            offset += copyLength
            counter++
        }

        return result
    }

    private fun randomBytes(size: Int): ByteArray {
        return ByteArray(size).also { secureRandom.nextBytes(it) }
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun b64decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    companion object {
        private const val ALG = "PULSE-P256-AESGCM-HKDF-SHA256"
        private const val GCM_IV_BYTES = 12
        private val HKDF_SALT = "PulseMessenger-E2EE-v1".toByteArray(Charsets.UTF_8)
    }
}
