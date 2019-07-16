package net.rphx.statistics.controller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.TickerMode
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.Comparator
import kotlin.collections.ArrayList

class TransactionWatchImpl(private val scope: CoroutineScope,
                           private val clock: TransactionClock = SimpleTransactionClock(),
                           private val timeCapacitySeconds: Long = 60) : TransactionWatch {

    private val transactionHolder = TransactionsHolder(clock, timeCapacitySeconds)

    private val transactionsChannel = Channel<Transaction>()
    private val statsChannel = Channel<TransactionStats>(capacity = Channel.CONFLATED)
    //500ms should be enough to clean up old events since we care only about last 60sec
    private val tickerChannel = ticker(delayMillis = 500, initialDelayMillis = 500, mode = TickerMode.FIXED_DELAY)

    private var lastStats: AtomicReference<TransactionStats> = AtomicReference(TransactionStats.empty())

    init {
        scope.launch {
            while (isActive) {
                transactionHolder.selectChannel(transactionsChannel, tickerChannel, statsChannel)
            }
        }
        scope.launch {
            while (isActive) {
                lastStats.set(statsChannel.receive())
            }
        }
    }

    /**
     * Validate datetime parameters
     */
    private fun buildLocalDateTime(source: Long, clock: TransactionClock): LocalDateTime {
        //we don't want to deal with negative or zero timestamp
        require(source > 0)
        val converted = convertToLocalDateTime(source)
        //also let's reduce the logic load and cut off old transactions here
        val now = clock.now()
        require(converted.isAfter(now.minusSeconds(timeCapacitySeconds)) && converted.isBefore(now))
        return converted
    }

    override suspend fun aggregateTransaction(amount: Double, timestamp: Long) {
        transactionsChannel.send(Transaction(amount, buildLocalDateTime(timestamp, clock)))
    }

    override suspend fun getLastMinuteStats(): TransactionStats {
        return lastStats.get()
    }

    override fun close() {
        scope.cancel()
        tickerChannel.cancel()
        statsChannel.close()
        transactionsChannel.close()
    }
}


class TransactionsHolder(private val clock: TransactionClock,
                         private val timeCapacitySeconds: Long) {
    private val log: Logger = LoggerFactory.getLogger("TransactionsHolder")

    private val lastTransactions = PriorityQueue<Transaction>(Comparator.comparing(Transaction::time))
    private var sum: Double = 0.0
    private var min: Double = Double.POSITIVE_INFINITY
    private var max: Double = Double.NEGATIVE_INFINITY

    private fun resetState() {
        sum = 0.0
        min = Double.POSITIVE_INFINITY
        max = Double.NEGATIVE_INFINITY
    }

    suspend fun selectChannel(trxChannel: ReceiveChannel<Transaction>,
                              timerChannel: ReceiveChannel<Unit>,
                              stats: SendChannel<TransactionStats>) {
        select<Unit> {
            // <Unit> means that this select expression does not produce any result
            trxChannel.onReceive { trx ->
                lastTransactions.add(trx)
                onAdded(trx, stats)
            }
            timerChannel.onReceive { _ ->
                val threshold = clock.now().minusSeconds(timeCapacitySeconds)
                val removed = lastTransactions.removeWhile { it.time.isBefore(threshold) }
                if (removed.isNotEmpty()) {
                    onRemoved(removed, stats)
                }
            }
        }
    }

    private suspend fun onAdded(transaction: Transaction, stats: SendChannel<TransactionStats>) {
        processTransaction(transaction)
        postNewStats(stats)
    }

    private suspend fun onRemoved(transactions: List<Transaction>, stats: SendChannel<TransactionStats>) {
        if (log.isDebugEnabled) {
            log.debug("removed $transactions, empty: ${lastTransactions.isEmpty()}")
        }
        if (lastTransactions.isEmpty()) {
            //reset local state
            resetState()
            stats.send(TransactionStats.empty())
            return
        }
        //fast track. Let's hope we can save on recalculations
        var updateBounds = false
        transactions.forEach {
            sum -= it.amount
            updateBounds = updateBounds || (it.amount == min || it.amount == max)
        }
        //slow track
        //since we do not store any extra information to restore next minimum/maximum,
        //we have to find them again in case one has been evicted
        if (updateBounds) {
            resetState()
            lastTransactions.forEach {
                //Well, in most cases it's enough to have the sum calculated earlier.
                //But since double is not a 100% precise type we can end up suffering from an error.
                //There exist algorithms to minimize the total error, but they don't fix
                //the issue, only move it further away. However having a better understanding
                //of the nature of the data may help to select the most suitable one.
                //Here for simplicity sake we just refresh the sum in case one of the bounds has been invalidated
                processTransaction(it)
            }
        }
        postNewStats(stats)
    }

    private fun processTransaction(transaction: Transaction) {
        if (min > transaction.amount) {
            min = transaction.amount
        }
        if (max < transaction.amount) {
            max = transaction.amount
        }
        sum += transaction.amount
    }

    private suspend fun postNewStats(stats: SendChannel<TransactionStats>) {
        stats.send(TransactionStats(sum, sum / lastTransactions.size, max, min, lastTransactions.size.toLong()))
    }
}

class SimpleTransactionClock : TransactionClock {
    override fun now(): LocalDateTime {
        return LocalDateTime.now()
    }
}

fun convertToLocalDateTime(source: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(source), TimeZone.getDefault().toZoneId())

inline fun <T> PriorityQueue<T>.removeWhile(predicate: (T) -> Boolean): List<T> {
    val iterator = iterator()
    val list = ArrayList<T>()
    while (iterator.hasNext()) {
        val next = iterator.next()
        if (predicate(next)) {
            list.add(next)
            iterator.remove()
        }
    }
    return list
}