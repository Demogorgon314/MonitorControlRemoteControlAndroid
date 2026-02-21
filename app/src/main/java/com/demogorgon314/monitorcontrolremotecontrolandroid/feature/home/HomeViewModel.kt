package com.demogorgon314.monitorcontrolremotecontrolandroid.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.ConnectionSettings
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.ConnectionSettingsDraft
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.ConnectionSettingsStore
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.ConnectionSettingsStoreDataSource
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.ConnectionSettingsValidation
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.ConnectionSettingsValidator
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.toDraft
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.DisplayStatus
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.MonitorControlNetworkFactory
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.scan.DefaultMonitorControlHostScanner
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.scan.MonitorControlHostScanner
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.scan.ScannedHostCandidate
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.repository.MonitorControlApiException
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.repository.MonitorControlRepository
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.repository.MonitorControlRepositoryContract
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.roundToInt

class HomeViewModel(
    private val settingsStore: ConnectionSettingsStoreDataSource,
    private val repositoryFactory: (ConnectionSettings) -> MonitorControlRepositoryContract = ::defaultRepositoryFactory,
    private val hostScanner: MonitorControlHostScanner = DefaultMonitorControlHostScanner(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var currentSettings: ConnectionSettings? = null
    private var repository: MonitorControlRepositoryContract? = null
    private var lastSentGlobalBrightness: Int? = null
    private val lastSentDisplayBrightness = mutableMapOf<Long, Int>()
    private var scanJob: Job? = null

    init {
        bootstrap()
    }

    fun refresh() {
        val settings = currentSettings
        val currentRepository = repository
        if (settings == null || currentRepository == null) {
            if (settings == null) {
                _uiState.update { it.copy(showSettingsDialog = true) }
                emitMessage("请先配置连接信息")
                return
            }
            connectAndLoad(settings, showLoading = false, showRefresh = true)
            return
        }

        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isRefreshing = true) }
            runCatching { loadDisplaysWithRetry(currentRepository) }
                .onSuccess { displays ->
                    _uiState.update { state ->
                        state.copy(
                            connectionStatus = ConnectionStatus.Connected,
                            isRefreshing = false,
                            displays = displays,
                            globalBrightness = calculateGlobalBrightness(displays, state.globalBrightness)
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            connectionStatus = ConnectionStatus.Disconnected
                        )
                    }
                    emitError(error)
                }
        }
    }

    fun openSettings() {
        val draft = currentSettings?.toDraft() ?: _uiState.value.settingsDraft
        _uiState.update {
            it.copy(
                showSettingsDialog = true,
                settingsDraft = draft,
                settingsValidation = ConnectionSettingsValidation(),
                scanErrorMessage = null,
                scanCandidates = emptyList(),
                showScanResultPicker = false,
                hasAutoScanRunForDialog = false
            )
        }
        onScanHostsRequested(manual = false)
    }

    fun dismissSettings() {
        cancelHostScan()
        _uiState.update {
            it.copy(
                showSettingsDialog = false,
                settingsValidation = ConnectionSettingsValidation(),
                isScanningHosts = false,
                scanCandidates = emptyList(),
                showScanResultPicker = false,
                scanErrorMessage = null,
                hasAutoScanRunForDialog = false
            )
        }
    }

    fun updateHost(host: String) {
        updateSettingsDraft(_uiState.value.settingsDraft.copy(host = host))
    }

    fun updatePort(port: String) {
        updateSettingsDraft(_uiState.value.settingsDraft.copy(port = port))
    }

    fun updateToken(token: String) {
        updateSettingsDraft(_uiState.value.settingsDraft.copy(token = token))
    }

    fun onScanHostsRequested(manual: Boolean) {
        val currentState = _uiState.value
        if (!currentState.showSettingsDialog || currentState.isScanningHosts) {
            return
        }
        if (!manual && currentState.hasAutoScanRunForDialog) {
            return
        }

        val draftHost = currentState.settingsDraft.host
        val draftToken = currentState.settingsDraft.token
        _uiState.update {
            it.copy(
                isScanningHosts = true,
                scanErrorMessage = null,
                showScanResultPicker = false,
                hasAutoScanRunForDialog = true
            )
        }

        cancelHostScan()
        scanJob = viewModelScope.launch(ioDispatcher) {
            try {
                runCatching {
                    hostScanner.scan(
                        token = draftToken,
                        preferredHost = draftHost
                    )
                }.onSuccess { results ->
                    handleScanSuccess(results)
                }.onFailure { error ->
                    if (error is CancellationException) {
                        return@onFailure
                    }
                    _uiState.update {
                        if (!it.showSettingsDialog) {
                            it
                        } else {
                            it.copy(
                                isScanningHosts = false,
                                scanCandidates = emptyList(),
                                showScanResultPicker = false,
                                scanErrorMessage = "扫描失败，请稍后重试"
                            )
                        }
                    }
                    if (manual) {
                        emitMessage("自动扫描失败，请稍后重试")
                    }
                }
            } finally {
                scanJob = null
            }
        }
    }

    fun onScanResultSelected(host: String) {
        val normalizedHost = host.trim()
        if (normalizedHost.isBlank()) {
            return
        }
        _uiState.update {
            it.copy(
                settingsDraft = it.settingsDraft.copy(host = normalizedHost),
                showScanResultPicker = false,
                scanErrorMessage = null
            )
        }
        emitMessage("已填充主机地址：$normalizedHost")
    }

    fun dismissScanResultPicker() {
        _uiState.update { it.copy(showScanResultPicker = false) }
    }

    fun saveSettings() {
        cancelHostScan()
        val draft = _uiState.value.settingsDraft
        val validation = ConnectionSettingsValidator.validate(
            host = draft.host,
            port = draft.port,
            token = draft.token
        )
        if (!validation.isValid) {
            _uiState.update { it.copy(settingsValidation = validation) }
            return
        }

        val settings = ConnectionSettings(
            host = draft.host.trim(),
            port = draft.port.toInt(),
            token = draft.token.trim()
        )
        viewModelScope.launch(ioDispatcher) {
            settingsStore.save(settings)
            currentSettings = settings
            _uiState.update {
                it.copy(
                    showSettingsDialog = false,
                    settingsValidation = ConnectionSettingsValidation(),
                    settingsDraft = settings.toDraft(),
                    isScanningHosts = false,
                    scanCandidates = emptyList(),
                    showScanResultPicker = false,
                    scanErrorMessage = null,
                    hasAutoScanRunForDialog = false
                )
            }
            connectAndLoad(settings, showLoading = true, showRefresh = false)
        }
    }

    fun onGlobalBrightnessChanged(value: Int) {
        val normalized = value.coerceIn(0, 100)
        val previous = _uiState.value.globalBrightness
        _uiState.update { state ->
            val updatedDisplays = state.displays.map { display ->
                if (display.canControlBrightness && display.powerOn) {
                    display.copy(brightness = normalized)
                } else {
                    display
                }
            }
            state.copy(
                globalBrightness = normalized,
                displays = updatedDisplays
            )
        }
        if (normalized != previous) {
            submitGlobalBrightness(normalized)
        }
    }

    fun onGlobalBrightnessChangeFinished() {
        val value = _uiState.value.globalBrightness
        if (lastSentGlobalBrightness != value) {
            submitGlobalBrightness(value)
        }
    }

    fun onPowerAll(turnOn: Boolean) {
        if (_uiState.value.isGlobalBusy) {
            return
        }
        val currentRepository = repository ?: run {
            emitMessage("连接未建立")
            return
        }

        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isGlobalBusy = true) }
            runCatching {
                if (turnOn) {
                    currentRepository.powerOnAll()
                } else {
                    currentRepository.powerOffAll()
                }
                loadDisplaysWithRetry(currentRepository)
            }.onSuccess { displays ->
                _uiState.update { state ->
                    state.copy(
                        connectionStatus = ConnectionStatus.Connected,
                        isGlobalBusy = false,
                        displays = displays,
                        globalBrightness = calculateGlobalBrightness(displays, state.globalBrightness)
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isGlobalBusy = false) }
                emitError(error)
            }
        }
    }

    fun onDisplayBrightnessChanged(displayId: Long, value: Int) {
        val normalized = value.coerceIn(0, 100)
        val previous = _uiState.value.displays.firstOrNull { it.id == displayId }?.brightness
        _uiState.update { state ->
            val updatedDisplays = state.displays.map { display ->
                if (display.id == displayId) display.copy(brightness = normalized) else display
            }
            state.copy(
                displays = updatedDisplays,
                globalBrightness = calculateGlobalBrightness(updatedDisplays, state.globalBrightness)
            )
        }
        if (previous != null && previous != normalized) {
            submitDisplayBrightness(displayId = displayId, value = normalized)
        }
    }

    fun onDisplayBrightnessChangeFinished(displayId: Long) {
        val display = _uiState.value.displays.firstOrNull { it.id == displayId } ?: return
        if (lastSentDisplayBrightness[displayId] != display.brightness) {
            submitDisplayBrightness(displayId = displayId, value = display.brightness)
        }
    }

    fun onDisplayPowerToggle(displayId: Long, turnOn: Boolean) {
        if (_uiState.value.displays.firstOrNull { it.id == displayId }?.isBusy == true) {
            return
        }
        val currentRepository = repository ?: run {
            emitMessage("连接未建立")
            return
        }

        setDisplayBusy(displayId = displayId, isBusy = true)
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                if (turnOn) {
                    currentRepository.powerOn(displayId)
                } else {
                    currentRepository.powerOff(displayId)
                }
                loadDisplaysWithRetry(currentRepository)
            }.onSuccess { displays ->
                _uiState.update { state ->
                    state.copy(
                        connectionStatus = ConnectionStatus.Connected,
                        displays = displays,
                        globalBrightness = calculateGlobalBrightness(displays, state.globalBrightness)
                    )
                }
            }.onFailure { error ->
                setDisplayBusy(displayId = displayId, isBusy = false)
                emitError(error)
            }
        }
    }

    override fun onCleared() {
        cancelHostScan()
        super.onCleared()
    }

    private fun bootstrap() {
        viewModelScope.launch(ioDispatcher) {
            val savedSettings = settingsStore.read()
            if (savedSettings == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        connectionStatus = ConnectionStatus.Disconnected,
                        showSettingsDialog = true,
                        hasAutoScanRunForDialog = false
                    )
                }
                onScanHostsRequested(manual = false)
                return@launch
            }

            currentSettings = savedSettings
            _uiState.update {
                it.copy(
                    settingsDraft = savedSettings.toDraft(),
                    showSettingsDialog = false
                )
            }
            connectAndLoad(savedSettings, showLoading = true, showRefresh = false)
        }
    }

    private fun connectAndLoad(
        settings: ConnectionSettings,
        showLoading: Boolean,
        showRefresh: Boolean
    ) {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.Connecting,
                    isLoading = showLoading,
                    isRefreshing = showRefresh
                )
            }

            runCatching {
                val createdRepository = repositoryFactory(settings)
                createdRepository.health()
                val displays = loadDisplaysWithRetry(createdRepository)
                createdRepository to displays
            }.onSuccess { (createdRepository, displays) ->
                repository = createdRepository
                _uiState.update { state ->
                    state.copy(
                        connectionStatus = ConnectionStatus.Connected,
                        isLoading = false,
                        isRefreshing = false,
                        displays = displays,
                        globalBrightness = calculateGlobalBrightness(displays, state.globalBrightness)
                    )
                }
            }.onFailure { error ->
                repository = null
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.Disconnected,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
                emitError(error)
            }
        }
    }

    private fun updateSettingsDraft(draft: ConnectionSettingsDraft) {
        _uiState.update {
            it.copy(
                settingsDraft = draft,
                settingsValidation = ConnectionSettingsValidation(),
                scanErrorMessage = null
            )
        }
    }

    private fun submitGlobalBrightness(value: Int) {
        lastSentGlobalBrightness = value
        viewModelScope.launch(ioDispatcher) {
            performGlobalBrightness(value)
        }
    }

    private fun submitDisplayBrightness(displayId: Long, value: Int) {
        lastSentDisplayBrightness[displayId] = value
        viewModelScope.launch(ioDispatcher) {
            performDisplayBrightness(displayId = displayId, value = value)
        }
    }

    private suspend fun performGlobalBrightness(value: Int) {
        val currentRepository = repository ?: return
        try {
            val displays = currentRepository.setAllBrightness(value)
                .asSequence()
                .filterNot { it.isDummy }
                .map { it.toUiModel() }
                .toList()
            _uiState.update { state ->
                if (state.globalBrightness != value) {
                    return@update state
                }
                state.copy(
                    connectionStatus = ConnectionStatus.Connected,
                    displays = displays,
                    globalBrightness = calculateGlobalBrightness(displays, state.globalBrightness)
                )
            }
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            emitError(error)
        }
    }

    private suspend fun performDisplayBrightness(displayId: Long, value: Int) {
        val currentRepository = repository ?: return
        try {
            val updatedDisplay = currentRepository
                .setBrightness(displayId = displayId, value = value)
                .toUiModel()
            _uiState.update { state ->
                val currentBrightness = state.displays.firstOrNull { it.id == displayId }?.brightness
                if (currentBrightness != value) {
                    return@update state
                }
                val displays = state.displays.map { display ->
                    if (display.id == displayId) updatedDisplay else display
                }
                state.copy(
                    connectionStatus = ConnectionStatus.Connected,
                    displays = displays,
                    globalBrightness = calculateGlobalBrightness(displays, state.globalBrightness)
                )
            }
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            emitError(error)
        }
    }

    private suspend fun loadDisplays(
        repository: MonitorControlRepositoryContract
    ): List<DisplayUiModel> {
        return repository.displays()
            .asSequence()
            .filterNot { it.isDummy }
            .map { it.toUiModel() }
            .toList()
    }

    private suspend fun loadDisplaysWithRetry(
        repository: MonitorControlRepositoryContract
    ): List<DisplayUiModel> {
        var attempt = 0
        while (true) {
            try {
                return loadDisplays(repository)
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                val isServerTimeoutBadRequest = error is MonitorControlApiException &&
                    error.httpCode == 400 &&
                    error.apiCode == "bad_request" &&
                    error.message.contains("request timeout", ignoreCase = true)
                if (isServerTimeoutBadRequest && attempt == 0) {
                    attempt += 1
                    delay(120)
                    continue
                }
                throw error
            }
        }
    }

    private fun handleScanSuccess(results: List<ScannedHostCandidate>) {
        _uiState.update { state ->
            if (!state.showSettingsDialog) {
                return@update state
            }

            when (results.size) {
                0 -> state.copy(
                    isScanningHosts = false,
                    scanCandidates = emptyList(),
                    showScanResultPicker = false,
                    scanErrorMessage = "未扫描到可用主机，请确认同一局域网且 Mac 已启用 Remote HTTP API"
                )

                1 -> {
                    val host = results.first().host
                    state.copy(
                        isScanningHosts = false,
                        settingsDraft = state.settingsDraft.copy(host = host),
                        scanCandidates = results,
                        showScanResultPicker = false,
                        scanErrorMessage = null
                    )
                }

                else -> state.copy(
                    isScanningHosts = false,
                    scanCandidates = results,
                    showScanResultPicker = true,
                    scanErrorMessage = null
                )
            }
        }

        when (results.size) {
            1 -> emitMessage("已自动填充主机地址：${results.first().host}")
            0 -> Unit
            else -> emitMessage("扫描到多个主机，请选择目标设备")
        }
    }

    private fun cancelHostScan() {
        scanJob?.cancel()
        scanJob = null
    }

    private fun setDisplayBusy(displayId: Long, isBusy: Boolean) {
        _uiState.update { state ->
            state.copy(
                displays = state.displays.map { display ->
                    if (display.id == displayId) {
                        display.copy(isBusy = isBusy)
                    } else {
                        display
                    }
                }
            )
        }
    }

    private fun emitError(error: Throwable) {
        when {
            error is MonitorControlApiException && error.httpCode in listOf(401, 503) -> {
                _uiState.update { it.copy(connectionStatus = ConnectionStatus.Disconnected) }
            }
            error is IOException && error !is MonitorControlApiException -> {
                _uiState.update { it.copy(connectionStatus = ConnectionStatus.Disconnected) }
            }
        }
        emitMessage(mapErrorMessage(error))
    }

    private fun emitMessage(message: String) {
        _messages.tryEmit(message)
    }

    private fun mapErrorMessage(error: Throwable): String {
        val apiError = error as? MonitorControlApiException
        if (apiError != null) {
            return when {
                apiError.httpCode == 401 -> "Token 无效，请检查连接配置"
                apiError.httpCode == 503 -> "服务暂不可用，请稍后重试"
                apiError.httpCode == 400 &&
                    apiError.apiCode == "bad_request" &&
                    apiError.message.contains("request timeout", ignoreCase = true) -> "请求过于频繁，请稍慢拖动亮度滑杆"
                apiError.message.isNotBlank() -> apiError.message
                else -> "请求失败（${apiError.httpCode}）"
            }
        }

        return if (error is IOException) {
            "连接失败，请确认主机地址与网络状态"
        } else {
            "操作失败，请稍后再试"
        }
    }

    private fun calculateGlobalBrightness(displays: List<DisplayUiModel>, fallback: Int): Int {
        val brightnessValues = displays
            .filter { it.canControlBrightness }
            .map { it.brightness }
        if (brightnessValues.isEmpty()) {
            return fallback
        }
        return brightnessValues.average().roundToInt().coerceIn(0, 100)
    }

    companion object {
        fun provideFactory(
            settingsStore: ConnectionSettingsStoreDataSource
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(settingsStore = settingsStore) as T
                }
            }
        }

        fun createDefaultSettingsStore(applicationContext: android.content.Context): ConnectionSettingsStore {
            return ConnectionSettingsStore.from(applicationContext)
        }
    }
}

private fun defaultRepositoryFactory(
    settings: ConnectionSettings
): MonitorControlRepositoryContract {
    val (api, moshi) = MonitorControlNetworkFactory.create(
        baseUrl = settings.baseUrl(),
        tokenProvider = { settings.token }
    )
    return MonitorControlRepository(api = api, moshi = moshi)
}

private fun DisplayStatus.toUiModel(): DisplayUiModel {
    return DisplayUiModel(
        id = id,
        name = if (friendlyName.isBlank()) name else friendlyName,
        brightness = brightness.coerceIn(0, 100),
        powerOn = !powerState.equals("off", ignoreCase = true),
        canControlBrightness = capabilities.brightness,
        canControlPower = capabilities.power,
        isVirtual = isVirtual
    )
}
