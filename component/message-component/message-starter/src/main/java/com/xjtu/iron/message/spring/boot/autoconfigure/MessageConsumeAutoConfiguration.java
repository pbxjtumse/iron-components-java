package com.xjtu.iron.message.spring.boot.autoconfigure;

import com.xjtu.iron.message.core.MessageComponentOptions;
import com.xjtu.iron.message.core.codec.MessageWireCodec;
import com.xjtu.iron.message.core.consume.ConsumeExceptionClassifier;
import com.xjtu.iron.message.core.consume.DefaultConsumeExceptionClassifier;
import com.xjtu.iron.message.core.consume.MessageConsumeExecutor;
import com.xjtu.iron.message.core.consume.MessageConsumerAdapter;
import com.xjtu.iron.message.core.consume.handler.MessageHandlerInvoker;
import com.xjtu.iron.message.core.consume.idempotency.*;
import com.xjtu.iron.message.core.consume.strategy.DefaultIdempotencyStrategy;
import com.xjtu.iron.message.core.consume.strategy.DefaultTransactionStrategy;
import com.xjtu.iron.message.core.consume.strategy.IdempotencyStrategy;
import com.xjtu.iron.message.core.consume.strategy.TransactionStrategy;
import com.xjtu.iron.message.core.consume.transaction.MessageConsumeTransactionExecutor;
import com.xjtu.iron.message.core.consume.transaction.NoopMessageConsumeTransactionExecutor;
import com.xjtu.iron.message.core.context.MessageContextAccessor;
import com.xjtu.iron.message.spring.boot.autoconfigure.properties.MessageProperties;
import com.xjtu.iron.message.spring.boot.autoconfigure.properties.consume.Idempotency.MessageConsumeIdempotencyProperties;
import com.xjtu.iron.message.spring.boot.autoconfigure.properties.consume.MessageConsumeTransactionProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 消息消费自动配置。
 *
 * <p>V4 后消费侧 Bean 装配关系固定为：IdempotencyStrategy、TransactionStrategy、
 * MessageHandlerInvoker 共同组成 MessageConsumeExecutor。幂等执行器不再直接依赖事务执行器。</p>
 */
@AutoConfiguration(after = MessageCoreAutoConfiguration.class)
@ConditionalOnProperty(prefix = "xjtu.iron.message", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MessageConsumeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConsumeExceptionClassifier consumeExceptionClassifier() {
        return new DefaultConsumeExceptionClassifier();
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageHandlerInvoker messageHandlerInvoker(ConsumeExceptionClassifier exceptionClassifier) {
        return new MessageHandlerInvoker(exceptionClassifier);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageConsumeTransactionExecutor messageConsumeTransactionExecutor(MessageProperties properties) {
        MessageConsumeTransactionProperties transaction = properties.getConsume().getTransaction();
        if (transaction.isEnabled() && transaction.isRequired()) {
            throw new IllegalStateException(
                    "xjtu.iron.message.consume.transaction.enabled=true and required=true, "
                            + "but MessageConsumeTransactionExecutor is missing. "
                            + "Please add a transaction integration bean or disable required.");
        }
        return new NoopMessageConsumeTransactionExecutor();
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionStrategy transactionStrategy(MessageConsumeTransactionExecutor transactionExecutor) {
        return new DefaultTransactionStrategy(transactionExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageIdempotencyContextFactory messageIdempotencyContextFactory(MessageComponentOptions options) {
        return new DefaultMessageIdempotencyContextFactory(
                new DefaultMessageIdempotencySceneResolver(),
                new DefaultMessageIdempotencyKeyResolver(),
                new MessageIdempotencyOwnerTokenGenerator(),
                options.clock());
    }

    @Bean
    @ConditionalOnBean(MessageIdempotentOperations.class)
    @ConditionalOnMissingBean
    public MessageIdempotencyStateManager messageIdempotencyStateManager(
            MessageIdempotentOperations operations) {
        return new DefaultMessageIdempotencyStateManager(operations);
    }

    @Bean
    @ConditionalOnBean(MessageIdempotencyStateManager.class)
    @ConditionalOnMissingBean
    public MessageIdempotencyDecisionHandler messageIdempotencyDecisionHandler(
            MessageIdempotencyStateManager stateManager) {
        return new MessageIdempotencyDecisionHandler(stateManager);
    }

    /**
     * 没有 idempotent-component 存储实现时，默认使用 Noop；如果配置显式要求全局幂等，则启动失败。
     */
    @Bean
    @ConditionalOnMissingBean
    public MessageIdempotencyExecutor messageIdempotencyExecutor(
            MessageProperties properties,
            MessageIdempotencyContextFactory contextFactory,
            ObjectProvider<MessageIdempotencyStateManager> stateManagerProvider,
            ObjectProvider<MessageIdempotencyDecisionHandler> decisionHandlerProvider) {
        MessageConsumeIdempotencyProperties idempotency = properties.getConsume().getIdempotency();
        MessageIdempotencyStateManager stateManager = stateManagerProvider.getIfAvailable();
        MessageIdempotencyDecisionHandler decisionHandler = decisionHandlerProvider.getIfAvailable();
        if (stateManager == null || decisionHandler == null) {
            if (idempotency.isEnabled()) {
                throw new IllegalStateException(
                        "xjtu.iron.message.consume.idempotency.enabled=true, "
                                + "but MessageIdempotentOperations is missing. "
                                + "Please add an idempotent integration bean or disable consume idempotency.");
            }
            return new NoopMessageIdempotencyExecutor();
        }
        return new DefaultMessageIdempotencyExecutor(
                contextFactory,
                stateManager,
                decisionHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyStrategy idempotencyStrategy(MessageIdempotencyExecutor idempotencyExecutor) {
        return new DefaultIdempotencyStrategy(idempotencyExecutor);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageConsumeExecutor messageConsumeExecutor(
            IdempotencyStrategy idempotencyStrategy,
            TransactionStrategy transactionStrategy,
            MessageHandlerInvoker handlerInvoker) {
        return new MessageConsumeExecutor(
                idempotencyStrategy,
                transactionStrategy,
                handlerInvoker);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageConsumerAdapter messageConsumerAdapter(
            MessageWireCodec wireCodec,
            MessageConsumeExecutor consumeExecutor,
            MessageContextAccessor contextAccessor) {
        return new MessageConsumerAdapter(wireCodec, consumeExecutor, contextAccessor);
    }
}
