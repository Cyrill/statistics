package net.rphx.statistics

import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals


class SubmitTransactionsTest {
    private val requestTime = 1563223832688
    private lateinit var clock: FakeClock

    @Test
    fun testCreateNTransactions() = testAppWithLogic(fakeTransactionWatch(requestTime + 60 * 1000)) {
        //100 trxs with amount 1
        //100 trxs with amount 2
        //100 trxs with amount 3
        repeat(100) {
            createTransactionRequest("""{"amount":1,"timestamp":${requestTime + 1 + Random.nextInt(59 * 1000)}}""")
        }
        repeat(100) {
            createTransactionRequest("""{"amount":2,"timestamp":${requestTime + 1 + Random.nextInt(59 * 1000)}}""")
        }
        repeat(100) {
            createTransactionRequest("""{"amount":3,"timestamp":${requestTime + 1 + Random.nextInt(59 * 1000)}}""")
        }
        getStatisticsRequest()
                .apply {
                    assertEquals(200, response.status()?.value)
                    assertEquals("""{"sum":600.0,"avg":2.0,"max":3.0,"min":1.0,"count":300}""", response.content)
                }
    }

    @BeforeTest
    fun setClock() {
        clock = FakeClock(AtomicLong(requestTime + 60 * 1000))
    }

    @Test
    fun testEvictedTransactions() = testAppWithLogic(fakeTransactionWatch(clock)) {
        //100 trxs with amount 1
        //100 trxs with amount 2
        //100 trxs with amount 3

        //first 5sec
        repeat(100) {
            createTransactionRequest("""{"amount":1,"timestamp":${requestTime + 1 + Random.nextInt(5 * 1000)}}""")
        }
        //next 5 sec
        repeat(100) {
            createTransactionRequest("""{"amount":2,"timestamp":${requestTime + 1 + 5 * 1000 + Random.nextInt(5 * 1000)}}""")
        }
        //remaining time
        repeat(100) {
            createTransactionRequest("""{"amount":3,"timestamp":${requestTime + 1 + 10 * 1000 + Random.nextInt(49 * 1000)}}""")
        }
        getStatisticsRequest()
                .apply {
                    assertEquals(200, response.status()?.value)
                    assertEquals("""{"sum":600.0,"avg":2.0,"max":3.0,"min":1.0,"count":300}""", response.content)
                }
        clock.fixedTime.set(requestTime + 65 * 1000)
        //make ticker tick
        Thread.sleep(1000)
        getStatisticsRequest()
                .apply {
                    assertEquals(200, response.status()?.value)
                    assertEquals("""{"sum":500.0,"avg":2.5,"max":3.0,"min":2.0,"count":200}""", response.content)
                }

        clock.fixedTime.addAndGet(5 * 1000)
        //make ticker tick
        Thread.sleep(1000)
        getStatisticsRequest()
                .apply {
                    assertEquals(200, response.status()?.value)
                    assertEquals("""{"sum":300.0,"avg":3.0,"max":3.0,"min":3.0,"count":100}""", response.content)
                }

        clock.fixedTime.addAndGet(60 * 1000)
        //make ticker tick
        Thread.sleep(1000)
        getStatisticsRequest()
                .apply {
                    assertEquals(200, response.status()?.value)
                    assertEquals("""{"sum":0.0,"avg":0.0,"max":0.0,"min":0.0,"count":0}""", response.content)
                }
    }


    @Test
    fun testCanWeHaveOverflow() = testAppWithLogic(fakeTransactionWatch(requestTime + 60 * 1000)) {
        //With the knowledge of usecases the test might have been better
        repeat(10_000) {
            createTransactionRequest("""{"amount":10000000.0,"timestamp":${requestTime + 1 + Random.nextInt(59 * 1000)}}""")
        }

        getStatisticsRequest()
                .apply {
                    assertEquals(200, response.status()?.value)
                    //What should  it look like? If 1 followed by zeroes, then we need a custom jackson serializer
                    assertEquals("""{"sum":1.0E11,"avg":1.0E7,"max":1.0E7,"min":1.0E7,"count":10000}""", response.content)
                }
    }
}