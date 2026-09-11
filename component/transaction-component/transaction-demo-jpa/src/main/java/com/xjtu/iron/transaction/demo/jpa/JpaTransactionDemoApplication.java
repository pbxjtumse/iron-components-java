package com.xjtu.iron.transaction.demo.jpa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * JPA 事务 Demo 启动类。
 */
@SpringBootApplication(scanBasePackages = "com.xjtu.iron.transaction.demo.jpa")
public class JpaTransactionDemoApplication {

    public static void main(String[] args) {
        // 启动后 Spring Data JPA 提供 JpaTransactionManager，事务组件仍只依赖 PlatformTransactionManager。
        SpringApplication.run(JpaTransactionDemoApplication.class, args);
    }
}
