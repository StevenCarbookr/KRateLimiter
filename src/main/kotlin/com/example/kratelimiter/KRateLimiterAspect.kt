package com.example.kratelimiter

import kotlinx.coroutines.runBlocking
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.stereotype.Component

/**
 * This Aspect intercepts all calls to @RestController.
 * It's where we apply our rate limiting logic before the method actually runs.
 */
@Aspect
@Component
class KRateLimiterAspect(private val rateLimiterService: RateLimiterService) {

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    fun rateLimit(joinPoint: ProceedingJoinPoint): Any? {
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method
        val annotation = method.getAnnotation(KRateLimit::class.java)

        if (annotation?.noRateLimit == true) {
            return joinPoint.proceed()
        }

        val key = method.name

        // Because standard Spring AOP isn't naturally "suspend-aware" without 
        // additional libraries, we are "forced" to use runBlocking.
        // The service will handle the suspension and queuing logic internally.
        runBlocking {
            rateLimiterService.execute(
                key,
                annotation?.priorityRawValue?.takeIf { it > -1 }
                    ?: annotation?.priority?.value
                    ?: Priority.MEDIUM.value,
                annotation?.maxSkip ?: 10
            )
        }
        return joinPoint.proceed()
    }
}
