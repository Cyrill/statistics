package net.rphx.statistics

import io.ktor.application.Application
import io.ktor.config.MapApplicationConfig
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.rphx.statistics.controller.TransactionClock
import net.rphx.statistics.controller.TransactionWatch
import net.rphx.statistics.controller.TransactionWatchImpl
import net.rphx.statistics.controller.convertToLocalDateTime
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

@UseExperimental(KtorExperimentalAPI::class)
fun testApp(callback: TestApplicationEngine.() -> Unit) {
    withTestApplication({
        applyConfig()
        mainModule()
    }, callback)
}

@UseExperimental(KtorExperimentalAPI::class)
fun testAppWithLogic(trxWatcher: TransactionWatch, callback: TestApplicationEngine.() -> Unit) {
    withTestApplication({
        applyConfig()
        module(trxWatcher)
    }, callback)
}

private fun Application.applyConfig() {
    (environment.config as MapApplicationConfig).apply {
        // Set here the properties
        put("ktor.environment", "dev")
    }
}


fun fakeTransactionWatch(nowTime: Long): TransactionWatch {
    return fakeTransactionWatch(FakeClock(AtomicLong(nowTime)))
}

fun fakeTransactionWatch(clock: TransactionClock): TransactionWatch {
    return TransactionWatchImpl(CoroutineScope(Dispatchers.Default), clock)
}

class FakeClock(var fixedTime: AtomicLong) : TransactionClock {
    override fun now(): LocalDateTime {
        return convertToLocalDateTime(fixedTime.get())
    }

}