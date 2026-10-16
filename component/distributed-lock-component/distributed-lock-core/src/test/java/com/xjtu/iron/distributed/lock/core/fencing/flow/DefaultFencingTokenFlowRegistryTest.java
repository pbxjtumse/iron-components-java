package com.xjtu.iron.distributed.lock.core.fencing.flow;

import com.xjtu.iron.distributed.lock.core.fencing.coordinator.FencingTokenMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultFencingTokenFlowRegistryTest {

    @Test
    void shouldFindFlowByMode() {
        FencingTokenFlow flow = new NoFencingTokenFlow();
        DefaultFencingTokenFlowRegistry registry = new DefaultFencingTokenFlowRegistry(List.of(flow));

        assertThat(registry.findFlow(FencingTokenMode.NONE)).contains(flow);
        assertThat(registry.modes()).containsExactly(FencingTokenMode.NONE);
    }

    @Test
    void shouldRejectDuplicateMode() {
        assertThatThrownBy(() -> new DefaultFencingTokenFlowRegistry(List.of(
                new NoFencingTokenFlow(), new NoFencingTokenFlow())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate fencing token flow");
    }

    @Test
    void getRequiredShouldFailFastWhenModeMissing() {
        DefaultFencingTokenFlowRegistry registry = new DefaultFencingTokenFlowRegistry(List.of());

        assertThatThrownBy(() -> registry.getRequired(FencingTokenMode.NATIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fencing token flow not found");
    }
}
