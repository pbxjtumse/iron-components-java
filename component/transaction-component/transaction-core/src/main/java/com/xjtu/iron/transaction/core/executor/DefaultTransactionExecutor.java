package com.xjtu.iron.transaction.core.executor;

import com.xjtu.iron.transaction.api.definition.TransactionOptions;
import com.xjtu.iron.transaction.api.event.TransactionEvent;
import com.xjtu.iron.transaction.api.event.TransactionEventListener;
import com.xjtu.iron.transaction.api.event.TransactionEventType;
import com.xjtu.iron.transaction.api.exception.TransactionExecutionException;
import com.xjtu.iron.transaction.api.execution.TransactionCallback;
import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import com.xjtu.iron.transaction.api.status.TransactionStage;
import com.xjtu.iron.transaction.core.context.DefaultTransactionContext;
import com.xjtu.iron.transaction.core.id.UuidTransactionExecutionIdGenerator;
import com.xjtu.iron.transaction.core.validation.TransactionOptionsValidator;
import com.xjtu.iron.transaction.spi.exception.ProviderTransactionException;
import com.xjtu.iron.transaction.spi.id.TransactionExecutionIdGenerator;
import com.xjtu.iron.transaction.spi.provider.TransactionProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * TransactionExecutor 默认实现。
 *
 * <p>core 只负责稳定 API 语义、executionId、上下文包装、异常转换和事件旁路；
 * REQUIRED / REQUIRES_NEW / MANDATORY 如何创建、复用、挂起和恢复真实事务全部交给 Provider。</p>
 */
public final class DefaultTransactionExecutor implements TransactionExecutor {

    private final TransactionProvider provider;
    private final TransactionExecutionIdGenerator executionIdGenerator;
    private final List<TransactionEventListener> listeners;

    public DefaultTransactionExecutor(TransactionProvider provider) {
        this(provider, new UuidTransactionExecutionIdGenerator(), Collections.emptyList());
    }

    public DefaultTransactionExecutor(
            TransactionProvider provider,
            List<TransactionEventListener> listeners) {
        this(provider, new UuidTransactionExecutionIdGenerator(), listeners);
    }

