package com.xjtu.iron.message.api.consume.definition;

import java.time.Duration;

/**
 * 单个消费者的事务模板配置。
 */
public final class MessageConsumeTransactionOptions {
    /** 是否由 message-component 调用事务模板包住业务 Handler 和幂等终态更新。 */
    private final boolean enabled;

    /** 启用事务后，如果找不到事务执行器，是否启动失败。 */
    private final boolean required;

    /** 本地事务执行超时时间。 */
    private final Duration timeout;

    /** 可选事务名称，用于日志、指标和事务模板诊断。 */
    private final String transactionName;

    public MessageConsumeTransactionOptions(
            boolean enabled,
            boolean required,
            Duration timeout,
            String transactionName) {
        this.enabled = enabled;
        this.required = required;
        this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        this.transactionName = normalize(transactionName);
    }

    public static MessageConsumeTransactionOptions disabled() {
        return new MessageConsumeTransactionOptions(false, false, Duration.ofSeconds(30), null);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean required() {
        return required;
    }

    public Duration timeout() {
        return timeout;
    }

    public String transactionName() {
        return transactionName;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
