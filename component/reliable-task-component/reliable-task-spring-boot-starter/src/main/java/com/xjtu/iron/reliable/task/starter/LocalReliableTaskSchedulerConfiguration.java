package com.xjtu.iron.reliable.task.starter;

import com.xjtu.iron.reliable.task.api.scan.ReliableTaskScanRequest;
import com.xjtu.iron.reliable.task.api.scan.ReliableTaskScanner;
import com.xjtu.iron.reliable.task.core.config.ReliableTaskRuntimePolicy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 开发和小规模部署使用的本地扫描触发器。
 *
 * <p>默认关闭。多实例同时启用不会破坏正确性，但会产生重复扫描；真正执行仍由 Repository CAS 决定。</p>
 */
@AutoConfiguration(after = ReliableTaskAutoConfiguration.class)
@EnableScheduling
@ConditionalOnBean(ReliableTaskScanner.class)
@ConditionalOnProperty(
        prefix = "xjtu.iron.reliable-task.local-scheduler",
        name = "enabled",
        havingValue = "true"
)
public class LocalReliableTaskSchedulerConfiguration {

    private static final System.Logger LOG = System.getLogger(LocalReliableTaskSchedulerConfiguration.class.getName());

    private final ReliableTaskScanner scanner;
    private final ReliableTaskRuntimePolicy runtimePolicy;
    private final ReliableTaskProperties properties;

    public LocalReliableTaskSchedulerConfiguration(
            ReliableTaskScanner scanner,
            ReliableTaskRuntimePolicy runtimePolicy,
            ReliableTaskProperties properties) {
        this.scanner = scanner;
        this.runtimePolicy = runtimePolicy;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${xjtu.iron.reliable-task.local-scheduler.fixed-delay:5s}")
    public void scanAllBuckets() {
        ReliableTaskProperties.LocalScheduler local = properties.getLocalScheduler();
        for (int bucket = 0; bucket < runtimePolicy.getScanBucketCount(); bucket++) {
            try {
                scanner.scan(new ReliableTaskScanRequest(local.getStoreName(), bucket, local.getBatchSize()));
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.ERROR, "Reliable Task local scan failed, bucket=" + bucket, failure);
            }
        }
    }
}
