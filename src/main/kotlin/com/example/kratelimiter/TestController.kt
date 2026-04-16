package com.example.kratelimiter

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import kotlinx.coroutines.delay

/**
 * Simple controller to demonstrate how the rate limiter works.
 */
@RestController
class TestController {

    /**
     * High priority endpoint. 
     * It allows 5 other tasks to pass it before it forces its way to the front.
     */
    @GetMapping("/high-priority")
    @KRateLimit(priority = Priority.HIGH, maxSkip = 5)
    suspend fun highPriority(): String {
        return "High Priority"
    }

    /**
     * Low priority endpoint. 
     * It's very patient (default maxSkip is 10), but we've lowered it to 2 here 
     * just to see it skip ahead quickly in tests.
     */
    @GetMapping("/low-priority")
    @KRateLimit(priority = Priority.LOW, maxSkip = 2)
    suspend fun lowPriority(): String {
        return "Low Priority"
    }

    /**
     * Shows that you can use any integer for priority if you need more than 3 levels.
     */
    @GetMapping("/custom-priority")
    @KRateLimit(priorityRawValue = 7)
    suspend fun customPriority(): String {
        return "Custom priority(7)"
    }
}
