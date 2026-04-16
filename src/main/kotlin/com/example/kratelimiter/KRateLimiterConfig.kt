package com.example.kratelimiter

import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * Configures the underlying Resilience4j RateLimiter.
 */
@Configuration
class KRateLimiterConfig {

    /**
     * We're setting it to 1 permit every 5 seconds.
     * This makes it easy to test our queueing logic without a flood of requests.
     */
    @Bean
    fun rateLimiter(): RateLimiter {
        val config = RateLimiterConfig.custom()
            .limitForPeriod(1)
            .limitRefreshPeriod(Duration.ofSeconds(5))
            .timeoutDuration(Duration.ZERO)
            .build()
        return RateLimiter.of("KRateLimiter", config)
    }
}
