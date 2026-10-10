package com.xjtu.iron.reliable.task.provider.mybatis.repository;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;
import com.xjtu.iron.reliable.task.api.model.ReliableTaskKey;
import com.xjtu.iron.reliable.task.api.state.ReliableTaskStatus;
import com.xjtu.iron.reliable.task.provider.mybatis.mapper.ReliableTaskMapper;
import com.xjtu.iron.reliable.task.provider.testkit.ReliableTaskTestSchema;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证业务 MyBatis Mapper 与 Reliable Task MyBatis Provider 的同库事务原子性。 */
class MyBatisProviderSharedTransactionIntegrationTest {

    /** 模拟业务订单访问的 MyBatis Mapper。 */
    private BusinessOrderMapper businessOrderMapper;
    /** 使用真实 MyBatis Mapper 的可靠任务仓储。 */
    private MyBatisReliableTaskRepository repository;
    /** 使用不存在表名、专门制造 Provider 失败的可靠任务仓储。 */
    private MyBatisReliableTaskRepository missingTableRepository;
    /** 管理业务表和技术表共同本地事务的模板。 */
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:reliable_task_mybatis_tx_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ReliableTaskTestSchema.create(dataSource);
        createBusinessTable(dataSource);

        Configuration configuration = new Configuration();
        configuration.addMapper(BusinessOrderMapper.class);
        configuration.addMapper(ReliableTaskMapper.class);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        SqlSessionTemplate sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);

        businessOrderMapper = sqlSessionTemplate.getMapper(BusinessOrderMapper.class);
        ReliableTaskMapper taskMapper = sqlSessionTemplate.getMapper(ReliableTaskMapper.class);
        repository = new MyBatisReliableTaskRepository(taskMapper);
        missingTableRepository = new MyBatisReliableTaskRepository(taskMapper, "missing_reliable_task");
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void businessAndTaskShouldCommitTogether() {
        transactionTemplate.executeWithoutResult(status -> {
            businessOrderMapper.insert("order-commit");
            repository.create(task("task-commit"));
        });

        assertThat(businessOrderMapper.countById("order-commit")).isEqualTo(1L);
        assertThat(repository.find(ReliableTaskKey.defaults("task-commit"))).isPresent();
    }

    @Test
    void taskProviderFailureShouldRollbackEarlierBusinessInsert() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            businessOrderMapper.insert("order-provider-failure");
            missingTableRepository.create(task("task-provider-failure"));
        })).isInstanceOf(RuntimeException.class);

        assertThat(businessOrderMapper.countById("order-provider-failure")).isZero();
    }

    @Test
    void businessFailureShouldRollbackEarlierTaskInsert() {
        businessOrderMapper.insert("order-duplicate");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            repository.create(task("task-before-business-failure"));
            businessOrderMapper.insert("order-duplicate");
        })).isInstanceOf(RuntimeException.class);

        assertThat(repository.find(ReliableTaskKey.defaults("task-before-business-failure"))).isEmpty();
        assertThat(businessOrderMapper.countById("order-duplicate")).isEqualTo(1L);
    }

    private static ReliableTask task(String taskId) {
        Instant now = Instant.parse("2026-10-10T00:00:00Z");
        return new ReliableTask(
                ReliableTaskKey.defaults(taskId),
                "demo",
                "business-1",
                "{}",
                "order-1",
                7,
                ReliableTaskStatus.READY,
                0,
                3,
                now,
                null,
                null,
                0,
                null,
                null,
                now,
                now,
                null
        );
    }

    private static void createBusinessTable(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE business_order (order_id VARCHAR(64) PRIMARY KEY)");
        }
    }

    /** 测试中模拟业务数据访问的 MyBatis Mapper。 */
    public interface BusinessOrderMapper {

        @Insert("INSERT INTO business_order(order_id) VALUES (#{orderId})")
        int insert(@Param("orderId") String orderId);

        @Select("SELECT COUNT(*) FROM business_order WHERE order_id = #{orderId}")
        long countById(@Param("orderId") String orderId);
    }
}
