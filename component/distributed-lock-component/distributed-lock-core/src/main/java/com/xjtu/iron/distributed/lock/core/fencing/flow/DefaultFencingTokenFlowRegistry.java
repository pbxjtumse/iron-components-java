package com.xjtu.iron.distributed.lock.core.fencing.flow;

import com.xjtu.iron.distributed.lock.core.fencing.coordinator.FencingTokenMode;

import java.util.*;

/** 默认 fencing token flow 注册表。 */
public final class DefaultFencingTokenFlowRegistry implements FencingTokenFlowRegistry {

    private final Map<FencingTokenMode, FencingTokenFlow> flows;

    public DefaultFencingTokenFlowRegistry(Collection<? extends FencingTokenFlow> flows) {
        EnumMap<FencingTokenMode, FencingTokenFlow> mapped = new EnumMap<>(FencingTokenMode.class);
        if (flows != null) {
            for (FencingTokenFlow flow : flows) {
                Objects.requireNonNull(flow, "fencing token flow must not be null");
                FencingTokenMode mode = Objects.requireNonNull(flow.mode(), "fencing token flow mode must not be null");
                FencingTokenFlow previous = mapped.putIfAbsent(mode, flow);
                if (previous != null) {
                    throw new IllegalArgumentException("duplicate fencing token flow: " + mode);
                }
            }
        }
        this.flows = Collections.unmodifiableMap(mapped);
    }

    @Override
    public Optional<FencingTokenFlow> findFlow(FencingTokenMode mode) {
        if (mode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(flows.get(mode));
    }

    @Override
    public Set<FencingTokenMode> modes() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(flows.keySet()));
    }
}
