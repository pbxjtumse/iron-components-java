package com.xjtu.iron.retry.config.observation;

import com.xjtu.iron.retry.api.event.RetryEvent;
import com.xjtu.iron.retry.api.event.RetryEventType;
import com.xjtu.iron.retry.api.event.RetryListener;
import io.micrometer.core.instrument.*;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 将核心重试事件转换为 Micrometer 指标。
 *
 * <p>operationName 和 policyName 必须来自有限集合，禁止使用订单号、用户号等高基数值。</p>
 */
public final class MicrometerRetryListener implements RetryListener {

    /** 指标注册表。 */
    private final MeterRegistry meterRegistry;
    /** 缓存已经注册的计数器，减少每个事件重复构建开销。 */
    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();
    /** 缓存已经注册的计时器。 */
    private final ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<>();
    /** 当前活跃逻辑执行数量。 */
    private final AtomicLong activeExecutions = new AtomicLong();

    public MicrometerRetryListener(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(
                meterRegistry,
                "meterRegistry must not be null"
        );
        Gauge.builder(
                "iron.retry.active",
                activeExecutions,
                AtomicLong::doubleValue
        ).description("Current active logical retry executions")
                .register(meterRegistry);
    }

    /** 根据事件类型更新对应指标。 */
    @Override
    public void onEvent(RetryEvent event) {
        RetryEvent actualEvent = Objects.requireNonNull(event, "event must not be null");
        RetryEventType eventType = actualEvent.getEventType();
        String operation = actualEvent.getOperationName();
        String policy = actualEvent.getPolicyName();

        if (eventType == RetryEventType.EXECUTION_STARTED) {
            activeExecutions.incrementAndGet();
        }

        if (eventType == RetryEventType.SAFETY_WARNING) {
            counter(
                    "iron.retry.safety.warning.total",
                    Tags.of("operation", operation, "policy", policy)
            ).increment();
        }

        if (eventType == RetryEventType.ATTEMPT_COMPLETED) {
            String outcome = actualEvent.getFailure() == null ? "returned" : "threw";
            counter(
                    "iron.retry.attempt.total",
                    Tags.of(
                            "operation", operation,
                            "policy", policy,
                            "outcome", outcome
                    )
            ).increment();
            timer(
                    "iron.retry.attempt.duration",
                    Tags.of("operation", operation, "policy", policy)
            ).record(actualEvent.getAttemptDuration());
        }

        if (eventType == RetryEventType.DECISION_MADE) {
            counter(
                    "iron.retry.decision.total",
                    Tags.of(
                            "operation", operation,
                            "policy", policy,
                            "decision", actualEvent.getDecisionType().name(),
                            "category", actualEvent.getFailureCategory().name()
                    )
            ).increment();
        }

        if (eventType == RetryEventType.RETRY_SCHEDULED) {
            counter(
                    "iron.retry.scheduled.total",
                    Tags.of(
                            "operation", operation,
                            "policy", policy,
                            "category", actualEvent.getFailureCategory().name(),
                            "delaySource", actualEvent.getRetryDelay().getSource().name()
                    )
            ).increment();
            timer(
                    "iron.retry.backoff.duration",
                    Tags.of(
                            "operation", operation,
                            "policy", policy,
                            "delaySource", actualEvent.getRetryDelay().getSource().name()
                    )
            ).record(actualEvent.getRetryDelay().getDuration());
        }

        if (actualEvent.getFinalStatus() != null) {
            activeExecutions.updateAndGet(current -> Math.max(0L, current - 1L));
            counter(
                    "iron.retry.execution.total",
                    Tags.of(
                            "operation", operation,
                            "policy", policy,
                            "status", actualEvent.getFinalStatus().name(),
                            "category", actualEvent.getFailureCategory().name()
                    )
            ).increment();
            timer(
                    "iron.retry.execution.duration",
                    Tags.of(
                            "operation", operation,
                            "policy", policy,
                            "status", actualEvent.getFinalStatus().name()
                    )
            ).record(actualEvent.getElapsedTime());
        }
    }

    /** 获取或创建指定名称和标签的计数器。 */
    private Counter counter(String name, Tags tags) {
        String key = name + '|' + tags;
        return counters.computeIfAbsent(
                key,
                ignored -> Counter.builder(name).tags(tags).register(meterRegistry)
        );
    }

    /** 获取或创建指定名称和标签的计时器。 */
    private Timer timer(String name, Tags tags) {
        String key = name + '|' + tags;
        return timers.computeIfAbsent(
                key,
                ignored -> Timer.builder(name).tags(tags).register(meterRegistry)
        );
    }
}
