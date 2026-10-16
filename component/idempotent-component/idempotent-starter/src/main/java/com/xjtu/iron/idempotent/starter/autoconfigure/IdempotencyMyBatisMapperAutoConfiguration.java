package com.xjtu.iron.idempotent.starter.autoconfigure;

import com.xjtu.iron.idempotent.provider.mybatis.mapper.IdempotencyMapper;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** 单 DataSource 模式下把 Idempotent Mapper 注册到业务 MyBatis 会话工厂。 */
@AutoConfiguration(
        afterName = "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration",
        before = IdempotencyAutoConfiguration.class
)
@ConditionalOnBean(SqlSessionTemplate.class)
@ConditionalOnProperty(
        prefix = "xjtu.iron.idempotent.mybatis.direct",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
@MapperScan(basePackageClasses = IdempotencyMapper.class)
public class IdempotencyMyBatisMapperAutoConfiguration {
}
