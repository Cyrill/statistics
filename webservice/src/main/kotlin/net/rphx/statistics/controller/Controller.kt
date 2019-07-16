package net.rphx.statistics.controller

import java.time.LocalDateTime

interface TransactionWatch {
    suspend fun getLastMinuteStats(): TransactionStats

    suspend fun aggregateTransaction(amount: Double, timestamp: Long)

    fun close()
}

data class TransactionStats(
        /**
         * the total sum of transaction value in the last 60sec
         */
        val sum: Double,
        /**
         * the average amount of transaction value in the last 60sec
         */
        val avg: Double,
        /**
         * single highest transaction value in the last 60sec
         */
        val max: Double,
        /**
         * single lowest transaction value in the last 60sec
         */
        val min: Double,
        /**
         * the total number of transactions happened in the last 60sec
         */
        val count: Long) {

    companion object Factory {
        fun empty(): TransactionStats = TransactionStats(0.0, 0.0, 0.0, 0.0, 0)
    }
}

data class Transaction(val amount: Double, val time: LocalDateTime)

interface TransactionClock {
    fun now(): LocalDateTime
}
