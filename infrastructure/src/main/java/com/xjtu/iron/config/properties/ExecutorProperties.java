package com.xjtu.iron.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 声明“线程池长什么样”
 * @author pbxjtu
 * @date 2025/12/29
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "executors")
public class ExecutorProperties {
    private Map<String, ExecutorItem> items = new HashMap<>();
    @Getter
    @Setter
    public static class ExecutorItem {
        private String name;
        private int coreSize;
        private int maxSize;
        private long keepAliveSeconds;
        private int queueSize;
        private Set<String> tags;

        // 构造方法
        public ExecutorItem() {}
    }
}

