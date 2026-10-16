package com.xjtu.iron.retry.config.properties;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证配置继承中的显式清空和循环检测。 */
class RetryPolicyPropertiesResolverTest {

    /** 验证子策略可以显式清空父策略异常列表。 */
    @Test
    void shouldAllowChildPolicyToClearInheritedLists() {
        RetryProperties properties = new RetryProperties();
        RetryProperties.PolicyProperties base = new RetryProperties.PolicyProperties();
        base.setRetryOn(List.of("java.io.IOException"));
        RetryProperties.PolicyProperties child = new RetryProperties.PolicyProperties();
        child.setBasePolicy("base");
        child.setRetryOn(List.of());
        Map<String, RetryProperties.PolicyProperties> policies = new LinkedHashMap<>();
        policies.put("base", base);
        policies.put("child", child);
        properties.setPolicies(policies);
        Map<String, ResolvedRetryPolicyProperties> resolved =
                new RetryPolicyPropertiesResolver().resolve(properties);
        assertEquals(List.of(), resolved.get("child").retryOn);
    }

    /** 验证循环继承错误包含完整路径。 */
    @Test
    void shouldReportCircularInheritancePath() {
        RetryProperties properties = new RetryProperties();
        RetryProperties.PolicyProperties first = new RetryProperties.PolicyProperties();
        first.setBasePolicy("second");
        RetryProperties.PolicyProperties second = new RetryProperties.PolicyProperties();
        second.setBasePolicy("first");
        properties.setPolicies(Map.of("first", first, "second", second));
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RetryPolicyPropertiesResolver().resolve(properties)
        );
        assertEquals(true, exception.getMessage().contains("first"));
        assertEquals(true, exception.getMessage().contains("second"));
    }
}
