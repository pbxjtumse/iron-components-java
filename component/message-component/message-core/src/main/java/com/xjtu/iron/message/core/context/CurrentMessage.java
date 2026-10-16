package com.xjtu.iron.message.core.context;

import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.model.MessageEnvelope;

import java.util.Objects;

/**
 * 表示当前线程正在处理的入站消息。
 *
 * <p>{@code envelope}：当前入站消息信封</p>
 * <p>{@code consumeContext}：当前投递运行时上下文</p>
 */
public final class CurrentMessage {
    /** 当前入站消息信封。 */
    private final MessageEnvelope<?> envelope;

    /** 当前投递运行时上下文。 */
    private final ConsumeContext consumeContext;


    /**
     * 校验当前消息上下文。
     */
    public CurrentMessage(
        MessageEnvelope<?> envelope,
        ConsumeContext consumeContext) {
        // 入站消息信封不能为空。
        envelope = Objects.requireNonNull(envelope, "envelope must not be null");
        // 消费上下文不能为空。
        consumeContext = Objects.requireNonNull(consumeContext, "consumeContext must not be null");
    
        // 保存完成校验和标准化后的 envelope。
        this.envelope = envelope;
        // 保存完成校验和标准化后的 consumeContext。
        this.consumeContext = consumeContext;
    }
    /**
     * 返回当前入站消息信封。
     *
     * @return 当前入站消息信封
     */
    public MessageEnvelope<?> envelope() {
        // 返回不可变字段。
        return envelope;
    }

    /**
     * 返回当前投递运行时上下文。
     *
     * @return 当前投递运行时上下文
     */
    public ConsumeContext consumeContext() {
        // 返回不可变字段。
        return consumeContext;
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
        CurrentMessage other = (CurrentMessage) object;
        return Objects.equals(envelope, other.envelope)
                && Objects.equals(consumeContext, other.consumeContext);
    }

    /**
     * 根据全部字段计算哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        // 使用与 equals 相同的字段计算哈希值。
        return Objects.hash(envelope, consumeContext);
    }

    /**
     * 返回便于诊断的字段摘要。
     *
     * @return 字符串摘要
     */
    @Override
    public String toString() {
        // 拼接全部字段，保持值对象可诊断。
        return "CurrentMessage{" +
                "envelope=" + envelope +
                ", consumeContext=" + consumeContext +
                '}';
    }

}
