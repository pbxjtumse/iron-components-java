package com.xjtu.iron.relational.spring.boot.autoconfigure;

import com.xjtu.iron.relational.api.RelationalTemplate;
import com.xjtu.iron.relational.core.connection.SingleDataSourceResolver;
import com.xjtu.iron.relational.integration.spring.SpringTransactionAwareConnectionProvider;
import com.xjtu.iron.relational.spi.ConnectionProvider;
import com.xjtu.iron.relational.spi.DataSourceResolver;
import com.xjtu.iron.relational.spi.SqlExceptionTranslator;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class RelationalAccessAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RelationalAccessAutoConfiguration.class))
            .withBean(DataSource.class, RelationalAccessAutoConfigurationTest::dataSource);

    @Test
    void shouldAutoConfigureSingleDataSourceJdbcFoundation() {
        contextRunner.run(context -> {
            assertThat(context.getBeansOfType(DataSourceResolver.class)).hasSize(1);
            assertThat(context.getBean(DataSourceResolver.class))
                    .isInstanceOf(SingleDataSourceResolver.class);
            assertThat(context.getBeansOfType(ConnectionProvider.class)).hasSize(1);
            assertThat(context.getBean(ConnectionProvider.class))
                    .isInstanceOf(SpringTransactionAwareConnectionProvider.class);
            assertThat(context.getBeansOfType(SqlExceptionTranslator.class)).hasSize(1);
            assertThat(context.getBeansOfType(RelationalTemplate.class)).hasSize(1);
        });
    }

    private static DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:relational_starter;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
