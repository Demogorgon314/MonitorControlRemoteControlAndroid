package com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote

data class HealthResponse(
    val status: String,
    val version: String
)

data class DisplayCapabilities(
    val brightness: Boolean,
    val volume: Boolean = false,
    val power: Boolean
)

data class DisplayStatus(
    val id: Long,
    val name: String,
    val friendlyName: String,
    val type: String,
    val isVirtual: Boolean,
    val isDummy: Boolean,
    val brightness: Int,
    val volume: Int? = null,
    val powerState: String,
    val capabilities: DisplayCapabilities
)

data class DisplaysResponse(
    val displays: List<DisplayStatus>
)

data class SingleDisplayResponse(
    val display: DisplayStatus
)

data class BrightnessRequest(
    val value: Int
)

data class PowerRequest(
    val state: String
)

data class SinglePowerResponse(
    val displayId: Long,
    val requestedState: String,
    val accepted: Boolean
)

data class AllPowerResponse(
    val requestedState: String,
    val acceptedDisplayIds: List<Long>
)

data class ApiErrorEnvelope(
    val error: ApiError
)

data class ApiError(
    val code: String,
    val message: String,
    val displayIds: List<Long>? = null
)
