package net.rphx.statistics

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import kotlin.test.Test
import kotlin.test.assertEquals

class ServicesTest {

    @Test
    fun testGetEmptyStatisticsWorks() = testApp {
        getStatisticsRequest()
                .apply {
                    assertEquals(200, response.status()?.value)
                    assertEquals("""{"sum":0.0,"avg":0.0,"max":0.0,"min":0.0,"count":0}""", response.content)
                }
    }

    @Test
    fun testPostValidationWorksWithInvalidInput() = testApp {
        createTransactionRequest("""{"number":2.5}""")
                .apply {
                    assertEquals(400, response.status()?.value)
                }
        createTransactionRequest("""{"amount":2.5}""")
                .apply {
                    assertEquals(400, response.status()?.value)
                }
    }

    //Just a timestamp off the top of my head. What we need is just a fixed timestamp
    private val requestTime = 1563223832688

    @Test
    fun testPostValidationWorksWithValidInput() = testAppWithLogic(fakeTransactionWatch(requestTime + 100)) {

        createTransactionRequest("""{"amount":2.5,"timestamp":$requestTime}""")
                .apply {
                    assertEquals(204, response.status()?.value)
                }
        createTransactionRequest("""{"amount":0,"timestamp":$requestTime}""")
                .apply {
                    assertEquals(204, response.status()?.value)
                }
    }

    @Test
    fun testPostValidationWorksWithInvalidTime() = testAppWithLogic(fakeTransactionWatch(requestTime + 100)) {
        createTransactionRequest("""{"amount":0,"timestamp":${requestTime + 101}}""")
                .apply {
                    assertEquals(400, response.status()?.value)
                }
    }
}

fun TestApplicationEngine.createTransactionRequest(body: String): TestApplicationCall {
    return handleRequest(HttpMethod.Post, "/transactions") {
        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(body)
    }
}

fun TestApplicationEngine.getStatisticsRequest(): TestApplicationCall {
    return handleRequest(HttpMethod.Get, "/statistics") {
        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}