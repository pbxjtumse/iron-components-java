package com.xjtu.iron.message.core.send;

import com.xjtu.iron.message.api.publish.*;
import com.xjtu.iron.message.spi.ProviderSendResult;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
/**
 * 一期直发执行器，在发送可靠性关闭时使用。
 *
 * <p>它只做一次 Provider 发送和一次确认等待，不进入 retry-component，也不会生成 retryId、attempts 等可靠性信息。
 * 这个类保留的目的有两个：第一，兼容一期的简单发送路径；第二，作为可靠发送关闭时的明确降级实现。</p>
 *
 * <p>即使不做重试，它仍然会保留 UNKNOWN 语义：如果等待 Provider 确认超时或者线程被中断，
 * 组件不能确认 Broker 是否已经收到消息，所以返回 {@code SendStatus.UNKNOWN}，而不是简单返回 FAILED。</p>
 */
public final class DirectMessageSender implements MessageSendExecutor {

    /** 组件统一时钟。 */
    private final Clock clock;

    public DirectMessageSender(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public SendResult send(PreparedMessageSend prepared) {
        Objects.requireNonNull(prepared, "prepared must not be null");
        try {
            CompletionStage<ProviderSendResult> providerStage = prepared.provider().send(prepared.request());
            if (providerStage == null) {
                return failureResult(prepared, SendStatus.FAILED, SendStage.SEND,
                        SendFailureType.CLIENT_ERROR, "provider returned null completion stage");
            }
            ProviderSendResult providerResult = providerStage.toCompletableFuture().get(
                    prepared.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (providerResult == null) {
                return failureResult(prepared, SendStatus.FAILED, SendStage.CONFIRM,
                        SendFailureType.CLIENT_ERROR, "provider returned null send result");
            }
            return mapProviderResult(prepared, providerResult, SendReliabilityInfo.disabled());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failureResult(prepared, SendStatus.UNKNOWN, SendStage.CONFIRM,
                    SendFailureType.INTERRUPTED, "thread interrupted while waiting for send confirmation");
        } catch (TimeoutException exception) {
            return failureResult(prepared, SendStatus.UNKNOWN, SendStage.CONFIRM,
                    SendFailureType.TIMEOUT, "send confirmation timeout after " + prepared.confirmTimeout());
        } catch (ExecutionException exception) {
            return providerThrowableResult(prepared, unwrap(exception));
        } catch (RuntimeException exception) {
            return providerThrowableResult(prepared, exception);
        }
    }

    @Override
    public CompletionStage<SendResult> sendAsync(PreparedMessageSend prepared) {
        CompletableFuture<SendResult> resultFuture = new CompletableFuture<>();
        try {
            CompletionStage<ProviderSendResult> providerStage = prepared.provider().send(prepared.request());
            if (providerStage == null) {
                resultFuture.complete(failureResult(prepared, SendStatus.FAILED, SendStage.SEND,
                        SendFailureType.CLIENT_ERROR, "provider returned null completion stage"));
                return resultFuture;
            }
            providerStage.toCompletableFuture()
                    .orTimeout(prepared.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS)
                    .whenComplete((providerResult, throwable) -> {
                        if (throwable != null) {
                            resultFuture.complete(providerThrowableResult(prepared, unwrap(throwable)));
                            return;
                        }
                        if (providerResult == null) {
                            resultFuture.complete(failureResult(prepared, SendStatus.FAILED, SendStage.CONFIRM,
                                    SendFailureType.CLIENT_ERROR, "provider returned null send result"));
                            return;
                        }
                        resultFuture.complete(mapProviderResult(
                                prepared, providerResult, SendReliabilityInfo.disabled()));
                    });
            return resultFuture;
        } catch (RuntimeException exception) {
            resultFuture.complete(providerThrowableResult(prepared, exception));
            return resultFuture;
        }
    }

    private SendResult mapProviderResult(
            PreparedMessageSend prepared,
            ProviderSendResult providerResult,
            SendReliabilityInfo reliabilityInfo) {
        SendStage stage = switch (providerResult.status()) {
            case CONFIRMED -> SendStage.COMPLETE;
            case UNKNOWN -> SendStage.CONFIRM;
            case REJECTED, FAILED -> SendStage.SEND;
        };
        return new SendResult(
                prepared.message().messageId(),
                prepared.destination(),
                prepared.providerDestination().providerName(),
                prepared.providerDestination().physicalName(),
                providerResult.status(),
                stage,
                providerResult.failureType(),
                providerResult.providerMessageId(),
                providerResult.description(),
                prepared.startedAt(),
                clock.instant(),
                providerResult.metadata(),
                reliabilityInfo);
    }

    private SendResult failureResult(
            PreparedMessageSend prepared,
            SendStatus status,
            SendStage stage,
            SendFailureType failureType,
            String description) {
        return new SendResult(
                prepared.message().messageId(),
                prepared.destination(),
                prepared.providerDestination().providerName(),
                prepared.providerDestination().physicalName(),
                status,
                stage,
                failureType,
                null,
                description,
                prepared.startedAt(),
                clock.instant(),
                Map.of(),
                SendReliabilityInfo.disabled());
    }

    private SendResult providerThrowableResult(PreparedMessageSend prepared, Throwable throwable) {
        if (throwable instanceof TimeoutException) {
            return failureResult(prepared, SendStatus.UNKNOWN, SendStage.CONFIRM,
                    SendFailureType.TIMEOUT, "send confirmation timeout after " + prepared.confirmTimeout());
        }
        return failureResult(prepared, SendStatus.UNKNOWN, SendStage.CONFIRM,
                SendFailureType.UNKNOWN_OUTCOME,
                throwable == null ? "unknown provider failure" : throwable.getMessage());
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof ExecutionException || current instanceof CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
