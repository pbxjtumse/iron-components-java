package com.xjtu.iron.message.integration.kafka.consume;

import com.xjtu.iron.message.spi.ProviderMessagePosition;

import java.util.Map;

/** Kafka 消息位置。 */
public final class KafkaMessagePosition implements ProviderMessagePosition {
    private final String topic;
    private final int partition;
    private final long offset;

    public KafkaMessagePosition(String topic, int partition, long offset) {
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
    }

    public String topic() { return topic; }
    public int partition() { return partition; }
    public long offset() { return offset; }

    @Override
    public Map<String, String> attributes() {
        return Map.of("kafka.topic", topic, "kafka.partition", Integer.toString(partition), "kafka.offset", Long.toString(offset));
    }
}
