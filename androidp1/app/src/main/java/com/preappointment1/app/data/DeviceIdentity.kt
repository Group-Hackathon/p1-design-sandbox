package com.preappointment1.app.data

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

object DeviceIdentity {
    private const val PREFS = "lpm_device_keys"
    private const val KEY_BINDING = "hardware_binding_id"

    fun hardwareBindingId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_BINDING, null)?.let { return it }

        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$androidId:${context.packageName}".toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }
        val binding = "and_$hex"
        prefs.edit().putString(KEY_BINDING, binding).apply()
        return binding
    }

    fun platform(): String = "android"
}
