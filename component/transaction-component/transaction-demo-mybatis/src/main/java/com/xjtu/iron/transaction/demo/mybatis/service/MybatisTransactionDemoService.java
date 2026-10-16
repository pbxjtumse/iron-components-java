package com.xjtu.iron.transaction.demo.mybatis.service;

import com.xjtu.iron.transaction.api.definition.TransactionOptions;
import com.xjtu.iron.transaction.api.definition.TransactionPropagation;
import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import com.xjtu.iron.transaction.demo.mybatis.domain.DemoRecord;
import com.xjtu.iron.transaction.demo.mybatis.mapper.DemoRecordMapper;
import org.springframework.stereotype.Service;

/**
 * MyBatis 事务场景 Demo。
 *
 * <p>SQL 在 XML 中，事务边界由 TransactionExecutor 显式控制；Mapper 本身不知道事务组件的存在。</p>
 */
@Service
public class MybatisTransactionDemoService {

    private final TransactionExecutor transactionExecutor;
    private final DemoRecordMapper mapper;

    public MybatisTransactionDemoService(TransactionExecutor transactionExecutor, DemoRecordMapper mapper) {
        this.transactionExecutor = transactionExecutor;
        this.mapper = mapper;
    }

    public void commit(long id) {
        // 1. 使用默认 REQUIRED：当前没有事务，因此 TransactionExecutor 会创建一个新事务。
        transactionExecutor.executeWithoutResult(ctx -> {
            // 2. MyBatis Mapper 获取与当前 Spring 事务绑定的数据库连接并执行 XML 中的 INSERT。
            mapper.insert(new DemoRecord(id, "commit"));

            // 3. callback 正常返回，Provider 随后提交事务，因此记录最终可见。
        });
    }

    public void rollback(long id) {
        // 1. 默认 REQUIRED 新建事务，并在该事务内执行 INSERT。
        transactionExecutor.executeWithoutResult(ctx -> {
            mapper.insert(new DemoRecord(id, "rollback"));

            // 2. 主动抛业务异常模拟后续业务失败；Provider 捕获后会先 rollback，再把原异常继续抛出。
            throw new IllegalStateException("demo rollback");
        });
    }

    public void outerRollbackInnerRequiresNew() {
        // 1. 内层事务明确使用 REQUIRES_NEW，用于验证“独立提交的小事务”。
        TransactionOptions innerOptions = TransactionOptions.builder()
                .name("mybatis-inner-requires-new")
                .propagation(TransactionPropagation.REQUIRES_NEW)
                .build();

        // 2. 外层默认 REQUIRED 创建 Tx-A。
        transactionExecutor.executeWithoutResult(
                TransactionOptions.builder().name("mybatis-outer").build(),
                outer -> {
                    // 3. outer 数据写入 Tx-A，此时还没有提交。
                    mapper.insert(new DemoRecord(1001L, "outer"));

                    // 4. 内层 REQUIRES_NEW：Spring 挂起 Tx-A，创建 Tx-B。
                    transactionExecutor.executeWithoutResult(innerOptions, inner -> {
                        // 5. inner 数据写入 Tx-B；inner callback 正常结束后 Tx-B 独立 COMMIT。
                        mapper.insert(new DemoRecord(1002L, "inner"));
                    });

                    // 6. 恢复 Tx-A 后故意失败，最终只回滚 outer 的 1001，已经提交的 1002 保留。
                    throw new IllegalStateException("rollback outer only");
                });
    }

    public void outerRollbackInnerRequired() {
        // 1. 外层 REQUIRED 创建 Tx-A。
        transactionExecutor.executeWithoutResult(
                TransactionOptions.builder().name("mybatis-outer-required").build(),
                outer -> {
                    mapper.insert(new DemoRecord(1101L, "outer-required"));

                    // 2. 内层仍是默认 REQUIRED，因此只加入 Tx-A，没有产生新的物理事务。
                    transactionExecutor.executeWithoutResult(inner -> {
                        mapper.insert(new DemoRecord(1102L, "inner-required"));
                    });

                    // 3. 外层失败时 Tx-A 整体回滚，所以 1101、1102 都不会留下。
                    throw new IllegalStateException("rollback same physical transaction");
                });
    }

    public void rollbackOnly(long id) {
        // 1. 正常执行 SQL，但不抛异常。
        transactionExecutor.executeWithoutResult(ctx -> {
            mapper.insert(new DemoRecord(id, "rollback-only"));

            // 2. 显式标记 rollback-only；callback 虽然正常结束，commit 阶段仍会执行回滚。
            ctx.setRollbackOnly();
        });
    }
}
