package com.xjtu.iron.transaction.provider.spring.transaction;

import com.xjtu.iron.transaction.api.definition.TransactionOptions;
import com.xjtu.iron.transaction.api.status.TransactionOutcome;
import com.xjtu.iron.transaction.api.status.TransactionStage;
import com.xjtu.iron.transaction.provider.spring.context.SpringProviderTransactionContext;
import com.xjtu.iron.transaction.provider.spring.mapping.SpringTransactionDefinitionMapper;
import com.xjtu.iron.transaction.spi.exception.ProviderTransactionException;
import com.xjtu.iron.transaction.spi.provider.TransactionProvider;
import com.xjtu.iron.transaction.spi.provider.TransactionProviderCallback;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.Objects;

/**
 * 基于 Spring PlatformTransactionManager 的本地事务 Provider。
 *
 * <p>本 Provider 不关心 MyBatis、JPA 或具体数据库。MyBatis/JDBC 通常使用 DataSource/JdbcTransactionManager，
 * JPA/Hibernate 使用 JpaTransactionManager；这里统一依赖 PlatformTransactionManager。</p>
 */
public final class SpringTransactionProvider implements TransactionProvider {

    private final PlatformTransactionManager transactionManager;

    public SpringTransactionProvider(PlatformTransactionManager transactionManager) {
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
    }

    @Override
    public <T> T execute(TransactionOptions options, TransactionProviderCallback<T> callback) {

        // 1. Provider 作为可独立调用的 SPI，再次检查必要参数，避免出现无法定位的空指针。
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(callback, "callback");

        final DefaultTransactionDefinition springDefinition;
        try {
            // 2. 将组件自己的 TransactionOptions 映射为 Spring TransactionDefinition。
            springDefinition = SpringTransactionDefinitionMapper.map(options);
        } catch (RuntimeException mappingFailure) {
            throw new ProviderTransactionException(
                    "Failed to resolve Spring transaction definition " + options.name(),
                    TransactionStage.RESOLVE,
                    TransactionOutcome.FAILED,
                    mappingFailure);
        }

        final TransactionStatus status;
        try {
            // 3. 真正交给 Spring 处理事务传播：
            // REQUIRED：当前有事务就继续使用，没有就创建；
            // REQUIRES_NEW：挂起当前事务并创建独立新事务；
            // MANDATORY：必须已有事务，否则这里直接失败。
            status = transactionManager.getTransaction(springDefinition);
        } catch (org.springframework.transaction.TransactionException beginFailure) {
            throw new ProviderTransactionException(
                    "Failed to begin or join transaction " + options.name(),
                    TransactionStage.BEGIN,
                    TransactionOutcome.FAILED,
                    beginFailure);
        }

        final T value;
        try {
            // 4. 事务环境已经准备好后，才真正执行用户业务代码。
            // MyBatis/JPA 在这一段执行的数据库操作会参与当前 Spring 管理的事务。
            value = callback.execute(new SpringProviderTransactionContext(status));
        } catch (RuntimeException | Error businessFailure) {
            // 5. 用户业务异常时，把当前 TransactionStatus 交回 Spring 进行 rollback。
            // 如果它是复用已有 REQUIRED 事务，Spring 自己负责相应的 rollback-only 传播语义。
            rollbackAfterBusinessFailure(status, businessFailure, options.name());

            // 6. 保持用户原始业务异常类型，不用 Provider 异常把它覆盖掉。
            throw businessFailure;
        }

        try {
            // 7. callback 正常完成后统一调用 commit(status)。
            // 这里“调用 commit”不等于我们自己声称一定发生了新的物理 COMMIT：
            // Spring 会根据 TransactionStatus 判断是提交新事务、处理 rollback-only，
            // 还是只完成一个参与已有事务的逻辑范围。
            transactionManager.commit(status);
        } catch (UnexpectedRollbackException unexpectedRollback) {
            // 8. Spring 明确告诉调用方事务最终回滚，因此这个异常结果可以确定为 ROLLED_BACK。
            throw new ProviderTransactionException(
                    "Transaction rolled back unexpectedly during commit: " + options.name(),
                    TransactionStage.COMMIT,
                    TransactionOutcome.ROLLED_BACK,
                    unexpectedRollback);
        } catch (org.springframework.transaction.TransactionException commitFailure) {
            // 9. 一般 commit 基础设施异常可能处在“数据库已提交，但客户端没有收到确认”的窗口。
            // 对可靠性组件来说，这种情况不能直接当成“肯定未提交”，必须保留 COMMIT_UNKNOWN。
            throw new ProviderTransactionException(
                    "Transaction commit result is unknown: " + options.name(),
                    TransactionStage.COMMIT,
                    TransactionOutcome.COMMIT_UNKNOWN,
                    commitFailure);
        }

        // 10. 正常完成只返回业务值，不再暴露 CREATED/JOINED、OWNER/PARTICIPANT 等内部传播事实。
        return value;
    }

    private void rollbackAfterBusinessFailure(
            TransactionStatus status,
            Throwable businessFailure,
            String transactionName) {
        try {
            // 把 TransactionStatus 原样交给 Spring。
            // 新事务会被实际回滚；参与已有事务时，具体标记/传播规则由 TransactionManager 负责。
            transactionManager.rollback(status);
        } catch (org.springframework.transaction.TransactionException rollbackFailure) {
            // rollback 失败是“第二故障”，不能把最初的业务异常覆盖掉。
            ProviderTransactionException rollbackInfrastructureFailure =
                    new ProviderTransactionException(
                            "Rollback failed after business failure: " + transactionName,
                            TransactionStage.ROLLBACK,
                            TransactionOutcome.FAILED,
                            rollbackFailure);

            // 通过 suppressed exception 同时保留业务根因和 rollback 基础设施故障。
            businessFailure.addSuppressed(rollbackInfrastructureFailure);
        }
    }
}
