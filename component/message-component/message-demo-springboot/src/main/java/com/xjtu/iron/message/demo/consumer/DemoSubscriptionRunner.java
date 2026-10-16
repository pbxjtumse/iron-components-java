package com.xjtu.iron.message.demo.consumer;

import com.xjtu.iron.message.api.consume.decision.ConsumeDecision;
import com.xjtu.iron.message.api.consume.definition.ConsumerDefinition;
import com.xjtu.iron.message.api.consume.handler.MessageSubscription;
import com.xjtu.iron.message.api.model.MessageDestination;
import com.xjtu.iron.message.core.MessageTemplate;
import com.xjtu.iron.message.demo.dto.ReceivedMessageView;
import com.xjtu.iron.message.demo.store.InMemoryReceivedMessageStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Demo 消费订阅验证入口。
 *
 * <p>启动时分别订阅 Kafka、Pulsar、RocketMQ 三个 Provider，验证
 * Provider -> Adapter -> ConsumeExecutor -> Handler -> ACK 全链路。</p>
 */
@Component
public class DemoSubscriptionRunner {

    private static final List<String> DEMO_PROVIDERS = List.of("kafka", "pulsar", "rocketmq");

    private final MessageTemplate messageTemplate;
    private final InMemoryReceivedMessageStore store;
    private final List<MessageSubscription> subscriptions = new ArrayList<>();

    public DemoSubscriptionRunner(
            MessageTemplate messageTemplate,
            InMemoryReceivedMessageStore store) {
        this.messageTemplate = messageTemplate;
        this.store = store;
    }

    @PostConstruct
    public void start() {
        for (String provider : DEMO_PROVIDERS) {
            subscribeProvider(provider);
        }
    }

    private void subscribeProvider(String provider) {
        ConsumerDefinition<Object> definition = ConsumerDefinition.of(
                MessageDestination.of("demo", "message").withProviderHint(provider),
                "message-demo-consumer-group-" + provider,
                Object.class);

        MessageSubscription subscription = messageTemplate.subscribe(
                definition,
                (message, context) -> {
                    store.add(new ReceivedMessageView(
                            context.providerName(),
                            context.physicalDestination(),
                            context.consumerGroup(),
                            context.providerMessageId(),
                            message.messageId(),
                            message.messageKey(),
                            message.messageType(),
                            message.payload(),
                            context.headers(),
                            context.receivedAt()));
                    return ConsumeDecision.ACK;
                });
        subscriptions.add(subscription);
    }

    @PreDestroy
    public void stop() {
        for (MessageSubscription subscription : subscriptions) {
            subscription.close();
        }
        subscriptions.clear();
    }
}
