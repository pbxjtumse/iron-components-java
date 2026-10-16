package com.xjtu.iron.distributed.lock.core.fencing;

import com.xjtu.iron.distributed.lock.core.fencing.registry.DefaultFencingTokenProviderRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultFencingTokenProviderRegistryTest {

    @Test
    void shouldFindProviderOnlyByExplicitName() {
        com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider jdbc = provider("mybatis-sequence");
        DefaultFencingTokenProviderRegistry registry =
                new DefaultFencingTokenProviderRegistry(List.of(jdbc));

        assertThat(registry.findProvider("mybatis-sequence")).contains(jdbc);
        assertThat(registry.findProvider("missing")).isEmpty();
        assertThat(registry.providerNames()).containsExactly("mybatis-sequence");
    }

    @Test
    void blankProviderNameShouldNotImplicitlySelectSoleProvider() {
        DefaultFencingTokenProviderRegistry registry =
                new DefaultFencingTokenProviderRegistry(List.of(provider("mybatis-sequence")));

        assertThat(registry.findProvider(null)).isEmpty();
        assertThat(registry.findProvider("   ")).isEmpty();
    }

    @Test
    void duplicateProviderNameShouldFailFast() {
        assertThatThrownBy(() -> new DefaultFencingTokenProviderRegistry(
                List.of(provider("jdbc"), provider("jdbc"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate fencing token provider");
    }

    private com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider provider(String name) {
        return new com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider() {
            @Override public String providerName() { return name; }
            @Override public boolean supports(com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenRequest request) { return true; }
            @Override public com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenResponse nextToken(com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenRequest request) {
                return com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenResponse.issued(1L);
            }
        };
    }
}
