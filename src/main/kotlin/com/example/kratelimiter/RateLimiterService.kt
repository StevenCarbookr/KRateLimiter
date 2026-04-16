package com.example.kratelimiter

import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

@Service
class RateLimiterService(
    private val rateLimiter: RateLimiter,
    private val queueTimeout: Long = 30000L
) : DisposableBean {
    private val queue = mutableListOf<RequestTask>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = Mutex()
    private val taskAddedChannel = Channel<Unit>(Channel.CONFLATED)
    private var selectionRound = 0L
    private val maxQueueSize = 100

    suspend fun execute(key: String, priority: Int, maxSkip: Int) {
        if (rateLimiter.acquirePermission()) return

        val deferred = CompletableDeferred<Unit>()
        val task = RequestTask(priority, maxSkip, System.currentTimeMillis(), selectionRound, deferred)

        mutex.withLock {
            if (queue.size >= maxQueueSize) {
                throw RuntimeException("Queue capacity exceeded")
            }
            queue.add(task)
            println("[DEBUG_LOG] Task added to queue: priority=${task.priority}, maxSkip=${task.maxSkip}")
            taskAddedChannel.trySend(Unit)
        }

        try {
            withTimeout(queueTimeout) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            mutex.withLock { queue.remove(task) }
            throw e
        }
    }

    init {
        scope.launch {
            processQueue()
        }
    }

    override fun destroy() {
        scope.cancel()
    }

    private suspend fun processQueue() {
        while (scope.isActive) {
            val taskToProcess = mutex.withLock {
                if (queue.isEmpty()) return@withLock null

                if (!rateLimiter.acquirePermission()) return@withLock null

                selectionRound++
                var selectedIndex = -1
                var bestUrgency = -1L

                // Walk through the queue to find if any task has been waiting for too long.
                // This prevents lower priority tasks from being stuck forever.
                for (i in queue.indices) {
                    val task = queue[i]
                    val urgency = selectionRound - task.entryRound
                    if (urgency >= task.maxSkip) {
                        if (selectedIndex == -1 || urgency > bestUrgency) {
                            bestUrgency = urgency
                            selectedIndex = i
                        }
                    }
                }

                if (selectedIndex == -1) {
                    // No one is "starving", so we'll just pick the one with the highest priority.
                    selectedIndex = queue.indices.minWithOrNull(
                        compareByDescending<Int> { queue[it].priority }
                            .thenBy { queue[it].arrivalTime }
                    ) ?: -1
                }

                if (selectedIndex != -1) {
                    val task = queue.removeAt(selectedIndex)
                    println("[DEBUG_LOG] Task selected for execution: priority=${task.priority}, arrivalTime=${task.arrivalTime}, urgency=${selectionRound - task.entryRound}")
                    task
                } else null
            }

            if (taskToProcess != null) {
                taskToProcess.continuation.complete(Unit)
            } else {
                // If there's nothing to do, we wait for a new task to arrive.
                // We also wake up every 10ms to re-check if the rate limiter has reset.
                withTimeoutOrNull(10) {
                    taskAddedChannel.receive()
                }
            }
        }
    }

    private class RequestTask(
        val priority: Int,
        val maxSkip: Int,
        val arrivalTime: Long,
        val entryRound: Long,
        val continuation: CompletableDeferred<Unit>
    )
}
