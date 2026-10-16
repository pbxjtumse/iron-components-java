package com.xjtu.iron.distributed.lock.core.fencing.flow;

import com.xjtu.iron.distributed.lock.core.fencing.coordinator.FencingTokenMode;

import java.util.Optional;
import java.util.Set;

/** fencing token 执行流程策略注册表。 */
public interface FencingTokenFlowRegistry {

    Optional<FencingTokenFlow> findFlow(FencingTokenMode mode);

    default FencingTokenFlow getRequired(FencingTokenMode mode) {
        return findFlow(mode)
                .orElseThrow(() -> new IllegalArgumentException("fencing token flow not found: " + mode));
    }

    Set<FencingTokenMode> modes();
}
