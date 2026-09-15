package com.xjtu.iron.message.core.send.reliability;

import com.xjtu.iron.message.api.model.MessageDestination;
import com.xjtu.iron.message.api.model.MessageEnvelope;
import com.xjtu.iron.message.api.publish.*;
import com.xjtu.iron.message.core.send.MessageSendReliabilityOptions;
import com.xjtu.iron.message.core.send.PreparedMessageSend;
import com.xjtu.iron.message.spi.MessageProvider;
import com.xjtu.iron.message.spi.ProviderDestination;
import com.xjtu.iron.message.spi.ProviderSendRequest;
import com.xjtu.iron.message.spi.ProviderSendResult;
import com.xjtu.iron.retry.api.execution.*;
import com.xjtu.iron.retry.api.policy.RetryDecision;
import com.xjtu.iron.retry.api.policy.RetryDecisionType;
import com.xjtu.iron.retry.api.policy.RetryPolicy;
import com.xjtu.iron.retry.api.policy.RetryPolicyRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static com.xjtu.iron.message.core.send.reliability.ScriptedFakeMessageProvider.ScriptedSendAction.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 使用脚本化 FakeProvider 验证二期可靠发送的核心异常分支。
 *
 * <p>这些测试只覆盖 message-core 的发送可靠性逻辑，不连接真实 MQ。每个用例都刻意构造一个 Provider
 * 返回序列，验证 {@link DefaultReliableMessageSender} 是否正确调用 retry-component、是否正确保留
 * UNKNOWN 语义、是否正确把最终 {@code RetryResult} 映射成业务可见的 {@link SendResult}。</p>
 */
class DefaultReliableMessageSenderTest {

    @Test
    void shouldRetryWhenProviderReturnsNetworkErrorAndThenConfirmed() {
        ScriptedFakeMessageProvider provider = ScriptedFakeMessageProvider.scripted(
                returning(ProviderSendResult.failed(SendStatus.FAILED, SendFailureType.NETWORK_ERROR, "network")),
                returning(ProviderSendResult.confirmed("provider-message-1")));

        SendResult result = sender(defaultOptions()).send(prepared(provider));

        assertEquals(SendStatus.CONFIRMED, result.status());
        assertEquals(SendStage.COMPLETE, result.stage());
        assertEquals(SendFailureType.NONE, result.failureType());
        assertEquals("provider-message-1", result.providerMessageId());
        assertEquals(2, provider.sendCount());
        assertReliability(result.reliabilityInfo(), "SUCCESS", 2, "", "");
    }

    @Test
    void shouldReturnRetryExhaustedWhenProviderKeepsReturningNetworkError() {
        ScriptedFakeMessageProvider provider = ScriptedFakeMessageProvider.scripted(
                returning(ProviderSendResult.failed(SendStatus.FAILED, SendFailureType.NETWORK_ERROR, "network-1")),
                returning(ProviderSendResult.failed(SendStatus.FAILED, SendFailureType.NETWORK_ERROR, "network-2")),
                returning(ProviderSendResult.failed(SendStatus.FAILED, SendFailureType.NETWORK_ERROR, "network-3")));

        SendResult result = sender(defaultOptions()).send(prepared(provider));

        assertEquals(SendStatus.FAILED, result.status());
        assertEquals(SendStage.RETRY, result.stage());
        assertEquals(SendFailureType.RETRY_EXHAUSTED, result.failureType());
        assertEquals(3, provider.sendCount());
        assertReliability(result.reliabilityInfo(), "EXHAUSTED", 3,
                "MESSAGE_SEND_NETWORK_ERROR", "DEPENDENCY_UNAVAILABLE");
    }

    @Test
    void shouldStopWithoutRetryWhenOutcomeIsUnknownByDefault() {
        ScriptedFakeMessageProvider provider = ScriptedFakeMessageProvider.scripted(
                returning(ProviderSendResult.failed(SendStatus.UNKNOWN, SendFailureType.TIMEOUT, "timeout")),
                returning(ProviderSendResult.confirmed("should-not-send")));

        SendResult result = sender(defaultOptions()).send(prepared(provider));

        assertEquals(SendStatus.UNKNOWN, result.status());
        assertEquals(SendStage.CONFIRM, result.stage());
        assertEquals(SendFailureType.TIMEOUT, result.failureType());
        assertEquals(1, provider.sendCount());
        assertReliability(result.reliabilityInfo(), "NOT_RETRYABLE", 1,
                "MESSAGE_SEND_TIMEOUT", "UNKNOWN");
    }

