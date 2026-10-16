package com.xjtu.iron.idempotent.core.policy;

import com.xjtu.iron.idempotent.api.policy.IdempotencyMode;
import com.xjtu.iron.idempotent.api.policy.IdempotencyPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultIdempotencyPolicyRegistryTest {

    @Test
    void namedPolicyShouldResolveAndCanonicalizeWindowedLifecycle() {
        IdempotencyPolicy windowed = IdempotencyPolicy.builder()
                .name("api-submit")
                .mode(IdempotencyMode.WINDOWED)
                .build();

        DefaultIdempotencyPolicyRegistry registry =
                new DefaultIdempotencyPolicyRegistry(
                        List.of(windowed),
                        "api-submit");

        assertThat(registry.resolve("api-submit", null).getMode())
                .isEqualTo(IdempotencyMode.WINDOWED);
        assertThat(registry.resolve(null, null).getName())
                .isEqualTo("api-submit");
    }
}
