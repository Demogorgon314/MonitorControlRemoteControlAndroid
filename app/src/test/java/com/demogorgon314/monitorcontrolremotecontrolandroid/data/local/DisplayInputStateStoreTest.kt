package com.demogorgon314.monitorcontrolremotecontrolandroid.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.InputSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class DisplayInputStateStoreTest {
    @Test
    fun `save and read should persist input state`() = runBlocking {
        val store = createStore()

        store.save(
            host = "192.168.1.10",
            port = 51423,
            displayId = 1L,
            input = InputSource(code = 17, name = "HDMI-1")
        )

        val result = store.readForConnection(host = "192.168.1.10", port = 51423)
        assertEquals(1, result.size)
        assertEquals(17, result[1L]?.code)
        assertEquals("HDMI-1", result[1L]?.name)
    }

    @Test
    fun `read should isolate host and port`() = runBlocking {
        val store = createStore()
        store.save(
            host = "192.168.1.10",
            port = 51423,
            displayId = 1L,
            input = InputSource(code = 17, name = "HDMI-1")
        )

        val wrongHost = store.readForConnection(host = "192.168.1.11", port = 51423)
        val wrongPort = store.readForConnection(host = "192.168.1.10", port = 51424)

        assertNull(wrongHost[1L])
        assertNull(wrongPort[1L])
    }

    @Test
    fun `read should isolate display id`() = runBlocking {
        val store = createStore()
        store.save(
            host = "192.168.1.10",
            port = 51423,
            displayId = 1L,
            input = InputSource(code = 17, name = "HDMI-1")
        )
        store.save(
            host = "192.168.1.10",
            port = 51423,
            displayId = 2L,
            input = InputSource(code = 15, name = "DP-1")
        )

        val result = store.readForConnection(host = "192.168.1.10", port = 51423)
        assertEquals(17, result[1L]?.code)
        assertEquals(15, result[2L]?.code)
        assertEquals(2, result.size)
    }

    private fun createStore(): DisplayInputStateStore {
        val file = File.createTempFile("display-input-state-test", ".preferences_pb")
        file.deleteOnExit()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { file }
        )
        return DisplayInputStateStore(dataStore)
    }
}
