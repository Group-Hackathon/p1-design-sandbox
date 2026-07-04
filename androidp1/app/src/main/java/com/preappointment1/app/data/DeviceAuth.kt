package com.preappointment1.app.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.preappointment1.app.data.api.ApiClient
import com.preappointment1.app.data.model.DeviceChallengeRequest
import com.preappointment1.app.data.model.DeviceRegisterRequest
import com.preappointment1.app.data.model.DeviceVerifyRequest
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.KeyGenerationParameters
import retrofit2.HttpException
import java.security.SecureRandom

object DeviceAuth {
    private const val TAG = "LPM_DEVICE_AUTH"
    private const val PREFS = "lpm_device_keys"

    private fun privateKeyKey(deviceId: String): String {
        val safe = deviceId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return "private_key_$safe"
    }

    private fun bytesToB64Url(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private data class KeyPairMaterial(val publicKey: String, val privateKey: ByteArray)

    private fun getOrCreateKeyPair(context: Context, deviceId: String): KeyPairMaterial {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(privateKeyKey(deviceId), null)
        if (stored != null) {
            val priv = Base64.decode(stored, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val pub = Ed25519PrivateKeyParameters(priv, 0).generatePublicKey().encoded
            return KeyPairMaterial(bytesToB64Url(pub), priv)
        }
        val generator = Ed25519KeyPairGenerator()
        generator.init(KeyGenerationParameters(SecureRandom(), 255))
        val kp = generator.generateKeyPair()
        val priv = (kp.private as Ed25519PrivateKeyParameters).encoded
        val pub = (kp.public as Ed25519PublicKeyParameters).encoded
        prefs.edit().putString(privateKeyKey(deviceId), bytesToB64Url(priv)).apply()
        return KeyPairMaterial(bytesToB64Url(pub), priv)
    }

    private fun sign(privateKey: ByteArray, message: String): String {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKey, 0))
        val bytes = message.toByteArray(Charsets.UTF_8)
        signer.update(bytes, 0, bytes.size)
        return bytesToB64Url(signer.generateSignature())
    }

    private fun challengePayload(deviceId: String, nonce: String) = "challenge:$deviceId:$nonce"

    private fun registerPayload(deviceId: String, publicKey: String, timestamp: Long, nonce: String) =
        "register:$deviceId:$publicKey:$timestamp:$nonce"

    private fun recoverPayload(deviceId: String, publicKey: String, timestamp: Long, nonce: String) =
        "recover:$deviceId:$publicKey:$timestamp:$nonce"

    suspend fun signIn(context: Context): Boolean {
        val binding = DeviceIdentity.hardwareBindingId(context)
        val deviceId = binding
        val platform = DeviceIdentity.platform()
        val keys = getOrCreateKeyPair(context, deviceId)

        return try {
            verifyExisting(deviceId, binding, keys)
        } catch (e: HttpException) {
            when (e.code()) {
                404 -> tryRegister(deviceId, binding, platform, keys)
                else -> {
                    Log.e(TAG, "Verify failed (${e.code()})", e)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sign in failed", e)
            false
        }
    }

    private suspend fun verifyExisting(deviceId: String, binding: String, keys: KeyPairMaterial): Boolean {
        val challenge = try {
            ApiClient.authApiService.deviceChallenge(
                DeviceChallengeRequest(deviceId = deviceId, intent = "verify")
            )
        } catch (e: HttpException) {
            if (e.code() == 404) throw e
            throw e
        }

        val signature = sign(keys.privateKey, challengePayload(deviceId, challenge.nonce))
        return try {
            val response = ApiClient.authApiService.deviceVerify(
                DeviceVerifyRequest(deviceId = deviceId, nonce = challenge.nonce, signature = signature)
            )
            SessionManager.saveTokens(response.accessToken, response.refreshToken)
            Log.d(TAG, "Device verified")
            true
        } catch (e: HttpException) {
            if (e.code() == 401) {
                tryRecover(deviceId, binding, keys)
            } else {
                throw e
            }
        }
    }

    private suspend fun tryRegister(
        deviceId: String,
        binding: String,
        platform: String,
        keys: KeyPairMaterial
    ): Boolean {
        val challenge = ApiClient.authApiService.deviceChallenge(
            DeviceChallengeRequest(deviceId = deviceId, intent = "register")
        )
        val timestamp = System.currentTimeMillis()
        val signature = sign(
            keys.privateKey,
            registerPayload(deviceId, keys.publicKey, timestamp, challenge.nonce)
        )
        val response = ApiClient.authApiService.deviceRegister(
            DeviceRegisterRequest(
                deviceId = deviceId,
                publicKey = keys.publicKey,
                timestamp = timestamp,
                nonce = challenge.nonce,
                signature = signature,
                hardwareBindingId = binding,
                hardwarePlatform = platform
            )
        )
        SessionManager.saveTokens(response.accessToken, response.refreshToken)
        Log.d(TAG, "Device registered")
        return true
    }

    private suspend fun tryRecover(
        deviceId: String,
        binding: String,
        keys: KeyPairMaterial
    ): Boolean {
        val challenge = ApiClient.authApiService.deviceChallenge(
            DeviceChallengeRequest(deviceId = deviceId, intent = "recover")
        )
        val timestamp = System.currentTimeMillis()
        val signature = sign(
            keys.privateKey,
            recoverPayload(deviceId, keys.publicKey, timestamp, challenge.nonce)
        )
        val response = ApiClient.authApiService.deviceRecover(
            DeviceRegisterRequest(
                deviceId = deviceId,
                publicKey = keys.publicKey,
                timestamp = timestamp,
                nonce = challenge.nonce,
                signature = signature,
                hardwareBindingId = binding,
                hardwarePlatform = DeviceIdentity.platform()
            )
        )
        SessionManager.saveTokens(response.accessToken, response.refreshToken)
        Log.d(TAG, "Device recovered")
        return true
    }
}
