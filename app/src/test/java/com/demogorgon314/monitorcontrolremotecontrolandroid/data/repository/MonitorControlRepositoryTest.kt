package com.demogorgon314.monitorcontrolremotecontrolandroid.data.repository

import com.demogorgon314.monitorcontrolremotecontrolandroid.data.remote.MonitorControlNetworkFactory
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MonitorControlRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: MonitorControlRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val (api, moshi) = MonitorControlNetworkFactory.create(
            baseUrl = server.url("/").toString(),
            tokenProvider = { TOKEN },
            enableLogging = false
        )
        repository = MonitorControlRepository(api = api, moshi = moshi)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `displays should parse payload and send auth header`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "displays": [
                        {
                          "id": 11,
                          "name": "LG",
                          "friendlyName": "LG UltraWide",
                          "type": "other",
                          "isVirtual": false,
                          "isDummy": false,
                          "brightness": 68,
                          "powerState": "on",
                          "capabilities": {
                            "brightness": true,
                            "power": true
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val displays = repository.displays()

        assertEquals(1, displays.size)
        assertEquals(11L, displays.first().id)
        assertEquals("LG UltraWide", displays.first().friendlyName)

        val request = server.takeRequest()
        assertEquals("/api/v1/displays", request.path)
        assertEquals("Bearer $TOKEN", request.getHeader("Authorization"))
    }

    @Test
    fun `setBrightness should send expected endpoint and body`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "display": {
                        "id": 7,
                        "name": "Dell",
                        "friendlyName": "Dell P2722H",
                        "type": "other",
                        "isVirtual": false,
                        "isDummy": false,
                        "brightness": 60,
                        "powerState": "on",
                        "capabilities": {
                          "brightness": true,
                          "power": true
                        }
                      }
                    }
                    """.trimIndent()
                )
        )

        repository.setBrightness(displayId = 7L, value = 60)

        val request = server.takeRequest()
        assertEquals("/api/v1/displays/7/brightness", request.path)
        assertEquals("POST", request.method)
        assertEquals("{\"value\":60}", request.body.readUtf8())
    }

    @Test
    fun `setVolume should send expected endpoint and body`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "display": {
                        "id": 7,
                        "name": "Dell",
                        "friendlyName": "Dell P2722H",
                        "type": "other",
                        "isVirtual": false,
                        "isDummy": false,
                        "brightness": 60,
                        "volume": 35,
                        "powerState": "on",
                        "capabilities": {
                          "brightness": true,
                          "volume": true,
                          "power": true
                        }
                      }
                    }
                    """.trimIndent()
                )
        )

        repository.setVolume(displayId = 7L, value = 35)

        val request = server.takeRequest()
        assertEquals("/api/v1/displays/7/volume", request.path)
        assertEquals("POST", request.method)
        assertEquals("{\"value\":35}", request.body.readUtf8())
    }

    @Test
    fun `empty successful body should throw api exception`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        try {
            repository.health()
            throw AssertionError("expected MonitorControlApiException")
        } catch (error: MonitorControlApiException) {
            assertEquals(200, error.httpCode)
            assertEquals("empty_body", error.apiCode)
        }
    }

    @Test
    fun `error envelope should be parsed with display ids`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "error": {
                        "code": "unsupported_operation",
                        "message": "operation not supported",
                        "displayIds": [3, 9]
                      }
                    }
                    """.trimIndent()
                )
        )

        try {
            repository.powerOffAll()
            throw AssertionError("expected MonitorControlApiException")
        } catch (error: MonitorControlApiException) {
            assertEquals(409, error.httpCode)
            assertEquals("unsupported_operation", error.apiCode)
            assertEquals("operation not supported", error.message)
            assertNotNull(error.displayIds)
            assertEquals(listOf(3L, 9L), error.displayIds)
        }
    }

    @Test
    fun `unknown error body should fallback to http status message`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .addHeader("Content-Type", "text/plain")
                .setBody("internal error")
        )

        try {
            repository.displays()
            throw AssertionError("expected MonitorControlApiException")
        } catch (error: MonitorControlApiException) {
            assertEquals(500, error.httpCode)
            assertTrue(error.message.contains("HTTP 500"))
        }
    }

    @Test
    fun `transient connection reset should retry once`() = runTest {
        server.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "displays": [
                        {
                          "id": 21,
                          "name": "BenQ",
                          "friendlyName": "BenQ SW270C",
                          "type": "other",
                          "isVirtual": false,
                          "isDummy": false,
                          "brightness": 50,
                          "powerState": "on",
                          "capabilities": {
                            "brightness": true,
                            "power": true
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
        )

        val displays = repository.displays()

        assertEquals(1, displays.size)
        assertEquals("BenQ SW270C", displays.first().friendlyName)
        assertEquals(2, server.requestCount)
    }

    companion object {
        private const val TOKEN = "test-token"
    }
}
