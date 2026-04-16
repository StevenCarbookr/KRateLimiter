package com.example.kratelimiter


@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class KRateLimit(
    /**
     * Set a priority for the request. High priority tasks skip the queue more often.
     */
    val priority: Priority = Priority.MEDIUM,

    /**
     * If you need a more granular priority than HIGH/MEDIUM/LOW, you can use a raw value.
     */
    val priorityRawValue: Int = -1,

    /**
     * After how many "skips" (other tasks getting picked) this task should force its way to the front.
     * Higher number means more patience.
     */
    val maxSkip: Int = 10,

    /**
     * If true, the rate limiter will ignore this method entirely.
     */
    val noRateLimit: Boolean = false,
)

/**
 * Common priority levels for easier usage in the @KRateLimit annotation.
 */
enum class Priority(val value: Int) {
    HIGH(10), MEDIUM(5), LOW(0)
}