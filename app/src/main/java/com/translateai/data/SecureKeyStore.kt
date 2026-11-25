package com.translateai.data

import android.content.Context

class SecureKeyStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("translate_ai_prefs", Context.MODE_PRIVATE)

    fun saveApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key).apply()
    }

    fun getApiKey(): String? {
        return prefs.getString("gemini_api_key", null)
    }
}
