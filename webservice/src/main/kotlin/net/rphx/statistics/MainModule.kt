package net.rphx.statistics

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.application.Application
import io.ktor.application.ApplicationStopped
import io.ktor.application.application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Locations
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.rphx.statistics.controller.TransactionWatch
import net.rphx.statistics.controller.TransactionWatchImpl
import net.rphx.statistics.service.transactions
import java.time.DateTimeException

const val apiPrefix = ""

@KtorExperimentalLocationsAPI
fun Application.mainModule() {
    val transactionWatch: TransactionWatch = TransactionWatchImpl(CoroutineScope(Dispatchers.Default))
    environment.monitor.subscribe(ApplicationStopped) { transactionWatch.close() }
    module(transactionWatch)
}

@KtorExperimentalLocationsAPI
fun Application.module(transactionWatch: TransactionWatch) {
    install(DefaultHeaders)
    install(Locations)
    install(ContentNegotiation) {
        jackson {
            enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        }
    }
    install(StatusPages) {
        exception<JsonProcessingException> { cause ->
            application.log.error("JSON parsing error", cause)
            call.respond(HttpStatusCode.BadRequest, "Incorrect request")
        }
        exception<DateTimeException> { cause ->
            application.log.error("Invalid date format", cause)
            call.respond(HttpStatusCode.BadRequest, "Incorrect date format")
        }
        exception<IllegalArgumentException> { cause ->
            application.log.error("Invalid request format", cause)
            call.respond(HttpStatusCode.BadRequest, "Date is too old")
        }
        exception<Throwable> { cause ->
            application.log.error("Unrecognized error", cause)
            call.respond(HttpStatusCode.InternalServerError, "Server error")
        }
    }
    routing {
        //the fastest way to check our tiny test app is alive
        //definitely not going to be a part of prod
        if (isDev) {
            get("/") {
                call.respond(mapOf("OK" to true))
            }
        }
        transactions(transactionWatch)
    }
}

val Application.envKind get() = environment.config.property("ktor.environment").getString()
val Application.isDev get() = envKind == "dev"