    @Test
    void shouldRetryUnknownWhenRetryWhenUnknownIsExplicitlyEnabled() {
        ScriptedFakeMessageProvider provider = ScriptedFakeMessageProvider.scripted(
                returning(ProviderSendResult.failed(SendStatus.UNKNOWN, SendFailureType.TIMEOUT, "timeout")),
                returning(ProviderSendResult.confirmed("provider-message-after-unknown")));

        SendResult result = sender(new MessageSendReliabilityOptions(true, "message-send", true, true))
                .send(prepared(provider));

        assertEquals(SendStatus.CONFIRMED, result.status());
        assertEquals(2, provider.sendCount());
        assertReliability(result.reliabilityInfo(), "SUCCESS", 2, "", "");
    }

    @Test
    void shouldStopWithoutRetryWhenProviderRejectsMessage() {
        ScriptedFakeMessageProvider provider = ScriptedFakeMessageProvider.scripted(
                returning(ProviderSendResult.failed(SendStatus.REJECTED, SendFailureType.ROUTING_ERROR, "invalid topic")),
                returning(ProviderSendResult.confirmed("should-not-send")));

        SendResult result = sender(defaultOptions()).send(prepared(provider));

        assertEquals(SendStatus.REJECTED, result.status());
        assertEquals(SendStage.SEND, result.stage());
        assertEquals(SendFailureType.ROUTING_ERROR, result.failureType());
        assertEquals(1, provider.sendCount());
        assertReliability(result.reliabilityInfo(), "NOT_RETRYABLE", 1,
                "MESSAGE_SEND_ROUTING_ERROR", "NON_RETRYABLE");
    }

    @Test
    void shouldRetryWhenProviderFutureFailsWithIOExceptionAndThenConfirmed() {
        ScriptedFakeMessageProvider provider = ScriptedFakeMessageProvider.scripted(
                failing(new IOException("connection reset")),
                returning(ProviderSendResult.confirmed("provider-message-after-io")));

        SendResult result = sender(defaultOptions()).send(prepared(provider));

        assertEquals(SendStatus.CONFIRMED, result.status());
        assertEquals(2, provider.sendCount());
        assertReliability(result.reliabilityInfo(), "SUCCESS", 2, "", "");
    }

    @Test
    void shouldTreatNullCompletionStageAsRetryableClientError() {
        ScriptedFakeMessageProvider provider = ScriptedFakeMessageProvider.scripted(
                nullStage(),
                returning(ProviderSendResult.confirmed("provider-message-after-null-stage")));

        SendResult result = sender(defaultOptions()).send(prepared(provider));

        assertEquals(SendStatus.CONFIRMED, result.status());
        assertEquals(2, provider.sendCount());
        assertReliability(result.reliabilityInfo(), "SUCCESS", 2, "", "");
    }

    @Test
    void shouldOmitReliabilityFailureFieldsWhenFinalResultIsSuccess() {
        ScriptedFakeMessageProvider provider = ScriptedFakeMessageProvider.scripted(
                returning(ProviderSendResult.confirmed("provider-message-1")));

        SendResult result = sender(defaultOptions()).send(prepared(provider));

        assertEquals(SendStatus.CONFIRMED, result.status());
        assertEquals(1, provider.sendCount());
        assertReliability(result.reliabilityInfo(), "SUCCESS", 1, "", "");
    }

    private static DefaultReliableMessageSender sender(MessageSendReliabilityOptions options) {
        RetryPolicy policy = RetryPolicy.builder("message-send")
                .maxAttempts(3)
                .maxDuration(Duration.ofSeconds(5))
                .classifier(new MessageSendRetryClassifier(options.retryWhenUnknown()))
                .build();
        return new DefaultReliableMessageSender(
                new InlineRetryExecutor(),
                new InMemoryRetryPolicyRegistry(policy),
                options,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                Runnable::run);
    }

    private static MessageSendReliabilityOptions defaultOptions() {
        return MessageSendReliabilityOptions.defaults();
    }

