package com.xjtu.iron.relational.integration.spring;

import com.xjtu.iron.relational.api.RelationalTemplate;
import com.xjtu.iron.relational.api.statement.SqlStatement;
import com.xjtu.iron.relational.core.DefaultRelationalTemplate;
import com.xjtu.iron.relational.core.connection.SingleDataSourceResolver;
import com.xjtu.iron.relational.core.exception.StandardSqlExceptionTranslator;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证真实 MyBatis Mapper 与 RelationalTemplate 共享同一个 Spring 本地事务。 */
class MyBatisSharedTransactionIntegrationTest {

    /** 当前测试使用的单库 DataSource。 */
    private DataSource dataSource;
    /** 模拟业务 Repository 的真实 MyBatis Mapper。 */
    private BusinessOrderMapper businessOrderMapper;
    /** 模拟技术表 JDBC Provider 的 RelationalTemplate。 */
    private RelationalTemplate relationalTemplate;
    /** 管理同一个 DataSource 本地事务的 Spring 模板。 */
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:mybatis_relational_tx_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource = h2;
        createTables(dataSource);

        Configuration configuration = new Configuration();
        configuration.addMapper(BusinessOrderMapper.class);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        businessOrderMapper = new SqlSessionTemplate(sqlSessionFactory)
                .getMapper(BusinessOrderMapper.class);

        relationalTemplate = new DefaultRelationalTemplate(
                new SpringTransactionAwareConnectionProvider(
                        new SingleDataSourceResolver(dataSource)
                ),
                new StandardSqlExceptionTranslator()
        );
        transactionTemplate = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource)
        );
    }

    @Test
    void myBatisBusinessAndJdbcIntentShouldCommitTogether() {
        transactionTemplate.executeWithoutResult(status -> {
            businessOrderMapper.insert("order-commit");
            insertIntent("intent-commit");
        });

        assertThat(businessOrderMapper.countById("order-commit")).isEqualTo(1L);
        assertThat(countIntent("intent-commit")).isEqualTo(1L);
    }

    @Test
    void relationalFailureShouldRollbackEarlierMyBatisInsert() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            businessOrderMapper.insert("order-relational-failure");
            relationalTemplate.update(SqlStatement.of(
                    "test.insert-missing-intent-table",
                    "INSERT INTO missing_technical_intent(intent_id) VALUES (?)",
                    "intent-failure"
            ));
        })).isInstanceOf(RuntimeException.class);

        assertThat(businessOrderMapper.countById("order-relational-failure")).isZero();
    }

    @Test
    void myBatisFailureShouldRollbackEarlierRelationalInsert() {
        businessOrderMapper.insert("order-duplicate");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            insertIntent("intent-before-mybatis-failure");
            businessOrderMapper.insert("order-duplicate");
        })).isInstanceOf(RuntimeException.class);

        assertThat(countIntent("intent-before-mybatis-failure")).isZero();
        assertThat(businessOrderMapper.countById("order-duplicate")).isEqualTo(1L);
    }

    private void insertIntent(String intentId) {
        relationalTemplate.update(SqlStatement.of(
                "test.insert-technical-intent",
                "INSERT INTO technical_intent(intent_id) VALUES (?)",
                intentId
        ));
    }

    private long countIntent(String intentId) {
        Long count = new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM technical_intent WHERE intent_id = ?",
                Long.class,
                intentId
        );
        return count == null ? 0L : count;
    }

    private static void createTables(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE business_order (order_id VARCHAR(64) PRIMARY KEY)");
            statement.execute("CREATE TABLE technical_intent (intent_id VARCHAR(64) PRIMARY KEY)");
        }
    }

    /** 测试中模拟业务数据访问的真实 MyBatis Mapper。 */
    public interface BusinessOrderMapper {

        @Insert("INSERT INTO business_order(order_id) VALUES (#{orderId})")
        int insert(@Param("orderId") String orderId);

        @Select("SELECT COUNT(*) FROM business_order WHERE order_id = #{orderId}")
        long countById(@Param("orderId") String orderId);
    }
}
