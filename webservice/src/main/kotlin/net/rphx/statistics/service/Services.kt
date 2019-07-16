@file:UseExperimental(KtorExperimentalLocationsAPI::class)

package net.rphx.statistics.service

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.post
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import net.rphx.statistics.apiPrefix
import net.rphx.statistics.controller.TransactionWatch

@Location("$apiPrefix/transactions")
class transactions

@Location("$apiPrefix/statistics")
class statistics


@KtorExperimentalLocationsAPI
fun Route.transactions(transactions: TransactionWatch) {

    get<statistics> {
        call.respond(transactions.getLastMinuteStats())
    }

    post<transactions> {
        val (amount, timestamp) = call.receive<TransactionRequest>()
        transactions.aggregateTransaction(amount, timestamp)
        call.respond(HttpStatusCode.NoContent, "")
    }
}

data class TransactionRequest(val amount: Double, val timestamp: Long)