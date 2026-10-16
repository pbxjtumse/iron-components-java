package com.xjtu.iron.start;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 最终 Spring Boot 应用入口。
 *
 * <p>
 * 业务层、领域层和基础设施层继续通过组件扫描注册；技术组件 Starter 的自动配置类
 * 则由 {@code AutoConfiguration.imports} 导入，不依赖业务应用扫描。
 * </p>
 */
@SpringBootApplication
@ComponentScan(
        basePackages = {"com.xjtu.iron", "com.alibaba.cola"},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.xjtu\\.iron\\..*\\.starter\\..*"
        )
)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
