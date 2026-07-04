package com.preappointment1.app.data

import android.content.Context
import android.content.SharedPreferences

object SessionManager {
    private const val PREFS_NAME = "lpm_prefs"
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_PROFILE_ID = "profile_id"
    private const val KEY_USER_NAME = "user_name"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveToken(token: String) {
        saveTokens(accessToken = token, refreshToken = null)
    }

    fun saveTokens(accessToken: String, refreshToken: String?) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_TOKEN, accessToken)
            .apply()
        if (refreshToken != null) {
            prefs.edit().putString(KEY_REFRESH_TOKEN, refreshToken).apply()
        }
    }

    fun getRefreshToken(): String? {
        if (!this::prefs.isInitialized) return null
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    fun getToken(): String? = getAccessToken()

    fun getAccessToken(): String? {
        if (!this::prefs.isInitialized) return null
        return prefs.getString(KEY_ACCESS_TOKEN, null)
            ?: prefs.getString(KEY_TOKEN, null)
    }

    fun clearToken() {
        if (!this::prefs.isInitialized) return
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    fun saveProfileId(profileId: String) {
        prefs.edit().putString(KEY_PROFILE_ID, profileId).apply()
    }

    fun getProfileId(): String? {
        if (!this::prefs.isInitialized) return null
        return prefs.getString(KEY_PROFILE_ID, null)
    }

    fun saveUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserName(): String? {
        if (!this::prefs.isInitialized) return null
        return prefs.getString(KEY_USER_NAME, null)
    }

    fun getOrCreateDeviceId(): String {
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = "device_" + java.util.UUID.randomUUID().toString().take(8)
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun clearSession() {
        if (!this::prefs.isInitialized) return
        prefs.edit().clear().apply()
    }
}
