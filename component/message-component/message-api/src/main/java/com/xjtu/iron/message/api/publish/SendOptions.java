package com.xjtu.iron.message.api.publish;


import java.time.Duration;
import java.util.Objects;

/**
 * 表示一次普通消息发送的调用选项。
 * 发送有三个时间 1 Provider确认超时 2 Retry 总耗时 3 业务调用总超时
 *
 * <p>{@code confirmTimeout}：等待发送确认的最大时间；为空时使用组件默认值</p>
 */
public final class SendOptions {
    /** 等待发送确认的最大时间；为空时使用组件默认值。 */
    private final Duration confirmTimeout;


    /**
     * 校验显式超时时间。
     */
    public SendOptions(
        Duration confirmTimeout) {
        // null 表示使用组件默认值。
        if (confirmTimeout != null && (confirmTimeout.isZero() || confirmTimeout.isNegative())) {
            // 非正超时没有可执行意义，因此直接拒绝。
            throw new IllegalArgumentException("confirmTimeout must be positive");
        }
    
        // 保存完成校验和标准化后的 confirmTimeout。
        this.confirmTimeout = confirmTimeout;
    }

    /**
     * 返回使用组件默认值的发送选项。
     *
     * @return 默认发送选项
     */
    public static SendOptions defaults() {
        // null 超时由 core 解析为 MessageComponentOptions 中的默认值。
        return new SendOptions(null);
    }

    /**
     * 创建显式确认超时选项。
     *
     * @param timeout 确认超时
     * @return 发送选项
     */
    public static SendOptions withConfirmTimeout(Duration timeout) {
        // 统一通过显式构造器执行合法性校验。
        return new SendOptions(timeout);
    }
    /**
     * 返回等待发送确认的最大时间；为空时使用组件默认值。
     *
     * @return 等待发送确认的最大时间；为空时使用组件默认值
     */
    public Duration confirmTimeout() {
        // 返回不可变字段。
        return confirmTimeout;
    }

    /**
     * 按全部字段比较两个值对象。
     *
     * @param object 待比较对象
     * @return 字段值全部一致时返回 true
     */
    @Override
    public boolean equals(Object object) {
        // 同一对象直接相等。
        if (this == object) {
            return true;
        }
        // 类型不同或对象为空时不相等。
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        // 转换为当前类型后逐字段比较。
        SendOptions other = (SendOptions) object;
        return Objects.equals(confirmTimeout, other.confirmTimeout);
    }

    /**
     * 根据全部字段计算哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        // 使用与 equals 相同的字段计算哈希值。
        return Objects.hash(confirmTimeout);
    }

    /**
     * 返回便于诊断的字段摘要。
     *
     * @return 字符串摘要
     */
    @Override
    public String toString() {
        // 拼接全部字段，保持值对象可诊断。
        return "SendOptions{" +
                "confirmTimeout=" + confirmTimeout +
                '}';
    }

}
