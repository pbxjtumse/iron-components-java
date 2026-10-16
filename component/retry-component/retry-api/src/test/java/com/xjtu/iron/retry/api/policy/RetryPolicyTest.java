package com.xjtu.iron.retry.api.policy;

import com.xjtu.iron.retry.api.backoff.BackoffStrategies;
import com.xjtu.iron.retry.api.backoff.RetryDelay;
import com.xjtu.iron.retry.api.backoff.RetryDelaySource;
import com.xjtu.iron.retry.api.execution.RetryAttempt;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** 验证 RetryPolicy 的分类、校验和退避契约。 */
class RetryPolicyTest {

    /** 验证异常规则可以携带稳定分类和失败码。 */
    @Test
    void shouldClassifyRetryableFailureWithCategoryAndCode() {
        RetryPolicy policy = RetryPolicy.builder("test")
                .retryOn(
                        RetryFailureCategory.TRANSIENT,
                        "IO_TRANSIENT",
                        IOException.class
                )
                .build();
        RetryDecision decision = policy.getRetryClassifier().classify(
                attempt(new IOException("temporary"))
        );
        assertEquals(RetryDecisionType.RETRY, decision.getType());
        assertEquals(RetryFailureCategory.TRANSIENT, decision.getFailureCategory());
        assertEquals("IO_TRANSIENT", decision.getFailureCode());
    }

    /** 验证更具体的异常规则不受声明顺序影响。 */
    @Test
    void shouldPreferMostSpecificRuleWithinSameAction() {
        RetryPolicy policy = RetryPolicy.builder("specific")
                .retryOn(
                        RetryFailureCategory.TRANSIENT,
                        "BROAD",
                        Exception.class
                )
                .retryOn(
                        RetryFailureCategory.DEPENDENCY_UNAVAILABLE,
                        "IO_SPECIFIC",
                        IOException.class
                )
                .build();
        RetryDecision decision = policy.getRetryClassifier().classify(
                attempt(new IOException("temporary"))
        );
        assertEquals("IO_SPECIFIC", decision.getFailureCode());
        assertEquals(
                RetryFailureCategory.DEPENDENCY_UNAVAILABLE,
                decision.getFailureCategory()
        );
    }

    /** 验证异常 cause 循环不会造成无限遍历。 */
    @Test
    void shouldStopTraversingWhenCauseGraphContainsCycle() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second");
        first.initCause(second);
        second.initCause(first);
        RetryPolicy policy = RetryPolicy.builder("cycle")
                .traverseCauses(true)
                .maxCauseDepth(8)
                .retryOn(IOException.class)
                .build();
        RetryDecision decision = policy.getRetryClassifier().classify(attempt(first));
        assertEquals(RetryDecisionType.STOP, decision.getType());
    }

    /** 验证自定义分类器不能与声明式规则混用。 */
    @Test
    void shouldRejectMixedCustomClassifierAndConfiguredRules() {
        assertThrows(IllegalArgumentException.class, () -> RetryPolicy.builder("mixed")
                .retryOn(IOException.class)
                .classifier(attempt -> RetryDecision.success("done"))
                .build());
    }

    /** 验证同一异常类型不能同时配置为重试和停止。 */
    @Test
    void shouldRejectExactRuleActionConflict() {
        assertThrows(IllegalArgumentException.class, () -> RetryPolicy.builder("conflict")
                .retryOn(IOException.class)
                .stopOn(IOException.class)
                .build());
    }

    /** 验证严格安全模式拒绝非幂等多次尝试。 */
    @Test
    void shouldRejectUnsafePolicyInRejectMode() {
        assertThrows(IllegalArgumentException.class, () -> RetryPolicy.builder("unsafe")
                .maxAttempts(2)
                .operationSafety(OperationSafety.NON_IDEMPOTENT)
                .safetyMode(RetrySafetyMode.REJECT)
                .build());
    }

    /** 验证零等待仍然保留服务端来源。 */
    @Test
    void shouldPreserveSourceForExplicitZeroDelay() {
        RetryDelay delay = RetryDelay.of(
                Duration.ZERO,
                RetryDelaySource.SERVER_DIRECTED,
                "retry immediately"
        );
        assertTrue(delay.isZero());
        assertEquals(RetryDelaySource.SERVER_DIRECTED, delay.getSource());
    }

    /** 验证可注入随机源可以稳定复现全抖动结果。 */
    @Test
    void shouldSupportDeterministicFullJitterForTests() {
        RetryPolicy policy = RetryPolicy.builder("jitter")
                .backoffStrategy(BackoffStrategies.exponentialWithFullJitter(
                        Duration.ofMillis(100),
                        Duration.ofSeconds(1),
                        2.0D,
                        new Random(7L)
                ))
                .build();
        RetryDecision decision = RetryDecision.retry(
                "temporary",
                "TEMPORARY",
                RetryFailureCategory.TRANSIENT
        );
        RetryDelay first = policy.getBackoffStrategy().nextDelay(
                attempt(new IOException("temporary")),
                decision
        );
        assertTrue(first.getDuration().compareTo(Duration.ofMillis(100)) <= 0);
        assertEquals(RetryDelaySource.FULL_JITTER, first.getSource());
    }

    /** 创建测试所需的第一尝试快照。 */
    private RetryAttempt<String> attempt(Throwable failure) {
        return new RetryAttempt<>(
                "retry-id",
                "operation",
                "test",
                1,
                Instant.EPOCH,
                Instant.EPOCH,
                Instant.EPOCH,
                Duration.ZERO,
                Duration.ZERO,
                Duration.ofSeconds(1),
                null,
                failure,
                Map.of()
        );
    }
}
