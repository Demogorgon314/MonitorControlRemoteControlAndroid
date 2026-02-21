package com.demogorgon314.monitorcontrolremotecontrolandroid.data.scan

import com.demogorgon314.monitorcontrolremotecontrolandroid.data.local.ConnectionSettingsValidator
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.ApiErrorEnvelope
import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.HealthResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

interface MonitorControlHostScanner {
    suspend fun scan(
        token: String,
        preferredHost: String? = null
    ): List<ScannedHostCandidate>
}

data class ScannedHostCandidate(
    val host: String,
    val latencyMs: Long,
    val matchKind: ScanMatchKind
)

enum class ScanMatchKind {
    HEALTH_OK,
    UNAUTHORIZED_SIGNATURE
}

class DefaultMonitorControlHostScanner(
    private val port: Int = ConnectionSettingsValidator.DEFAULT_PORT,
    private val maxConcurrency: Int = 64,
    private val candidateHostResolver: (String?) -> List<String> = ::buildCandidateHosts,
    private val probeOverride: (suspend (host: String, token: String, port: Int) -> ScannedHostCandidate?)? = null
) : MonitorControlHostScanner {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val healthAdapter = moshi.adapter(HealthResponse::class.java)
    private val errorAdapter = moshi.adapter(ApiErrorEnvelope::class.java)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(250, TimeUnit.MILLISECONDS)
        .readTimeout(250, TimeUnit.MILLISECONDS)
        .callTimeout(500, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    override suspend fun scan(token: String, preferredHost: String?): List<ScannedHostCandidate> {
        val targetHosts = candidateHostResolver(preferredHost)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        if (targetHosts.isEmpty()) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            val semaphore = Semaphore(maxConcurrency)
            coroutineScope {
                targetHosts.map { host ->
                    async {
                        semaphore.withPermit {
                            probe(host = host, token = token)
                        }
                    }
                }.awaitAll()
                    .filterNotNull()
            }.sortedWith(
                compareBy<ScannedHostCandidate>(
                    { it.matchKind.priority() },
                    { it.latencyMs },
                    { it.host }
                )
            )
        }
    }

    private suspend fun probe(
        host: String,
        token: String
    ): ScannedHostCandidate? {
        probeOverride?.let { override ->
            return override(host, token, port)
        }

        val requestBuilder = Request.Builder()
            .url("http://$host:$port/api/v1/health")
            .header("Accept", "application/json")
            .get()
        if (token.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${token.trim()}")
        }

        val startNs = System.nanoTime()
        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val latencyMs = ((System.nanoTime() - startNs) / 1_000_000.0).roundToLong()
                val bodyText = response.body?.string().orEmpty()
                when {
                    response.code == 200 && isHealthSignature(bodyText) -> {
                        ScannedHostCandidate(
                            host = host,
                            latencyMs = latencyMs,
                            matchKind = ScanMatchKind.HEALTH_OK
                        )
                    }

                    response.code == 401 && isUnauthorizedEnvelope(bodyText) -> {
                        ScannedHostCandidate(
                            host = host,
                            latencyMs = latencyMs,
                            matchKind = ScanMatchKind.UNAUTHORIZED_SIGNATURE
                        )
                    }

                    else -> null
                }
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun isHealthSignature(rawBody: String): Boolean {
        val parsed = runCatching { healthAdapter.fromJson(rawBody) }.getOrNull() ?: return false
        return parsed.status.equals("ok", ignoreCase = true) &&
            parsed.version.equals("v1", ignoreCase = true)
    }

    private fun isUnauthorizedEnvelope(rawBody: String): Boolean {
        val parsed = runCatching { errorAdapter.fromJson(rawBody) }.getOrNull()?.error ?: return false
        return parsed.code.equals("unauthorized", ignoreCase = true)
    }
}

private fun buildCandidateHosts(preferredHost: String?): List<String> {
    val orderedHosts = LinkedHashSet<String>()

    normalizeHost(preferredHost)?.let { orderedHosts.add(it) }
    orderedHosts.add("10.0.2.2")

    val localHosts = LinkedHashSet<String>()
    val prefixes = LinkedHashSet<String>()

    val interfaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }
        .getOrElse { emptyList() }
    for (networkInterface in interfaces) {
        val isUsable = runCatching { networkInterface.isUp && !networkInterface.isLoopback }.getOrDefault(false)
        if (!isUsable) {
            continue
        }
        val addresses = Collections.list(networkInterface.inetAddresses)
        for (address in addresses) {
            if (address !is Inet4Address || !address.isSiteLocalAddress || address.isLoopbackAddress) {
                continue
            }
            val host = address.hostAddress ?: continue
            localHosts.add(host)
            val pieces = host.split('.')
            if (pieces.size == 4) {
                prefixes.add("${pieces[0]}.${pieces[1]}.${pieces[2]}")
            }
        }
    }

    for (prefix in prefixes.sorted()) {
        for (index in 1..254) {
            val host = "$prefix.$index"
            if (host !in localHosts) {
                orderedHosts.add(host)
            }
        }
    }

    return orderedHosts.toList()
}

private fun normalizeHost(raw: String?): String? {
    if (raw.isNullOrBlank()) {
        return null
    }

    var candidate = raw.trim()
    candidate = candidate.removePrefix("http://").removePrefix("https://")
    candidate = candidate.substringBefore('/')
    candidate = candidate.substringBefore(':')

    val pieces = candidate.split('.')
    if (pieces.size != 4) {
        return null
    }
    val allInRange = pieces.all { piece ->
        val value = piece.toIntOrNull() ?: return@all false
        value in 0..255
    }
    return if (allInRange) candidate else null
}

private fun ScanMatchKind.priority(): Int = when (this) {
    ScanMatchKind.HEALTH_OK -> 0
    ScanMatchKind.UNAUTHORIZED_SIGNATURE -> 1
}
