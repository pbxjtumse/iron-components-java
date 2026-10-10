package com.xjtu.iron.relational.spring.boot.autoconfigure;

import com.xjtu.iron.relational.mybatis.MyBatisAccess;
import com.xjtu.iron.relational.mybatis.MyBatisAccessResolver;
import com.xjtu.iron.relational.mybatis.MyBatisMapperWork;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证公共 MyBatis Access 的单库默认装配与用户覆盖语义。 */
class RelationalAccessAutoConfigurationTest {

    /** 同时加载官方 MyBatis 与组件 Access 自动配置的上下文运行器。 */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MybatisAutoConfiguration.class,
                    RelationalAccessAutoConfiguration.class));

    @Test
    void shouldAutoConfigureAccessAndResolverForSingleDataSource() {
        contextRunner
                .withBean(DataSource.class, RelationalAccessAutoConfigurationTest::dataSource)
                .run(context -> {
                    assertThat(context).hasSingleBean(MyBatisAccess.class);
                    assertThat(context).hasSingleBean(MyBatisAccessResolver.class);
                });
    }

    @Test
    void shouldNotGuessAccessWhenMultipleDataSourcesExist() {
        contextRunner
                .withBean("firstDataSource", DataSource.class,
                        RelationalAccessAutoConfigurationTest::dataSource)
                .withBean("secondDataSource", DataSource.class,
                        RelationalAccessAutoConfigurationTest::dataSource)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MyBatisAccess.class);
                    assertThat(context).doesNotHaveBean(MyBatisAccessResolver.class);
                });
    }

    @Test
    void shouldPreserveUserProvidedAccess() {
        MyBatisAccess custom = new NoopMyBatisAccess();
        contextRunner
                .withBean(DataSource.class, RelationalAccessAutoConfigurationTest::dataSource)
                .withBean(MyBatisAccess.class, () -> custom)
                .run(context -> {
                    assertThat(context.getBean(MyBatisAccess.class)).isSameAs(custom);
                    assertThat(context).hasSingleBean(MyBatisAccessResolver.class);
                });
    }

    private static DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:relational_starter_"
                + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    /** 只用于验证条件装配退让行为的用户自定义 Access。 */
    private static final class NoopMyBatisAccess implements MyBatisAccess {

        @Override
        public <M, T> T execute(
                String operationName,
                Class<M> mapperType,
                MyBatisMapperWork<M, T> work
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public <M, T> T executeInCurrentTransaction(
                String operationName,
                Class<M> mapperType,
                MyBatisMapperWork<M, T> work
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public <M, T> T executeInNewTransaction(
                String operationName,
                Class<M> mapperType,
                MyBatisMapperWork<M, T> work
        ) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public boolean supportsCurrentTransactionParticipation() {
            return false;
        }
    }
}
