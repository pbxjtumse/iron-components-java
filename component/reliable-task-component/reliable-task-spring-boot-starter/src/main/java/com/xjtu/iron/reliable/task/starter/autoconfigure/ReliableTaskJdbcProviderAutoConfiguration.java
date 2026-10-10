package com.xjtu.iron.reliable.task.starter.autoconfigure;

import com.xjtu.iron.relational.api.RelationalTemplate;
import com.xjtu.iron.reliable.task.api.repository.ReliableTaskRepository;
import com.xjtu.iron.reliable.task.provider.jdbc.repository.JdbcReliableTaskRepository;
import com.xjtu.iron.reliable.task.starter.properties.ReliableTaskProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 选择 JDBC 时创建 ReliableTaskRepository。 */
@AutoConfiguration(
        afterName = "com.xjtu.iron.relational.spring.boot.autoconfigure.RelationalAccessAutoConfiguration",
        before = ReliableTaskAutoConfiguration.class
)
@ConditionalOnClass({RelationalTemplate.class, JdbcReliableTaskRepository.class})
@EnableConfigurationProperties(ReliableTaskProperties.class)
@ConditionalOnProperty(
        prefix = "xjtu.iron.reliable-task",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@ConditionalOnProperty(
        prefix = "xjtu.iron.reliable-task",
        name = "provider",
        havingValue = "jdbc",
        matchIfMissing = true
)
public class ReliableTaskJdbcProviderAutoConfiguration {

    @Bean
    @ConditionalOnBean(RelationalTemplate.class)
    @ConditionalOnMissingBean(ReliableTaskRepository.class)
    public ReliableTaskRepository jdbcReliableTaskRepository(
            RelationalTemplate relationalTemplate,
            ReliableTaskProperties properties) {
        return new JdbcReliableTaskRepository(relationalTemplate, properties.getTableName());
    }
}
