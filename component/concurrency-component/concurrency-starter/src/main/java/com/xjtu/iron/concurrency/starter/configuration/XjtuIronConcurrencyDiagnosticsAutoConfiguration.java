package com.xjtu.iron.concurrency.starter.configuration;

import com.xjtu.iron.concurrency.api.execution.pool.ThreadPoolManager;
import com.xjtu.iron.concurrency.api.execution.pool.ThreadPoolSnapshot;
import com.xjtu.iron.concurrency.starter.properties.XjtuIronConcurrencyProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * 并发组件诊断自动装配。
 */
@AutoConfiguration(after = XjtuIronConcurrencyExecutionAutoConfiguration.class)
@ConditionalOnProperty(prefix = "xjtu.iron.concurrency.diagnostics", name = "enabled", havingValue = "true", matchIfMissing = true)
public class XjtuIronConcurrencyDiagnosticsAutoConfiguration {

    @Bean(name = "ironConcurrencyHealthIndicator")
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean(name = "ironConcurrencyHealthIndicator")
    public HealthIndicator ironConcurrencyHealthIndicator(
            ThreadPoolManager threadPoolManager,
            XjtuIronConcurrencyProperties properties
    ) {
        return () -> {
            Map<String, ThreadPoolSnapshot> snapshots = threadPoolManager.snapshots();
            boolean busy = false;
            for (ThreadPoolSnapshot snapshot : snapshots.values()) {
                boolean currentBusy = snapshot.getActiveUsageRatio() >= properties.getDiagnostics().getActiveUsageWarnThreshold()
                        || snapshot.getQueueUsageRatio() >= properties.getDiagnostics().getQueueUsageWarnThreshold();
                snapshot.setBusy(currentBusy);
                busy = busy || currentBusy;
            }

            Health.Builder builder = busy && properties.getDiagnostics().isDownWhenBusy()
                    ? Health.down()
                    : Health.up();

            return builder
                    .withDetail("component", "xjtu-iron-concurrency")
                    .withDetail("busy", busy)
                    .withDetail("activeUsageWarnThreshold", properties.getDiagnostics().getActiveUsageWarnThreshold())
                    .withDetail("queueUsageWarnThreshold", properties.getDiagnostics().getQueueUsageWarnThreshold())
                    .withDetail("threadPools", snapshots)
                    .build();
        };
    }
}