    private static PreparedMessageSend prepared(MessageProvider provider) {
        MessageDestination destination = MessageDestination.of("demo", "message").withProviderHint("fake");
        ProviderDestination providerDestination = new ProviderDestination("fake", "fake-topic", Map.of());
        MessageEnvelope<String> message = MessageEnvelope.builder("DemoMessage", "payload")
                .messageId("message-1")
                .messageKey("order-1")
                .build();
        ProviderSendRequest request = new ProviderSendRequest(
                providerDestination,
                "message-1",
                "order-1",
                Map.of(),
                new byte[]{1});
        return new PreparedMessageSend(
                destination,
                message,
                providerDestination,
                provider,
                request,
                Duration.ofMillis(100),
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static void assertReliability(
            SendReliabilityInfo reliabilityInfo,
            String retryStatus,
            int attempts,
            String lastFailureCode,
            String lastFailureCategory) {
        assertEquals(true, reliabilityInfo.enabled());
        assertEquals("message-send", reliabilityInfo.retryPolicy());
        assertEquals(retryStatus, reliabilityInfo.retryStatus());
        assertEquals(attempts, reliabilityInfo.attempts());
        assertEquals(lastFailureCode, reliabilityInfo.lastFailureCode());
        assertEquals(lastFailureCategory, reliabilityInfo.lastFailureCategory());
    }

    /**
     * 只用于单元测试的内联 RetryExecutor。
     *
     * <p>它按照 retry-api 的核心语义循环执行 operation 和 classifier，但不 sleep、不发布事件、不做指标。
     * 这样测试可以精确验证 message-core 和 retry-api 的协作，不被真实退避时间影响。</p>
     */
    private static final class InlineRetryExecutor implements RetryExecutor {

        @Override
        public <T> RetryResult<T> execute(RetryExecution<T> execution) {
            String retryId = execution.getRetryId() == null ? "retry-inline" : execution.getRetryId();
            String operation = execution.getOperationName();
            RetryPolicy policy = execution.getRetryPolicy();
            RetryAttempt<T> lastAttempt = null;
            RetryDecision lastDecision = null;
            Instant now = Instant.parse("2026-01-01T00:00:00Z");
            for (int attemptNumber = 1; attemptNumber <= policy.getMaxAttempts(); attemptNumber++) {
                T result = null;
                Throwable failure = null;
                try {
                    RetryContext context = new RetryContext(
                            retryId,
                            operation,
                            policy.getPolicyName(),
                            attemptNumber,
                            now,
                            Duration.ZERO,
                            policy.getMaxDuration(),
                            lastAttempt,
                            execution.getAttributes(),
                            execution.getCancellationToken());
                    result = execution.getOperation().execute(context);
                } catch (Exception exception) {
                    failure = exception;
                }
                lastAttempt = new RetryAttempt<>(
                        retryId,
                        operation,
                        policy.getPolicyName(),
                        attemptNumber,
                        now,
                        now,
                        now,
                        Duration.ZERO,
                        Duration.ZERO,
                        policy.getMaxDuration(),
                        result,
                        failure,
                        execution.getAttributes());
                lastDecision = policy.getRetryClassifier().classify(lastAttempt);
                if (lastDecision.getType() == RetryDecisionType.SUCCESS) {
                    return RetryResult.of(retryId, operation, policy.getPolicyName(), RetryStatus.SUCCESS,
                            result, null, attemptNumber, Duration.ZERO, lastAttempt, lastDecision);
                }
                if (lastDecision.getType() == RetryDecisionType.STOP) {
                    return RetryResult.of(retryId, operation, policy.getPolicyName(), RetryStatus.NOT_RETRYABLE,
                            result, failure, attemptNumber, Duration.ZERO, lastAttempt, lastDecision);
                }
                if (lastDecision.getType() == RetryDecisionType.ABORT) {
                    return RetryResult.of(retryId, operation, policy.getPolicyName(), RetryStatus.ABORTED,
                            result, failure, attemptNumber, Duration.ZERO, lastAttempt, lastDecision);
                }
            }
            T lastValue = lastAttempt == null ? null : lastAttempt.getResult();
            Throwable lastFailure = lastAttempt == null ? null : lastAttempt.getFailure();
            return RetryResult.of(retryId, operation, policy.getPolicyName(), RetryStatus.EXHAUSTED,
                    lastValue, lastFailure, policy.getMaxAttempts(), Duration.ZERO, lastAttempt, lastDecision);
        }

        @Override
        public <T> RetryResult<T> execute(String operationName,
                                          Map<String, Object> attributes,
                                          com.xjtu.iron.retry.api.execution.RetryOperation<T> operation,
                                          String policyName) {
            throw new UnsupportedOperationException("named policy lookup is not needed in message sender tests");
        }
    }

    /**
     * 极简内存策略注册表，用于把 message-send 策略提供给 DefaultReliableMessageSender。
     */
    private static final class InMemoryRetryPolicyRegistry implements RetryPolicyRegistry {
        private final RetryPolicy policy;

        private InMemoryRetryPolicyRegistry(RetryPolicy policy) {
            this.policy = policy;
        }

        @Override
        public void register(RetryPolicy retryPolicy) {
            throw new UnsupportedOperationException("test registry is read only");
        }

        @Override
        public void replace(RetryPolicy retryPolicy) {
            throw new UnsupportedOperationException("test registry is read only");
        }

        @Override
        public Optional<RetryPolicy> find(String policyName) {
            return policy.getPolicyName().equals(policyName) ? Optional.of(policy) : Optional.empty();
        }

        @Override
        public RetryPolicy getRequired(String policyName) {
            return find(policyName).orElseThrow(() -> new IllegalArgumentException(policyName));
        }

        @Override
        public Collection<String> policyNames() {
            return java.util.List.of(policy.getPolicyName());
        }

        @Override
        public Map<String, RetryPolicy> snapshot() {
            return Map.of(policy.getPolicyName(), policy);
        }
    }
}
