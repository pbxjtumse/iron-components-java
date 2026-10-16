package com.xjtu.iron.message.api.publish;

import java.util.Objects;
/**
 * 发送可靠性执行信息，用来承载 retry-component 的执行摘要。
 *
 * <p>message-api 不直接暴露 retry-api 类型，避免对外 API 和 retry-component 形成强耦合。
 * 因此这里用字符串保存 retryStatus、lastFailureCode、lastFailureCategory 等信息。
 * 业务方只需要知道是否启用了可靠发送、实际尝试了几次、最终 retry 状态是什么。</p>
 *
 * <p>当可靠发送关闭或不需要暴露可靠性细节时，可以使用 {@code disabled()} 返回空信息。</p>
 */
public final class SendReliabilityInfo {

    /** 是否启用了可靠发送。 */
    private final boolean enabled;

    /** 本次发送关联的 retryId。 */
    private final String retryId;

    /** 使用的 retry 策略名称。 */
    private final String retryPolicy;

    /** retry-component 返回的终态名称。 */
    private final String retryStatus;

    /** 实际执行的 Provider 发送尝试次数。 */
    private final int attempts;

    /** 最后一次分类失败码。 */
    private final String lastFailureCode;

    /** 最后一次分类失败分类。 */
    private final String lastFailureCategory;

    public SendReliabilityInfo(
            boolean enabled,
            String retryId,
            String retryPolicy,
            String retryStatus,
            int attempts,
            String lastFailureCode,
            String lastFailureCategory) {
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must not be negative");
        }
        this.enabled = enabled;
        this.retryId = retryId;
        this.retryPolicy = retryPolicy;
        this.retryStatus = retryStatus;
        this.attempts = attempts;
        this.lastFailureCode = lastFailureCode;
        this.lastFailureCategory = lastFailureCategory;
    }

    /**
     * 创建未启用可靠发送时的信息。
     *
     * @return 未启用可靠发送的信息
     */
    public static SendReliabilityInfo disabled() {
        return new SendReliabilityInfo(false, null, null, null, 1, null, null);
    }

    /**
     * 创建启用可靠发送时的信息。
     *
     * @param retryId 重试 ID
     * @param retryPolicy 策略名称
     * @param retryStatus 重试终态
     * @param attempts 尝试次数
     * @param lastFailureCode 最后失败码
     * @param lastFailureCategory 最后失败分类
     * @return 可靠性信息
     */
    public static SendReliabilityInfo enabled(
            String retryId,
            String retryPolicy,
            String retryStatus,
            int attempts,
            String lastFailureCode,
            String lastFailureCategory) {
        return new SendReliabilityInfo(
                true,
                retryId,
                retryPolicy,
                retryStatus,
                attempts,
                lastFailureCode,
                lastFailureCategory);
    }

    public boolean enabled() {
        return enabled;
    }

    public String retryId() {
        return retryId;
    }

    public String retryPolicy() {
        return retryPolicy;
    }

    public String retryStatus() {
        return retryStatus;
    }

    public int attempts() {
        return attempts;
    }

    public String lastFailureCode() {
        return lastFailureCode;
    }

    public String lastFailureCategory() {
        return lastFailureCategory;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        SendReliabilityInfo other = (SendReliabilityInfo) object;
        return enabled == other.enabled
                && attempts == other.attempts
                && Objects.equals(retryId, other.retryId)
                && Objects.equals(retryPolicy, other.retryPolicy)
                && Objects.equals(retryStatus, other.retryStatus)
                && Objects.equals(lastFailureCode, other.lastFailureCode)
                && Objects.equals(lastFailureCategory, other.lastFailureCategory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                enabled,
                retryId,
                retryPolicy,
                retryStatus,
                attempts,
                lastFailureCode,
                lastFailureCategory);
    }

    @Override
    public String toString() {
        return "SendReliabilityInfo{"
                + "enabled=" + enabled
                + ", retryId='" + retryId + '\''
                + ", retryPolicy='" + retryPolicy + '\''
                + ", retryStatus='" + retryStatus + '\''
                + ", attempts=" + attempts
                + ", lastFailureCode='" + lastFailureCode + '\''
                + ", lastFailureCategory='" + lastFailureCategory + '\''
                + '}';
    }
}
