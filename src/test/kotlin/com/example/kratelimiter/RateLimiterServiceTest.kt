package com.example.kratelimiter

import io.github.resilience4j.ratelimiter.RateLimiter
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.*
import java.util.concurrent.TimeUnit

class RateLimiterServiceTest {

    private lateinit var rateLimiter: RateLimiter
    private lateinit var service: RateLimiterService

    @BeforeEach
    fun setup() {
        rateLimiter = mock(RateLimiter::class.java)
        service = RateLimiterService(rateLimiter)
    }

    @Test
    fun `execute returns immediately if permission is granted`() = runTest {
        // If the rate limiter has capacity, we shouldn't wait at all.
        `when`(rateLimiter.acquirePermission()).thenReturn(true)

        service.execute("test", 5, 10)
    }

    @Test
    fun `execute throws exception when queue capacity is exceeded`() = runTest {
        // Prevent all processing to force the queue to fill up.
        `when`(rateLimiter.acquirePermission()).thenReturn(false)

        // Fill the queue to its limit.
        repeat(100) {
            launch {
                try {
                    service.execute("test", 5, 10)
                } catch (e: Exception) {
                    // Just filling the queue, don't care about individual results here.
                }
            }
        }
        
        // Wait a small amount of time for the background launches to actually add their tasks.
        delay(100)

        // This one should fail because the queue is full.
        assertThrows(RuntimeException::class.java) {
            runBlocking {
                service.execute("overflow", 5, 10)
            }
        }.also {
            assertEquals("Queue capacity exceeded", it.message)
        }
    }

    @Test
    fun `scope is cancelled on destroy`() = runTest {
        service.destroy()
        // We can't easily check the private scope, but calling destroy should stop the processQueue loop.
    }

    @Test
    fun `tasks are processed in priority order`() = runBlocking {
        val testRateLimiter = mock(RateLimiter::class.java)
        val testService = RateLimiterService(testRateLimiter)
        // Block all tasks initially to build up a queue.
        `when`(testRateLimiter.acquirePermission()).thenReturn(false)

        val results = Collections.synchronizedList(mutableListOf<String>())

        val job1 = launch {
            testService.execute("low", Priority.LOW.value, 100)
            results.add("low")
        }
        val job2 = launch {
            testService.execute("high", Priority.HIGH.value, 100)
            results.add("high")
        }

        // Give them time to settle into the queue.
        delay(1000)

        // Now start allowing them through.
        `when`(testRateLimiter.acquirePermission()).thenReturn(true)
        
        // Wait for the background worker to notice and process them.
        repeat(50) {
            if (results.size >= 1) return@repeat
            delay(200)
        }

        // High priority should always come out first if they both arrived before any processing happened.
        assertTrue(results.isNotEmpty(), "No tasks were processed.")
        assertEquals("high", results[0])
        
        job1.cancel()
        job2.cancel()
    }

    @Test
    fun `starvation prevention works with maxSkip`() = runBlocking {
        val testRateLimiter = mock(RateLimiter::class.java)
        val testService = RateLimiterService(testRateLimiter)
        `when`(testRateLimiter.acquirePermission()).thenReturn(false)

        val results = Collections.synchronizedList(mutableListOf<String>())

        // Low priority with small maxSkip
        val lowJob = launch {
            testService.execute("low", Priority.LOW.value, 1)
            results.add("low")
        }
        
        delay(500)

        // Keep adding high priority tasks
        val highJobs = (1..5).map {
            launch {
                testService.execute("high", Priority.HIGH.value, 10)
                results.add("high")
            }
        }

        delay(1000)

        // Allow permissions
        `when`(testRateLimiter.acquirePermission()).thenReturn(true)

        // Wait for at least some tasks to be processed
        repeat(50) {
            if (results.size >= 4) return@repeat
            delay(200)
        }

        // "low" should be processed relatively early due to maxSkip = 1
        assertTrue(results.contains("low"), "Low priority task was not processed. Results: $results")
        // With maxSkip=1, it should be selected after at most 1 other task is selected (approximately)
        assertTrue(results.indexOf("low") <= 2, "Low priority task should have been bumped up. Results: $results")
        
        lowJob.cancel()
        highJobs.forEach { it.cancel() }
    }

    @Test
    fun `execute times out if not processed`() = runTest {
        val shortService = RateLimiterService(rateLimiter, queueTimeout = 100L)
        `when`(rateLimiter.acquirePermission()).thenReturn(false)

        assertThrows(TimeoutCancellationException::class.java) {
            runBlocking {
                shortService.execute("timeout-test", 5, 10)
            }
        }
    }

    @Test
    fun `tasks with same priority are processed FIFO`() = runBlocking {
        val testRateLimiter = mock(RateLimiter::class.java)
        val testService = RateLimiterService(testRateLimiter)
        `when`(testRateLimiter.acquirePermission()).thenReturn(false)

        val results = Collections.synchronizedList(mutableListOf<Int>())

        val jobs = (1..5).map { i ->
            launch {
                testService.execute("task-$i", Priority.MEDIUM.value, 100)
                results.add(i)
            }
        }

        delay(500) // let them all get into the queue

        `when`(testRateLimiter.acquirePermission()).thenReturn(true)

        // Wait for all
        repeat(50) {
            if (results.size >= 5) return@repeat
            delay(100)
        }

        assertEquals(listOf(1, 2, 3, 4, 5), results, "Tasks with same priority should be FIFO")
        jobs.forEach { it.cancel() }
    }

    @Test
    fun `cancelled task is removed from queue`() = runBlocking {
        val testRateLimiter = mock(RateLimiter::class.java)
        val testService = RateLimiterService(testRateLimiter)
        `when`(testRateLimiter.acquirePermission()).thenReturn(false)

        val results = Collections.synchronizedList(mutableListOf<String>())

        val jobToCancel = launch {
            try {
                testService.execute("cancel-me", Priority.HIGH.value, 100)
                results.add("finished")
            } catch (e: CancellationException) {
                results.add("cancelled")
            }
        }

        delay(500) // into the queue
        jobToCancel.cancelAndJoin()

        `when`(testRateLimiter.acquirePermission()).thenReturn(true)

        val otherJob = launch {
            testService.execute("other", Priority.LOW.value, 100)
            results.add("other")
        }

        repeat(50) {
            if (results.contains("other")) return@repeat
            delay(100)
        }

        assertEquals(listOf("cancelled", "other"), results)
        assertFalse(results.contains("finished"))
        otherJob.cancel()
    }
}
