package com.demogorgon314.monitorcontrolremotecontrolandroid.data.repository

import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.AllPowerResponse
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.ApiErrorEnvelope
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.BrightnessRequest
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.DisplayStatus
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.HealthResponse
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.MonitorControlApi
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.PowerRequest
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.SinglePowerResponse
import com.squareup.moshi.Moshi
import kotlinx.coroutines.delay
import retrofit2.Response
import java.net.ConnectException
import java.io.EOFException
import java.io.IOException
import java.net.SocketException

interface MonitorControlRepositoryContract {
    suspend fun health(): HealthResponse
    suspend fun displays(): List<DisplayStatus>
    suspend fun setBrightness(displayId: Long, value: Int): DisplayStatus
    suspend fun setAllBrightness(value: Int): List<DisplayStatus>
    suspend fun setVolume(displayId: Long, value: Int): DisplayStatus
    suspend fun setAllVolume(value: Int): List<DisplayStatus>
    suspend fun powerOff(displayId: Long): SinglePowerResponse
    suspend fun powerOn(displayId: Long): SinglePowerResponse
    suspend fun powerOffAll(): AllPowerResponse
    suspend fun powerOnAll(): AllPowerResponse
}

class MonitorControlApiException(
    val httpCode: Int,
    val apiCode: String?,
    override val message: String,
    val displayIds: List<Long>? = null
) : IOException(message)

class MonitorControlRepository(
    private val api: MonitorControlApi,
    private val moshi: Moshi
) : MonitorControlRepositoryContract {
    private val errorAdapter = moshi.adapter(ApiErrorEnvelope::class.java)

    override suspend fun health(): HealthResponse = execute { api.health() }

    override suspend fun displays(): List<DisplayStatus> = execute { api.getDisplays() }.displays

    override suspend fun setBrightness(displayId: Long, value: Int): DisplayStatus {
        return execute { api.setDisplayBrightness(displayId, BrightnessRequest(value)) }.display
    }

    override suspend fun setAllBrightness(value: Int): List<DisplayStatus> {
        return execute { api.setAllBrightness(BrightnessRequest(value)) }.displays
    }

    override suspend fun setVolume(displayId: Long, value: Int): DisplayStatus {
        return execute { api.setDisplayVolume(displayId, BrightnessRequest(value)) }.display
    }

    override suspend fun setAllVolume(value: Int): List<DisplayStatus> {
        return execute { api.setAllVolume(BrightnessRequest(value)) }.displays
    }

    override suspend fun powerOff(displayId: Long): SinglePowerResponse {
        return execute { api.setDisplayPower(displayId, PowerRequest("off")) }
    }

    override suspend fun powerOn(displayId: Long): SinglePowerResponse {
        return execute { api.setDisplayPower(displayId, PowerRequest("on")) }
    }

    override suspend fun powerOffAll(): AllPowerResponse {
        return execute { api.setAllPower(PowerRequest("off")) }
    }

    override suspend fun powerOnAll(): AllPowerResponse {
        return execute { api.setAllPower(PowerRequest("on")) }
    }

    private suspend fun <T> execute(call: suspend () -> Response<T>): T {
        val response = executeWithRetry(call)
        return response.unwrap()
    }

    private suspend fun <T> executeWithRetry(call: suspend () -> Response<T>): Response<T> {
        var lastNetworkError: IOException? = null

        repeat(2) { attempt ->
            try {
                return call()
            } catch (_: EOFException) {
                throw MonitorControlApiException(
                    httpCode = 200,
                    apiCode = "empty_body",
                    message = "Response body is empty"
                )
            } catch (error: IOException) {
                lastNetworkError = error
                val canRetry = attempt == 0 && error.isTransientNetworkFailure()
                if (canRetry) {
                    delay(120)
                    return@repeat
                }
                throw error
            }
        }

        throw lastNetworkError ?: IOException("network request failed")
    }

    private fun <T> Response<T>.unwrap(): T {
        if (isSuccessful) {
            val parsedBody = try {
                body()
            } catch (_: Exception) {
                null
            }

            return parsedBody ?: throw MonitorControlApiException(
                httpCode = code(),
                apiCode = "empty_body",
                message = "Response body is empty"
            )
        }

        val raw = errorBody()?.string().orEmpty()
        val parsed = runCatching { errorAdapter.fromJson(raw) }.getOrNull()?.error
        throw MonitorControlApiException(
            httpCode = code(),
            apiCode = parsed?.code,
            message = parsed?.message ?: "HTTP ${code()} ${message()}",
            displayIds = parsed?.displayIds
        )
    }
}

private fun IOException.isTransientNetworkFailure(): Boolean {
    if (this is ConnectException) {
        return true
    }

    if (this is SocketException) {
        val messageText = message.orEmpty()
        return messageText.contains("Connection reset", ignoreCase = true) ||
            messageText.contains("Broken pipe", ignoreCase = true)
    }

    val messageText = message.orEmpty()
    return messageText.contains("unexpected end of stream", ignoreCase = true) ||
        messageText.contains("stream was reset", ignoreCase = true)
}
