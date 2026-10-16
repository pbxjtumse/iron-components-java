package com.xjtu.iron.concurrency.starter.configuration;

import com.xjtu.iron.concurrency.starter.properties.XjtuIronConcurrencyProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@EnableConfigurationProperties(XjtuIronConcurrencyProperties.class)
@ConditionalOnProperty(prefix = "xjtu.iron.concurrency", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({
        XjtuIronConcurrencyContextAutoConfiguration.class,
        XjtuIronConcurrencyObservabilityAutoConfiguration.class,
        XjtuIronConcurrencyExecutionAutoConfiguration.class,
        XjtuIronConcurrencyDiagnosticsAutoConfiguration.class,
        XjtuIronConcurrencyManagementAutoConfiguration.class
})
public class XjtuIronConcurrencyAutoConfiguration {
}