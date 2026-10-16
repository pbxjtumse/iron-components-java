package com.xjtu.iron.message.core.send.reliability;

import com.xjtu.iron.message.api.publish.SendFailureType;
import com.xjtu.iron.message.api.publish.SendStatus;
import com.xjtu.iron.message.spi.ProviderSendResult;
import com.xjtu.iron.retry.api.execution.RetryAttempt;
import com.xjtu.iron.retry.api.policy.RetryDecision;
import com.xjtu.iron.retry.api.policy.RetryDecisionType;
import com.xjtu.iron.retry.api.policy.RetryFailureCategory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证消息发送结果到 retry-component 决策的核心映射。
 *
 * <p>这个测试类专门锁定 {@link MessageSendRetryClassifier} 的语义边界：哪些 Provider 结果应该成功、
 * 哪些应该重试、哪些必须停止，避免后续接入 Outbox 或消费可靠性时误改发送侧判断。</p>
 */
class MessageSendRetryClassifierTest {

    @Test
    void confirmedResultShouldFinishSuccessfully() {
        RetryDecision decision = classifier(false).classify(attempt(ProviderSendResult.confirmed("provider-1"), 1));

        assertDecision(decision, RetryDecisionType.SUCCESS, "", RetryFailureCategory.UNKNOWN);
    }

    @Test
    void networkFailureShouldRetry() {
        ProviderSendResult result = ProviderSendResult.failed(
                SendStatus.FAILED,
                SendFailureType.NETWORK_ERROR,
                "temporary network failure");

        RetryDecision decision = classifier(false).classify(attempt(result, 1));

        assertDecision(decision, RetryDecisionType.RETRY,
                "MESSAGE_SEND_NETWORK_ERROR", RetryFailureCategory.DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void clientErrorShouldRetryInShortProcessLevelRetry() {
        ProviderSendResult result = ProviderSendResult.failed(
                SendStatus.FAILED,
                SendFailureType.CLIENT_ERROR,
                "temporary client error");

        RetryDecision decision = classifier(false).classify(attempt(result, 1));

        assertDecision(decision, RetryDecisionType.RETRY,
                "MESSAGE_SEND_CLIENT_ERROR", RetryFailureCategory.TRANSIENT);
    }

    @Test
    void timeoutUnknownResultShouldStopByDefault() {
        ProviderSendResult result = ProviderSendResult.failed(
                SendStatus.UNKNOWN,
                SendFailureType.TIMEOUT,
                "confirm timeout");

        RetryDecision decision = classifier(false).classify(attempt(result, 1));

        assertDecision(decision, RetryDecisionType.STOP,
                "MESSAGE_SEND_TIMEOUT", RetryFailureCategory.UNKNOWN);
    }

    @Test
    void unknownResultCanRetryWhenExplicitlyEnabled() {
        ProviderSendResult result = ProviderSendResult.failed(
                SendStatus.UNKNOWN,
                SendFailureType.TIMEOUT,
                "confirm timeout");

        RetryDecision decision = classifier(true).classify(attempt(result, 1));

        assertDecision(decision, RetryDecisionType.RETRY,
                "MESSAGE_SEND_TIMEOUT", RetryFailureCategory.UNKNOWN);
    }

    @Test
    void rejectedResultShouldStop() {
        ProviderSendResult result = ProviderSendResult.failed(
                SendStatus.REJECTED,
                SendFailureType.ROUTING_ERROR,
                "invalid topic");

        RetryDecision decision = classifier(false).classify(attempt(result, 1));

        assertDecision(decision, RetryDecisionType.STOP,
                "MESSAGE_SEND_ROUTING_ERROR", RetryFailureCategory.NON_RETRYABLE);
    }

    @Test
    void nonRetryableFailedResultShouldStop() {
        ProviderSendResult result = ProviderSendResult.failed(
                SendStatus.FAILED,
                SendFailureType.AUTHORIZATION_ERROR,
                "authorization failed");

        RetryDecision decision = classifier(false).classify(attempt(result, 1));

        assertDecision(decision, RetryDecisionType.STOP,
                "MESSAGE_SEND_AUTHORIZATION_ERROR", RetryFailureCategory.NON_RETRYABLE);
    }

    @Test
    void invalidResultTypeShouldAbort() {
        RetryDecision decision = classifier(false).classify(attempt("not-provider-result", 1));

        assertDecision(decision, RetryDecisionType.ABORT,
                "MESSAGE_SEND_INVALID_RESULT", RetryFailureCategory.NON_RETRYABLE);
    }

    private static MessageSendRetryClassifier classifier(boolean retryWhenUnknown) {
        return new MessageSendRetryClassifier(retryWhenUnknown);
    }

    private static <T> RetryAttempt<T> attempt(T result, int attemptNumber) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new RetryAttempt<>(
                "retry-test",
                "message-send",
                "message-send",
                attemptNumber,
                now,
                now,
                now,
                Duration.ZERO,
                Duration.ZERO,
                Duration.ofSeconds(10),
                result,
                null,
                Map.of());
    }

    private static void assertDecision(
            RetryDecision decision,
            RetryDecisionType type,
            String failureCode,
            RetryFailureCategory failureCategory) {
        assertEquals(type, decision.getType());
        assertEquals(failureCode, decision.getFailureCode());
        assertEquals(failureCategory, decision.getFailureCategory());
    }
}
