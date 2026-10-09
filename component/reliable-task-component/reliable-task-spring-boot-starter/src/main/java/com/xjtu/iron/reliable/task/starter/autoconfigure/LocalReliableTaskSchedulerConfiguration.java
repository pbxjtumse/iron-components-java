package com.xjtu.iron.reliable.task.starter.autoconfigure;

import com.xjtu.iron.reliable.task.api.scan.ReliableTaskScanRequest;
import com.xjtu.iron.reliable.task.api.scan.ReliableTaskScanner;
import com.xjtu.iron.reliable.task.core.policy.ReliableTaskRuntimePolicy;
import com.xjtu.iron.reliable.task.starter.properties.ReliableTaskProperties;
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

    /** 本地扫描失败时使用的系统日志记录器。 */
    private static final System.Logger LOG = System.getLogger(LocalReliableTaskSchedulerConfiguration.class.getName());

    /** 执行单桶扫描的组件入口。 */
    private final ReliableTaskScanner scanner;
    /** 提供扫描桶总数的运行时策略。 */
    private final ReliableTaskRuntimePolicy runtimePolicy;
    /** 提供本地扫描范围和批量大小的外部配置。 */
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
