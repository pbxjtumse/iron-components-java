package com.xjtu.iron.reliable.task.provider.mybatis.repository;

import com.xjtu.iron.reliable.task.api.repository.ReliableTaskRepository;
import com.xjtu.iron.reliable.task.provider.mybatis.mapper.ReliableTaskMapper;
import com.xjtu.iron.reliable.task.provider.testkit.ReliableTaskRepositoryContract;
import com.xjtu.iron.reliable.task.provider.testkit.ReliableTaskTestSchema;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/** 验证 MyBatis Provider 遵守统一的 ReliableTaskRepository 契约。 */
class MyBatisReliableTaskRepositoryTest extends ReliableTaskRepositoryContract {

    /** 每个测试用例持有的自动提交 MyBatis 会话。 */
    private SqlSession sqlSession;
    /** 每个测试用例使用的 MyBatis 可靠任务仓储。 */
    private MyBatisReliableTaskRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:reliable_task_mybatis_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ReliableTaskTestSchema.create(dataSource);

        Environment environment = new Environment(
                "reliable-task-provider-contract",
                new JdbcTransactionFactory(),
                dataSource
        );
        Configuration configuration = new Configuration(environment);
        configuration.addMapper(ReliableTaskMapper.class);
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        sqlSession = sqlSessionFactory.openSession(true);
        repository = new MyBatisReliableTaskRepository(
                sqlSession.getMapper(ReliableTaskMapper.class)
        );
    }

    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    @Override
    protected ReliableTaskRepository repository() {
        return repository;
    }
}
