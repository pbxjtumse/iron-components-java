package com.xjtu.iron.cache.spring.boot.starter.configuration;

import com.xjtu.iron.cache.core.CacheMetricsRecorder;
import com.xjtu.iron.cache.core.impl.NoopCacheMetricsRecorder;
import com.xjtu.iron.cache.integrations.observability.MicrometerCacheMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 缓存组件可观测性自动装配。
 *
 * <p>负责装配 {@link CacheMetricsRecorder}。</p>
 *
 * <p>如果业务系统存在 Micrometer 的 {@link MeterRegistry}，则使用
 * {@link MicrometerCacheMetricsRecorder} 记录指标；否则使用
 * {@link NoopCacheMetricsRecorder}，保证缓存组件不依赖指标系统也能启动。</p>
 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "xjtu.iron.cache",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class XjtuIronCacheObservabilityAutoConfiguration {

    /**
     * 创建 Micrometer 指标记录器。
     *
     * @param meterRegistry Micrometer 指标注册表
     * @return 缓存指标记录器
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(CacheMetricsRecorder.class)
    public CacheMetricsRecorder micrometerCacheMetricsRecorder(MeterRegistry meterRegistry) {
        return new MicrometerCacheMetricsRecorder(meterRegistry);
    }

    /**
     * 创建空指标记录器。
     *
     * <p>当应用没有引入 Micrometer 或用户没有自定义 CacheMetricsRecorder 时使用。</p>
     *
     * @return 空指标记录器
     */
    @Bean
    @ConditionalOnMissingBean(CacheMetricsRecorder.class)
    public CacheMetricsRecorder noopCacheMetricsRecorder() {
        return new NoopCacheMetricsRecorder();
    }
}
