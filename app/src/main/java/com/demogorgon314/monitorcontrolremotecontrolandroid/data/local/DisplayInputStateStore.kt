package com.demogorgon314.monitorcontrolremotecontrolandroid.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.InputSource
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.security.MessageDigest

private val Context.displayInputStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "display_input_state")

interface DisplayInputStateStoreDataSource {
    suspend fun readForConnection(host: String, port: Int): Map<Long, InputSource>
    suspend fun save(host: String, port: Int, displayId: Long, input: InputSource)
}

class DisplayInputStateStore(
    private val dataStore: DataStore<Preferences>
) : DisplayInputStateStoreDataSource {
    override suspend fun readForConnection(host: String, port: Int): Map<Long, InputSource> {
        val preferences = dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .first()

        val prefix = connectionPrefix(host = host, port = port)
        val inputByDisplayId = mutableMapOf<Long, InputSource>()
        preferences.asMap().forEach { (key, rawValue) ->
            val keyName = key.name
            if (!keyName.startsWith(prefix) || !keyName.endsWith(CODE_SUFFIX)) {
                return@forEach
            }

            val displayId = keyName
                .removePrefix(prefix)
                .removeSuffix(CODE_SUFFIX)
                .toLongOrNull()
                ?: return@forEach
            val code = rawValue as? Int ?: return@forEach
            if (code !in 0..255) {
                return@forEach
            }

            val name = preferences[nameKey(prefix = prefix, displayId = displayId)]
                ?.takeIf { it.isNotBlank() }
                ?: "UNKNOWN-$code"
            inputByDisplayId[displayId] = InputSource(code = code, name = name)
        }
        return inputByDisplayId
    }

    override suspend fun save(host: String, port: Int, displayId: Long, input: InputSource) {
        if (input.code !in 0..255) {
            return
        }
        val prefix = connectionPrefix(host = host, port = port)
        dataStore.edit { preferences ->
            preferences[codeKey(prefix = prefix, displayId = displayId)] = input.code
            preferences[nameKey(prefix = prefix, displayId = displayId)] = input.name
        }
    }

    companion object {
        private const val CODE_SUFFIX = "_code"
        private const val NAME_SUFFIX = "_name"
        private const val KEY_PREFIX = "display_input_"

        fun from(context: Context): DisplayInputStateStore {
            return DisplayInputStateStore(context.displayInputStateDataStore)
        }

        private fun codeKey(prefix: String, displayId: Long) = intPreferencesKey("$prefix$displayId$CODE_SUFFIX")

        private fun nameKey(prefix: String, displayId: Long) = stringPreferencesKey("$prefix$displayId$NAME_SUFFIX")

        private fun connectionPrefix(host: String, port: Int): String {
            val normalizedHost = host.trim()
                .removePrefix("http://")
                .removePrefix("https://")
                .trimEnd('/')
                .lowercase()
            val raw = "$normalizedHost:$port"
            return "$KEY_PREFIX${raw.sha256Hex()}_"
        }

        private fun String.sha256Hex(): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
            return digest.joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xFF)
            }
        }
    }
}
