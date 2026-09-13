package com.xjtu.iron.observability.core.noop;

import com.xjtu.iron.observability.api.enums.TraceProviderType;
import com.xjtu.iron.observability.api.tracing.ITraceService;
import com.xjtu.iron.observability.api.tracing.context.TraceProviderContext;
import com.xjtu.iron.observability.api.tracing.provider.TraceProvider;

public class NoopTraceProviderImpl implements TraceProvider {

    @Override
    public TraceProviderType type() {
        return TraceProviderType.NOOP;
    }

    @Override
    public ITraceService createTraceService(TraceProviderContext context) {
        return new NoopTraceServiceImpl();
    }
}