    public DefaultTransactionExecutor(
            TransactionProvider provider,
            TransactionExecutionIdGenerator executionIdGenerator,
            List<TransactionEventListener> listeners) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.executionIdGenerator = Objects.requireNonNull(executionIdGenerator, "executionIdGenerator");
        this.listeners = Collections.unmodifiableList(new ArrayList<>(
                listeners == null ? Collections.emptyList() : listeners));
    }

    @Override
    public <T> T execute(TransactionOptions options, TransactionCallback<T> callback) {
        // 1. 先校验回调和事务选项；配置错误应在触碰数据库连接或 TransactionManager 之前暴露。
        Objects.requireNonNull(callback, "callback");
        TransactionOptionsValidator.validate(options);

        // 2. 为“本次 TransactionExecutor 调用”生成逻辑 executionId。
        // 它只用于日志/事件关联，不表示数据库的真实 transaction id。
        String executionId = executionIdGenerator.nextId();
        long startedNanos = System.nanoTime();

        // 3. STARTED 表示事务模板调用开始；此时 Provider 还没有建立/复用底层事务。
        publish(new TransactionEvent(
                TransactionEventType.STARTED,
                executionId,
                options.name(),
                TransactionStage.BEGIN,
                null,
                Duration.ZERO,
                null));

        try {
            // 4. Provider 根据 TransactionOptions 进入真实事务边界。
            // 对 REQUIRED 而言，可能创建新事务，也可能直接复用当前线程已有事务；
            // core 不再为这两个情况发明 OWNER/PARTICIPANT 等公共术语。
            T value = provider.execute(options, providerContext -> {
                // 4.1 将 Provider 的最小运行时能力包装为稳定 TransactionContext。
                DefaultTransactionContext context = new DefaultTransactionContext(executionId, options.name(), providerContext);

                // 4.2 这是用户 TransactionCallback 真正执行的位置。
                // MyBatis/JPA 的数据库操作此时已经处在 Provider 准备好的 Spring 事务环境中。
                return callback.execute(context);
            });

            // 5. COMPLETED 只表示“本次逻辑 execute 调用正常完成”。
            // 如果 REQUIRED 正在复用外层事务，这里绝不宣称数据库已经发生独立物理 COMMIT。
            publish(new TransactionEvent(
                    TransactionEventType.COMPLETED,
                    executionId,
                    options.name(),
                    TransactionStage.COMPLETION,
                    null,
                    elapsed(startedNanos),
                    null));

            // 6. 事务组件不修改业务结果，直接返回用户 callback 的返回值。
            return value;
        } catch (ProviderTransactionException infrastructureFailure) {
            // 7. BEGIN / COMMIT / ROLLBACK 等底层事务基础设施错误转换成稳定 API 异常。
            TransactionExecutionException apiFailure = new TransactionExecutionException(
                    "Transaction infrastructure failed at stage " + infrastructureFailure.stage()
                            + ", transaction=" + options.name(),
                    executionId,
                    options.name(),
                    infrastructureFailure.stage(),
                    infrastructureFailure.outcome(),
                    infrastructureFailure.getCause() == null
                            ? infrastructureFailure
                            : infrastructureFailure.getCause());

            // 8. rollback 二次失败等诊断信息通过 suppressed exception 保留下来，不覆盖首要异常。
            for (Throwable suppressed : infrastructureFailure.getSuppressed()) {
                apiFailure.addSuppressed(suppressed);
            }

            publish(new TransactionEvent(
                    TransactionEventType.INFRASTRUCTURE_FAILED,
                    executionId,
                    options.name(),
                    infrastructureFailure.stage(),
                    infrastructureFailure.outcome(),
                    elapsed(startedNanos),
                    apiFailure));

            throw apiFailure;
        } catch (RuntimeException | Error businessFailure) {
            // 9. 用户 callback 的业务异常保持原类型；Provider 已经按照底层事务规则调用 rollback。
            // REQUIRED 若复用外部事务，Spring 会按自身语义影响那个同一事务；core 不自行猜最终物理结果。
            publish(new TransactionEvent(
                    TransactionEventType.BUSINESS_FAILED,
                    executionId,
                    options.name(),
                    TransactionStage.EXECUTE,
                    null,
                    elapsed(startedNanos),
                    businessFailure));

            // 10. 如果 rollback 自身也失败，Provider 会把基础设施异常挂到业务异常的 suppressed 列表中。
            // 这里额外发布基础设施失败事件，但仍然不改变最终抛出的主业务异常。
            for (Throwable suppressed : businessFailure.getSuppressed()) {
                if (suppressed instanceof ProviderTransactionException providerFailure) {
                    publish(new TransactionEvent(
                            TransactionEventType.INFRASTRUCTURE_FAILED,
                            executionId,
                            options.name(),
                            providerFailure.stage(),
                            providerFailure.outcome(),
                            elapsed(startedNanos),
                            providerFailure));
                }
            }

            // 11. 不使用统一事务异常覆盖业务异常，调用方仍然可以按原业务异常类型处理。
            throw businessFailure;
        }
    }

    private Duration elapsed(long startedNanos) {
        // 使用单调时钟计算耗时，避免系统时间回拨导致事务耗时出现负数。
        return Duration.ofNanos(Math.max(0L, System.nanoTime() - startedNanos));
    }

    private void publish(TransactionEvent event) {
        // Event/Metrics/Logging 都是旁路能力，监听器故障不能反向破坏真实业务事务。
        for (TransactionEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException ignored) {
                // 一期只做故障隔离；二期接统一 observability 后再补 listener 自身故障指标。
            }
        }
    }
}
