package com.preappointment1.app.data

import android.util.Log
import com.preappointment1.app.data.api.ApiClient
import com.preappointment1.app.data.model.AuthRequest

object AuthHelper {
    private const val TAG = "LPM_AUTH"
    private const val DEVICE_PASSWORD = "secret_device_password"

    suspend fun ensureAuthenticated(): Boolean {
        if (SessionManager.getToken() != null) return true
        return refreshToken()
    }

    /** Clears stale token and obtains a fresh JWT (login, then register fallback). */
    suspend fun refreshToken(): Boolean {
        SessionManager.clearToken()
        val email = "${SessionManager.getOrCreateDeviceId()}@local.device"
        val request = AuthRequest(email = email, password = DEVICE_PASSWORD)

        return try {
            val response = ApiClient.authApiService.login(request)
            SessionManager.saveToken(response.token)
            Log.d(TAG, "Token refreshed (login)")
            true
        } catch (e: Exception) {
            try {
                val response = ApiClient.authApiService.register(request)
                SessionManager.saveToken(response.token)
                Log.d(TAG, "Token refreshed (register)")
                true
            } catch (registerError: Exception) {
                Log.e(TAG, "Token refresh failed", registerError)
                false
            }
        }
    }

    suspend fun ensureProfile(): String? {
        SessionManager.getProfileId()?.let { return it }

        return try {
            val profile = ApiClient.apiService.createProfile(
                com.preappointment1.app.data.model.ProfileRequest(
                    first_name = "Patient",
                    last_name = "Local",
                    relation = "Self"
                )
            )
            SessionManager.saveProfileId(profile.id)
            profile.id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create profile", e)
            null
        }
    }
}
