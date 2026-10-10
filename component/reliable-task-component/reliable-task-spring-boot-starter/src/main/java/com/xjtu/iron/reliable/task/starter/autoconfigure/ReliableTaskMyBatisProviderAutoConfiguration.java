package com.xjtu.iron.reliable.task.starter.autoconfigure;

import com.xjtu.iron.reliable.task.api.repository.ReliableTaskRepository;
import com.xjtu.iron.reliable.task.provider.mybatis.mapper.ReliableTaskMapper;
import com.xjtu.iron.reliable.task.provider.mybatis.repository.MyBatisReliableTaskRepository;
import com.xjtu.iron.reliable.task.starter.properties.ReliableTaskProperties;
import com.xjtu.iron.relational.mybatis.MyBatisAccess;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 选择 MyBatis 时注册 Mapper 并创建 ReliableTaskRepository。 */
@AutoConfiguration(
        afterName = "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration",
        before = ReliableTaskAutoConfiguration.class
)
@ConditionalOnClass({SqlSessionFactory.class, ReliableTaskMapper.class})
@EnableConfigurationProperties(ReliableTaskProperties.class)
@ConditionalOnProperty(
        prefix = "xjtu.iron.reliable-task",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@MapperScan(basePackageClasses = ReliableTaskMapper.class)
public class ReliableTaskMyBatisProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ReliableTaskRepository.class)
    public ReliableTaskRepository myBatisReliableTaskRepository(
            MyBatisAccess access,
            ReliableTaskProperties properties) {
        return new MyBatisReliableTaskRepository(access, properties.getTableName());
    }
}
