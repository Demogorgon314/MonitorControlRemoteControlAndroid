package com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote

import com.demogorgon314.monitorcontrolremotecontrolandroid.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class BearerAuthInterceptor(
    private val tokenProvider: () -> String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val token = tokenProvider().trim()
        val requestBuilder = chain.request().newBuilder()
            .header("Accept", "application/json")

        if (token.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }
}

object MonitorControlNetworkFactory {
    fun create(
        baseUrl: String,
        tokenProvider: () -> String,
        enableLogging: Boolean = BuildConfig.DEBUG
    ): Pair<MonitorControlApi, Moshi> {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(BearerAuthInterceptor(tokenProvider))
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)

        if (enableLogging) {
            val logging = HttpLoggingInterceptor().apply {
                redactHeader("Authorization")
                level = HttpLoggingInterceptor.Level.BODY
            }
            clientBuilder.addInterceptor(logging)
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(clientBuilder.build())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(MonitorControlApi::class.java) to moshi
    }
}

private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"
