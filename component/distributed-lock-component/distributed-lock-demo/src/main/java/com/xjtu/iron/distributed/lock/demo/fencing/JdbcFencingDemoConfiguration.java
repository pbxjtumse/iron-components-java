package com.xjtu.iron.distributed.lock.demo.fencing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** MyBatis fencing 业务防旧写演示配置。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "xjtu.iron.distributed-lock.fencing.mybatis", name = "enabled", havingValue = "true")
public class JdbcFencingDemoConfiguration {

    @Bean
    public JdbcFencedDemoRepository jdbcFencedDemoRepository(JdbcTemplate jdbcTemplate) {
        JdbcFencedDemoRepository repository = new JdbcFencedDemoRepository(jdbcTemplate);
        repository.initializeSchema();
        return repository;
    }
}
