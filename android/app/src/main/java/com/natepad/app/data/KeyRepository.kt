package com.natepad.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KeyRepository private constructor(context: Context) {

    private val prefs: SharedPreferences

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _keys = MutableStateFlow<List<KeyRecord>>(emptyList())
    val keys: StateFlow<List<KeyRecord>> = _keys.asStateFlow()

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        _keys.value = loadKeys()
    }

    fun getKeys(): List<KeyRecord> = _keys.value

    fun addKey(record: KeyRecord) {
        val current = _keys.value.toMutableList()
        // Replace existing entry with same fingerprint, or add new
        val existingIdx = current.indexOfFirst { it.id == record.id }
        if (existingIdx >= 0) {
            // Merge: if we're adding a private key for an existing public-only record, merge
            val existing = current[existingIdx]
            val merged = if (record.hasPrivate && !existing.hasPrivate) {
                existing.copy(
                    hasPrivate = true,
                    armoredPrivate = record.armoredPrivate
                )
            } else {
                record
            }
            current[existingIdx] = merged
        } else {
            current.add(record)
        }
        _keys.value = current
        saveKeys(current)
    }

    fun removeKey(id: String) {
        val updated = _keys.value.filter { it.id != id }
        _keys.value = updated
        saveKeys(updated)
    }

    fun clear() {
        _keys.value = emptyList()
        saveKeys(emptyList())
    }

    private fun saveKeys(keys: List<KeyRecord>) {
        val encoded = json.encodeToString(keys)
        prefs.edit().putString(KEY_LIST, encoded).apply()
    }

    private fun loadKeys(): List<KeyRecord> {
        val encoded = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return try {
            json.decodeFromString(encoded)
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS_FILE = "natepad_keys"
        private const val KEY_LIST = "key_list"

        @Volatile
        private var instance: KeyRepository? = null

        fun getInstance(context: Context): KeyRepository {
            return instance ?: synchronized(this) {
                instance ?: KeyRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
