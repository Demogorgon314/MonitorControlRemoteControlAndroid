package com.demogorgon314.monitorcontrolremotecontrolandroid.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.connectionSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "connection_settings")

data class ConnectionSettings(
    val host: String,
    val port: Int,
    val token: String
) {
    fun baseUrl(): String {
        val normalizedHost = host.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        return "http://$normalizedHost:$port/"
    }
}

data class ConnectionSettingsDraft(
    val host: String = "",
    val port: String = ConnectionSettingsValidator.DEFAULT_PORT.toString(),
    val token: String = ""
)

data class ConnectionSettingsValidation(
    val hostError: String? = null,
    val portError: String? = null,
    val tokenError: String? = null
) {
    val isValid: Boolean
        get() = hostError == null && portError == null && tokenError == null
}

object ConnectionSettingsValidator {
    const val DEFAULT_PORT: Int = 51423
    const val MIN_PORT: Int = 1024
    const val MAX_PORT: Int = 65535

    fun validate(host: String, port: String, token: String): ConnectionSettingsValidation {
        val hostError = if (host.trim().isBlank()) "请输入主机地址" else null
        val parsedPort = port.toIntOrNull()
        val portError = when {
            port.isBlank() -> "请输入端口"
            parsedPort == null -> "端口必须是数字"
            parsedPort !in MIN_PORT..MAX_PORT -> "端口范围需在 $MIN_PORT-$MAX_PORT"
            else -> null
        }
        val tokenError = if (token.trim().isBlank()) "请输入 Bearer Token" else null
        return ConnectionSettingsValidation(
            hostError = hostError,
            portError = portError,
            tokenError = tokenError
        )
    }
}

interface ConnectionSettingsStoreDataSource {
    val settingsFlow: Flow<ConnectionSettings?>
    suspend fun read(): ConnectionSettings?
    suspend fun save(settings: ConnectionSettings)
    suspend fun clear()
}

class ConnectionSettingsStore(
    private val dataStore: DataStore<Preferences>
) : ConnectionSettingsStoreDataSource {
    override val settingsFlow: Flow<ConnectionSettings?> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            val host = preferences[KEY_HOST].orEmpty().trim()
            val token = preferences[KEY_TOKEN].orEmpty().trim()
            val port = preferences[KEY_PORT] ?: ConnectionSettingsValidator.DEFAULT_PORT

            if (host.isBlank() || token.isBlank() || port !in ConnectionSettingsValidator.MIN_PORT..ConnectionSettingsValidator.MAX_PORT) {
                null
            } else {
                ConnectionSettings(host = host, port = port, token = token)
            }
        }

    override suspend fun read(): ConnectionSettings? = settingsFlow.first()

    override suspend fun save(settings: ConnectionSettings) {
        dataStore.edit { preferences ->
            preferences[KEY_HOST] = settings.host.trim()
            preferences[KEY_PORT] = settings.port
            preferences[KEY_TOKEN] = settings.token.trim()
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_HOST)
            preferences.remove(KEY_PORT)
            preferences.remove(KEY_TOKEN)
        }
    }

    companion object {
        fun from(context: Context): ConnectionSettingsStore {
            return ConnectionSettingsStore(context.connectionSettingsDataStore)
        }

        private val KEY_HOST = stringPreferencesKey("host")
        private val KEY_PORT = intPreferencesKey("port")
        private val KEY_TOKEN = stringPreferencesKey("token")
    }
}

fun ConnectionSettings.toDraft(): ConnectionSettingsDraft {
    return ConnectionSettingsDraft(
        host = host,
        port = port.toString(),
        token = token
    )
}
