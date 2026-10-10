package com.xjtu.iron.distributed.lock.starter.autoconfigure;

import com.xjtu.iron.distributed.lock.provider.mybatis.fencing.FencingTokenMapper;
import com.xjtu.iron.distributed.lock.provider.mybatis.fencing.MyBatisSequenceFencingTokenProvider;
import com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider;
import com.xjtu.iron.distributed.lock.starter.properties.MyBatisFencingTokenProperties;
import com.xjtu.iron.relational.mybatis.MyBatisAccess;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.util.Locale;

/** MyBatis fencing token Provider 自动配置。 */
@AutoConfiguration(afterName = {
        "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration",
        "com.xjtu.iron.relational.spring.boot.autoconfigure.RelationalAccessAutoConfiguration"
})
@ConditionalOnClass({FencingTokenMapper.class, MyBatisAccess.class})
@ConditionalOnBean(MyBatisAccess.class)
@EnableConfigurationProperties(MyBatisFencingTokenProperties.class)
@ConditionalOnProperty(
        prefix = "xjtu.iron.distributed-lock.fencing.mybatis",
        name = "enabled",
        havingValue = "true"
)
@MapperScan(basePackageClasses = FencingTokenMapper.class)
public class MyBatisFencingTokenAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(
            prefix = "xjtu.iron.distributed-lock.fencing.mybatis",
            name = "initialize-schema",
            havingValue = "true"
    )
    public InitializingBean myBatisFencingTokenSchemaInitializer(
            DataSource dataSource,
            MyBatisFencingTokenProperties properties
    ) {
        return () -> new ResourceDatabasePopulator(new ClassPathResource(
                "META-INF/iron-lock/mybatis/schema-" + requirePlatform(properties) + ".sql"
        )).execute(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(name = "myBatisSequenceFencingTokenProvider")
    public FencingTokenProvider myBatisSequenceFencingTokenProvider(
            MyBatisAccess access,
            MyBatisFencingTokenProperties properties
    ) {
        return new MyBatisSequenceFencingTokenProvider(
                access,
                properties.getTableName(),
                properties.getMaxRetries()
        );
    }

    private static String requirePlatform(MyBatisFencingTokenProperties properties) {
        String platform = properties.getSchemaPlatform() == null
                ? "mysql"
                : properties.getSchemaPlatform().trim().toLowerCase(Locale.ROOT);
        if (!"mysql".equals(platform) && !"h2".equals(platform)) {
            throw new IllegalArgumentException(
                    "unsupported MyBatis fencing schema platform: " + platform
            );
        }
        return platform;
    }
}
