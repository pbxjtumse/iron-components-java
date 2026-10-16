package com.xjtu.iron.message.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
/**
 * message-component 的 Spring Boot 演示应用入口。
 *
 * <p>该 demo 用于验证 Kafka、Pulsar、RocketMQ4 的单 Provider 收发、三 Provider 并行收发，
 * 以及二期可靠发送返回的 retryStatus、attempts、retryId 等字段，不作为生产业务工程模板。</p>
 */
@SpringBootApplication

public class MessageDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageDemoApplication.class, args);
    }
}
