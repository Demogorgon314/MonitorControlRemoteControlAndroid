package com.demogorgon314.monitorcontrolremotecontrolandroid.data.scan

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections

class DefaultMonitorControlHostScannerTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `scan should match health signature`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok","version":"v1"}""")
        )
        val scanner = DefaultMonitorControlHostScanner(
            port = server.port,
            candidateHostResolver = { listOf("127.0.0.1") }
        )

        val result = scanner.scan(token = "token")

        assertEquals(1, result.size)
        assertEquals("127.0.0.1", result.first().host)
        assertEquals(ScanMatchKind.HEALTH_OK, result.first().matchKind)
    }

    @Test
    fun `scan should match unauthorized signature`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"unauthorized","message":"invalid token"}}""")
        )
        val scanner = DefaultMonitorControlHostScanner(
            port = server.port,
            candidateHostResolver = { listOf("127.0.0.1") }
        )

        val result = scanner.scan(token = "")

        assertEquals(1, result.size)
        assertEquals(ScanMatchKind.UNAUTHORIZED_SIGNATURE, result.first().matchKind)
    }

    @Test
    fun `scan should ignore non monitorcontrol response`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":"forbidden","message":"x"}}""")
        )
        val scanner = DefaultMonitorControlHostScanner(
            port = server.port,
            candidateHostResolver = { listOf("127.0.0.1") }
        )

        val result = scanner.scan(token = "")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `scan should dedupe candidates and sort by kind then latency`() = runTest {
        val probedHosts = Collections.synchronizedSet(mutableSetOf<String>())
        val scanner = DefaultMonitorControlHostScanner(
            candidateHostResolver = { listOf("a", "b", "a", "c") },
            probeOverride = { host, _, _ ->
                probedHosts += host
                when (host) {
                    "a" -> ScannedHostCandidate("a", 20, ScanMatchKind.UNAUTHORIZED_SIGNATURE)
                    "b" -> ScannedHostCandidate("b", 35, ScanMatchKind.HEALTH_OK)
                    "c" -> ScannedHostCandidate("c", 10, ScanMatchKind.HEALTH_OK)
                    else -> null
                }
            }
        )

        val result = scanner.scan(token = "")

        assertEquals(setOf("a", "b", "c"), probedHosts)
        assertEquals(listOf("c", "b", "a"), result.map { it.host })
    }
}
