package com.demogorgon314.monitorcontrolremotecontrolandroid.feature.home

import app.cash.turbine.test
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.ConnectionSettings
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.ConnectionSettingsStoreDataSource
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.AllPowerResponse
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.DisplayCapabilities
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.DisplayStatus
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.HealthResponse
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.SinglePowerResponse
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.repository.MonitorControlApiException
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.repository.MonitorControlRepositoryContract
import com.demogorgon314.monitorcontrolremotecontrolandroid.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `init without settings should open settings dialog`() = runTest {
        val store = FakeSettingsStore(initialSettings = null)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = HomeViewModel(
            settingsStore = store,
            repositoryFactory = { FakeRepository() },
            ioDispatcher = dispatcher
        )

        advanceUntilIdle()
        val state = viewModel.uiState.value

        assertTrue(state.showSettingsDialog)
        assertEquals(ConnectionStatus.Disconnected, state.connectionStatus)
    }

    @Test
    fun `init with settings should connect and load displays`() = runTest {
        val store = FakeSettingsStore(initialSettings = TEST_SETTINGS)
        val repository = FakeRepository()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = HomeViewModel(
            settingsStore = store,
            repositoryFactory = { repository },
            ioDispatcher = dispatcher
        )

        advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(ConnectionStatus.Connected, state.connectionStatus)
        assertEquals(1, state.displays.size)
        assertFalse(state.showSettingsDialog)
    }

    @Test
    fun `refresh should request latest displays`() = runTest {
        val store = FakeSettingsStore(initialSettings = TEST_SETTINGS)
        val repository = FakeRepository()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = HomeViewModel(
            settingsStore = store,
            repositoryFactory = { repository },
            ioDispatcher = dispatcher
        )

        advanceUntilIdle()
        val callCountBeforeRefresh = repository.displaysCalls

        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(repository.displaysCalls > callCountBeforeRefresh)
    }

    @Test
    fun `global brightness should sync on every percent change`() = runTest {
        val store = FakeSettingsStore(initialSettings = TEST_SETTINGS)
        val repository = FakeRepository()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = HomeViewModel(
            settingsStore = store,
            repositoryFactory = { repository },
            ioDispatcher = dispatcher
        )

        advanceUntilIdle()

        viewModel.onGlobalBrightnessChanged(30)
        advanceUntilIdle()
        assertEquals(1, repository.setAllBrightnessCalls)
        assertEquals(30, repository.lastSetAllBrightness)

        viewModel.onGlobalBrightnessChanged(45)
        advanceUntilIdle()
        assertEquals(2, repository.setAllBrightnessCalls)
        assertEquals(45, repository.lastSetAllBrightness)

        viewModel.onGlobalBrightnessChanged(45)
        advanceUntilIdle()
        assertEquals(2, repository.setAllBrightnessCalls)

        viewModel.onGlobalBrightnessChanged(55)
        viewModel.onGlobalBrightnessChangeFinished()
        advanceUntilIdle()
        assertEquals(3, repository.setAllBrightnessCalls)
        assertEquals(55, repository.lastSetAllBrightness)
    }

    @Test
    fun `display power failure should clear busy state and emit snackbar message`() = runTest {
        val store = FakeSettingsStore(initialSettings = TEST_SETTINGS)
        val repository = FakeRepository(failPowerToggle = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = HomeViewModel(
            settingsStore = store,
            repositoryFactory = { repository },
            ioDispatcher = dispatcher
        )

        advanceUntilIdle()

        viewModel.messages.test {
            viewModel.onDisplayPowerToggle(displayId = 1L, turnOn = false)
            advanceUntilIdle()

            val message = awaitItem()
            assertTrue(message.isNotBlank())
        }

        val display = viewModel.uiState.value.displays.first()
        assertFalse(display.isBusy)
    }

    private class FakeSettingsStore(initialSettings: ConnectionSettings?) : ConnectionSettingsStoreDataSource {
        private val state = MutableStateFlow(initialSettings)

        override val settingsFlow: Flow<ConnectionSettings?> = state.asStateFlow()

        override suspend fun read(): ConnectionSettings? = state.value

        override suspend fun save(settings: ConnectionSettings) {
            state.value = settings
        }

        override suspend fun clear() {
            state.value = null
        }
    }

    private class FakeRepository(
        private val failPowerToggle: Boolean = false
    ) : MonitorControlRepositoryContract {
        private var displaysState: List<DisplayStatus> = listOf(
            DisplayStatus(
                id = 1L,
                name = "LG",
                friendlyName = "LG UltraWide",
                type = "other",
                isVirtual = false,
                isDummy = false,
                brightness = 68,
                powerState = "on",
                capabilities = DisplayCapabilities(
                    brightness = true,
                    power = true
                )
            )
        )

        var displaysCalls: Int = 0
            private set
        var setAllBrightnessCalls: Int = 0
            private set
        var lastSetAllBrightness: Int = -1
            private set

        override suspend fun health(): HealthResponse {
            return HealthResponse(status = "ok", version = "v1")
        }

        override suspend fun displays(): List<DisplayStatus> {
            displaysCalls += 1
            return displaysState
        }

        override suspend fun setBrightness(displayId: Long, value: Int): DisplayStatus {
            val current = displaysState.first { it.id == displayId }
            val updated = current.copy(brightness = value)
            displaysState = displaysState.map { if (it.id == displayId) updated else it }
            return updated
        }

        override suspend fun setAllBrightness(value: Int): List<DisplayStatus> {
            setAllBrightnessCalls += 1
            lastSetAllBrightness = value
            displaysState = displaysState.map { it.copy(brightness = value) }
            return displaysState
        }

        override suspend fun powerOff(displayId: Long): SinglePowerResponse {
            if (failPowerToggle) {
                throw MonitorControlApiException(
                    httpCode = 500,
                    apiCode = "internal_error",
                    message = "power off failed"
                )
            }
            displaysState = displaysState.map {
                if (it.id == displayId) it.copy(powerState = "off") else it
            }
            return SinglePowerResponse(
                displayId = displayId,
                requestedState = "off",
                accepted = true
            )
        }

        override suspend fun powerOn(displayId: Long): SinglePowerResponse {
            displaysState = displaysState.map {
                if (it.id == displayId) it.copy(powerState = "on") else it
            }
            return SinglePowerResponse(
                displayId = displayId,
                requestedState = "on",
                accepted = true
            )
        }

        override suspend fun powerOffAll(): AllPowerResponse {
            displaysState = displaysState.map { it.copy(powerState = "off") }
            return AllPowerResponse(
                requestedState = "off",
                acceptedDisplayIds = displaysState.map { it.id }
            )
        }

        override suspend fun powerOnAll(): AllPowerResponse {
            displaysState = displaysState.map { it.copy(powerState = "on") }
            return AllPowerResponse(
                requestedState = "on",
                acceptedDisplayIds = displaysState.map { it.id }
            )
        }
    }

    companion object {
        private val TEST_SETTINGS = ConnectionSettings(
            host = "192.168.1.10",
            port = 51423,
            token = "token"
        )
    }
}
