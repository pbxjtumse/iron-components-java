package com.xjtu.iron.observability.api.tracing.context;

import com.xjtu.iron.observability.api.tracing.resolver.TraceErrorResolver;

import java.util.Collections;
import java.util.List;

/**
 * TraceProvider 创建 ITraceService 所需的上下文。
 *
 * <p>这是你自己的可观测 SDK 上下文，不是 OpenTelemetry Context，
 * 也不是 SkyWalking Context。</p>
 *
 * <p>它用于把 starter 层读取到的配置和扩展点传递给具体 Provider。</p>
 */
public class TraceProviderContext {

    /**
     * 当前业务服务名。
     *
     * <p>例如：</p>
     * <pre>
     * order-service
     * payment-service
     * observability-demo-app
     * </pre>
     *
     * <p>如果使用 Java Agent，真正上报到后端的服务名通常由 Agent 参数控制：</p>
     * <pre>
     * -Dotel.service.name=order-service
     * -Dskywalking.agent.service_name=order-service
     * </pre>
     */
    private final String serviceName;

    /**
     * 当前自定义埋点 SDK 的 instrumentation scope 名称。
     *
     * <p>这个字段主要给 OpenTelemetry Provider 使用。</p>
     *
     * <p>它表示：当前自定义 Span 是由哪套 SDK / instrumentation 创建的。</p>
     *
     * <p>例如：</p>
     * <pre>
     * xjtu-iron-observability
     * company-observability-sdk
     * cola-observability-starter
     * </pre>
     *
     * <p>它不是业务服务名。</p>
     */
    private final String instrumentationScopeName;

    /**
     * 异常解析器列表。
     *
     * <p>用于把业务异常转换成 Span 标签。</p>
     */
    private final List<TraceErrorResolver> errorResolvers;

    public TraceProviderContext(
            String serviceName,
            String instrumentationScopeName,
            List<TraceErrorResolver> errorResolvers
    ) {
        this.serviceName = serviceName;
        this.instrumentationScopeName = instrumentationScopeName;
        this.errorResolvers = errorResolvers == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(errorResolvers);
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getInstrumentationScopeName() {
        return instrumentationScopeName;
    }

    public List<TraceErrorResolver> getErrorResolvers() {
        return errorResolvers;
    }
}