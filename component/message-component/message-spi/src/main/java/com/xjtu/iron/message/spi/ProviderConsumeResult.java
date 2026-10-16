package com.xjtu.iron.message.spi;

import com.xjtu.iron.message.api.consume.decision.ConsumeDecision;
import com.xjtu.iron.message.api.consume.decision.ConsumeFailureType;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Core 返回给 Provider 的消费结果。
 */
public final class ProviderConsumeResult {
    private final ConsumeDecision decision;
    private final ConsumeFailureType failureType;
    private final String description;
    private final Duration retryAfter;
    private final Map<String, String> metadata;

    public ProviderConsumeResult(
            ConsumeDecision decision,
            ConsumeFailureType failureType,
            String description,
            Duration retryAfter,
            Map<String, String> metadata) {
        this.decision = decision == null ? ConsumeDecision.RETRY : decision;
        this.failureType = failureType == null ? ConsumeFailureType.NONE : failureType;
        this.description = normalize(description);
        this.retryAfter = retryAfter;
        this.metadata = metadata == null || metadata.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public static ProviderConsumeResult of(ConsumeDecision decision) {
        return new ProviderConsumeResult(decision, ConsumeFailureType.NONE, null, null, Map.of());
    }

    public static ProviderConsumeResult retry(ConsumeFailureType failureType, String description) {
        return new ProviderConsumeResult(ConsumeDecision.RETRY, failureType, description, null, Map.of());
    }

    public ConsumeDecision decision() { return decision; }
    public ConsumeFailureType failureType() { return failureType; }
    public String description() { return description; }
    public Duration retryAfter() { return retryAfter; }
    public Map<String, String> metadata() { return metadata; }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public String toString() {
        return "ProviderConsumeResult{" +
                "decision=" + decision +
                ", failureType=" + failureType +
                ", description='" + description + '\'' +
                ", retryAfter=" + retryAfter +
                ", metadata=" + metadata +
                '}';
    }
}
