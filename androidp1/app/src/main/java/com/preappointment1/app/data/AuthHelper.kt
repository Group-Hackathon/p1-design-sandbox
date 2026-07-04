package com.preappointment1.app.data

import android.content.Context
import android.util.Log
import com.preappointment1.app.data.api.ApiClient
import com.preappointment1.app.data.model.RefreshTokenRequest

object AuthHelper {
    private const val TAG = "LPM_AUTH"
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun ensureAuthenticated(): Boolean {
        if (SessionManager.getAccessToken() != null) return true
        return refreshToken()
    }

    /** Refresh via refresh token, else device Ed25519 sign-in (RankMyAura-style). */
    suspend fun refreshToken(): Boolean {
        SessionManager.getRefreshToken()?.let { refresh ->
            try {
                val response = ApiClient.authApiService.refreshTokens(
                    RefreshTokenRequest(refreshToken = refresh)
                )
                SessionManager.saveTokens(response.accessToken, response.refreshToken)
                Log.d(TAG, "Token refreshed (refresh token)")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Refresh token invalid, falling back to device auth", e)
            }
        }

        SessionManager.clearToken()
        if (!::appContext.isInitialized) {
            Log.e(TAG, "AuthHelper not initialized")
            return false
        }
        return DeviceAuth.signIn(appContext)
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
