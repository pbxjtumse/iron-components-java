package com.xjtu.iron.reliable.task.provider.jdbc.repository;

import com.xjtu.iron.relational.api.RelationalTemplate;
import com.xjtu.iron.relational.core.DefaultRelationalTemplate;
import com.xjtu.iron.relational.core.connection.DefaultConnectionProvider;
import com.xjtu.iron.relational.core.connection.SingleDataSourceResolver;
import com.xjtu.iron.relational.core.exception.StandardSqlExceptionTranslator;
import com.xjtu.iron.reliable.task.api.repository.ReliableTaskRepository;
import com.xjtu.iron.reliable.task.provider.testkit.ReliableTaskRepositoryContract;
import com.xjtu.iron.reliable.task.provider.testkit.ReliableTaskTestSchema;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;

/** 验证 JDBC Provider 遵守统一的 ReliableTaskRepository 契约。 */
class JdbcReliableTaskRepositoryTest extends ReliableTaskRepositoryContract {

    /** 每个测试用例使用的 JDBC 可靠任务仓储。 */
    private JdbcReliableTaskRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:reliable_task_jdbc_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ReliableTaskTestSchema.create(dataSource);

        RelationalTemplate relational = new DefaultRelationalTemplate(
                new DefaultConnectionProvider(new SingleDataSourceResolver(dataSource)),
                new StandardSqlExceptionTranslator()
        );
        repository = new JdbcReliableTaskRepository(relational);
    }

    @Override
    protected ReliableTaskRepository repository() {
        return repository;
    }
}
