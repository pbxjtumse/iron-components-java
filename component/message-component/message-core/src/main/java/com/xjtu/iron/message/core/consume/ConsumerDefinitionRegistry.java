package com.xjtu.iron.message.core.consume;

import com.xjtu.iron.message.api.consume.definition.ConsumerDefinition;
import com.xjtu.iron.message.api.model.MessageDestination;

import java.util.Optional;

/**
 * 消费者定义注册表。
 */
public interface ConsumerDefinitionRegistry {
    void register(ConsumerDefinition<?> definition);
    Optional<ConsumerDefinition<?>> find(MessageDestination destination, String consumerGroup);
}
