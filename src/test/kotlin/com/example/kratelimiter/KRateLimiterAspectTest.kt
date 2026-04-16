package com.example.kratelimiter

import kotlinx.coroutines.test.runTest
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*
import java.lang.reflect.Method

class KRateLimiterAspectTest {

    private lateinit var rateLimiterService: RateLimiterService
    private lateinit var aspect: KRateLimiterAspect
    private lateinit var joinPoint: ProceedingJoinPoint
    private lateinit var methodSignature: MethodSignature
    private lateinit var method: Method

    @BeforeEach
    fun setup() {
        rateLimiterService = mock(RateLimiterService::class.java)
        aspect = KRateLimiterAspect(rateLimiterService)
        joinPoint = mock(ProceedingJoinPoint::class.java)
        methodSignature = mock(MethodSignature::class.java)
        method = TestTarget::class.java.getMethod("limitedMethod")
        
        `when`(joinPoint.signature).thenReturn(methodSignature)
        `when`(methodSignature.method).thenReturn(method)
        // Default to returning something so we don't get NullPointerException.
        `when`(joinPoint.proceed()).thenReturn("default")
    }

    @Test
    fun `aspect calls service execute`() = runTest {
        // Mocking the behavior of a successful controller call.
        `when`(joinPoint.proceed()).thenReturn("result")
        
        val result = aspect.rateLimit(joinPoint)

        // Ensure the aspect actually tells the service to handle the rate limit.
        verify(rateLimiterService).execute(anyString(), anyInt(), anyInt())
        // Ensure the original method is actually called.
        verify(joinPoint).proceed()
        assertEquals("result", result)
    }

    @Test
    fun `aspect bypasses service if noRateLimit is true`() = runTest {
        val noLimitMethod = TestTarget::class.java.getMethod("noLimitMethod")
        `when`(methodSignature.method).thenReturn(noLimitMethod)
        `when`(joinPoint.proceed()).thenReturn("noLimitResult")

        val result = aspect.rateLimit(joinPoint)

        // If noRateLimit is set, the service should NOT be called.
        verify(rateLimiterService, never()).execute(anyString(), anyInt(), anyInt())
        verify(joinPoint).proceed()
        assertEquals("noLimitResult", result)
    }

    @Test
    fun `aspect uses default values when annotation is missing`() = runTest {
        val noAnnotationMethod = TestTarget::class.java.getMethod("noAnnotationMethod")
        `when`(methodSignature.method).thenReturn(noAnnotationMethod)
        `when`(joinPoint.proceed()).thenReturn("noAnnotationResult")

        val result = aspect.rateLimit(joinPoint)

        // Should use defaults: MEDIUM priority (5) and maxSkip (10)
        verify(rateLimiterService).execute(anyString(), eq(5), eq(10))
        verify(joinPoint).proceed()
        assertEquals("noAnnotationResult", result)
    }

    @Test
    fun `aspect priorityRawValue takes precedence over priority`() = runTest {
        val rawValueMethod = TestTarget::class.java.getMethod("rawValueMethod")
        `when`(methodSignature.method).thenReturn(rawValueMethod)
        `when`(joinPoint.proceed()).thenReturn("rawValueResult")

        val result = aspect.rateLimit(joinPoint)

        // priorityRawValue = 7, priority = HIGH (10). 7 should win.
        verify(rateLimiterService).execute(anyString(), eq(7), anyInt())
        verify(joinPoint).proceed()
        assertEquals("rawValueResult", result)
    }

    @Suppress("unused")
    class TestTarget {
        @KRateLimit(priority = Priority.HIGH, maxSkip = 5)
        fun limitedMethod() {}

        @KRateLimit(noRateLimit = true)
        fun noLimitMethod() {}

        fun noAnnotationMethod() {}

        @KRateLimit(priority = Priority.HIGH, priorityRawValue = 7)
        fun rawValueMethod() {}
    }
}
