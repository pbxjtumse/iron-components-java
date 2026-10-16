package com.xjtu.iron.foundation.context;

import java.util.List;

/**
 * 标准字符串上下文编解码器。
 */
public final class StandardContextCodec implements ContextCodec {

    private final List<ContextKey<String>> keys;

    public StandardContextCodec() {
        this(List.of(
                StandardContextKeys.REQUEST_ID,
                StandardContextKeys.CORRELATION_ID,
                StandardContextKeys.TENANT_ID,
                StandardContextKeys.OPERATOR_ID));
    }

    public StandardContextCodec(List<ContextKey<String>> keys) {
        this.keys = List.copyOf(keys);
    }

    @Override
    public ExecutionContext read(ContextCarrier carrier) {
        ExecutionContextBuilder builder = ExecutionContext.builder();
        for (ContextKey<String> key : keys) {
            builder.put(key, carrier.get(key.getName()));
        }
        return builder.build();
    }

    @Override
    public void write(ExecutionContext context, ContextCarrier carrier) {
        for (ContextKey<String> key : keys) {
            context.get(key).ifPresent(value -> carrier.put(key.getName(), value));
        }
    }
}
