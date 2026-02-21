package com.demogorgon314.monitorcontrolremotecontrolandroid.feature.home

import app.cash.turbine.test
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.ConnectionSettings
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.ConnectionSettingsStoreDataSource
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.DisplayInputStateStoreDataSource
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.AllPowerResponse
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.DisplayCapabilities
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.DisplayInputStatus
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.DisplayStatus
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.HealthResponse
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.InputSource
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.SinglePowerResponse
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.scan.MonitorControlHostScanner
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.scan.ScanMatchKind
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.scan.ScannedHostCandidate
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.repository.MonitorControlApiException
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.repository.MonitorControlRepositoryContract
import com.demogorgon314.monitorcontrolremotecontrolandroid.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
            hostScanner = FakeHostScanner(),
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
            hostScanner = FakeHostScanner(),
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
            hostScanner = FakeHostScanner(),
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
            hostScanner = FakeHostScanner(),
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
    fun `global volume should sync on every percent change`() = runTest {
        val store = FakeSettingsStore(initialSettings = TEST_SETTINGS)
        val repository = FakeRepository()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = HomeViewModel(
            settingsStore = store,
            repositoryFactory = { repository },
            ioDispatcher = dispatcher
        )

        advanceUntilIdle()

        viewModel.onGlobalVolumeChanged(20)
        advanceUntilIdle()
        assertEquals(1, repository.setAllVolumeCalls)
        assertEquals(20, repository.lastSetAllVolume)

        viewModel.onGlobalVolumeChanged(35)
        advanceUntilIdle()
        assertEquals(2, repository.setAllVolumeCalls)
        assertEquals(35, repository.lastSetAllVolume)

        viewModel.onGlobalVolumeChanged(35)
        advanceUntilIdle()
        assertEquals(2, repository.setAllVolumeCalls)

        viewModel.onGlobalVolumeChanged(42)
        viewModel.onGlobalVolumeChangeFinished()
        advanceUntilIdle()
        assertEquals(3, repository.setAllVolumeCalls)
        assertEquals(42, repository.lastSetAllVolume)
    }

    @Test
    fun `display power failure should clear busy state and emit snackbar message`() = runTest {
        val store = FakeSettingsStore(initialSettings = TEST_SETTINGS)
        val repository = FakeRepository(failPowerToggle = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = HomeViewModel(
            settingsStore = store,
            repositoryFactory = { repository },
            hostScanner = FakeHostScanner(),
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

    @Test
    fun `init should fallback to local cached input when current is unknown`() = runTest {
        val store = FakeSettingsStore(initialSettings = TEST_SETTINGS)
        val inputStore = FakeDisplayInputStateStore()
        inputStore.save(
            host = TEST_SETTINGS.host,
            port = TEST_SETTINGS.port,
            displayId = 1L,
            input = InputSource(code = 17, name = "HDMI-1")
        )
        val repository = FakeRepository(
            initialDisplays = listOf(
                buildDisplayStatus(
                    input = DisplayInputStatus(
                        supported = true,
                        bestEffort = true,
                        current = null,
                        available = listOf(
                            InputSource(code = 17, name = "HDMI-1"),
                            InputSource(code = 15, name = "DP-1")
                        )
                    )
                )
            )
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = HomeViewModel(
            settingsStore = store,
            repositoryFactory = { repository },
            hostScanner = FakeHostScanner(),
            inputStateStore = inputStore,
            ioDispatcher = dispatcher
        )

        advanceUntilIdle()

        val display = viewModel.uiState.value.displays.first()
        assertEquals(17, display.currentInput?.code)
        assertEquals("HDMI-1", display.currentInput?.name)
        assertTrue(display.isInputFromLocalCache)
    }

    @Test
    fun `remote current input should override local cache and sync store`() = runTest {
        val store = FakeSettingsStore(initialSettings = TEST_SETTINGS)
        val inputStore = FakeDisplayInputStateStore()
        inputStore.save(
            host = TEST_SETTINGS.host,
            port = TEST_SETTINGS.port,
            displayId = 1L,
            input = InputSource(code = 17, name = "HDMI-1")
        )
        val repository = FakeRepository(
            initialDisplays = listOf(
                buildDisplayStatus(
                    input = DisplayInputStatus(
                        supported = true,
                        bestEffort = true,
                        current = InputSource(code = 15, name = "DP-1"),
                        available = listOf(
                            InputSource(code = 17, name = "HDMI-1"),
                            InputSource(code = 15, name = "DP-1")
                        )
                    )
                )
            )
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = HomeViewModel(
            settingsStore = store,
            repositoryFactory = { repository },
            hostScanner = FakeHostScanner(),
            inputStateStore = inputStore,
            ioDispatcher = dispatcher
        )

        advanceUntilIdle()

        val display = viewModel.uiState.value.displays.first()
        assertEquals(15, display.currentInput?.code)
        assertFalse(display.isInputFromLocalCache)

        val persisted = inputStore.readForConnection(TEST_SETTINGS.host, TEST_SETTINGS.port)[1L]
        assertEquals(15, persisted?.code)
        assertEquals("DP-1", persisted?.name)
    }

    @Test
    fun `select input should call repository and persist local state`() = runTest {
        val store = FakeSettingsStore(initialSettings = TEST_SETTINGS)
        val repository = FakeRepository()
        val inputStore = FakeDisplayInputStateStore()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = HomeViewModel(
            settingsStore = store,
            repositoryFactory = { repository },
            hostScanner = FakeHostScanner(),
            inputStateStore = inputStore,
            ioDispatcher = dispatcher
        )

        advanceUntilIdle()
        viewModel.onDisplayInputSelected(displayId = 1L, code = 15, name = "DP-1")
        advanceUntilIdle()

        assertEquals(1, repository.setInputCalls)
        assertEquals(15, repository.lastSetInputCode)
        val display = viewModel.uiState.value.displays.first()
        assertEquals(15, display.currentInput?.code)
        assertEquals("DP-1", display.currentInput?.name)
        assertFalse(display.isInputFromLocalCache)

        val persisted = inputStore.readForConnection(TEST_SETTINGS.host, TEST_SETTINGS.port)[1L]
        assertEquals(15, persisted?.code)
        assertEquals("DP-1", persisted?.name)
    }

    @Test
    fun `select input failure should clear busy and emit message`() = runTest {
        val store = FakeSettingsStore(initialSettings = TEST_SETTINGS)
        val repository = FakeRepository(failInputToggle = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = HomeViewModel(
            settingsStore = store,
            repositoryFactory = { repository },
            hostScanner = FakeHostScanner(),
            ioDispatcher = dispatcher
        )

        advanceUntilIdle()

        viewModel.messages.test {
            viewModel.onDisplayInputSelected(displayId = 1L, code = 15, name = "DP-1")
            advanceUntilIdle()
            val message = awaitItem()
            assertTrue(message.isNotBlank())
        }

        assertEquals(1, repository.setInputCalls)
        val display = viewModel.uiState.value.displays.first()
        assertFalse(display.isBusy)
    }

    @Test
    fun `open settings should auto scan and fill host on single result`() = runTest {
        val store = FakeSettingsStore(initialSettings = TEST_SETTINGS)
        val scanner = FakeHostScanner(
            results = listOf(
                ScannedHostCandidate(
                    host = "192.168.1.10",
                    latencyMs = 15,
                    matchKind = ScanMatchKind.HEALTH_OK
                )
            )
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = HomeViewModel(
            settingsStore = store,
            repositoryFactory = { FakeRepository() },
            hostScanner = scanner,
            ioDispatcher = dispatcher
        )

        advanceUntilIdle()
        viewModel.openSettings()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, scanner.calls)
        assertEquals("192.168.1.10", state.settingsDraft.host)
        assertFalse(state.showScanResultPicker)
    }

    @Test
    fun `scan should show picker when multiple results found`() = runTest {
        val store = FakeSettingsStore(initialSettings = TEST_SETTINGS)
        val scanner = FakeHostScanner(
            results = listOf(
                ScannedHostCandidate("192.168.1.10", 15, ScanMatchKind.HEALTH_OK),
                ScannedHostCandidate("192.168.1.22", 25, ScanMatchKind.UNAUTHORIZED_SIGNATURE)
            )
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = HomeViewModel(
            settingsStore = store,
            repositoryFactory = { FakeRepository() },
            hostScanner = scanner,
            ioDispatcher = dispatcher
        )

        advanceUntilIdle()
        viewModel.openSettings()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showScanResultPicker)
        assertEquals(2, viewModel.uiState.value.scanCandidates.size)
    }

    @Test
    fun `manual scan should run again after auto scan`() = runTest {
        val store = FakeSettingsStore(initialSettings = TEST_SETTINGS)
        val scanner = FakeHostScanner(
            results = listOf(
                ScannedHostCandidate("192.168.1.10", 15, ScanMatchKind.HEALTH_OK)
            )
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = HomeViewModel(
            settingsStore = store,
            repositoryFactory = { FakeRepository() },
            hostScanner = scanner,
            ioDispatcher = dispatcher
        )

        advanceUntilIdle()
        viewModel.openSettings()
        advanceUntilIdle()
        viewModel.onScanHostsRequested(manual = true)
        advanceUntilIdle()

        assertEquals(2, scanner.calls)
    }

    @Test
    fun `scan should show error when no candidate found`() = runTest {
        val store = FakeSettingsStore(initialSettings = TEST_SETTINGS)
        val scanner = FakeHostScanner(results = emptyList())
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = HomeViewModel(
            settingsStore = store,
            repositoryFactory = { FakeRepository() },
            hostScanner = scanner,
            ioDispatcher = dispatcher
        )

        advanceUntilIdle()
        viewModel.openSettings()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showScanResultPicker)
        assertTrue(state.scanErrorMessage?.contains("未扫描到可用主机") == true)
    }

    @Test
    fun `scan should ignore repeated request while running and cancel on dismiss`() = runTest {
        val store = FakeSettingsStore(initialSettings = TEST_SETTINGS)
        val scanner = FakeHostScanner(
            results = listOf(
                ScannedHostCandidate("192.168.1.66", 25, ScanMatchKind.HEALTH_OK)
            ),
            delayMs = 1_000
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = HomeViewModel(
            settingsStore = store,
            repositoryFactory = { FakeRepository() },
            hostScanner = scanner,
            ioDispatcher = dispatcher
        )

        advanceUntilIdle()
        viewModel.openSettings()
        advanceTimeBy(1)
        viewModel.onScanHostsRequested(manual = true)
        viewModel.dismissSettings()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, scanner.calls)
        assertFalse(state.showSettingsDialog)
        assertFalse(state.isScanningHosts)
        assertEquals(TEST_SETTINGS.host, state.settingsDraft.host)
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
        private val failPowerToggle: Boolean = false,
        private val failInputToggle: Boolean = false,
        initialDisplays: List<DisplayStatus> = listOf(
            DisplayStatus(
                id = 1L,
                name = "LG",
                friendlyName = "LG UltraWide",
                type = "other",
                isVirtual = false,
                isDummy = false,
                brightness = 68,
                volume = 26,
                powerState = "on",
                capabilities = DisplayCapabilities(
                    brightness = true,
                    volume = true,
                    power = true
                ),
                input = DisplayInputStatus(
                    supported = true,
                    bestEffort = true,
                    current = InputSource(code = 17, name = "HDMI-1"),
                    available = listOf(
                        InputSource(code = 17, name = "HDMI-1"),
                        InputSource(code = 15, name = "DP-1")
                    )
                )
            )
        )
    ) : MonitorControlRepositoryContract {
        private var displaysState: List<DisplayStatus> = initialDisplays

        var displaysCalls: Int = 0
            private set
        var setAllBrightnessCalls: Int = 0
            private set
        var lastSetAllBrightness: Int = -1
            private set
        var setAllVolumeCalls: Int = 0
            private set
        var lastSetAllVolume: Int = -1
            private set
        var setInputCalls: Int = 0
            private set
        var lastSetInputCode: Int = -1
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

        override suspend fun setVolume(displayId: Long, value: Int): DisplayStatus {
            val current = displaysState.first { it.id == displayId }
            val updated = current.copy(volume = value)
            displaysState = displaysState.map { if (it.id == displayId) updated else it }
            return updated
        }

        override suspend fun setAllVolume(value: Int): List<DisplayStatus> {
            setAllVolumeCalls += 1
            lastSetAllVolume = value
            displaysState = displaysState.map { it.copy(volume = value) }
            return displaysState
        }

        override suspend fun setInput(displayId: Long, code: Int): DisplayStatus {
            setInputCalls += 1
            lastSetInputCode = code
            if (failInputToggle) {
                throw MonitorControlApiException(
                    httpCode = 500,
                    apiCode = "internal_error",
                    message = "input toggle failed"
                )
            }

            val current = displaysState.first { it.id == displayId }
            if (!current.input.supported) {
                throw MonitorControlApiException(
                    httpCode = 409,
                    apiCode = "unsupported_operation",
                    message = "input not supported"
                )
            }

            val nextName = current.input.available.firstOrNull { it.code == code }?.name ?: "UNKNOWN-$code"
            val nextCurrent = InputSource(code = code, name = nextName)
            val nextAvailable = if (current.input.available.any { it.code == code }) {
                current.input.available
            } else {
                listOf(nextCurrent) + current.input.available
            }
            val updated = current.copy(
                input = current.input.copy(
                    current = nextCurrent,
                    available = nextAvailable
                )
            )
            displaysState = displaysState.map { if (it.id == displayId) updated else it }
            return updated
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

    private class FakeDisplayInputStateStore : DisplayInputStateStoreDataSource {
        private val state = mutableMapOf<String, MutableMap<Long, InputSource>>()

        override suspend fun readForConnection(host: String, port: Int): Map<Long, InputSource> {
            return state[key(host = host, port = port)]?.toMap().orEmpty()
        }

        override suspend fun save(host: String, port: Int, displayId: Long, input: InputSource) {
            state.getOrPut(key(host = host, port = port)) { mutableMapOf() }[displayId] = input
        }

        private fun key(host: String, port: Int): String {
            return "${host.trim().lowercase()}:$port"
        }
    }

    private class FakeHostScanner(
        private val results: List<ScannedHostCandidate> = emptyList(),
        private val delayMs: Long = 0
    ) : MonitorControlHostScanner {
        var calls: Int = 0
            private set

        override suspend fun scan(token: String, preferredHost: String?): List<ScannedHostCandidate> {
            calls += 1
            if (delayMs > 0) {
                delay(delayMs)
            }
            return results
        }
    }

    companion object {
        private val TEST_SETTINGS = ConnectionSettings(
            host = "192.168.1.10",
            port = 51423,
            token = "token"
        )

        private fun buildDisplayStatus(input: DisplayInputStatus): DisplayStatus {
            return DisplayStatus(
                id = 1L,
                name = "LG",
                friendlyName = "LG UltraWide",
                type = "other",
                isVirtual = false,
                isDummy = false,
                brightness = 68,
                volume = 26,
                powerState = "on",
                capabilities = DisplayCapabilities(
                    brightness = true,
                    volume = true,
                    power = true
                ),
                input = input
            )
        }
    }
}
