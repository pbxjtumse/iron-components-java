package com.xjtu.iron.transaction.demo.jpa.service;

import com.xjtu.iron.transaction.api.definition.TransactionOptions;
import com.xjtu.iron.transaction.api.definition.TransactionPropagation;
import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import com.xjtu.iron.transaction.demo.jpa.domain.DemoRecordEntity;
import com.xjtu.iron.transaction.demo.jpa.repository.DemoRecordRepository;
import org.springframework.stereotype.Service;

/**
 * JPA 事务场景 Demo。
 */
@Service
public class JpaTransactionDemoService {

    private final TransactionExecutor transactionExecutor;
    private final DemoRecordRepository repository;

    public JpaTransactionDemoService(TransactionExecutor transactionExecutor, DemoRecordRepository repository) {
        this.transactionExecutor = transactionExecutor;
        this.repository = repository;
    }

    public void commit(long id) {
        // 1. 默认 REQUIRED 在当前无事务时创建一个新 JPA 事务。
        transactionExecutor.executeWithoutResult(ctx -> {
            // 2. Repository 操作使用当前 JpaTransactionManager 绑定的 EntityManager。
            repository.save(new DemoRecordEntity(id, "commit"));

            // 3. callback 正常结束后由 SpringTransactionProvider 提交事务。
        });
    }

    public void rollback(long id) {
        // 1. 在事务中保存实体。
        transactionExecutor.executeWithoutResult(ctx -> {
            repository.save(new DemoRecordEntity(id, "rollback"));

            // 2. 模拟业务失败，Provider 负责回滚 JPA 事务并保持原异常。
            throw new IllegalStateException("demo rollback");
        });
    }

    public void outerRollbackInnerRequiresNew() {
        // 1. 内层明确使用 REQUIRES_NEW，验证 JPA 下也遵循相同事务组件语义。
        TransactionOptions innerOptions = TransactionOptions.builder()
                .name("jpa-inner-requires-new")
                .propagation(TransactionPropagation.REQUIRES_NEW)
                .build();

        // 2. 外层 Tx-A 保存 outer 实体。
        transactionExecutor.executeWithoutResult(
                TransactionOptions.builder().name("jpa-outer").build(),
                outer -> {
                    repository.save(new DemoRecordEntity(2001L, "outer"));

                    // 3. 内层 Tx-B 独立保存并提交 inner 实体。
                    transactionExecutor.executeWithoutResult(innerOptions, inner -> {
                        repository.save(new DemoRecordEntity(2002L, "inner"));
                    });

                    // 4. 外层随后失败，只回滚 Tx-A，不影响已提交的 Tx-B。
                    throw new IllegalStateException("rollback outer only");
                });
    }
}
