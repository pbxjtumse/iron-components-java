package com.xjtu.iron.message.core.send.reliability;

import com.xjtu.iron.message.api.publish.SendFailureType;
import com.xjtu.iron.message.api.publish.SendStatus;
import com.xjtu.iron.message.spi.ProviderSendResult;
import com.xjtu.iron.retry.api.execution.RetryAttempt;
import com.xjtu.iron.retry.api.policy.RetryClassifier;
import com.xjtu.iron.retry.api.policy.RetryDecision;
import com.xjtu.iron.retry.api.policy.RetryFailureCategory;

import java.util.Objects;
/**
 * 消息发送专用的重试分类器，用来把 Provider 层结果转换成 retry-component 能理解的动作。
 *
 * <p>这个类是“消息语义”和“通用重试语义”的分界点。Provider 会返回统一的 {@code ProviderSendResult}，
 * 但 retry-component 不应该知道 Kafka offset、Pulsar messageId、RocketMQ SEND_OK 等细节。
 * 所以这里把结果归纳成 SUCCESS、RETRY、STOP 或 ABORT。</p>
 *
 * <p>分类原则：</p>
 * <ul>
 *   <li>CONFIRMED：明确成功，返回 SUCCESS；</li>
 *   <li>REJECTED：参数、路由、权限、序列化等明确不可重试，返回 STOP；</li>
 *   <li>UNKNOWN：默认 STOP，避免重复消息；</li>
 *   <li>FAILED + NETWORK_ERROR / CLIENT_ERROR：认为是短暂失败，允许有限重试。</li>
 * </ul>
 */
public final class MessageSendRetryClassifier implements RetryClassifier {

    /** 发送结果 UNKNOWN 时是否允许继续重试。 */
    private final boolean retryWhenUnknown;

    public MessageSendRetryClassifier(boolean retryWhenUnknown) {
        this.retryWhenUnknown = retryWhenUnknown;
    }

    @Override
    public RetryDecision classify(RetryAttempt<?> attempt) {
        Objects.requireNonNull(attempt, "attempt must not be null");
        if (attempt.hasFailure()) {
            return classifyThrowable(attempt.getFailure());
        }
        Object result = attempt.getResult();
        if (!(result instanceof ProviderSendResult providerResult)) {
            return RetryDecision.abort(
                    "message send operation returned non ProviderSendResult",
                    "MESSAGE_SEND_INVALID_RESULT");
        }
        return classifyProviderResult(providerResult);
    }

    private RetryDecision classifyProviderResult(ProviderSendResult result) {
        if (result.status() == SendStatus.CONFIRMED) {
            return RetryDecision.success("message send confirmed by provider");
        }
        if (result.status() == SendStatus.REJECTED) {
            return RetryDecision.stop(
                    "message send rejected by provider",
                    failureCode(result),
                    RetryFailureCategory.NON_RETRYABLE);
        }
        if (result.status() == SendStatus.UNKNOWN) {
            // UNKNOWN 表示 Broker 状态不确定。V1 默认停止，只有显式打开 retryWhenUnknown 才冒重复风险继续重试。
            if (retryWhenUnknown) {
                return RetryDecision.retry(
                        "message send outcome is unknown but retryWhenUnknown is enabled",
                        failureCode(result),
                        RetryFailureCategory.UNKNOWN);
            }
            return RetryDecision.stop(
                    "message send outcome is unknown, stop to avoid duplicate message",
                    failureCode(result),
                    RetryFailureCategory.UNKNOWN);
        }
        // 只有“明确失败且失败类型可重试”的结果才进入 retry，状态不确定的 UNKNOWN 不走这里。
        if (isRetryableFailure(result.failureType())) {
            return RetryDecision.retry(
                    "message send failed with retryable failure type",
                    failureCode(result),
                    retryCategory(result.failureType()));
        }
        return RetryDecision.stop(
                "message send failed with non retryable failure type",
                failureCode(result),
                RetryFailureCategory.NON_RETRYABLE);
    }

    private RetryDecision classifyThrowable(Throwable throwable) {
        if (throwable instanceof InterruptedException) {
            return RetryDecision.abort("message send interrupted", "MESSAGE_SEND_INTERRUPTED");
        }
        return RetryDecision.stop(
                throwable == null ? "unknown message send exception" : throwable.getMessage(),
                "MESSAGE_SEND_EXCEPTION",
                RetryFailureCategory.UNKNOWN);
    }

    private static boolean isRetryableFailure(SendFailureType failureType) {
        return failureType == SendFailureType.NETWORK_ERROR
                || failureType == SendFailureType.CLIENT_ERROR;
    }

    private static RetryFailureCategory retryCategory(SendFailureType failureType) {
        if (failureType == SendFailureType.NETWORK_ERROR) {
            return RetryFailureCategory.DEPENDENCY_UNAVAILABLE;
        }
        return RetryFailureCategory.TRANSIENT;
    }

    private static String failureCode(ProviderSendResult result) {
        if (result.failureType() == null) {
            return "MESSAGE_SEND_UNKNOWN_FAILURE";
        }
        return "MESSAGE_SEND_" + result.failureType().name();
    }
}
