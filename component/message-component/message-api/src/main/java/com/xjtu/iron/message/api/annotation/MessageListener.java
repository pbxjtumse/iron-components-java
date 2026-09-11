package com.xjtu.iron.message.api.annotation;

import java.lang.annotation.*;

/**
 * 声明式消息监听注解，预留给后续 Spring Boot Starter 扫描和自动订阅使用。
 *
 * <p>当前组件的一期和二期主要使用显式 {@code MessageTemplate.subscribe} 完成订阅，
 * 这个注解用于后续把消费端写法简化成类似 {@code @MessageListener} 的形式。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MessageListener {

    /** 支持 ${...} 占位符。 */
    String topic();

    /** Pulsar subscription / Kafka group / RocketMQ consumer group。支持 ${...} 占位符。 */
    String subscription() default "";

    String consumerGroup() default "";
}
