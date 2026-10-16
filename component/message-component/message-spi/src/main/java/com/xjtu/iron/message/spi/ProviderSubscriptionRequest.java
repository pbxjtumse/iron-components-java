package com.xjtu.iron.message.spi;
import java.util.Objects;

/**
 * 表示 core 交给 Provider 的普通消费订阅请求。
 *
 * <p>{@code destination}：已解析物理目的地</p>
 * <p>{@code consumerGroup}：消费组或订阅名称</p>
 * <p>{@code listener}：core 入站监听器</p>
 */
public final class ProviderSubscriptionRequest {
    /** 已解析物理目的地。 */
    private final ProviderDestination destination;

    /** 消费组或订阅名称。 */
    private final String consumerGroup;

    /** core 入站监听器。 */
    private final ProviderMessageListener listener;


    /**
     * 校验订阅请求。
     */
    public ProviderSubscriptionRequest(
        ProviderDestination destination,
        String consumerGroup,
        ProviderMessageListener listener) {
        // 物理目的地不能为空。
        destination = Objects.requireNonNull(destination, "destination must not be null");
        // 消费组不能为空。
        if (consumerGroup == null || consumerGroup.isBlank()) {
            // 所有一期 Provider 都需要消费组或订阅名。
            throw new IllegalArgumentException("consumerGroup must not be blank");
        }
        // 去除消费组首尾空白。
        consumerGroup = consumerGroup.trim();
        // 入站监听器不能为空。
        listener = Objects.requireNonNull(listener, "listener must not be null");
    
        // 保存完成校验和标准化后的 destination。
        this.destination = destination;
        // 保存完成校验和标准化后的 consumerGroup。
        this.consumerGroup = consumerGroup;
        // 保存完成校验和标准化后的 listener。
        this.listener = listener;
    }
    /**
     * 返回已解析物理目的地。
     *
     * @return 已解析物理目的地
     */
    public ProviderDestination destination() {
        // 返回不可变字段。
        return destination;
    }

    /**
     * 返回消费组或订阅名称。
     *
     * @return 消费组或订阅名称
     */
    public String consumerGroup() {
        // 返回不可变字段。
        return consumerGroup;
    }

    /**
     * 返回core 入站监听器。
     *
     * @return core 入站监听器
     */
    public ProviderMessageListener listener() {
        // 返回不可变字段。
        return listener;
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
        ProviderSubscriptionRequest other = (ProviderSubscriptionRequest) object;
        return Objects.equals(destination, other.destination)
                && Objects.equals(consumerGroup, other.consumerGroup)
                && Objects.equals(listener, other.listener);
    }

    /**
     * 根据全部字段计算哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        // 使用与 equals 相同的字段计算哈希值。
        return Objects.hash(destination, consumerGroup, listener);
    }

    /**
     * 返回便于诊断的字段摘要。
     *
     * @return 字符串摘要
     */
    @Override
    public String toString() {
        // 拼接全部字段，保持值对象可诊断。
        return "ProviderSubscriptionRequest{" +
                "destination=" + destination +
                ", consumerGroup=" + consumerGroup +
                ", listener=" + listener +
                '}';
    }

}
