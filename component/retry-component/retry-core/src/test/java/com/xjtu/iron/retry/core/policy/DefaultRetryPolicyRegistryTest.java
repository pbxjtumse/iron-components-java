package com.xjtu.iron.retry.core.policy;

import com.xjtu.iron.retry.api.policy.RetryPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 验证默认命名策略注册表的显式覆盖语义。 */
class DefaultRetryPolicyRegistryTest {

    /** 验证注册、查询和稳定排序快照。 */
    @Test
    void shouldRegisterResolveAndExposeSortedSnapshot() {
        DefaultRetryPolicyRegistry registry = new DefaultRetryPolicyRegistry();
        RetryPolicy second = RetryPolicy.builder("z-policy").build();
        RetryPolicy first = RetryPolicy.builder("a-policy").build();
        registry.register(second);
        registry.register(first);
        assertSame(first, registry.getRequired("a-policy"));
        assertEquals(
                java.util.List.of("a-policy", "z-policy"),
                registry.policyNames()
        );
        assertEquals(
                java.util.List.of("a-policy", "z-policy"),
                registry.snapshot().keySet().stream().toList()
        );
    }

    /** 验证 register 不再静默覆盖同名策略。 */
    @Test
    void shouldRejectDuplicateRegistration() {
        DefaultRetryPolicyRegistry registry = new DefaultRetryPolicyRegistry();
        registry.register(RetryPolicy.builder("duplicate").maxAttempts(2).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(
                        RetryPolicy.builder("duplicate").maxAttempts(4).build()
                )
        );
    }

    /** 验证 replace 明确允许替换同名策略。 */
    @Test
    void shouldReplacePolicyExplicitly() {
        DefaultRetryPolicyRegistry registry = new DefaultRetryPolicyRegistry();
        registry.register(RetryPolicy.builder("replaceable").maxAttempts(2).build());
        RetryPolicy replacement = RetryPolicy.builder("replaceable")
                .maxAttempts(5)
                .build();
        registry.replace(replacement);
        assertSame(replacement, registry.getRequired("replaceable"));
    }

    /** 验证必需策略不存在时快速失败。 */
    @Test
    void shouldFailWhenRequiredPolicyDoesNotExist() {
        DefaultRetryPolicyRegistry registry = new DefaultRetryPolicyRegistry();
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.getRequired("missing")
        );
    }
}
