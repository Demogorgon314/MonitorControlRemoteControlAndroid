package com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface MonitorControlApi {
    @GET("api/v1/health")
    suspend fun health(): Response<HealthResponse>

    @GET("api/v1/displays")
    suspend fun getDisplays(): Response<DisplaysResponse>

    @POST("api/v1/displays/{id}/brightness")
    suspend fun setDisplayBrightness(
        @Path("id") displayId: Long,
        @Body body: BrightnessRequest
    ): Response<SingleDisplayResponse>

    @POST("api/v1/displays/brightness")
    suspend fun setAllBrightness(
        @Body body: BrightnessRequest
    ): Response<DisplaysResponse>

    @POST("api/v1/displays/{id}/power")
    suspend fun setDisplayPower(
        @Path("id") displayId: Long,
        @Body body: PowerRequest
    ): Response<SinglePowerResponse>

    @POST("api/v1/displays/power")
    suspend fun setAllPower(
        @Body body: PowerRequest
    ): Response<AllPowerResponse>
}
