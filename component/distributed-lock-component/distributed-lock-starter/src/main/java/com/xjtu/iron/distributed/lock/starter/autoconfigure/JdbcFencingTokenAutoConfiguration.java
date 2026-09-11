package com.xjtu.iron.distributed.lock.starter.autoconfigure;

import com.xjtu.iron.distributed.lock.provider.jdbc.fencing.JdbcFencingTokenSchemaInitializer;
import com.xjtu.iron.distributed.lock.provider.jdbc.fencing.JdbcSequenceFencingTokenProvider;
import com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider;
import com.xjtu.iron.distributed.lock.starter.properties.JdbcFencingTokenProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/** JDBC fencing token Provider 自动配置。 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass({DataSource.class, JdbcSequenceFencingTokenProvider.class})
@EnableConfigurationProperties(JdbcFencingTokenProperties.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "xjtu.iron.distributed-lock.fencing.jdbc", name = "enabled", havingValue = "true")
public class JdbcFencingTokenAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "xjtu.iron.distributed-lock.fencing.jdbc", name = "initialize-schema", havingValue = "true")
    public InitializingBean jdbcFencingTokenSchemaInitializer(DataSource dataSource, JdbcFencingTokenProperties properties) {
        return () -> new JdbcFencingTokenSchemaInitializer(
                dataSource, properties.getTableName(), properties.getSchemaPlatform()).initialize();
    }

    @Bean
    @ConditionalOnMissingBean(name = "jdbcSequenceFencingTokenProvider")
    public FencingTokenProvider jdbcSequenceFencingTokenProvider(DataSource dataSource, JdbcFencingTokenProperties properties) {
        return new JdbcSequenceFencingTokenProvider(dataSource, properties.getTableName(), properties.getMaxRetries());
    }
}
