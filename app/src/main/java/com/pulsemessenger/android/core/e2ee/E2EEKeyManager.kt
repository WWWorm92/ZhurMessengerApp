package com.pulsemessenger.android.core.e2ee

import android.content.Context
import android.util.Base64
import android.util.Log
import com.pulsemessenger.android.core.network.E2EEDeviceKeysResponse
import com.pulsemessenger.android.core.network.E2EEOneTimePreKeyDto
import com.pulsemessenger.android.core.network.E2EERegisterDeviceKeysRequest
import com.pulsemessenger.android.core.network.E2EEUploadOneTimePreKeysRequest
import com.pulsemessenger.android.core.network.NetworkProvider
import com.pulsemessenger.android.core.privacy.SecurePrefs
import com.pulsemessenger.android.core.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec

data class E2EELocalPublicBundle(
    val registrationId: Int,
    val identityKeyPublic: String,
    val signedPreKeyId: Int,
    val signedPreKeyPublic: String,
    val signedPreKeySignature: String,
)

class E2EEKeyManager(
    context: Context,
    private val networkProvider: NetworkProvider,
    private val sessionStore: SessionStore,
) {
    private val prefs = SecurePrefs(context, PREFS_NAME)
    private val secureRandom = SecureRandom()

    suspend fun ensureRegistered(): Result<E2EEDeviceKeysResponse> = withContext(Dispatchers.IO) {
        try {
            val token = sessionStore.currentToken().trim()
            if (token.isBlank()) {
                return@withContext Result.failure(IllegalStateException("No session token"))
            }

            val auth = "Bearer $token"
            val local = getOrCreateLocalBundle()
            val statusResponse = runCatching { networkProvider.api.e2eeStatus(auth) }.getOrNull()
            val status = statusResponse?.takeIf { it.isSuccessful }?.body()

            if (status?.hasDeviceKeys == true && status.oneTimePreKeyCount >= MIN_SERVER_PREKEYS) {
                return@withContext Result.success(
                    E2EEDeviceKeysResponse(
                        ok = true,
                        deviceId = status.deviceId,
                        uploadedPreKeys = 0,
                    )
                )
            }

            val preKeys = if (status?.hasDeviceKeys == true) {
                generateAndStoreAdditionalOneTimePreKeys(DEFAULT_PREKEY_BATCH)
            } else {
                local.oneTimePreKeys
            }

            val response = if (status?.hasDeviceKeys == true) {
                networkProvider.api.uploadE2EEOneTimePreKeys(
                    authorization = auth,
                    body = E2EEUploadOneTimePreKeysRequest(oneTimePreKeys = preKeys),
                )
            } else {
                networkProvider.api.registerE2EEDeviceKeys(
                    authorization = auth,
                    body = E2EERegisterDeviceKeysRequest(
                        registrationId = local.registrationId,
                        identityKeyPublic = local.identityKeyPublic,
                        signedPreKeyId = local.signedPreKeyId,
                        signedPreKeyPublic = local.signedPreKeyPublic,
                        signedPreKeySignature = local.signedPreKeySignature,
                        oneTimePreKeys = preKeys,
                    ),
                )
            }

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("E2EE key registration failed: HTTP ${response.code()}")
                )
            }

            Result.success(response.body() ?: E2EEDeviceKeysResponse(ok = true))
        } catch (error: Throwable) {
            Log.w("E2EE", "ensureRegistered failed: ${error.message}")
            Result.failure(IllegalStateException(error.message ?: "E2EE key registration failed"))
        }
    }

    fun hasLocalIdentity(): Boolean {
        return prefs.getString(KEY_IDENTITY_PRIVATE).isNotBlank() &&
            prefs.getString(KEY_IDENTITY_PUBLIC).isNotBlank()
    }

    private fun getOrCreateLocalBundle(): LocalE2EEBundle {
        val existingIdentityPrivate = prefs.getString(KEY_IDENTITY_PRIVATE)
        val existingIdentityPublic = prefs.getString(KEY_IDENTITY_PUBLIC)
        val existingSignedPreKeyPublic = prefs.getString(KEY_SIGNED_PREKEY_PUBLIC)
        val existingSignedPreKeySignature = prefs.getString(KEY_SIGNED_PREKEY_SIGNATURE)
        val existingRegistrationId = prefs.getString(KEY_REGISTRATION_ID).toIntOrNull() ?: 0
        val existingSignedPreKeyId = prefs.getString(KEY_SIGNED_PREKEY_ID).toIntOrNull() ?: 0

        if (
            existingIdentityPrivate.isNotBlank() &&
            existingIdentityPublic.isNotBlank() &&
            existingSignedPreKeyPublic.isNotBlank() &&
            existingSignedPreKeySignature.isNotBlank() &&
            existingRegistrationId > 0 &&
            existingSignedPreKeyId > 0
        ) {
            val preKeys = readStoredOneTimePreKeys().ifEmpty {
                generateAndStoreAdditionalOneTimePreKeys(DEFAULT_PREKEY_BATCH)
            }

            return LocalE2EEBundle(
                registrationId = existingRegistrationId,
                identityKeyPublic = existingIdentityPublic,
                signedPreKeyId = existingSignedPreKeyId,
                signedPreKeyPublic = existingSignedPreKeyPublic,
                signedPreKeySignature = existingSignedPreKeySignature,
                oneTimePreKeys = preKeys,
            )
        }

        return generateInitialLocalBundle()
    }

    private fun generateInitialLocalBundle(): LocalE2EEBundle {
        val identity = generateEcKeyPair()
        val signedPreKey = generateEcKeyPair()
        val registrationId = randomPositiveInt(REGISTRATION_ID_MAX)
        val signedPreKeyId = randomPositiveInt(PREKEY_ID_MAX)
        val signedPreKeySignature = signPublicKey(
            signingKeyPair = identity,
            publicKeyBytes = signedPreKey.public.encoded,
        )

        val identityPublic = base64(identity.public.encoded)
        val identityPrivate = base64(identity.private.encoded)
        val signedPublic = base64(signedPreKey.public.encoded)
        val signedPrivate = base64(signedPreKey.private.encoded)
        val signature = base64(signedPreKeySignature)

        prefs.putString(KEY_REGISTRATION_ID, registrationId.toString())
        prefs.putString(KEY_IDENTITY_PUBLIC, identityPublic)
        prefs.putString(KEY_IDENTITY_PRIVATE, identityPrivate)
        prefs.putString(KEY_SIGNED_PREKEY_ID, signedPreKeyId.toString())
        prefs.putString(KEY_SIGNED_PREKEY_PUBLIC, signedPublic)
        prefs.putString(KEY_SIGNED_PREKEY_PRIVATE, signedPrivate)
        prefs.putString(KEY_SIGNED_PREKEY_SIGNATURE, signature)

        val oneTimePreKeys = generateAndStoreAdditionalOneTimePreKeys(DEFAULT_PREKEY_BATCH)

        return LocalE2EEBundle(
            registrationId = registrationId,
            identityKeyPublic = identityPublic,
            signedPreKeyId = signedPreKeyId,
            signedPreKeyPublic = signedPublic,
            signedPreKeySignature = signature,
            oneTimePreKeys = oneTimePreKeys,
        )
    }

    private fun generateAndStoreAdditionalOneTimePreKeys(count: Int): List<E2EEOneTimePreKeyDto> {
        val previous = readStoredPreKeyJsonArray()
        val usedIds = mutableSetOf<Int>()
        for (i in 0 until previous.length()) {
            val id = previous.optJSONObject(i)?.optInt("preKeyId", 0) ?: 0
            if (id > 0) usedIds += id
        }

        val created = mutableListOf<E2EEOneTimePreKeyDto>()

        repeat(count.coerceAtLeast(1)) {
            var id = randomPositiveInt(PREKEY_ID_MAX)
            while (usedIds.contains(id)) {
                id = randomPositiveInt(PREKEY_ID_MAX)
            }
            usedIds += id

            val keyPair = generateEcKeyPair()
            val publicKey = base64(keyPair.public.encoded)
            val privateKey = base64(keyPair.private.encoded)

            previous.put(
                JSONObject()
                    .put("preKeyId", id)
                    .put("preKeyPublic", publicKey)
                    .put("preKeyPrivate", privateKey)
            )

            created += E2EEOneTimePreKeyDto(
                preKeyId = id,
                preKeyPublic = publicKey,
            )
        }

        prefs.putString(KEY_ONE_TIME_PREKEYS, previous.toString())
        return created
    }

    private fun readStoredOneTimePreKeys(): List<E2EEOneTimePreKeyDto> {
        val array = readStoredPreKeyJsonArray()
        val result = mutableListOf<E2EEOneTimePreKeyDto>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optInt("preKeyId", 0)
            val publicKey = item.optString("preKeyPublic", "")
            if (id > 0 && publicKey.isNotBlank()) {
                result += E2EEOneTimePreKeyDto(
                    preKeyId = id,
                    preKeyPublic = publicKey,
                )
            }
        }

        return result
    }

    private fun readStoredPreKeyJsonArray(): JSONArray {
        val raw = prefs.getString(KEY_ONE_TIME_PREKEYS)
        if (raw.isBlank()) return JSONArray()
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    private fun generateEcKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        return generator.generateKeyPair()
    }

    private fun signPublicKey(signingKeyPair: KeyPair, publicKeyBytes: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(signingKeyPair.private, secureRandom)
        signature.update(publicKeyBytes)
        return signature.sign()
    }

    private fun randomPositiveInt(maxExclusive: Int): Int {
        return secureRandom.nextInt(maxExclusive - 1) + 1
    }

    private fun base64(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun localPublicBundleForSelf(): E2EELocalPublicBundle {
        val local = getOrCreateLocalBundle()
        return E2EELocalPublicBundle(
            registrationId = local.registrationId,
            identityKeyPublic = local.identityKeyPublic,
            signedPreKeyId = local.signedPreKeyId,
            signedPreKeyPublic = local.signedPreKeyPublic,
            signedPreKeySignature = local.signedPreKeySignature,
        )
    }

    suspend fun currentDeviceId(): Long? = withContext(Dispatchers.IO) {
        val token = sessionStore.currentToken().trim()
        if (token.isBlank()) return@withContext null
        runCatching {
            val response = networkProvider.api.e2eeStatus("Bearer $token")
            if (response.isSuccessful) response.body()?.deviceId else null
        }.getOrNull()
    }

    fun signedPreKeyPrivateBase64(): String {
        getOrCreateLocalBundle()
        return prefs.getString(KEY_SIGNED_PREKEY_PRIVATE)
    }

    fun oneTimePreKeyPrivateBase64(preKeyId: Int): String {
        if (preKeyId <= 0) return ""
        val array = readStoredPreKeyJsonArray()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optInt("preKeyId", 0) == preKeyId) {
                return item.optString("preKeyPrivate", "")
            }
        }
        return ""
    }

    private data class LocalE2EEBundle(
        val registrationId: Int,
        val identityKeyPublic: String,
        val signedPreKeyId: Int,
        val signedPreKeyPublic: String,
        val signedPreKeySignature: String,
        val oneTimePreKeys: List<E2EEOneTimePreKeyDto>,
    )

    companion object {
        private const val PREFS_NAME = "pulse_e2ee_keys_v1"
        private const val KEY_REGISTRATION_ID = "registration_id"
        private const val KEY_IDENTITY_PUBLIC = "identity_public"
        private const val KEY_IDENTITY_PRIVATE = "identity_private"
        private const val KEY_SIGNED_PREKEY_ID = "signed_prekey_id"
        private const val KEY_SIGNED_PREKEY_PUBLIC = "signed_prekey_public"
        private const val KEY_SIGNED_PREKEY_PRIVATE = "signed_prekey_private"
        private const val KEY_SIGNED_PREKEY_SIGNATURE = "signed_prekey_signature"
        private const val KEY_ONE_TIME_PREKEYS = "one_time_prekeys"
        private const val DEFAULT_PREKEY_BATCH = 30
        private const val MIN_SERVER_PREKEYS = 10
        private const val REGISTRATION_ID_MAX = 16_384
        private const val PREKEY_ID_MAX = 16_777_215
    }
}
