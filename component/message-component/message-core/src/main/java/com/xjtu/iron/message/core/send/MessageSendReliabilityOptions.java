package com.xjtu.iron.message.core.send;

/**
 * message-core 使用的发送可靠性参数。
 *
 * <p>
 * Spring Boot 配置对象不直接下沉到 core。
 * core 只依赖这个稳定的不可变选项对象。
 * </p>
 */
public final class MessageSendReliabilityOptions {

    /** 是否启用发送可靠性增强。 */
    private final boolean enabled;

    /** 使用的 retry 策略名称。 */
    private final String retryPolicyName;

    /**
     * 发送结果 UNKNOWN 时是否允许继续重试。
     *
     * <p>V1 默认 false，避免重复消息。</p>
     */
    private final boolean retryWhenUnknown;

    /** 是否在 SendResult 中暴露可靠性信息。 */
    private final boolean includeReliabilityInfo;

    public MessageSendReliabilityOptions(
            boolean enabled,
            String retryPolicyName,
            boolean retryWhenUnknown,
            boolean includeReliabilityInfo) {
        if (enabled && (retryPolicyName == null || retryPolicyName.isBlank())) {
            throw new IllegalArgumentException("retryPolicyName must not be blank when reliability is enabled");
        }
        this.enabled = enabled;
        this.retryPolicyName = retryPolicyName == null ? null : retryPolicyName.trim();
        this.retryWhenUnknown = retryWhenUnknown;
        this.includeReliabilityInfo = includeReliabilityInfo;
    }

    public static MessageSendReliabilityOptions disabled() {
        return new MessageSendReliabilityOptions(false, null, false, false);
    }

    public static MessageSendReliabilityOptions defaults() {
        return new MessageSendReliabilityOptions(true, "message-send", false, true);
    }

    public boolean enabled() {
        return enabled;
    }

    public String retryPolicyName() {
        return retryPolicyName;
    }

    public boolean retryWhenUnknown() {
        return retryWhenUnknown;
    }

    public boolean includeReliabilityInfo() {
        return includeReliabilityInfo;
    }
}
