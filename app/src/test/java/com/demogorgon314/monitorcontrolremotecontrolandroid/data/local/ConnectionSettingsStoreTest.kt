package com.demogorgon314.monitorcontrolremotecontrolandroid.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConnectionSettingsStoreTest {
    @Test
    fun `read should return null by default`() = runBlocking {
        val store = createStore()

        val settings = store.read()

        assertNull(settings)
    }

    @Test
    fun `save and read should persist settings`() = runBlocking {
        val store = createStore()
        val expected = ConnectionSettings(
            host = "192.168.1.10",
            port = 51423,
            token = "abc"
        )

        store.save(expected)
        val actual = store.read()

        assertEquals(expected, actual)
    }

    @Test
    fun `read should return null when persisted port is invalid`() = runBlocking {
        val store = createStore()
        store.save(
            ConnectionSettings(
                host = "192.168.1.10",
                port = 80,
                token = "abc"
            )
        )

        assertNull(store.read())
    }

    @Test
    fun `validator should reject invalid port and empty fields`() {
        val invalid = ConnectionSettingsValidator.validate(
            host = "",
            port = "abc",
            token = ""
        )
        val valid = ConnectionSettingsValidator.validate(
            host = "192.168.1.10",
            port = "51423",
            token = "token"
        )

        assertFalse(invalid.isValid)
        assertTrue(valid.isValid)
    }

    private fun createStore(): ConnectionSettingsStore {
        val file = File.createTempFile("settings-test", ".preferences_pb")
        file.deleteOnExit()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { file }
        )
        return ConnectionSettingsStore(dataStore)
    }
}
