package com.xjtu.iron.distributed.lock.starter.redisson;

import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ListableBeanFactory;

import java.util.Map;

/** 选择组件使用的 RedissonClient，支持多 Bean 时通过 beanName 显式指定。 */
public final class RedissonClientSelector {

    private RedissonClientSelector() {}

    public static RedissonClient select(ListableBeanFactory beanFactory, String beanName) {
        if (hasText(beanName)) {
            return beanFactory.getBean(beanName.trim(), RedissonClient.class);
        }
        Map<String, RedissonClient> clients = beanFactory.getBeansOfType(RedissonClient.class);
        if (clients.size() == 1) {
            return clients.values().iterator().next();
        }
        if (clients.isEmpty()) {
            throw new IllegalStateException("no RedissonClient bean is available");
        }
        throw new IllegalStateException(
                "multiple RedissonClient beans found " + clients.keySet()
                        + "; configure xjtu.iron.distributed-lock.redisson.client-bean-name");
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
