package com.translateai.data

class GeminiRepository(private val keyStore: SecureKeyStore) {
    fun hasApiKey(): Boolean {
        return !keyStore.getApiKey().isNullOrBlank()
    }
}
