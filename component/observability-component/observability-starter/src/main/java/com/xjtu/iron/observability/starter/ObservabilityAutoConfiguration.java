package com.xjtu.iron.observability.starter;

import com.xjtu.iron.observability.api.tracing.ITraceService;
import com.xjtu.iron.observability.api.tracing.context.TraceProviderContext;
import com.xjtu.iron.observability.api.tracing.provider.TraceProvider;
import com.xjtu.iron.observability.api.tracing.resolver.TraceErrorResolver;
import com.xjtu.iron.observability.core.TraceTemplate;
import com.xjtu.iron.observability.core.noop.NoopTraceProviderImpl;
import com.xjtu.iron.observability.otel.impl.OtelTraceProviderImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(
        prefix = "xjtu.iron.observability",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "otelTraceProvider")
    public TraceProvider otelTraceProvider() {
        return new OtelTraceProviderImpl();
    }

    @Bean
    @ConditionalOnMissingBean(name = "noopTraceProvider")
    public TraceProvider noopTraceProvider() {
        return new NoopTraceProviderImpl();
    }
   /*
   * ObservabilityAutoConfiguration
      ↓
    读取配置、收集扩展点
      ↓
    组装 TraceProviderContext
      ↓
    交给 OtelTraceProvider / NoopTraceProvider / 未来 SkyWalkingTraceProvider
      ↓
    创建 ITraceService
   *
   * */
   @Bean
   @ConditionalOnMissingBean
   public ITraceService traceService(
           ObservabilityProperties properties,
           List<TraceErrorResolver> errorResolvers,
           List<TraceProvider> traceProviders,
           Environment environment
   ) {
       String providerName = normalize(properties.getProvider(), "otel");

       TraceProvider provider = traceProviders.stream()
               .filter(item -> providerName.equalsIgnoreCase(item.type().name()))
               .findFirst()
               .orElseThrow(() -> new IllegalStateException(
                       "Unsupported observability trace provider: " + providerName
               ));

       String serviceName = normalize(
               properties.getServiceName(),
               environment.getProperty("spring.application.name", "unknown-service")
       );

       String instrumentationScopeName = normalize(
               properties.getInstrumentationScopeName(),
               "xjtu-iron-observability"
       );

       TraceProviderContext context = new TraceProviderContext(
               serviceName,
               instrumentationScopeName,
               errorResolvers
       );

       return provider.createTraceService(context);
   }


    @Bean
    @ConditionalOnMissingBean
    public TraceTemplate traceTemplate(
            ITraceService traceService,
            ObservabilityProperties properties
    ) {
        return new TraceTemplate(traceService, properties.isTemplateMdcEnabled());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "xjtu.iron.observability",
            name = "method-tracing-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public TraceAspect traceAspect(
            ITraceService traceService,
            ObservabilityProperties properties
    ) {
        return new TraceAspect(traceService, properties.isMethodMdcEnabled());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(OncePerRequestFilter.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(
            prefix = "xjtu.iron.observability",
            name = "web-mdc-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public ObservabilityWebFilter observabilityWebFilter(ITraceService traceService) {
        return new ObservabilityWebFilter(traceService);
    }


    private String normalize(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }
}


