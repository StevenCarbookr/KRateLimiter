

The KRateLimiter is a high-performance, asynchronous rate-limiting and task-scheduling framework built on the Spring Boot ecosystem. It is specifically designed to manage high-throughput traffic by leveraging Kotlin Coroutines and the Resilience4j library to provide structured concurrency and non-blocking execution.

1. Core Component: RateLimiterService

The RateLimiterService acts as the central orchestrator for request admission control. It transitions from a standard rate-limiting model to a priority-aware queuing system when the underlying throughput capacity is reached.

Reactive Processing Pipeline: Instead of traditional thread-blocking or active polling, the service utilizes a Channel-driven notification system. This ensures that the worker coroutine remains suspended (consuming zero CPU cycles) until either a new task is enqueued or the rate-limiter permits become available.

Priority & Fairness Algorithm: The service implements a custom scheduling logic that balances strict priority (Weighted Fair Queuing) with starvation prevention. It utilizes a maxSkip threshold; if a task is bypassed by higher-priority requests for a specific number of selection rounds, it is prioritized to ensure bounded waiting time.

Lifecycle Management: By implementing DisposableBean, the service ensures that its dedicated CoroutineScope is cancelled gracefully upon application shutdown, preventing memory leaks and orphaned background tasks.

2. Declarative Control: @KRateLimit Annotation
The framework provides a declarative API via the @KRateLimit annotation. This allows developers to define fine-grained traffic policies at the method level without polluting business logic.

Granularity: Supports both predefined Priority enums and raw integer values for complex multi-tier priority structures.

Customizable Latency Tolerance: The maxSkip parameter allows per-endpoint configuration of how aggressive the priority jumping should be.

3. Interception Layer: KRateLimiterAspect

Utilizing Spring AOP, the KRateLimiterAspect intercepts requests directed at REST controllers.
It acts as a bridge between the standard Spring MVCrequest lifecycle and the suspending RateLimiterService.

Non-Invasive Integration: The aspect automatically extracts metadata from the annotation and delegates the execution permission to the service, ensuring that the target method only proceeds once the rate-limiting constraints and priority requirements are satisfied.

4. Validation

The project includes test files using kotlinx-coroutines-test and Mockito to verify:

Concurrency Safety, ensuring thread-safe queue operations using Mutex.

Deterministic Scheduling: Validating that tasks are processed according to the priority and maxSkip logic under heavy load.

Timeout Handling: Confirming that requests are automatically evicted from the queue and the caller is notified if the queueTimeout is exceeded.
