package com.xjtu.iron.reliable.task.starter.autoconfigure;

import com.xjtu.iron.relational.spring.boot.autoconfigure.RelationalAccessAutoConfiguration;
import com.xjtu.iron.reliable.task.api.client.ReliableTaskClient;
import com.xjtu.iron.reliable.task.api.client.ReliableTaskAdminClient;
import com.xjtu.iron.reliable.task.api.repository.ReliableTaskRepository;
import com.xjtu.iron.reliable.task.api.scan.ReliableTaskScanner;
import com.xjtu.iron.reliable.task.starter.properties.ReliableTaskProperties;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReliableTaskAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RelationalAccessAutoConfiguration.class,
                    ReliableTaskAutoConfiguration.class,
                    LocalReliableTaskSchedulerConfiguration.class
            ))
            .withBean(DataSource.class, ReliableTaskAutoConfigurationTest::dataSource);

    @Test
    void shouldCreateCoreRuntimeButKeepLocalSchedulerDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ReliableTaskRepository.class);
            assertThat(context).hasSingleBean(ReliableTaskClient.class);
            assertThat(context).hasSingleBean(ReliableTaskAdminClient.class);
            assertThat(context).hasSingleBean(ReliableTaskScanner.class);
            assertThat(context).doesNotHaveBean(LocalReliableTaskSchedulerConfiguration.class);
        });
    }

    @Test
    void shouldAllowDisablingTheWholeComponent() {
        contextRunner
                .withPropertyValues("xjtu.iron.reliable-task.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ReliableTaskClient.class));
    }

    private static DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:reliable_task_starter;MODE=MySQL;DB_CLOSE_DELAY=-1");
        return dataSource;
    }
}
