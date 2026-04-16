package com.example.kratelimiter

import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.time.Duration

@SpringBootApplication
class KRateLimiterApplication

fun main(args: Array<String>) {
    runApplication<KRateLimiterApplication>(*args)
}
